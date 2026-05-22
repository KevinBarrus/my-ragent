package com.tkevinb.ragent.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tkevinb.ragent.framework.context.UserContext;
import com.tkevinb.ragent.framework.convention.ChatMessage;
import com.tkevinb.ragent.framework.convention.ChatRequest;
import com.tkevinb.ragent.framework.exception.ClientException;
import com.tkevinb.ragent.infra.chat.LLMService;
import com.tkevinb.ragent.rag.config.MemoryProperties;
import com.tkevinb.ragent.rag.controller.request.ConversationUpdateRequest;
import com.tkevinb.ragent.rag.controller.vo.ConversationVO;
import com.tkevinb.ragent.rag.core.prompt.PromptTemplateLoader;
import com.tkevinb.ragent.rag.dao.entity.ConversationDO;
import com.tkevinb.ragent.rag.dao.entity.ConversationMessageDO;
import com.tkevinb.ragent.rag.dao.entity.ConversationSummaryDO;
import com.tkevinb.ragent.rag.dao.mapper.ConversationMapper;
import com.tkevinb.ragent.rag.dao.mapper.ConversationMessageMapper;
import com.tkevinb.ragent.rag.dao.mapper.ConversationSummaryMapper;
import com.tkevinb.ragent.rag.service.ConversationService;
import com.tkevinb.ragent.rag.service.bo.ConversationCreateBO;
import jdk.jfr.MemoryAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.tkevinb.ragent.rag.constant.RAGConstant.CONVERSATION_TITLE_PROMPT_PATH;

/**
 * 会话服务实现类
 * 处理会话的创建、更新、重命名和删除等业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final MemoryProperties memoryProperties;
    private final ConversationMessageMapper messageMapper;
    private final ConversationSummaryMapper summaryMapper;
    private final PromptTemplateLoader promptTemplateLoader;
    private final LLMService llmService;

    /**
     * 根据用户ID获取会话列表
     *
     * @param userId 用户ID
     * @return 会话视图对象列表
     */
    @Override
    public List<ConversationVO> listByUserId(String userId) {

        //1.如果是 null 或空串，返回空列表
        if(StrUtil.isBlank(userId)) {
            return List.of();
        }

        //2.去数据库查找是否有匹配的条目，按照最新时间倒序排序
        List<ConversationDO> records = conversationMapper.selectList(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getUserId,userId)
                        .eq(ConversationDO::getDeleted,0)
                        .orderByDesc(ConversationDO::getLastTime)
        );

        //3.如果没匹配到，返回空列表
        if(records == null || records.isEmpty()) {
            return List.of();
        }

        //4.将 DO 封装为 VO，返回
        return records.stream()
                .map(item -> ConversationVO.builder()
                        .conversationId(item.getConversationId())
                        .title(item.getTitle())
                        .lastTime(item.getLastTime())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 创建或更新会话
     * 如果 ConversationCreateBO 里的会话 ID 存在则更新，不存在则创建
     *
     * @param request 创建请求对象
     */
    @Override
    public void createOrUpdate(ConversationCreateBO request) {

        //1.先抽取字段，因为后面要判断用户是否存在/根据用户问题生成标题
        String userId = request.getUserId();
        String question = request.getQuestion();
        Date lastTime = request.getLastTime();
        String conversationId = request.getConversationId();

        //2.如果用户不存在，返回错误
        if(StrUtil.isBlank(userId)) {
            throw new ClientException("用户信息缺失");
        }

        //3.查找是否存在这样的会话
        ConversationDO conversation = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getUserId,userId)
                        .eq(ConversationDO::getConversationId,conversationId)
                        .eq(ConversationDO::getDeleted,0)
        );

        //4.如果不存在，就创建会话
        if(conversation == null) {
            //生成标题
            String title = generateTitleFromQuestion(question);

            //组装
            ConversationDO record = ConversationDO.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .title(title)
                    .lastTime(lastTime)
                    .build();

            //向数据库插入一条新数据
            conversationMapper.insert(record);

            return;
        }

        //如果存在，只需更新最新时间
        conversation.setLastTime(request.getLastTime());

        //更新数据库数据
        conversationMapper.updateById(conversation);
    }

    private String generateTitleFromQuestion(String question) {
        int maxLen = memoryProperties.getTitleMaxLength();
        if (maxLen <= 0) {
            maxLen = 30;
        }
        String prompt = promptTemplateLoader.render(
                CONVERSATION_TITLE_PROMPT_PATH,
                Map.of(
                        "title_max_chars", String.valueOf(maxLen),
                        "question", question
                )
        );

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(prompt)))
                    .temperature(0.7D)
                    .topP(0.3D)
                    .thinking(false)
                    .build();

            //在 LLMService 接口中没有给出默认实现，具体 chat 的实现看实现类的重写
            return llmService.chat(request);
        } catch (Exception ex) {
            log.warn("生成会话标题失败", ex);

            //标题生成也只是锦上添花的功能，如果没能完成，就直接返回新对话
            return "新对话";
        }
    }
    /**
     * 重命名会话
     *
     * @param conversationId 会话 ID
     * @param request        更新请求对象，里面只有 title 字段
     */
    @Override
    public void rename(String conversationId, ConversationUpdateRequest request) {

        //1.获取用户 Id ，如果是 null 或空，抛出异常
        String userId = UserContext.getUserId();
        if(StrUtil.isBlank(userId)) {
            throw new ClientException("用户信息缺失");
        }

        //2.如果对话 Id 是 null 或空，抛出异常
        if(StrUtil.isBlank(conversationId)) {
            throw new ClientException("会话信息缺失");
        }

        //3.会话标题不能为空
        String title = request.getTitle();
        if(StrUtil.isBlank(title)) {
            throw new ClientException("会话标题不能为空");
        }

        //4.会话标题不能过长
        int maxLen = memoryProperties.getTitleMaxLength();
        if(title.length() > maxLen) {
            throw new ClientException("会话标题不能超过" + maxLen + "个字符");
        }

        //5.去数据库查找对应会话
        ConversationDO record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );

        //6.如果没有对应会话，抛出异常
        if(record == null) {
            throw new ClientException("会话不存在");
        }

        //7.更新数据库对应数据
        record.setTitle(title);
        conversationMapper.updateById(record);

    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(String conversationId) {

        //1.获取用户 Id ，如果是 null 或空，抛出异常
        // TODO 在 framework 模块的 context 包下创建 UserContext 类
        String userId = UserContext.getUserId();
        if(StrUtil.isBlank(userId)) {
            throw new ClientException("用户信息缺失");
        }

        //2.如果对话 Id 是 null 或空，抛出异常
        if(StrUtil.isBlank(conversationId)) {
            throw new ClientException("会话信息缺失");
        }

        //3.去数据库查找对应会话
        ConversationDO record = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );

        //4.如果没有对应会话，抛出异常
        if(record == null) {
            throw new ClientException("会话不存在");
        }

        //5.删除数据库对应数据
        conversationMapper.deleteById(record.getId());

        //6.删除存储消息的数据库对应数据
        messageMapper.delete(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId,conversationId)
                        .eq(ConversationMessageDO::getUserId,userId)
                        .eq(ConversationMessageDO::getDeleted,0)
        );
        summaryMapper.delete(
                Wrappers.lambdaQuery(ConversationSummaryDO.class)
                        .eq(ConversationSummaryDO::getConversationId, conversationId)
                        .eq(ConversationSummaryDO::getUserId, userId)
                        .eq(ConversationSummaryDO::getDeleted, 0)
        );
    }
}
