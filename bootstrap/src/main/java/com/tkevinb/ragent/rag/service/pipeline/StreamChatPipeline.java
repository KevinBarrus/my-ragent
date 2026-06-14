package com.tkevinb.ragent.rag.service.pipeline;

import com.tkevinb.ragent.framework.convention.ChatMessage;
import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.infra.chat.LLMService;
import com.tkevinb.ragent.infra.chat.StreamCancellationHandle;
import com.tkevinb.ragent.rag.config.AblationProperties;
import com.tkevinb.ragent.rag.core.guidance.IntentGuidanceService;
import com.tkevinb.ragent.rag.core.intent.IntentResolver;
import com.tkevinb.ragent.rag.core.memory.ConversationMemoryService;
import com.tkevinb.ragent.rag.enums.IntentKind;
import com.tkevinb.ragent.rag.core.prompt.PromptContext;
import com.tkevinb.ragent.rag.core.prompt.PromptTemplateLoader;
import com.tkevinb.ragent.rag.core.prompt.RAGPromptService;
import com.tkevinb.ragent.rag.core.retrieve.RetrievalEngine;
import com.tkevinb.ragent.rag.core.rewrite.QueryRewriteService;
import com.tkevinb.ragent.rag.core.rewrite.RewriteResult;
import com.tkevinb.ragent.rag.dto.RetrievalContext;
import com.tkevinb.ragent.rag.dto.SubQuestionIntent;
import com.tkevinb.ragent.rag.service.handler.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.List;

