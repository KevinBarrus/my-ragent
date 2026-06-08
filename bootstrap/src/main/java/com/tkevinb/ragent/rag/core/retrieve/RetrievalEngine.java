package com.tkevinb.ragent.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import com.tkevinb.ragent.rag.core.intent.NodeScore;
import com.tkevinb.ragent.rag.core.intent.NodeScoreFilters;
import com.tkevinb.ragent.rag.dto.RetrievalContext;
import com.tkevinb.ragent.rag.dto.SubQuestionIntent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多通道检索引擎
 * <p>
 * 双通道并行检索 + RRF 融合 + 去重 + 证据预算：
 * 通道1：BGE 语义向量检索（PGVector）
 * 通道2：关键词匹配（二元组滑动窗口）
 * 后处理：RRF 排名融合 → 文本相似度去重 → 证据预算裁剪
 */
@Slf4j
@Service
public class RetrievalEngine {

    private static final int PER_QUESTION_BUDGET = 2200;
    private static final int TOTAL_BUDGET = 5200;
    /** RRF 参数 k */
    private static final int RRF_K = 60;

    private final VectorStoreService vectorStoreService;
    private final KeywordSearchChannel keywordChannel;
    private final JdbcTemplate vectorJdbc;

    /** 文档缓存 — 关键词通道需要 */
    private List<String> allDocuments = List.of();

    public RetrievalEngine(VectorStoreService vectorStoreService,
                           @Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbc) {
        this.vectorStoreService = vectorStoreService;
        this.vectorJdbc = vectorJdbc;
        this.keywordChannel = new KeywordSearchChannel();
    }

    @PostConstruct
    public void loadDocs() {
        try {
            allDocuments = vectorJdbc.queryForList(
                    "SELECT content FROM t_knowledge_chunk_vector WHERE enabled = 1 AND deleted = 0",
                    String.class);
            log.info("多通道引擎: 已加载 {} 条文档用于关键词检索", allDocuments.size());
        } catch (Exception e) {
            log.warn("关键词通道加载文档失败: {}", e.getMessage());
            allDocuments = List.of();
        }
    }

    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK) {
        if (CollUtil.isEmpty(subIntents)) return RetrievalContext.empty();
        int k = topK > 0 ? topK : 5;

        Map<String, List<RetrievedChunk>> intentChunks = new HashMap<>();
        List<String> contexts = new ArrayList<>();

        for (SubQuestionIntent intent : subIntents) {
            String q = intent.subQuestion();
            List<NodeScore> kbIntents = NodeScoreFilters.kb(intent.nodeScores());

            // ==== 双通道并行检索 ====
            List<RetrievedChunk> vecChunks = vectorStoreService.search(q, k);
            List<RetrievedChunk> kwChunks = keywordChannel.search(q, allDocuments, k);

            // ==== RRF 融合 ====
            List<RetrievedChunk> fused = rrfFusion(vecChunks, kwChunks);

            // ==== 去重（相似文本合并）====
            fused = deduplicate(fused);

            if (CollUtil.isEmpty(fused)) {
                log.info("子问题未检索到相关文档：{}", q);
                continue;
            }

            // ==== 预算裁剪 ====
            fused = applyPerQuestionBudget(fused);
            log.info("子问题检索到 {} 个文档片段（预算裁剪后）：{}", fused.size(), q);

            String key = CollUtil.isNotEmpty(kbIntents) ? kbIntents.get(0).getNode().getId() : "default";
            intentChunks.put(key, fused);
            contexts.add(formatContext(q, fused));
        }

        contexts = applyTotalBudget(contexts);
        String kbContext = String.join("\n\n---\n\n", contexts);
        return RetrievalContext.builder().kbContext(kbContext).intentChunks(intentChunks).build();
    }

    // ==================== RRF 融合 ====================

    private List<RetrievedChunk> rrfFusion(List<RetrievedChunk> vec, List<RetrievedChunk> kw) {
        // 为每个 chunk 分配唯一 ID（用文本 hash）
        Map<String, RetrievedChunk> dedup = new LinkedHashMap<>();

        for (int i = 0; i < vec.size(); i++) {
            String key = textKey(vec.get(i).getText());
            double score = 1.0 / (RRF_K + i + 1);
            vec.get(i).setScore((float) score);
            dedup.put(key, vec.get(i));
        }
        for (int i = 0; i < kw.size(); i++) {
            String key = textKey(kw.get(i).getText());
            double score = 1.0 / (RRF_K + i + 1);
            if (dedup.containsKey(key)) {
                dedup.get(key).setScore(dedup.get(key).getScore() + (float) score);
            } else {
                kw.get(i).setScore((float) score);
                dedup.put(key, kw.get(i));
            }
        }

        List<RetrievedChunk> result = new ArrayList<>(dedup.values());
        result.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return result;
    }

    // ==================== 去重 ====================

    private List<RetrievedChunk> deduplicate(List<RetrievedChunk> chunks) {
        if (chunks.size() <= 1) return chunks;
        List<RetrievedChunk> result = new ArrayList<>();
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            String text = chunks.get(i).getText();
            boolean dup = false;
            for (RetrievedChunk kept : result) {
                if (textOverlap(text, kept.getText()) > 0.7) {
                    dup = true;
                    break;
                }
            }
            if (!dup) result.add(chunks.get(i));
        }
        return result;
    }

    private double textOverlap(String a, String b) {
        Set<String> ta = tokenize2gram(a), tb = tokenize2gram(b);
        Set<String> isect = new HashSet<>(ta); isect.retainAll(tb);
        return (double) isect.size() / Math.min(ta.size(), tb.size());
    }

    private Set<String> tokenize2gram(String text) {
        Set<String> s = new HashSet<>();
        for (int i = 0; i < text.length() - 1; i++) s.add(text.substring(i, i+2));
        return s;
    }

    private String textKey(String text) {
        return text != null && text.length() > 40 ? text.substring(0, 40) : text;
    }

    // ==================== 预算控制（不变） ====================

    private List<RetrievedChunk> applyPerQuestionBudget(List<RetrievedChunk> chunks) {
        int used = 0;
        List<RetrievedChunk> kept = new ArrayList<>();
        for (RetrievedChunk c : chunks) {
            String text = c.getText();
            if (text == null) continue;
            int len = text.length();
            if (used + len > PER_QUESTION_BUDGET) {
                int remain = PER_QUESTION_BUDGET - used;
                if (remain > 50) { c.setText(text.substring(0, remain) + "..."); kept.add(c); }
                break;
            }
            used += len;
            kept.add(c);
        }
        return kept;
    }

    private List<String> applyTotalBudget(List<String> contexts) {
        int used = 0;
        List<String> kept = new ArrayList<>();
        for (String ctx : contexts) {
            int len = ctx.length();
            if (used + len > TOTAL_BUDGET) {
                int remain = TOTAL_BUDGET - used;
                if (remain > 100) kept.add(ctx.substring(0, remain) + "\n...");
                break;
            }
            used += len;
            kept.add(ctx);
        }
        return kept;
    }

    private String formatContext(String question, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("【问题】").append(question).append("\n");
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sb.append("[文档片段 ").append(i + 1).append("] ");
            if (c.getScore() != null) sb.append("(相关度: ").append(String.format("%.2f", c.getScore())).append(") ");
            sb.append(c.getText()).append("\n");
        }
        return sb.toString().trim();
    }
}
