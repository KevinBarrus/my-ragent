package com.tkevinb.ragent.rag.dto;

import cn.hutool.core.util.StrUtil;
import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 检索上下文
 * <p>
 * 统一承载知识库检索和 MCP 工具调用的结果，供 Prompt 构建阶段使用。
 * MVP 阶段仅填充 kbContext 和 intentChunks，mcpContext 留空。
 */
@Data
@Builder
public class RetrievalContext {

    /**
     * 知识库检索结果（已格式化为文本）
     */
    @Builder.Default
    private String kbContext = "";

    /**
     * MCP 工具调用结果（已格式化为文本）
     * MVP 阶段为空
     */
    @Builder.Default
    private String mcpContext = "";

    /**
     * 意图节点 ID → 对应检索到的文档片段列表
     */
    @Builder.Default
    private Map<String, List<RetrievedChunk>> intentChunks = Collections.emptyMap();

    // ========== 工具方法 ==========

    public boolean hasKb() {
        return StrUtil.isNotBlank(kbContext);
    }

    public boolean hasMcp() {
        return StrUtil.isNotBlank(mcpContext);
    }

    public boolean isEmpty() {
        return !hasKb() && !hasMcp();
    }

    public static RetrievalContext empty() {
        return RetrievalContext.builder().build();
    }
}