/**
 * 流式对话流水线
 * <p>
 * 承载从 RAGChatServiceImpl 提取的业务编排逻辑：
 * 记忆加载 -> 改写拆分 -> 意图解析 -> 歧义引导 -> 系统响应 / 检索 -> Prompt 组装 -> 流式输出
 * <p>
 * 支持消融实验：通过 AblationProperties 控制各阶段开关。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamChatPipeline {

    private final ConversationMemoryService memoryService;
    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final RetrievalEngine retrievalEngine;
    private final RAGPromptService promptService;
    private final LLMService llmService;
    private final StreamTaskManager taskManager;
    private final PromptTemplateLoader templateLoader;
    private final AblationProperties ablation;
    private final IntentGuidanceService guidanceService;
    private final AgentCore agentCore;

    /**
     * 执行流式对话管道
     */
    public void execute(StreamChatContext ctx) {
        StopWatch sw = new StopWatch("RAG Pipeline");
        long start = System.currentTimeMillis();

        //记录消融实验配置
        String tags = String.format("rewrite=%s,intent=%s,split=%s",
                ablation.isQueryRewriteEnabled(), ablation.isIntentEnabled(), ablation.isQuestionSplitEnabled());
        ctx.setAblationTags(tags);
        log.info("[Ablation] Pipeline 开始 - {}", tags);

        //1.加载历史记忆
        sw.start("loadMemory");
        loadMemory(ctx);
        sw.stop();

        //2.重写问题（受 feature flag 控制）
        sw.start("rewriteQuestion");
        rewriteQuestion(ctx);
        sw.stop();

        //3.意图解析（受 feature flag 控制）
        sw.start("parseIntent");
        parseIntent(ctx);
        sw.stop();

        //4.歧义引导，如果有歧义，就再次发起对话
        if(handleGuideAmbiguity(ctx)) {
            ctx.setTotalMs(System.currentTimeMillis() - start);
            logTiming(ctx, sw);
            return;
        }

        //5.判断是否为纯系统问题，如果是则不需要检索
        if(handleSystemOnlyQuestion(ctx)) {
            ctx.setTotalMs(System.currentTimeMillis() - start);
            logTiming(ctx, sw);
            return;
        }

        //6-7. Agent 循环（替代固定 retrieve + response）
        sw.start("agent");
        agentCore.execute(ctx, ctx.getSubIntents(), ctx.getCallback());
        sw.stop();

        ctx.setTotalMs(System.currentTimeMillis() - start);
        logTiming(ctx, sw);
    }

    private void logTiming(StreamChatContext ctx, StopWatch sw) {

        log.info("[Ablation] Pipeline 完成 - {} | totalMs={} | {}",
                ctx.getAblationTags(), ctx.getTotalMs(), sw.prettyPrint());
    }

    //加载本次对话之前的历史记录，同时将当前用户问题写入数据库
    private void loadMemory(StreamChatContext ctx) {
        long t = System.currentTimeMillis();
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(), ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history);
        ctx.setLoadMemoryMs(System.currentTimeMillis() - t);
    }

    //改写为子问题+重写问题
    private void rewriteQuestion(StreamChatContext ctx) {
        long t = System.currentTimeMillis();

        if (!ablation.isQueryRewriteEnabled()) {
            // 关闭改写：直接使用原问题，不拆分
            ctx.setRewriteResult(new RewriteResult(ctx.getQuestion(), List.of()));
            ctx.setRewriteMs(System.currentTimeMillis() - t);
            return;
        }

        RewriteResult result = queryRewriteService.rewriteWithSplit(
                ctx.getQuestion(), ctx.getHistory()
        );
        ctx.setRewriteResult(result);
        ctx.setRewriteMs(System.currentTimeMillis() - t);
    }

    //进行意图解析，采用树状结构意图节点
    private void parseIntent(StreamChatContext ctx) {
        long t = System.currentTimeMillis();

        if (!ablation.isIntentEnabled()) {
            // 关闭意图识别：以重写后的问题作为唯一意图，走 KB 检索
            SubQuestionIntent defaultIntent = new SubQuestionIntent(
                    ctx.getRewriteResult().rewrittenQuestion(), List.of());
            ctx.setSubIntents(List.of(defaultIntent));
            ctx.setIntentMs(System.currentTimeMillis() - t);
            return;
        }

        List<SubQuestionIntent> subIntents = intentResolver.resolve(ctx.getRewriteResult());
        ctx.setSubIntents(subIntents);
        ctx.setIntentMs(System.currentTimeMillis() - t);
    }

    //意图模糊时反问用户，不再硬往下走
    private boolean handleGuideAmbiguity(StreamChatContext ctx) {
        String prompt = guidanceService.check(ctx.getQuestion(), ctx.getSubIntents());
        if (prompt == null) {
            return false; // 意图明确，继续
        }
        log.info("意图模糊，反问用户: {}", prompt);
        ctx.getCallback().onContent(prompt);
        ctx.getCallback().onComplete();
        return true; // 短路，不检索
    }

    //判断是否为纯系统问题（无需检索），直接 LLM 回答
    private boolean handleSystemOnlyQuestion(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = ctx.getSubIntents();
        if (subIntents != null && !subIntents.isEmpty()) {
            boolean hasKbOrMcp = subIntents.stream()
                    .flatMap(si -> si.nodeScores().stream())
                    .anyMatch(ns -> ns.getNode() != null &&
                            (ns.getNode().getKind() == IntentKind.KB ||
                             ns.getNode().getKind() == IntentKind.MCP));
            if (hasKbOrMcp) {
                return false; // 有 KB 或 MCP 意图，走检索/工具调用
            }
        }
        log.info("无 KB/MCP 意图匹配，走纯 LLM 回答");
        RagResponse(ctx, RetrievalContext.empty());
        return true;
    }

    //进行检索
    private RetrievalContext retrieve(StreamChatContext ctx) {
        long t = System.currentTimeMillis();
        RetrievalContext result = retrievalEngine.retrieve(ctx.getSubIntents(), 5);
        ctx.setRetrieveMs(System.currentTimeMillis() - t);
        return result;
    }

    //检索为空的处理手段
    private boolean handleEmptySearchResult(StreamChatContext ctx, RetrievalContext c) {
        if (!c.isEmpty()) return false;
        ctx.getCallback().onContent("未检索到与问题相关的文档内容。");
        ctx.getCallback().onComplete();
        return true;
    }

    //构建流式响应
    private void RagResponse(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        long t = System.currentTimeMillis();

        PromptContext promptCtx = PromptContext.builder()
                .question(ctx.getRewriteResult().rewrittenQuestion())
                .kbContext(retrievalCtx.getKbContext())
                .mcpContext(retrievalCtx.getMcpContext())
                .intentChunks(retrievalCtx.getIntentChunks())
                .build();

        List<ChatMessage> messages = promptService.buildStructuredPrompt(
                promptCtx, ctx.getHistory(),
                ctx.getRewriteResult().rewrittenQuestion(),
                ctx.getRewriteResult().subQuestions()
        );

        ChatRequest req = ChatRequest.builder()
                .messages(messages)
                .thinking(ctx.isDeepThinking())
                .temperature(0D)
                .build();

        StreamCancellationHandle handle = llmService.streamChat(req, ctx.getCallback());
        taskManager.bindHandle(ctx.getTaskId(), handle);
        ctx.setLlmMs(System.currentTimeMillis() - t);
    }
}
