package com.tkevinb.ragent.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 内存关键词检索引擎（MVP 消融实验用）
 * <p>
 * 启动时从 t_knowledge_chunk 表加载文档片段，
 * 检索时对 query 做关键词匹配 + TF 打分。
 * <p>
 * TODO 后续替换为 MilvusVectorStoreService 或 PgVectorStoreService
 */
@Slf4j
//@Service  // 已被 PgVectorStoreService 替代，保留备用
public class InMemoryVectorStoreService implements VectorStoreService {

    private final JdbcTemplate jdbcTemplate;

    /** 内存中的文档片段列表 */
    private List<ChunkEntry> chunks = List.of();

    public InMemoryVectorStoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void loadChunks() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, kb_id, doc_id, content FROM t_knowledge_chunk WHERE enabled = 1 AND deleted = 0"
            );
            if (CollUtil.isEmpty(rows)) {
                log.warn("InMemoryVectorStore: t_knowledge_chunk 表无数据，检索将返回空");
                this.chunks = List.of();
                return;
            }
            this.chunks = rows.stream()
                    .map(row -> new ChunkEntry(
                            ((Number) row.get("id")).longValue(),
                            ((Number) row.get("kb_id")).longValue(),
                            ((Number) row.get("doc_id")).longValue(),
                            (String) row.get("content")
                    ))
                    .toList();
            log.info("InMemoryVectorStore: 已加载 {} 条文档片段", chunks.size());
        } catch (Exception e) {
            log.error("InMemoryVectorStore: 加载文档片段失败", e);
            this.chunks = List.of();
        }
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        if (CollUtil.isEmpty(chunks) || query == null || query.isBlank()) {
            return List.of();
        }

        // 1. 分词：按空格/标点拆分 query
        Set<String> queryTokens = tokenize(query);

        // 2. 对每个 chunk 计算关键词匹配得分
        List<ScoredChunk> scored = new ArrayList<>();
        for (ChunkEntry chunk : chunks) {
            Set<String> chunkTokens = tokenize(chunk.content);
            // 取交集：命中的关键词
            Set<String> intersection = new HashSet<>(queryTokens);
            intersection.retainAll(chunkTokens);
            if (intersection.isEmpty()) {
                continue;
            }
            // 得分 = 命中关键词数 / query 总词数（TF 简化版）
            double score = (double) intersection.size() / Math.max(queryTokens.size(), 1);
            // 加入位置奖励：命中的词越多，分数越高
            score = Math.min(1.0, score + (intersection.size() * 0.05));
            scored.add(new ScoredChunk(chunk, score));
        }

        // 3. 按分数降序，取 topK
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<ScoredChunk> top = scored.subList(0, Math.min(topK, scored.size()));

        log.info("InMemoryVectorStore: query='{}', 命中 {} 条, 返回 {} 条, 最高分={}",
                query, scored.size(), top.size(),
                top.isEmpty() ? 0 : String.format("%.2f", top.get(0).score));

        return top.stream()
                .map(s -> RetrievedChunk.builder()
                        .text(s.chunk.content)
                        .score((float) s.score)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 简单中文分词：按标点拆分 + 二元组滑动窗口
     * <p>
     * 例："请假需要什么材料" → ["请假", "需要", "什么", "材料", "请假需要", "需要什么", "什么材料"]
     * 这样既能匹配单句中的关键词，也能匹配短词组。
     */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        // 1. 按标点拆分为短语
        String[] phrases = text.split("[，。！？、；：（）【】《》\\s,.!?;:()\\-]+");
        Set<String> tokens = new HashSet<>();
        for (String phrase : phrases) {
            phrase = phrase.trim();
            if (phrase.length() < 2) continue;
            // 2. 对每个短语：按字符拆二元组
            // 连续中文：每 2-4 个字符作为一个 token
            for (int i = 0; i < phrase.length(); i++) {
                for (int j = i + 2; j <= Math.min(i + 4, phrase.length()); j++) {
                    tokens.add(phrase.substring(i, j));
                }
            }
        }
        return tokens;
    }

    private record ChunkEntry(long id, long kbId, long docId, String content) {}

    private record ScoredChunk(ChunkEntry chunk, double score) {}
}
