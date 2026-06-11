package com.tkevinb.ragent.rag.core.tool;

import java.util.Map;

/**
 * 工具接口 — 所有工具的统一抽象
 * <p>
 * 当前实现返回模拟数据；后续可通过 Tavily API、gRPC 等方式接入真实数据。
 */
public interface Tool {

    /** 工具唯一标识 */
    String name();

    /** 工具描述（给 LLM 看的，用于决定调哪个工具） */
    String description();

    /** 参数描述（JSON Schema 格式，供后续参数提取使用） */
    String parameterSchema();

    /**
     * 执行业务逻辑
     * @param params 调用参数（由 LLM 提取或用户指定）
     * @return 执行结果文本
     */
    String execute(Map<String, Object> params);
}
