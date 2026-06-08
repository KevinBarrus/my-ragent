package com.tkevinb.ragent.rag.core.guidance;

import cn.hutool.core.collection.CollUtil;
import com.tkevinb.ragent.rag.core.intent.NodeScore;
import com.tkevinb.ragent.rag.dto.SubQuestionIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 意图歧义引导服务（简化版）
 * <p>
 * 判断逻辑：
 * 1. 多个子问题时跳过（用户已拆清楚）
 * 2. 最高分 < 0.5 → 意图不清
 * 3. 第二名 / 第一名 > 0.8 → 候选太接近
 */
@Slf4j
@Service
public class IntentGuidanceService {

    private static final double MIN_SCORE = 0.5;
    private static final double AMBIGUITY_RATIO = 0.8;

    /**
     * 检查是否需要反问用户
     *
     * @return 反问文本，null 表示不需要反问
     */
    public String check(String question, List<SubQuestionIntent> subIntents) {
        // 多个子问题 → 不反问
        if (CollUtil.isEmpty(subIntents) || subIntents.size() != 1) {
            return null;
        }

        List<NodeScore> scores = subIntents.get(0).nodeScores();
        if (CollUtil.isEmpty(scores) || scores.size() < 2) {
            return null;
        }

        NodeScore top = scores.get(0);
        NodeScore second = scores.get(1);

        // 最高分太低 → 意图不清
        if (top.getScore() < MIN_SCORE) {
            return buildPrompt("未能确定您的问题属于哪个类别，请补充更多信息。");
        }

        // 两名太接近 → 反问
        double ratio = second.getScore() / top.getScore();
        if (ratio > AMBIGUITY_RATIO) {
            String topName = top.getNode().getName();
            String secondName = second.getNode().getName();
            String msg = String.format("您是想咨询【%s】还是【%s】相关的问题？请补充说明以便我更准确地为您解答。",
                    topName, secondName);
            return buildPrompt(msg);
        }

        return null; // 意图明确
    }

    private String buildPrompt(String msg) {
        return msg;
    }
}
