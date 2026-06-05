package com.tkevinb.ragent.rag.core.retrieve;

import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存向量存储实现（MVP 版本，用于快速跑通链路）
 * <p>
 * TODO 后续替换为 MilvusVectorStoreService 或 PgVectorStoreService
 */
@Service  // 跑通后再启用
public class InMemoryVectorStoreService implements VectorStoreService {

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        // TODO 实现真实的向量检索：query → embedding → 向量库相似度搜索
        // 当前返回空列表，让 Pipeline 走到 handleEmptyRetrieval 分支
        return new ArrayList<>();
    }
}
