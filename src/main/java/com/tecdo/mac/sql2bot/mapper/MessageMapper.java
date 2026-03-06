package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话消息 Mapper
 */
@Mapper
public interface MessageMapper {

    /**
     * 插入消息
     */
    int insert(Message message);

    /**
     * 根据ID删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据会话ID删除所有消息
     */
    int deleteByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 根据ID查询
     */
    Message selectById(@Param("id") Long id);

    /**
     * 根据会话ID查询所有消息（按时间排序）
     */
    List<Message> selectByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 查询会话的最近N条消息
     */
    List<Message> selectRecentMessages(@Param("conversationId") Long conversationId,
                                       @Param("limit") Integer limit);
}
