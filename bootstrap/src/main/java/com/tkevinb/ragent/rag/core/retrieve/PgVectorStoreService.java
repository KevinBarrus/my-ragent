package com.tkevinb.ragent.rag.core.retrieve;

import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * PGVector 向量检索引擎 — 支持 n-gram 和 BGE 两种 embedding
 */
@Slf4j
@Service
public class PgVectorStoreService implements VectorStoreService {

    private static final int DIM = 1024; // BGE-large-zh 输出维度

    private final JdbcTemplate vectorJdbc;
    private final BgeEmbeddingService bgeService;

    public PgVectorStoreService(@Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbc,
                                BgeEmbeddingService bgeService) {
        this.vectorJdbc = vectorJdbc;
        this.bgeService = bgeService;
    }

    @PostConstruct
    public void init() {
        try {
            vectorJdbc.execute("""
                    CREATE TABLE IF NOT EXISTS t_knowledge_chunk_vector (
                        id BIGSERIAL PRIMARY KEY, kb_id BIGINT, doc_id BIGINT,
                        chunk_index INT DEFAULT 0, content TEXT NOT NULL,
                        embedding vector(1024), enabled SMALLINT DEFAULT 1,
                        created_by VARCHAR(64) DEFAULT 'admin',
                        create_time TIMESTAMP DEFAULT NOW(), deleted SMALLINT DEFAULT 0
                    )
                    """);
        } catch (Exception ignored) {}

        try {
            List<Row> unindexed = vectorJdbc.query(
                    "SELECT id, content FROM t_knowledge_chunk_vector WHERE embedding IS NULL AND enabled = 1 AND deleted = 0",
                    (rs, rn) -> new Row(rs.getLong("id"), rs.getString("content"))
            );
            if (unindexed.isEmpty()) return;
            for (Row row : unindexed) {
                float[] vec = embed(row.content);
                if (vec == null) continue;
                vectorJdbc.update("UPDATE t_knowledge_chunk_vector SET embedding = ?::vector WHERE id = ?",
                        arrayToPgVector(vec), row.id);
            }
            log.info("PGVector: BGE embedding 已为 {} 条文档生成", unindexed.size());
        } catch (Exception e) {
            log.warn("PGVector: embedding 初始化失败。error={}", e.getMessage());
        }
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        float[] vec = embed(query);
        if (vec == null) {
            log.warn("PGVector: query embedding 生成失败, query='{}'", query);
            return List.of();
        }
        String vecStr = arrayToPgVector(vec);
        try {
            List<Row> rows = vectorJdbc.query(
                    "SELECT content, 1 - (embedding <=> ?::vector) AS score FROM t_knowledge_chunk_vector " +
                            "WHERE enabled = 1 AND deleted = 0 AND embedding IS NOT NULL " +
                            "ORDER BY embedding <=> ?::vector LIMIT ?",
                    ps -> { ps.setString(1, vecStr); ps.setString(2, vecStr); ps.setInt(3, topK); },
                    (rs, rn) -> new Row(rs.getString("content"), rs.getDouble("score"), 0L)
            );
            if (rows.isEmpty()) { log.info("PGVector: query='{}', 无结果", query); return List.of(); }
            log.info("PGVector: query='{}', 返回 {} 条, 最高分={}", query, rows.size(), String.format("%.2f", rows.get(0).score));
            return rows.stream().map(r -> RetrievedChunk.builder().text(r.content).score((float) r.score).build()).toList();
        } catch (Exception e) {
            log.error("PGVector: 检索失败 query='{}'", query, e);
            return List.of();
        }
    }

    private float[] embed(String text) {
        // 先试 BGE（语义），失败则降级为 null（不兜底 n-gram，保证数据纯净）
        return bgeService.embed(text);
    }

    private String arrayToPgVector(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) { if (i > 0) sb.append(","); sb.append(String.format("%.6f", vec[i])); }
        sb.append("]"); return sb.toString();
    }

    private record Row(String content, double score, long id) {
        Row(long id, String content) { this(content, 0, id); }
    }
}
