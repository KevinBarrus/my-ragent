package com.tkevinb.ragent.rag.controller.request;

import lombok.Data;

/**
 * 会话更新请求类
 * <p>
 *     因为用户手动更新，只能更新标题，因此只有一个 title 字段
 * </p>
 */
@Data
public class ConversationUpdateRequest {

    /**
     * 会话标题
     */
    private String title;
}

