package com.tkevinb.ragent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * A/B 测试开关配置
 * <p>
 * 控制 Pipeline 各阶段的启用/禁用，用于消融实验：
 * - 全关 = 基线（纯 LLM + 检索）
 * - 仅开改写 = 基线 + QueryRewrite
 * - 开改写 + 意图 = 完整链路
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.ablation")
public class AblationProperties {

    /** 问题改写（归一化 + 多问句拆分） */
    private boolean queryRewriteEnabled = true;

    /** 意图识别 */
    private boolean intentEnabled = true;

    /** 多问句拆分（QueryRewrite 的子步骤） */
    private boolean questionSplitEnabled = true;
}
