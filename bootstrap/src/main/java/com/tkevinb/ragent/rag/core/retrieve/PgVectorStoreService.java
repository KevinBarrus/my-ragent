package com.tkevinb.ragent.rag.core.retrieve;

import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.util.*;

/**
 * PGVector 向量检索引擎
 * <p>
 * 使用 PostgreSQL + pgvector 做语义检索。
 * embedding 使用字符 n-gram 哈希映射到 768 维向量，
 * 实现"相似文本 → 相近向量"的效果。
 * <p>
 * TODO: 替换为真实 embedding 模型（如 text2vec-large-chinese）
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
    public void initTable() {
        // 确保 PG 中有向量表（如果用户之前跑过建表脚本就不需要重新建）
        try {
            boolean exists = Boolean.TRUE.equals(
                    vectorJdbc.queryForObject(
                            "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 't_knowledge_chunk_vector')",
                            Boolean.class));
            if (!exists) {
                vectorJdbc.execute("""
                        CREATE TABLE IF NOT EXISTS t_knowledge_chunk_vector (
                            id BIGSERIAL PRIMARY KEY,
                            kb_id BIGINT NOT NULL DEFAULT 1,
                            doc_id BIGINT NOT NULL DEFAULT 1,
                            chunk_index INT NOT NULL DEFAULT 0,
                            content TEXT NOT NULL,
                            embedding vector(768),
                            enabled SMALLINT DEFAULT 1,
                            created_by VARCHAR(64) DEFAULT 'admin',
                            create_time TIMESTAMP DEFAULT NOW(),
                            deleted SMALLINT DEFAULT 0
                        )
                        """);
                log.info("PGVector: 自动创建向量表 t_knowledge_chunk_vector");
            }
        } catch (Exception e) {
            log.warn("PGVector: 自动建表失败（可能已存在），继续启动。error={}", e.getMessage());
        }
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();

        float[] queryVec = embed(query);
        String vectorStr = arrayToPgVector(queryVec);

        try {
            // PG Vector 余弦相似度检索: <=> 操作符
            List<ChunkRow> rows = vectorJdbc.query(
                    "SELECT content, 1 - (embedding <=> ?::vector) AS score " +
                            "FROM t_knowledge_chunk_vector " +
                            "WHERE enabled = 1 AND deleted = 0 AND embedding IS NOT NULL " +
                            "ORDER BY embedding <=> ?::vector LIMIT ?",
                    ps -> {
                        ps.setString(1, vectorStr);
                        ps.setString(2, vectorStr);
                        ps.setInt(3, topK);
                    },
                    (rs, rowNum) -> new ChunkRow(
                            rs.getString("content"),
                            rs.getDouble("score")
                    )
            );

            if (rows.isEmpty()) {
                log.info("PGVector: query='{}', 无结果", query);
                return List.of();
            }

            log.info("PGVector: query='{}', 返回 {} 条, 最高分={}",
                    query, rows.size(), String.format("%.2f", rows.get(0).score));

            return rows.stream()
                    .map(r -> RetrievedChunk.builder()
                            .text(r.content)
                            .score((float) r.score)
                            .build())
                    .toList();
        } catch (Exception e) {
            log.error("PGVector: 检索失败 query='{}'", query, e);
            return List.of();
        }
    }

    /**
     * 将文档插入向量库并生成 embedding
     */
    public void index(String content) {
        float[] vec = embed(content);
        String vecStr = arrayToPgVector(vec);
        vectorJdbc.update(
                "INSERT INTO t_knowledge_chunk_vector (content, embedding) VALUES (?, ?::vector)",
                content, vecStr);
    }

    /**
     * 批量索引（从 MySQL 已导入的 chunk 迁移到 PG）
     */
    public void batchIndex(List<String> chunks) {
        vectorJdbc.batchUpdate(
                "INSERT INTO t_knowledge_chunk_vector (content, embedding) VALUES (?, ?::vector)",
                chunks,
                500,
                (PreparedStatement ps, String content) -> {
                    ps.setString(1, content);
                    ps.setString(2, arrayToPgVector(embed(content)));
                });
        log.info("PGVector: 批量索引 {} 条文档完成", chunks.size());
    }

    // ==================== Embedding 算法 ====================

    /**
     * 字符 n-gram 哈希 → 768 维向量
     * <p>
     * 原理：对文本中的每个 2-gram，用 SHA-256 哈希映射到 0-767 的槽位，
     * 在对应位置 +1（类似 TF），最后归一化。
     * <p>
     * 效果：相似文本 → 相近的 n-gram 集合 → 相似的向量 → 余弦相似度高。
     * 注意：这是简化方案，不如真实语义模型的 embedding。面试时可说明。
     */
    private float[] embed(String text) {
        float[] vec = new float[DIM];
        if (text == null || text.isBlank()) return vec;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // 对每个 2-gram 做哈希 → 映射到 768 维
            for (int i = 0; i < text.length() - 1; i++) {
                String gram = text.substring(i, i + 2);
                byte[] hash = md.digest(gram.getBytes(StandardCharsets.UTF_8));
                // 取 hash 的前 4 字节转 int，模 DIM 确定槽位
                int slot = Math.abs(
                        ((hash[0] & 0xFF) << 24) |
                                ((hash[1] & 0xFF) << 16) |
                                ((hash[2] & 0xFF) << 8) |
                                (hash[3] & 0xFF)
                ) % DIM;
                vec[slot] += 1.0f;
            }
            // 归一化
            float norm = 0;
            for (float v : vec) norm += v * v;
            if (norm > 0) {
                norm = (float) Math.sqrt(norm);
                for (int i = 0; i < DIM; i++) vec[i] /= norm;
            }
        } catch (Exception e) {
            log.error("Embedding 生成失败", e);
        }
        return vec;
    }

    /**
     * float[] → PGVector 格式字符串 "[0.1,0.2,...]"
     */
    private String arrayToPgVector(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.6f", vec[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    private record ChunkRow(String content, double score) {}
}
