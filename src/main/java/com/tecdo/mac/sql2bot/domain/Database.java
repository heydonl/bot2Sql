package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据库实体
 */
@Data
public class Database {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 数据源ID
     */
    private Long datasourceId;

    /**
     * 数据库名
     */
    private String databaseName;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
