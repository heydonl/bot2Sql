package com.tecdo.mac.sql2bot.service;

import com.google.gson.Gson;
import com.tecdo.mac.sql2bot.domain.Message;
import com.tecdo.mac.sql2bot.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 对话消息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;
    private final Gson gson = new Gson();

    /**
     * 保存用户消息
     */
    @Transactional
    public Message saveUserMessage(Long conversationId, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setContent(content);

        messageMapper.insert(message);
        log.info("Saved user message: id={}, conversationId={}", message.getId(), conversationId);
        return message;
    }

    /**
     * 保存助手消息
     */
    @Transactional
    public Message saveAssistantMessage(Long conversationId, String content,
                                       String sql, List<Map<String, Object>> resultData) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole("assistant");
        message.setContent(content);
        message.setSqlQuery(sql);

        if (resultData != null) {
            message.setResultData(gson.toJson(resultData));
        }

        messageMapper.insert(message);
        log.info("Saved assistant message: id={}, conversationId={}", message.getId(), conversationId);
        return message;
    }

    /**
     * 保存错误消息
     */
    @Transactional
    public Message saveErrorMessage(Long conversationId, String errorMessage) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole("assistant");
        message.setContent("抱歉，查询执行失败：" + errorMessage);
        message.setErrorMessage(errorMessage);

        messageMapper.insert(message);
        log.info("Saved error message: id={}, conversationId={}", message.getId(), conversationId);
        return message;
    }

    /**
     * 根据会话ID查询所有消息
     */
    public List<Message> listByConversationId(Long conversationId) {
        return messageMapper.selectByConversationId(conversationId);
    }

    /**
     * 查询会话的最近N条消息
     */
    public List<Message> getRecentMessages(Long conversationId, Integer limit) {
        return messageMapper.selectRecentMessages(conversationId, limit);
    }

    /**
     * 删除会话的所有消息
     */
    @Transactional
    public void deleteByConversationId(Long conversationId) {
        messageMapper.deleteByConversationId(conversationId);
        log.info("Deleted all messages for conversation: {}", conversationId);
    }

    /**
     * 构建对话历史上下文（用于多轮对话）
     */
    public String buildConversationContext(Long conversationId, Integer limit) {
        List<Message> recentMessages = getRecentMessages(conversationId, limit != null ? limit : 5);

        if (recentMessages.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\n## 对话历史\n\n");

        // 反转顺序，从旧到新
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            Message msg = recentMessages.get(i);
            if ("user".equals(msg.getRole())) {
                context.append("用户: ").append(msg.getContent()).append("\n");
            } else if ("assistant".equals(msg.getRole())) {
                if (msg.getSqlQuery() != null) {
                    context.append("助手: 生成了 SQL 查询\n");
                    context.append("```sql\n").append(msg.getSqlQuery()).append("\n```\n");
                }
                if (msg.getContent() != null) {
                    context.append(msg.getContent()).append("\n");
                }
            }
            context.append("\n");
        }

        return context.toString();
    }
}
