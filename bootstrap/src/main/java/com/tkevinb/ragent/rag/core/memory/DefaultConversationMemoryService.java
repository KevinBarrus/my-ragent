package com.tkevinb.ragent.rag.core.memory;

import cn.hutool.core.util.StrUtil;
import com.tkevinb.ragent.framework.convention.ChatMessage;
import com.tkevinb.ragent.rag.controller.vo.ConversationMessageVO;
import com.tkevinb.ragent.rag.enums.ConversationMessageOrder;
import com.tkevinb.ragent.rag.service.ConversationMessageService;
import com.tkevinb.ragent.rag.service.bo.ConversationMessageBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认对话记忆服务（简化版）
 * <p>
 * 使用已有的 ConversationMessageService 实现对话历史的加载与追加。
 * MVP 阶段不引入摘要记忆和并行加载，保持链路简洁可讲解。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultConversationMemoryService implements ConversationMemoryService {

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final ConversationMessageService messageService;

    @Override
    public List<ChatMessage> load(String conversationId, String userId) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return List.of();
        }

        try {
            // 按时间正序获取最近 N 条消息
            List<ConversationMessageVO> messages = messageService.listMessages(
                    conversationId, userId, DEFAULT_HISTORY_LIMIT, ConversationMessageOrder.ASC
            );

            if (messages == null || messages.isEmpty()) {
                return List.of();
            }

            List<ChatMessage> result = new ArrayList<>();
            for (ConversationMessageVO vo : messages) {
                ChatMessage msg = new ChatMessage(
                        ChatMessage.Role.fromString(vo.getRole()),
                        vo.getContent()
                );
                if (vo.getThinkingContent() != null) {
                    msg.setThinkingContent(vo.getThinkingContent());
                }
                result.add(msg);
            }

            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            log.error("加载对话记忆失败 - conversationId: {}, userId: {}", conversationId, userId, e);
            return List.of();
        }
    }

    @Override
    public String append(String conversationId, String userId, ChatMessage message) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId) || message == null) {
            return null;
        }

        ConversationMessageBO bo = ConversationMessageBO.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(message.getRole().name().toLowerCase())
                .content(message.getContent())
                .thinkingContent(message.getThinkingContent())
                .thinkingDuration(message.getThinkingDuration())
                .build();

        try {
            return messageService.addMessage(bo);
        } catch (Exception e) {
            log.error("追加消息失败 - conversationId: {}, userId: {}", conversationId, userId, e);
            return null;
        }
    }
}
