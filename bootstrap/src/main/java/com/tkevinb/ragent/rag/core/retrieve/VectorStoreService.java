package com.tkevinb.ragent.rag.core.retrieve;

import com.tkevinb.ragent.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * 向量存储服务接口
 * <p>
 * 抽象向量检索操作，屏蔽底层向量库差异（Milvus / PGVector / 内存模拟）
 */
public interface VectorStoreService {

    /**
     * 向量相似度检索
     *
     * @param query 查询文本（调用方负责 embedding）
     * @param topK  返回 Top-K 个最相似的文档片段
     * @return 命中的文档片段列表
     */
    List<RetrievedChunk> search(String query, int topK);
}
