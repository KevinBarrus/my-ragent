package com.tkevinb.ragent.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.tkevinb.ragent.framework.context.UserContext;
import com.tkevinb.ragent.infra.chat.StreamCallBack;
import com.tkevinb.ragent.rag.service.RAGChatService;
import com.tkevinb.ragent.rag.service.handler.StreamCallbackFactory;
import com.tkevinb.ragent.rag.service.handler.StreamTaskManager;
import com.tkevinb.ragent.rag.service.pipeline.StreamChatContext;
import com.tkevinb.ragent.rag.service.pipeline.StreamChatPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话服务默认实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGChatServiceImpl implements RAGChatService {

    private final StreamChatPipeline chatPipeline;
    private final StreamCallbackFactory callbackFactory;
    private final StreamTaskManager taskManager;

    @Override
    public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {

        //生成ConversationId
        String actualConversationId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;

        //生成taskId
        String taskId = IdUtil.getSnowflakeNextIdStr();
        log.info("开始流式对话，会话ID：{}，任务ID：{}", actualConversationId, taskId);
        boolean thinkingEnabled = Boolean.TRUE.equals(deepThinking);

        //创建SSE事件处理器
        StreamCallBack callback = callbackFactory.createChatEventHandler(emitter, actualConversationId, taskId);

        //构造StreamChatContext
        //关键点：callback 被放进 Context，Pipeline 每一步产生的内容都通过 callback.onContent() 推送到前端
        // TODO 进一步深入理解 callback
        StreamChatContext ctx = StreamChatContext.builder()
                .question(question)
                .conversationId(actualConversationId)
                .taskId(taskId)
                .deepThinking(thinkingEnabled)
                .userId(UserContext.getUserId())
                .callback(callback)
                .build();

        try {
            chatPipeline.execute(ctx);
        } catch (Exception e) {
            log.error("流式对话处理异常，会话ID：{}，任务ID：{}", actualConversationId, taskId, e);
            callback.onError(e);
        }
    }

    @Override
    public void stopTask(String taskId) {
        taskManager.cancel(taskId);
    }
}
