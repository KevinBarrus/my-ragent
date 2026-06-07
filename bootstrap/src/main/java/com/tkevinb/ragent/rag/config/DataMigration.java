package com.tkevinb.ragent.rag.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时将 MySQL 中的文档片段迁移到 PGVector
 */
@Slf4j
@Component
public class DataMigration {

    private final JdbcTemplate mysqlJdbc;
    private final JdbcTemplate vectorJdbc;

    public DataMigration(JdbcTemplate mysqlJdbc,
                         @org.springframework.beans.factory.annotation.Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbc) {
        this.mysqlJdbc = mysqlJdbc;
        this.vectorJdbc = vectorJdbc;
    }

    @PostConstruct
    public void migrate() {
        try {
            // 检查 PGVector 表中是否有数据
            Integer count = vectorJdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_knowledge_chunk_vector WHERE deleted = 0", Integer.class);
            if (count != null && count > 0) {
                log.info("DataMigration: PGVector 已有 {} 条数据，跳过迁移", count);
                return;
            }
        } catch (Exception e) {
            // 表可能不存在，由 PgVectorStoreService.initTable() 创建
        }

        try {
            List<String> chunks = mysqlJdbc.queryForList(
                    "SELECT content FROM t_knowledge_chunk WHERE enabled = 1 AND deleted = 0", String.class);
            if (chunks.isEmpty()) {
                log.info("DataMigration: MySQL 无数据，跳过迁移");
                return;
            }
            for (String content : chunks) {
                // 生成 vector 并插入
                float[] vec = embed(content);
                String vecStr = arrayToPgVector(vec);
                vectorJdbc.update(
                        "INSERT INTO t_knowledge_chunk_vector (content, embedding) VALUES (?, ?::vector)",
                        content, vecStr);
            }
            log.info("DataMigration: 已迁移 {} 条文档到 PGVector", chunks.size());
        } catch (Exception e) {
            log.warn("DataMigration: 迁移失败，可使用关键词检索作为 fallback。error={}", e.getMessage());
        }
    }

    // ==================== 和 PgVectorStoreService 一致的 embedding ====================
    private static final int DIM = 768;

    private float[] embed(String text) {
        float[] vec = new float[DIM];
        if (text == null || text.isBlank()) return vec;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < text.length() - 1; i++) {
                String gram = text.substring(i, i + 2);
                byte[] hash = md.digest(gram.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                int slot = Math.abs(
                        ((hash[0] & 0xFF) << 24) |
                                ((hash[1] & 0xFF) << 16) |
                                ((hash[2] & 0xFF) << 8) |
                                (hash[3] & 0xFF)
                ) % DIM;
                vec[slot] += 1.0f;
            }
            float norm = 0;
            for (float v : vec) norm += v * v;
            if (norm > 0) {
                norm = (float) Math.sqrt(norm);
                for (int i = 0; i < DIM; i++) vec[i] /= norm;
            }
        } catch (Exception ignored) {}
        return vec;
    }

    private String arrayToPgVector(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.6f", vec[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
