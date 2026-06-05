package com.tkevinb.ragent.rag.controller;

import com.tkevinb.ragent.framework.convention.Result;
import com.tkevinb.ragent.framework.web.Results;
import com.tkevinb.ragent.rag.config.RAGDefaultProperties;
import com.tkevinb.ragent.rag.service.RAGChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话控制器
 * 提供流式问答与任务取消接口
 */
@RestController
@RequiredArgsConstructor
public class RAGChatController {

    private final RAGChatService ragChatService;
    private final RAGDefaultProperties ragDefaultProperties;

    /**
     * 发起 SSE 流式对话
     */
    @GetMapping(value = "/rag/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam String question,
                           @RequestParam(required = false) String conversationId,
                           @RequestParam(required = false, defaultValue = "false") Boolean deepThinking) {
        //Spring的SSE通道
        SseEmitter emitter = new SseEmitter(ragDefaultProperties.getSseTimeoutMs());

        //传入emitter
        ragChatService.streamChat(question, conversationId, deepThinking, emitter);

        //Spring接管，持续推送
        return emitter;
    }

    /**
     * 停止指定任务
     */
    @PostMapping(value = "/rag/stop")
    public Result<Void> stop(@RequestParam String taskId) {
        ragChatService.stopTask(taskId);
        return Results.success();
    }
}

