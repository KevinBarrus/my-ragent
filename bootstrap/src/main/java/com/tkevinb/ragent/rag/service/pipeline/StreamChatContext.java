package com.tkevinb.ragent.rag.service.pipeline;

import com.tkevinb.ragent.rag.core.rewrite.RewriteResult;
import lombok.Builder;
import lombok.Getter;
import com.tkevinb.ragent.framework.convention.ChatMessage;
import lombok.Setter;

import java.util.List;

/**
 * 流式对话上下文
 */
@Getter
@Builder
public class StreamChatContext {

    /**
    不可变参数
     */
    private final String userId;
    private final String conversationId;
    private final String taskId;
    private final boolean deepThinking;
    private final String question;

    /**
     * 需要填充的中间状态
     */

    @Setter
    private RewriteResult rewriteResult;

    @Setter
    private List<ChatMessage> history;
}
