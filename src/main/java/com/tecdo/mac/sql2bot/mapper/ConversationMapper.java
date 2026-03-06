package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话会话 Mapper
 */
@Mapper
public interface ConversationMapper {

    /**
     * 插入会话
     */
    int insert(Conversation conversation);

    /**
     * 根据ID删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新会话
     */
    int update(Conversation conversation);

    /**
     * 根据ID查询
     */
    Conversation selectById(@Param("id") Long id);

    /**
     * 根据用户ID查询所有会话
     */
    List<Conversation> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询所有会话
     */
    List<Conversation> selectAll();

    /**
     * 更新会话标题
     */
    int updateTitle(@Param("id") Long id, @Param("title") String title);
}
