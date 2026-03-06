package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对话消息实体
 */
@Data
public class Message {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 角色: user, assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 生成的SQL查询
     */
    private String sqlQuery;

    /**
     * 查询结果数据（JSON格式）
     */
    private String resultData;

    /**
     * 图表配置（JSON格式）
     */
    private String chartConfig;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
