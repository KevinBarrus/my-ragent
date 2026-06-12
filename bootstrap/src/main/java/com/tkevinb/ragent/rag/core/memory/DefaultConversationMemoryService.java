package com.tkevinb.ragent.rag.core.memory;

import cn.hutool.core.util.StrUtil;
import com.tkevinb.ragent.framework.convention.ChatMessage;
import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.infra.chat.LLMService;
import com.tkevinb.ragent.rag.controller.vo.ConversationMessageVO;
import com.tkevinb.ragent.rag.enums.ConversationMessageOrder;
import com.tkevinb.ragent.rag.service.ConversationGroupService;
import com.tkevinb.ragent.rag.service.ConversationMessageService;
import com.tkevinb.ragent.rag.service.bo.ConversationMessageBO;
import com.tkevinb.ragent.rag.service.bo.ConversationSummaryBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对话记忆服务（支持摘要压缩）
 * <p>
 * 三种策略：
 * ① 滑动窗口（默认）：最近 20 条消息
 * ② 摘要压缩（触发条件 > 10 条）：前 6 条压缩为摘要 → 替代原始消息
 */
@Slf4j
@Service
public class DefaultConversationMemoryService implements ConversationMemoryService {

    private static final int DEFAULT_HISTORY_LIMIT = 20;
    private static final int COMPRESS_THRESHOLD = 10;
    private static final int COMPRESS_COUNT = 6;

    private final ConversationMessageService messageService;
    private final ConversationGroupService groupService;
    private final LLMService llmService;

    public DefaultConversationMemoryService(ConversationMessageService messageService,
                                             ConversationGroupService groupService,
                                             LLMService llmService) {
        this.messageService = messageService;
        this.groupService = groupService;
        this.llmService = llmService;
    }

    @Override
    public List<ChatMessage> load(String conversationId, String userId) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) return List.of();

        try {
            // 1. 检查是否有摘要
            var summaryDo = groupService.findLatestSummary(conversationId, userId);
            String summaryText = (summaryDo != null) ? summaryDo.getContent() : null;

            // 2. 取最近消息
            List<ConversationMessageVO> messages = messageService.listMessages(
                    conversationId, userId, DEFAULT_HISTORY_LIMIT, ConversationMessageOrder.ASC);
            if (messages == null || messages.isEmpty()) return List.of();

            List<ChatMessage> result = new ArrayList<>();

            // 3. 有摘要时：作为 SYSTEM 消息放在最前面（节省 token）
            if (StrUtil.isNotBlank(summaryText)) {
                result.add(ChatMessage.system("以下是之前的对话摘要：\n" + summaryText));
            }

            // 4. 追加消息
            for (ConversationMessageVO vo : messages) {
                ChatMessage msg = new ChatMessage(
                        ChatMessage.Role.fromString(vo.getRole()), vo.getContent());
                if (vo.getThinkingContent() != null) msg.setThinkingContent(vo.getThinkingContent());
                result.add(msg);
            }

            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            log.error("加载对话记忆失败 - conversationId: {}", conversationId, e);
            return List.of();
        }
    }

    @Override
    public String append(String conversationId, String userId, ChatMessage message) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId) || message == null) return null;

        ConversationMessageBO bo = ConversationMessageBO.builder()
                .conversationId(conversationId).userId(userId)
                .role(message.getRole().name().toLowerCase()).content(message.getContent())
                .thinkingContent(message.getThinkingContent()).thinkingDuration(message.getThinkingDuration())
                .build();
        try {
            String msgId = messageService.addMessage(bo);
            compressIfNeeded(conversationId, userId, message);
            return msgId;
        } catch (Exception e) {
            log.error("追加消息失败 - conversationId: {}", conversationId, e);
            return null;
        }
    }

    /** 消息数量超阈值 → 压缩前几条消息为摘要 */
    private void compressIfNeeded(String conversationId, String userId, ChatMessage message) {
        try {
            long count = groupService.countUserMessages(conversationId, userId);
            if (count < COMPRESS_THRESHOLD) return;

            // 取前 COMPRESS_COUNT 条消息 + 新消息 → 压缩
            List<ConversationMessageVO> recent = messageService.listMessages(
                    conversationId, userId, COMPRESS_COUNT, ConversationMessageOrder.ASC);
            if (recent == null || recent.isEmpty()) return;

            StringBuilder sb = new StringBuilder();
            for (ConversationMessageVO m : recent) {
                sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
            }
            sb.append("assistant: ").append(message.getContent());

            String prompt = "请将以下对话压缩成一段 100 字以内的摘要，保留关键信息和上下文：\n\n" + sb;

            String summary = llmService.chat(prompt);
            if (StrUtil.isBlank(summary)) return;

            messageService.addMessageSummary(ConversationSummaryBO.builder()
                    .conversationId(conversationId).userId(userId)
                    .content(summary).build());
            log.info("会话 {} 已压缩摘要: {}", conversationId, StrUtil.maxLength(summary, 80));
        } catch (Exception e) {
            log.warn("会话摘要压缩失败: {}", e.getMessage());
        }
    }
}
