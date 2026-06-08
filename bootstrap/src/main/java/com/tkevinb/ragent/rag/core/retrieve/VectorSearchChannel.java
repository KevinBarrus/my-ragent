package com.tkevinb.ragent.rag.core.retrieve;

import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量检索通道 — 包装 PgVectorStoreService
 */
@Component
public class VectorSearchChannel implements SearchChannel {

    private final VectorStoreService vectorStoreService;

    public VectorSearchChannel(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        return vectorStoreService.search(query, topK);
    }
}
