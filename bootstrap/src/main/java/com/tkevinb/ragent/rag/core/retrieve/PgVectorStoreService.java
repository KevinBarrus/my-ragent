package com.tkevinb.ragent.rag.core.retrieve;

import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * PGVector 向量检索引擎
 */
@Slf4j
@Service
public class PgVectorStoreService implements VectorStoreService {

    private static final int DIM = 768;

    private final JdbcTemplate vectorJdbc;

    public PgVectorStoreService(@Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbc) {
        this.vectorJdbc = vectorJdbc;
    }

    @PostConstruct
    public void init() {
        try {
            vectorJdbc.execute("""
                    CREATE TABLE IF NOT EXISTS t_knowledge_chunk_vector (
                        id BIGSERIAL PRIMARY KEY, kb_id BIGINT, doc_id BIGINT,
                        chunk_index INT DEFAULT 0, content TEXT NOT NULL,
                        embedding vector(768), enabled SMALLINT DEFAULT 1,
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
                vectorJdbc.update("UPDATE t_knowledge_chunk_vector SET embedding = ?::vector WHERE id = ?",
                        arrayToPgVector(embed(row.content)), row.id);
            }
            log.info("PGVector: 已为 {} 条文档生成 embedding", unindexed.size());
        } catch (Exception e) {
            log.warn("PGVector: 初始化 embedding 失败。error={}", e.getMessage());
        }
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        String vecStr = arrayToPgVector(embed(query));
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

    // ==================== Embedding ====================

    private float[] embed(String text) {
        float[] vec = new float[DIM];
        if (text == null || text.isBlank()) return vec;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < text.length() - 1; i++) {
                byte[] hash = md.digest(text.substring(i, i + 2).getBytes(StandardCharsets.UTF_8));
                int slot = Math.abs(((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16) | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF)) % DIM;
                vec[slot] += 1.0f;
            }
            float norm = 0; for (float v : vec) norm += v * v;
            if (norm > 0) { norm = (float) Math.sqrt(norm); for (int i = 0; i < DIM; i++) vec[i] /= norm; }
        } catch (Exception ignored) {}
        return vec;
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
