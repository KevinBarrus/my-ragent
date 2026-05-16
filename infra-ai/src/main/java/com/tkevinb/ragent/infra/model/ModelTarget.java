package com.tkevinb.ragent.infra.model;


/**
 * 模型目标配置记录
 * <p>
 *     用于封装 AI 模型的配置信息，包括模型标识、候选模型配置和提供商配置
 * </p>
 * @param id    模型唯一标识符
 */
// TODO 添加候选模型配置与提供商配置
public record ModelTarget(
        String id
) {
}
