package com.tkevinb.ragent.rag.core.retrieve;

import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 关键词检索通道
 * <p>
 * 用二元组滑动窗口做字面匹配，弥补向量检索对精确关键词的弱点。
 */
@Slf4j
@Component
public class KeywordSearchChannel implements SearchChannel {

    private final JdbcTemplate vectorJdbc;
    private List<String> documents = List.of();

    public KeywordSearchChannel(@Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbc) {
        this.vectorJdbc = vectorJdbc;
    }

    @PostConstruct
    public void loadDocs() {
        try {
            documents = vectorJdbc.queryForList(
                    "SELECT content FROM t_knowledge_chunk_vector WHERE enabled = 1 AND deleted = 0",
                    String.class);
            log.info("关键词通道: 已加载 {} 条文档", documents.size());
        } catch (Exception e) {
            log.warn("关键词通道加载文档失败: {}", e.getMessage());
        }
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        if (documents.isEmpty()) return List.of();
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
            for (int i = 0; i < phrase.length(); i++)
                for (int j = i + 2; j <= Math.min(i + 4, phrase.length()); j++)
                    tokens.add(phrase.substring(i, j));
        }
        return tokens;
    }

    private record ScoredDoc(String doc, double score) {}
}
