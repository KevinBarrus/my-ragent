package com.tkevinb.ragent.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.tkevinb.ragent.framework.convention.RetrievedChunk;
import com.tkevinb.ragent.rag.core.intent.NodeScore;
import com.tkevinb.ragent.rag.core.intent.NodeScoreFilters;
import com.tkevinb.ragent.rag.dto.RetrievalContext;
import com.tkevinb.ragent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索引擎（简化版）
 * <p>
 * MVP 阶段仅做 KB 向量检索，暂不引入 MCP 工具调用和多通道检索。
 * 后续可按需扩展：多通道并行、后处理链（去重 + 重排序）、MCP 工具调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalEngine {

    private final VectorStoreService vectorStoreService;

    /**
     * 检索入口：对每个子问题进行向量检索，合并结果
     *
     * @param subIntents 子问题意图列表
     * @param topK       每个子问题返回的文档片段数
     * @return 检索上下文（kbContext + intentChunks）
     */
    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK) {

        //空意图直接返回空上下文。RetrievalContext.empty() 返回的 kbContext 是空字符串，Pipeline 里 handleEmptyRetrieval 会返回"未检索到相关文档"。
        if (CollUtil.isEmpty(subIntents)) {
            return RetrievalContext.empty();
        }

        //如果调用方没传 topK 或传了 0，默认返回 5 条
        int finalTopK = topK > 0 ? topK : 5;

        Map<String, List<RetrievedChunk>> mergedIntentChunks = new HashMap<>();
        List<String> allContexts = new ArrayList<>();

        for (SubQuestionIntent intent : subIntents) {

            // NodeScoreFilters.kb() 从意图分类结果里筛出"属于 KB 检索"的意图节点（排除 SYSTEM 和 MCP 类型的）
            // 即使这行现在不直接影响检索结果（因为只调了 vectorStoreService.search），但后面需要用它来决定 intentChunks 怎么分组。
            List<NodeScore> kbIntents = NodeScoreFilters.kb(intent.nodeScores());
            List<RetrievedChunk> chunks = vectorStoreService.search(intent.subQuestion(), finalTopK);

            if (CollUtil.isEmpty(chunks)) {
                log.info("子问题未检索到相关文档：{}", intent.subQuestion());
                continue;
            }

            log.info("子问题检索到 {} 个文档片段：{}", chunks.size(), intent.subQuestion());

            //把搜到的文档片段按意图节点 ID 分组
            //例如：
            //意图"请假制度"（ID=node_001）→ chunks 全挂它下面
            //意图"报销制度"（ID=node_002）→ 同样的 chunks 也挂它下面（简化版先这么干）
            if (CollUtil.isNotEmpty(kbIntents)) {
                for (NodeScore ns : kbIntents) {
                    //这个 map 最终传给 PromptContext，Prompt 构建时按意图分组展示证据
                    mergedIntentChunks.put(ns.getNode().getId(), chunks);
                }
            } else {
                //没有意图节点时，用 default 作为 key
                mergedIntentChunks.put("default", chunks);
            }

            //格式化上下文
            allContexts.add(formatContext(intent.subQuestion(), chunks));
        }

        //多个子问题的上下文用分隔线拼接。比如：
        //【问题】请假需要什么材料？
        //[文档片段 1] (相关度: 0.92) 请假需提交...
        //[文档片段 2] (相关度: 0.87) 审批流程...
        //---
        //【问题】请假审批需要多久？
        //[文档片段 1] (相关度: 0.89) 一般 1-3 个工作日...
        //最后返回 RetrievalContext，包含 kbContext（格式化的文本）和 intentChunks（分组后的原始 chunk 数据）
        String kbContext = String.join("\n\n---\n\n", allContexts);
        return RetrievalContext.builder()
                .kbContext(kbContext)
                .intentChunks(mergedIntentChunks)
                .build();

        //kbContext：RAGPromptService 会读这个字段，把它们包在 <evidence> 标签里塞进 system prompt
        //mcpContext：留给 MCP 工具调用的结果，现在没有，字段先占位
        //intentChunks：按意图节点 ID 分组的数据，RAGPromptService 用这个判断"哪些意图有检索结果、哪些没有"，决定用哪个模板
    }

    /**
     * 将检索到的文档片段格式化为 LLM 可读的文本
     */
    private String formatContext(String question, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("【问题】").append(question).append("\n");
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            sb.append("[文档片段 ").append(i + 1).append("] ");
            if (chunk.getScore() != null) {
                sb.append("(相关度: ").append(String.format("%.2f", chunk.getScore())).append(") ");
            }
            sb.append(chunk.getText()).append("\n");
        }
        return sb.toString().trim();
    }
}
