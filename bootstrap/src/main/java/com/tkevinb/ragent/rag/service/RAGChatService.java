package com.tkevinb.ragent.rag.service;

/**
 * RAG 对话服务接口
 * 对外暴露流式问答与任务停止能力，屏蔽控制器层之外的实现细节
 */
public interface RAGChatService {

    /**
     * 发起一次 SSE 流式问答
     *
     * @param question       用户问题
     * @param conversationId 会话 ID（可选，空时创建新会话）
     * @param deepThinking   是否开启深度思考模式
     */
    // TODO 后续添加 SSE 发射器参数 SseEmitter emitter
    void streamChat(String question, String conversationId, Boolean deepThinking);

    /**
     * 停止指定任务 ID 的流式会话
     *
     * @param taskId 任务 ID
     */
    void stopTask(String taskId);
}
