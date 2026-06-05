package com.tkevinb.ragent.rag.service.pipeline;

import com.tkevinb.ragent.framework.convention.ChatMessage;
import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.infra.chat.LLMService;
import com.tkevinb.ragent.infra.chat.StreamCancellationHandle;
import com.tkevinb.ragent.rag.core.intent.IntentResolver;
import com.tkevinb.ragent.rag.core.memory.ConversationMemoryService;
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

import java.util.List;

/**
 * 流式对话流水线
 * <p>
 * 承载从 RAGChatServiceImpl 提取的业务编排逻辑：
 * 记忆加载 -> 改写拆分 -> 意图解析 -> 歧义引导 -> 系统响应 / 检索 -> Prompt 组装 -> 流式输出
 * <p>
 * 流水线模式：通过私有方法 + boolean 返回值（handleXxx 返回 true 表示已处理并短路）
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

    /**
     * 执行流式对话管道
     */
    public void execute(StreamChatContext ctx) {
        //1.加载历史记忆
        loadMemory(ctx);

        //2.重写问题
        rewriteQuestion(ctx);

        //3.意图解析
        parseIntent(ctx);

        //4.歧义引导，如果有歧义，就再次发起对话
        if(handleGuideAmbiguity(ctx)) {
            return;
        }

        //5.判断是否为纯系统问题，如果是则不需要检索
        if(handleSystemOnlyQuestion(ctx)) {
            return;
        }

        //6.判断检索是否为空，如果为空，则返回纯系统响应
        RetrievalContext retrievalCtx = retrieve(ctx);
        if(handleEmptySearchResult(ctx, retrievalCtx)) {
            return;
        }

        //7.检索
        RagResponse(ctx, retrievalCtx);
    }

    //加载本次对话之前的历史记录，同时将当前用户问题写入数据库
    private void loadMemory(StreamChatContext ctx) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(), ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history);
    }

    //改写为子问题+重写问题
    private void rewriteQuestion(StreamChatContext ctx) {
        RewriteResult result = queryRewriteService.rewriteWithSplit(
                ctx.getQuestion(), ctx.getHistory()
        );
        ctx.setRewriteResult(result);
    }

    //进行意图解析，采用树状结构意图节点
    private void parseIntent(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = intentResolver.resolve(ctx.getRewriteResult());
        ctx.setSubIntents(subIntents);
    }

    //MVP阶段暂时跳过
    private boolean handleGuideAmbiguity(StreamChatContext ctx) {
        log.info("处理歧义引导");
        return false;
    }

    //MVP阶段暂时跳过
    private boolean handleSystemOnlyQuestion(StreamChatContext ctx) {
        log.info("处理纯系统问题");
        return false;
    }

    //进行检索
    private RetrievalContext retrieve(StreamChatContext ctx) {
        return retrievalEngine.retrieve(ctx.getSubIntents(), 5);
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
                .temperature(0D) //当前只有kb，不需要发散，后续加入MCP需要调整
                .build();

        StreamCancellationHandle handle = llmService.streamChat(req, ctx.getCallback());
        taskManager.bindHandle(ctx.getTaskId(), handle);
    }

}
