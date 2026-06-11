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
 * 关键词检索通道 — 倒排索引版
 * <p>
 * 启动时构建 {词 → [文档ID]} 倒排索引，检索时 O(1) 定位文档。
 */
@Slf4j
@Component
public class KeywordSearchChannel implements SearchChannel {

    private final JdbcTemplate vectorJdbc;
    /** 文档内容缓存，id → content */
    private final List<String> documents = new ArrayList<>();
    /** 倒排索引：词 → 文档ID Set */
    private final Map<String, Set<Integer>> invertedIndex = new HashMap<>();

    public KeywordSearchChannel(@Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbc) {
        this.vectorJdbc = vectorJdbc;
    }

    @PostConstruct
    public void loadDocs() {
        try {
            List<String> contents = vectorJdbc.queryForList(
                    "SELECT content FROM t_knowledge_chunk_vector WHERE enabled = 1 AND deleted = 0",
                    String.class);
            documents.addAll(contents);
            // 构建倒排索引
            for (int i = 0; i < contents.size(); i++) {
                Set<String> tokens = tokenize(contents.get(i));
                for (String token : tokens) {
                    invertedIndex.computeIfAbsent(token, k -> new HashSet<>()).add(i);
                }
            }
            log.info("关键词通道(倒排): 已加载 {} 条文档, {} 个索引项", documents.size(), invertedIndex.size());
        } catch (Exception e) {
            log.warn("关键词通道加载失败: {}", e.getMessage());
        }
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        if (documents.isEmpty()) return List.of();
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) return List.of();

        // 倒排检索：找到至少命中一个 token 的文档
        Map<Integer, Integer> hitCount = new HashMap<>();
        for (String token : queryTokens) {
            Set<Integer> docIds = invertedIndex.get(token);
            if (docIds != null) {
                for (int id : docIds) {
                    hitCount.merge(id, 1, Integer::sum);
                }
            }
        }

        // 按命中数排序
        return hitCount.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    int id = e.getKey();
                    int hits = e.getValue();
                    double score = Math.min(1.0, (double) hits / queryTokens.size() + hits * 0.05);
                    return RetrievedChunk.builder()
                            .text(documents.get(id))
                            .score((float) score)
                            .build();
                })
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
}
