package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.Conversation;
import com.tecdo.mac.sql2bot.mapper.ConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 对话会话服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;

    /**
     * 创建会话
     */
    @Transactional
    public Conversation create(Long userId, String title, Long datasourceId) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title != null ? title : "新对话");
        conversation.setDatasourceId(datasourceId);

        conversationMapper.insert(conversation);
        log.info("Created conversation: id={}, userId={}, title={}",
                conversation.getId(), userId, title);
        return conversation;
    }

    /**
     * 根据ID查询
     */
    public Conversation getById(Long id) {
        return conversationMapper.selectById(id);
    }

    /**
     * 根据用户ID查询所有会话
     */
    public List<Conversation> listByUserId(Long userId) {
        return conversationMapper.selectByUserId(userId);
    }

    /**
     * 查询所有会话
     */
    public List<Conversation> listAll() {
        return conversationMapper.selectAll();
    }

    /**
     * 更新会话标题
     */
    @Transactional
    public void updateTitle(Long id, String title) {
        conversationMapper.updateTitle(id, title);
        log.info("Updated conversation title: id={}, title={}", id, title);
    }

    /**
     * 删除会话
     */
    @Transactional
    public void delete(Long id) {
        conversationMapper.deleteById(id);
        log.info("Deleted conversation: id={}", id);
    }

    /**
     * 验证会话是否属于指定用户
     */
    public boolean belongsToUser(Long conversationId, Long userId) {
        Conversation conversation = getById(conversationId);
        return conversation != null && conversation.getUserId().equals(userId);
    }
}
