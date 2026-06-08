package com.tkevinb.ragent.rag.core.retrieve;

import com.tkevinb.ragent.framework.convention.RetrievedChunk;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 关键词检索通道
 * <p>
 * 用二元组滑动窗口做字面匹配，弥补向量检索对精确关键词的弱点。
 * 例如"OA系统"这种专有名词，BGE 可能不敏感，但关键词匹配能精准命中。
 */
public class KeywordSearchChannel {

    /**
     * 从给定文档列表中按关键词匹配搜索
     * @param query 用户问题
     * @param documents 文档内容列表
     * @param topK 返回数量
     */
    public List<RetrievedChunk> search(String query, List<String> documents, int topK) {
        Set<String> queryTokens = tokenize(query);
        List<ScoredDoc> scored = new ArrayList<>();

        for (String doc : documents) {
            Set<String> docTokens = tokenize(doc);
            Set<String> intersection = new HashSet<>(queryTokens);
            intersection.retainAll(docTokens);
            if (intersection.isEmpty()) continue;
            double score = Math.min(1.0,
                    (double) intersection.size() / Math.max(queryTokens.size(), 1)
                            + intersection.size() * 0.05);
            scored.add(new ScoredDoc(doc, score));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored.subList(0, Math.min(topK, scored.size())).stream()
                .map(s -> RetrievedChunk.builder().text(s.doc).score((float) s.score).build())
                .collect(Collectors.toList());
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String[] phrases = text.split("[，。！？、；：（）【】《》\\s,.!?;:()\\-]+");
        Set<String> tokens = new HashSet<>();
        for (String phrase : phrases) {
            phrase = phrase.trim();
            if (phrase.length() < 2) continue;
            for (int i = 0; i < phrase.length(); i++) {
                for (int j = i + 2; j <= Math.min(i + 4, phrase.length()); j++) {
                    tokens.add(phrase.substring(i, j));
                }
            }
        }
        return tokens;
    }

    private record ScoredDoc(String doc, double score) {}
}
