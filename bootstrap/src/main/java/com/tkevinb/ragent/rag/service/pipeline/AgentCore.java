package com.tkevinb.ragent.rag.service.pipeline;

import cn.hutool.core.util.StrUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkevinb.ragent.framework.convention.ChatMessage;
import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import com.tkevinb.ragent.infra.chat.LLMService;
import com.tkevinb.ragent.infra.chat.StreamCallBack;
import com.tkevinb.ragent.infra.chat.StreamCancellationHandle;
import com.tkevinb.ragent.rag.core.retrieve.*;
import com.tkevinb.ragent.rag.core.tool.McpParameterExtractor;
import com.tkevinb.ragent.rag.core.tool.Tool;
import com.tkevinb.ragent.rag.core.tool.ToolRegistry;
import com.tkevinb.ragent.rag.dto.RetrievalContext;
import com.tkevinb.ragent.rag.dto.SubQuestionIntent;
import com.tkevinb.ragent.rag.service.handler.StreamTaskManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ReAct Agent — LLM 决策 + 自我纠偏
 */
@Slf4j
@Service
public class AgentCore {

    private static final int MAX_ROUNDS = 5;

    private final RetrievalEngine retrievalEngine;
    private final LLMService llmService;
    private final StreamTaskManager taskManager;
    private final ToolRegistry toolRegistry;
    private final McpParameterExtractor mcpExtractor;
    private final BgeRerankService rerankService;

    public AgentCore(RetrievalEngine retrievalEngine, LLMService llmService,
                     StreamTaskManager taskManager, ToolRegistry toolRegistry,
                     McpParameterExtractor mcpExtractor, BgeRerankService rerankService) {
        this.retrievalEngine = retrievalEngine;
        this.llmService = llmService;
        this.taskManager = taskManager;
        this.toolRegistry = toolRegistry;
        this.mcpExtractor = mcpExtractor;
        this.rerankService = rerankService;
    }

    public void execute(StreamChatContext ctx, List<SubQuestionIntent> subIntents, StreamCallBack callback) {
        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.system(buildSystemPrompt()));
        history.add(ChatMessage.user(ctx.getQuestion()));

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            log.info("[Agent] 第 {} 轮...", round);

            String decision = llmService.chat(ChatRequest.builder().messages(history).temperature(0.1).build());
            log.info("[Agent] LLM 决策: {}", StrUtil.maxLength(decision, 200));

            Action action = parse(decision);
            if (action == null) {
                // LLM 未输出合法 JSON → 清理非法字符后输出
                String cleaned = decision.replaceAll("[{}]", "").replaceAll("\"[^\"]*\"", "").trim();
                if (cleaned.length() < 5) cleaned = "抱歉，我暂时无法回答这个问题。";
                callback.onContent(cleaned);
                callback.onComplete();
                return;
            }

            if ("answer".equals(action.type)) {
                callback.onContent(action.content);
                callback.onComplete();
                return;
            }

            // 执行动作 + 返回包含质量信息的观察结果
            String observation = executeAction(action, ctx.getQuestion(), subIntents);

            history.add(ChatMessage.assistant(decision));
            history.add(ChatMessage.system("【观察结果】\n" + observation));
            log.info("[Agent] 第 {} 轮 → {}('{}') → {}", round, action.type, action.content,
                    StrUtil.maxLength(observation, 120));
        }

        callback.onContent("抱歉，我暂时无法完成这个任务（超出最大思考轮数）。");
        callback.onComplete();
    }

    private String buildSystemPrompt() {
        String tools = toolRegistry.all().isEmpty() ? "" :
                toolRegistry.all().entrySet().stream()
                        .map(e -> "  - " + e.getValue().name() + ": " + e.getValue().description())
                        .collect(Collectors.joining("\n"));
        return """
                你是企业内部知识助手。你要通过"思考→行动→观察"的循环来收集信息，然后回答用户问题。
                
                可用的行动：
                  - search_kb: 搜索企业内部知识库。输入搜索关键词，返回文档片段及其相关度分数。
                %s
                
                工作流程：
                第1步：思考用户需要什么信息，输出搜索行动
                第2步：观察搜索结果（注意检查相关度分数，<0.7说明可能没搜到最相关的内容）
                第3步：如果相关度偏低或信息不全 → 换更精准的关键词重新搜索
                第4步：如果信息足够 → 输出最终回答
                
                优先级规则：
                - 工具返回的实时数据优先于知识库内容
                - 知识库内容与工具返回矛盾时，以工具返回为准
                - 如果工具参数不足（如需要员工工号但用户没提供），直接反问用户补充，不要反复调工具
                
                格式要求（每次只输出一个JSON，不要有其他文字）：
                {"tool": "search_kb", "query": "搜索关键词"}
                {"answer": "你的最终回答"}
                """.formatted(tools);
    }

    private Action parse(String text) {
        if (text == null || text.isBlank()) return null;
        text = text.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) return null;
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (json.has("answer")) return new Action("answer", json.get("answer").getAsString());
            if (json.has("tool")) return new Action(json.get("tool").getAsString(),
                    json.has("query") ? json.get("query").getAsString() : "");
        } catch (Exception ignored) {}
        return null;
    }

    /** 执行动作，返回含质量信息的观察结果 */
    private String executeAction(Action action, String question, List<SubQuestionIntent> subIntents) {
        if ("search_kb".equals(action.type)) {
            String query = action.content != null && !action.content.isBlank() ? action.content : question;
            // 用 LLM 指定的 query 创建临时意图，确保检索使用 LLM 的关键词而非固定 query
            List<SubQuestionIntent> dynamicIntents = subIntents.stream()
                    .map(si -> new SubQuestionIntent(query, si.nodeScores()))
                    .toList();
            RetrievalContext rc = retrievalEngine.retrieve(dynamicIntents, 5);
            List<RetrievedChunk> chunks = new ArrayList<>();
            if (rc.getIntentChunks() != null) {
                for (var entry : rc.getIntentChunks().entrySet()) chunks.addAll(entry.getValue());
            }
            if (chunks.isEmpty()) return "检索结果为空，请尝试其他关键词。";

            float maxScore = chunks.stream().map(RetrievedChunk::getScore).max(Float::compare).orElse(0f);
            chunks = rerankService.rerank(query, chunks);

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("共命中 %d 篇文档，最高相关度 %.2f。\n", chunks.size(), maxScore));
            sb.append("---\n");
            for (int i = 0; i < chunks.size(); i++) {
                sb.append(String.format("文档%d(相关度%.2f): %s\n",
                        i + 1, chunks.get(i).getScore(), chunks.get(i).getText()));
            }
            return sb.toString();
        }
        Tool tool = toolRegistry.get(action.type);
        if (tool != null) {
            Map<String, Object> params = mcpExtractor.extract(question, tool);
            try {
                String result = tool.execute(params);
                return "工具调用成功：\n" + result;
            } catch (Exception e) {
                return "工具调用失败：" + e.getMessage();
            }
        }
        return "未知工具：" + action.type;
    }

    private record Action(String type, String content) {}
}
