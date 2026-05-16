package com.tkevinb.ragent.infra.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 模型提供商枚举
 * 统一管理提供商名称，避免散落的字符串常量
 */
@Getter
@RequiredArgsConstructor
public enum ModelProvider {

    /**
     * Ollama 本地模型服务
     */
    OLLAMA("ollama"),

    /**
     * 阿里云百炼大模型平台
     */
    BAI_LIAN("bailian"),

    /**
     * 硅基流动 AI 模型服务
     */
    SILICON_FLOW("siliconflow"),

    /**
     * OpenAI 服务
     */
    OPEN_AI("openai"),

    /**
     * Anthropic 服务
     */
    ANTHROPIC("anthropic"),

    /**
     * 空实现，用于测试或占位
     */
    NOOP("noop");

    /**
     * 每个模型提供商的 id
     */
    private final String id;

    /**
     * 判断字符串与枚举
     * @param provider
     * @return
     */
    public boolean matches(String provider) {
        return provider != null && provider.equalsIgnoreCase(id);
    }
}