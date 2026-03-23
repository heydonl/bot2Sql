package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.dto.QueryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话管理服务
 * 处理会话创建、验证和消息保存
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationManagementService {

    private final ConversationService conversationService;
    private final MessageService messageService;

    /**
     * 处理会话逻辑
     */
    @Transactional
    public Long handleConversation(QueryRequest request) {
        Long conversationId = request.getConversationId();

        // 验证现有会话
        if (conversationId != null) {
            if (!conversationService.belongsToUser(conversationId, request.getUserId())) {
                throw new IllegalArgumentException("无权访问该会话");
            }
        }
        // 创建新会话
        else if (Boolean.TRUE.equals(request.getCreateNewConversation())) {
            var conversation = conversationService.create(
                request.getUserId(),
                generateConversationTitle(request.getQuestion()),
                null
            );
            conversationId = conversation.getId();
            log.info("创建新会话: id={}, userId={}", conversationId, request.getUserId());
        }

        // 保存用户消息
        if (conversationId != null) {
            messageService.saveUserMessage(conversationId, request.getQuestion());
        }

        return conversationId;
    }

    /**
     * 保存错误消息
     */
    @Transactional
    public void saveErrorMessage(Long conversationId, String errorMessage) {
        if (conversationId != null) {
            messageService.saveErrorMessage(conversationId, errorMessage);
        }
    }

    /**
     * 生成会话标题
     */
    private String generateConversationTitle(String question) {
        if (question.length() <= 30) {
            return question;
        }
        return question.substring(0, 27) + "...";
    }
}