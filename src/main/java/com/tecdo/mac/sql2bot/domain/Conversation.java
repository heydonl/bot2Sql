package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对话会话实体
 */
@Data
public class Conversation {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 关联数据源ID
     */
    private Long datasourceId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
