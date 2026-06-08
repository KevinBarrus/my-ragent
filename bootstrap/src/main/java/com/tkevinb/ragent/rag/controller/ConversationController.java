package com.tkevinb.ragent.rag.controller;

import com.tkevinb.ragent.framework.convention.Result;
import com.tkevinb.ragent.framework.web.Results;
import com.tkevinb.ragent.rag.service.ConversationService;
import com.tkevinb.ragent.rag.service.ConversationMessageService;
import com.tkevinb.ragent.rag.service.bo.ConversationCreateBO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话控制器 — 兼容 ragent 前端
 */
@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMessageService messageService;

    public ConversationController(ConversationService conversationService,
                                   ConversationMessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    /** 获取会话列表 — 临时返回空列表 */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Results.success(List.of());
    }

    /** 获取会话消息列表 — 临时返回空列表 */
    @GetMapping("/{id}/messages")
    public Result<List<Map<String, Object>>> messages(@PathVariable String id) {
        return Results.success(List.of());
    }

    /** 创建会话 */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Results.success(Map.of("conversationId", "new-" + System.currentTimeMillis()));
    }

    @PutMapping("/{id}")
    public Result<Void> rename(@PathVariable String id, @RequestBody Map<String, String> body) {
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        return Results.success();
    }
}
