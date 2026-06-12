package com.tkevinb.ragent.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import com.tkevinb.ragent.rag.core.intent.NodeScore;
import com.tkevinb.ragent.rag.core.intent.NodeScoreFilters;
import com.tkevinb.ragent.rag.dto.RetrievalContext;
import com.tkevinb.ragent.rag.dto.SubQuestionIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import cn.hutool.core.util.StrUtil;
import com.tkevinb.ragent.rag.core.tool.McpParameterExtractor;
import com.tkevinb.ragent.rag.core.tool.Tool;
import com.tkevinb.ragent.rag.core.tool.ToolRegistry;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 多通道检索引擎 — 通过 SearchChannel 接口解耦
 * <p>
 * 并行调用所有通道 → RRF 融合 → 去重 → 证据预算
 * <p>
 * 新增通道只需实现 SearchChannel，无需改动本类。
 */
@Slf4j
@Service
public class RetrievalEngine {

    private static final int PER_QUESTION_BUDGET = 2200;
    private static final int TOTAL_BUDGET = 5200;
    private static final int RRF_K = 60;

    private final List<SearchChannel> channels;
    private final Executor retrieveExecutor;
    private final BgeRerankService rerankService;
    private final JdbcTemplate vectorJdbc;
    private final ToolRegistry toolRegistry;
    private final McpParameterExtractor mcpExtractor;

    public RetrievalEngine(List<SearchChannel> channels,
                           @org.springframework.beans.factory.annotation.Qualifier("retrieveExecutor") Executor retrieveExecutor,
                           BgeRerankService rerankService,
                           @org.springframework.beans.factory.annotation.Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbc,
                           ToolRegistry toolRegistry,
                           McpParameterExtractor mcpExtractor) {
        this.channels = channels;
        this.retrieveExecutor = retrieveExecutor;
        this.rerankService = rerankService;
        this.vectorJdbc = vectorJdbc;
        this.toolRegistry = toolRegistry;
        this.mcpExtractor = mcpExtractor;
    }

    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK) {
        if (CollUtil.isEmpty(subIntents)) return RetrievalContext.empty();
        int k = topK > 0 ? topK : 5;

        Map<String, List<RetrievedChunk>> intentChunks = new HashMap<>();
        List<String> contexts = new ArrayList<>();
        List<String> mcpResults = new ArrayList<>();

        for (SubQuestionIntent intent : subIntents) {
            String q = intent.subQuestion();
            List<NodeScore> kbIntents = NodeScoreFilters.kb(intent.nodeScores());
            List<NodeScore> mcpIntents = NodeScoreFilters.mcp(intent.nodeScores());

            // ==== MCP 工具调用 ====
            boolean hasMcp = false;
            for (NodeScore ns : mcpIntents) {
                hasMcp = true;
                String toolId = ns.getNode().getMcpToolId();
                Tool tool = toolRegistry.get(toolId);
                if (tool == null) { log.warn("MCP 工具不存在: {}", toolId); continue; }
                Map<String, Object> params = mcpExtractor.extract(q, tool);
                String result = tool.execute(params);
                mcpResults.add(result);
                log.info("MCP 工具调用: {} → {}", toolId, StrUtil.maxLength(result, 200));
            }

            // 只有 MCP 意图，没有 KB 意图 → 跳过检索
            if (hasMcp && kbIntents.isEmpty()) {
                continue;
            }

            // ==== 并行调用所有检索通道 ====
            List<CompletableFuture<List<RetrievedChunk>>> futures = channels.stream()
                    .map(ch -> CompletableFuture.supplyAsync(() -> ch.search(q, k), retrieveExecutor))
                    .toList();
            List<List<RetrievedChunk>> allResults = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            // ==== RRF 融合 ====
            List<RetrievedChunk> fused = rrfFusion(allResults);

            // ==== 去重 ====
            fused = deduplicate(fused);

            // ==== Rerank 精排 ====
            fused = rerankService.rerank(q, fused);

            // ==== Parent-Child 块聚合 ====
            fused = enrichWithParent(fused);

            if (CollUtil.isEmpty(fused)) {
                log.info("子问题未检索到相关文档：{}", q);
                continue;
            }

            fused = applyPerQuestionBudget(fused);
            log.info("子问题检索到 {} 个文档片段（{} 通道, 预算裁剪后）：{}",
                    fused.size(), channels.size(), q);

            String key = CollUtil.isNotEmpty(kbIntents) ? kbIntents.get(0).getNode().getId() : "default";
            intentChunks.put(key, fused);
            contexts.add(formatContext(q, fused));
        }

        contexts = applyTotalBudget(contexts);
        return RetrievalContext.builder()
                .kbContext(String.join("\n\n---\n\n", contexts))
                .mcpContext(String.join("\n", mcpResults))
                .intentChunks(intentChunks).build();
    }

    // ==================== Parent-Child ====================

    private List<RetrievedChunk> enrichWithParent(List<RetrievedChunk> chunks) {
        return chunks.stream().map(chunk -> {
            try {
                Long parentId = vectorJdbc.queryForObject(
                        "SELECT parent_id FROM t_knowledge_chunk_vector WHERE id = ?::bigint",
                        Long.class, chunk.getId());
                if (parentId == null || parentId == Long.parseLong(chunk.getId())) return chunk;

                String parentText = vectorJdbc.queryForObject(
                        "SELECT content FROM t_knowledge_chunk_vector WHERE id = ?", String.class, parentId);
                if (parentText != null && parentText.length() > chunk.getText().length() + 50) {
                    chunk.setText(parentText); // 用 Parent 完整上下文替换
                }
            } catch (Exception ignored) {}
            return chunk;
        }).toList();
    }

    // ==================== RRF ====================

    private List<RetrievedChunk> rrfFusion(List<List<RetrievedChunk>> allResults) {
        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        for (List<RetrievedChunk> results : allResults) {
            for (int rank = 0; rank < results.size(); rank++) {
                String key = textKey(results.get(rank).getText());
                double score = 1.0 / (RRF_K + rank + 1);
                if (merged.containsKey(key)) {
                    merged.get(key).setScore(merged.get(key).getScore() + (float) score);
                } else {
                    RetrievedChunk c = results.get(rank);
                    c.setScore((float) score);
                    merged.put(key, c);
                }
            }
        }
        List<RetrievedChunk> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return result;
    }

    // ==================== 去重 ====================

    private List<RetrievedChunk> deduplicate(List<RetrievedChunk> chunks) {
        if (chunks.size() <= 1) return chunks;
        List<RetrievedChunk> result = new ArrayList<>();
        result.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            boolean dup = false;
            for (RetrievedChunk kept : result) {
                if (textOverlap(chunks.get(i).getText(), kept.getText()) > 0.7) { dup = true; break; }
            }
            if (!dup) result.add(chunks.get(i));
        }
        return result;
    }

    private double textOverlap(String a, String b) {
        Set<String> ta = new HashSet<>(), tb = new HashSet<>();
        for (int i = 0; i < a.length() - 1; i++) ta.add(a.substring(i, i + 2));
        for (int i = 0; i < b.length() - 1; i++) tb.add(b.substring(i, i + 2));
        Set<String> isect = new HashSet<>(ta); isect.retainAll(tb);
        return (double) isect.size() / Math.min(ta.size(), tb.size());
    }

    private String textKey(String text) {
        return text != null && text.length() > 40 ? text.substring(0, 40) : text;
    }

    // ==================== 预算 ====================

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
