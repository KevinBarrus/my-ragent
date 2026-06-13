package com.tkevinb.ragent.rag.ingestion;

import com.tkevinb.ragent.rag.core.retrieve.BgeEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档入库流水线编排
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final DocumentParser parser;
    private final ChunkStrategy chunker;
    private final BgeEmbeddingService embeddingService;
    private final @Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbc;
    private final JdbcTemplate defaultJdbc;

    public void ingest(byte[] bytes, String fileName, long kbId) {
        long docId = System.currentTimeMillis();
        log.info("Ingestion 开始: fileName={}, docId={}, size={}", fileName, docId, bytes.length);

        // 1. 写入 MySQL t_knowledge_document
        try {
            defaultJdbc.update("INSERT INTO t_knowledge_document (kb_id, doc_name, file_url, file_type, status, created_by, create_time, deleted) VALUES (?, ?, ?, ?, 'completed', 'admin', NOW(), 0)",
                    kbId, fileName, "/docs/" + fileName, fileName.endsWith(".md") ? "md" : "file");
        } catch (Exception e) {
            log.warn("写入 t_knowledge_document 失败: {}", e.getMessage());
        }

        String text;
        try { text = parser.parse(bytes, fileName); }
        catch (Exception e) { log.error("解析失败: {}", fileName, e); return; }

        List<ChunkStrategy.ChunkResult> chunks = chunker.chunk(text);
        if (chunks.isEmpty()) { log.warn("分块结果为空: {}", fileName); return; }
        log.info("分块完成: {} 个块", chunks.size());

        // 先插 Parent 块
        for (ChunkStrategy.ChunkResult c : chunks) {
            if (!c.isParent) continue;
            float[] vec = embeddingService.embed(c.text);
            if (vec == null) continue;
            vectorJdbc.update(
                "INSERT INTO t_knowledge_chunk_vector (content, embedding, kb_id, doc_id, chunk_index) VALUES (?, ?::vector, ?, ?, 99)",
                c.text, arrayToPgVector(vec), kbId, docId);
        }

        // 获取刚插入的 Parent id
        List<Long> parentIds = vectorJdbc.queryForList(
                "SELECT id FROM t_knowledge_chunk_vector WHERE doc_id = ? AND chunk_index = 99 ORDER BY id", Long.class, docId);

        // 插 Child 块
        int pIdx = 0;
        for (ChunkStrategy.ChunkResult c : chunks) {
            if (c.isParent) { pIdx++; continue; }
            float[] vec = embeddingService.embed(c.text);
            if (vec == null) continue;
            long parentId = parentIds.get(Math.min(pIdx, parentIds.size() - 1));
            vectorJdbc.update(
                "INSERT INTO t_knowledge_chunk_vector (content, embedding, kb_id, doc_id, chunk_index, parent_id) VALUES (?, ?::vector, ?, ?, ?, ?)",
                c.text, arrayToPgVector(vec), kbId, docId, pIdx, parentId);
        }
        log.info("Ingestion 完成: fileName={}, chunks={}", fileName, chunks.size());
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
