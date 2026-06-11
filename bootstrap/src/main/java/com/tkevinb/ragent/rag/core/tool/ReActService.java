package com.tkevinb.ragent.rag.core.tool;

import cn.hutool.core.util.StrUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkevinb.ragent.framework.convention.ChatMessage;
import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.infra.chat.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.tkevinb.ragent.rag.constant.RAGConstant.REACT_SYSTEM_PROMPT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct Agent 循环
 * <p>
 * 独立的 Agent 执行器，不依赖现有 Pipeline。
 * Think → Act → Observe 循环，最多执行 8 轮。
 */
@Slf4j
@Service
public class ReActService {

    private static final int MAX_ROUNDS = 8;

    private final LLMService llmService;
    private final ToolRegistry toolRegistry;

    public ReActService(LLMService llmService, ToolRegistry toolRegistry) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行 ReAct 循环
     * @param userQuestion 用户问题
     * @return 最终回答
     */
    public String execute(String userQuestion) {
        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.system(buildSystemPrompt()));
        history.add(ChatMessage.user(userQuestion));

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            log.info("[ReAct] 第 {} 轮思考...", round);

            ChatRequest req = ChatRequest.builder()
                    .messages(history)
                    .temperature(0.1)
                    .build();

            String response = llmService.chat(req);

            // 尝试解析为 JSON 工具调用
            ToolCall call = tryParseToolCall(response);

            if (call == null) {
                // 不是工具调用 → 最终回答
                log.info("[ReAct] 第 {} 轮完成，得到最终回答", round);
                return response;
            }

            log.info("[ReAct] 第 {} 轮 → 调用工具: {}({})", round, call.tool, call.params);

            Tool tool = toolRegistry.get(call.tool);
            if (tool == null) {
                history.add(ChatMessage.assistant(response));
                history.add(ChatMessage.user("工具 '" + call.tool + "' 不存在，请使用可用工具"));
                continue;
            }

            try {
                String result = tool.execute(call.params);
                log.info("[ReAct] 工具返回: {}", StrUtil.maxLength(result, 200));
                history.add(ChatMessage.assistant(response));
                history.add(ChatMessage.user("工具执行结果:\n" + result));
            } catch (Exception e) {
                log.warn("[ReAct] 工具执行失败: {}", e.getMessage());
                history.add(ChatMessage.assistant(response));
                history.add(ChatMessage.user("工具执行失败: " + e.getMessage()));
            }
        }

        return "抱歉，我暂时无法完成这个任务（超出最大思考轮数）。";
    }

    /** 构建 ReAct System Prompt */
    private String buildSystemPrompt() {
        return REACT_SYSTEM_PROMPT.replace("{tools}", toolRegistry.getToolDescriptions());
    }

    /** 尝试将 LLM 输出解析为工具调用 */
    private ToolCall tryParseToolCall(String text) {
        if (text == null || text.isBlank()) return null;
        text = text.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) return null;

        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (!json.has("tool")) return null;
            String tool = json.get("tool").getAsString();
            if (StrUtil.isBlank(tool)) return null;

            Map<String, Object> params = new HashMap<>();
            if (json.has("params") && json.get("params").isJsonObject()) {
                var pObj = json.getAsJsonObject("params");
                for (var key : pObj.keySet()) {
                    params.put(key, pObj.get(key).getAsString());
                }
            }
            return new ToolCall(tool, params);
        } catch (Exception e) {
            return null;
        }
    }

    private record ToolCall(String tool, Map<String, Object> params) {}
}
