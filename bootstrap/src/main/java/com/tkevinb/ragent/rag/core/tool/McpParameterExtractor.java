package com.tkevinb.ragent.rag.core.tool;

import cn.hutool.core.util.StrUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkevinb.ragent.framework.convention.ChatMessage;
import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.infra.chat.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 驱动的 MCP 参数提取器
 * <p>
 * 将工具定义 + 用户问题发给 LLM，让 LLM 返回 JSON 格式的工具参数。
 */
@Slf4j
@Service
public class McpParameterExtractor {

    private final LLMService llmService;

    public McpParameterExtractor(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * 从用户问题中提取工具参数
     * @param question 用户问题
     * @param tool     要调用的工具
     * @return 工具参数 Map
     */
    public Map<String, Object> extract(String question, Tool tool) {
        String prompt = buildPrompt(tool);
        ChatRequest req = ChatRequest.builder()
                .messages(java.util.List.of(
                        ChatMessage.system(prompt),
                        ChatMessage.user(question)
                ))
                .temperature(0.1)
                .build();

        try {
            String raw = llmService.chat(req);
            raw = raw.trim();
            if (raw.startsWith("{") && raw.endsWith("}")) {
                JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                Map<String, Object> params = new HashMap<>();
                for (var key : json.keySet()) {
                    var el = json.get(key);
                    params.put(key, el.isJsonPrimitive() ? el.getAsString() : el.toString());
                }
                log.info("MCP 参数提取: tool={}, question='{}', params={}", tool.name(), question, params);
                return params;
            }
        } catch (Exception e) {
            log.warn("MCP 参数提取失败, tool={}, question='{}'", tool.name(), question, e);
        }
        return new HashMap<>();
    }

    private String buildPrompt(Tool tool) {
        return """
                你是一个参数提取助手。请从用户问题中提取调用工具所需的参数。
                
                工具名称：%s
                工具描述：%s
                参数格式：%s
                
                请只输出一个 JSON 对象，不要加任何其他文字。
                """.formatted(tool.name(), tool.description(), tool.parameterSchema());
    }
}
