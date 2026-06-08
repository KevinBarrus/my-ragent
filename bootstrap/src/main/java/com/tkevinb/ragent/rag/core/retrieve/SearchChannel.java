package com.tkevinb.ragent.rag.core.retrieve;

import com.tkevinb.ragent.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * 检索通道接口 — 所有检索通道的统一抽象
 * <p>
 * 向量通道、关键词通道、后续的 MCP 通道等都实现此接口，
 * 新增通道只需加一个实现类，RetrievalEngine 无需改动。
 */
public interface SearchChannel {

    /**
     * @param query 用户问题
     * @param topK  返回数量
     * @return 命中的文档片段列表（按相关度降序）
     */
    List<RetrievedChunk> search(String query, int topK);
}
