package com.tkevinb.ragent.rag.service.pipeline;

import com.tkevinb.ragent.infra.chat.StreamCallBack;
import com.tkevinb.ragent.rag.core.rewrite.RewriteResult;
import com.tkevinb.ragent.rag.dto.SubQuestionIntent;
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

    // ==================== 不可变输入参数 ====================

    private final String question;
    private final String conversationId;
    private final String taskId;
    private final boolean deepThinking;
    private final String userId;
    private final StreamCallBack callback;

    // ==================== 管道中填充的中间状态 ====================

    @Setter
    private List<ChatMessage> history;

    @Setter
    private RewriteResult rewriteResult;

    @Setter
    private List<SubQuestionIntent> subIntents;
}
