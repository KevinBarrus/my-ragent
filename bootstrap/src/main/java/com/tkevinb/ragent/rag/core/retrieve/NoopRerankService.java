package com.tkevinb.ragent.rag.core.retrieve;

import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Noop Rerank 降级 — 硅基流动不可用时，直接返回原始排序
 */
@Slf4j
@Component
@ConditionalOnMissingBean(BgeRerankService.class)
public class NoopRerankService extends BgeRerankService {

    public NoopRerankService() {
        super("");
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates) {
        return candidates;
    }
}
