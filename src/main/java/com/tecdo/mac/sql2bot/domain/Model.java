package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 表模型实体
 */
@Data
public class Model {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 数据源ID
     */
    private Long datasourceId;

    /**
     * 物理表名
     */
    private String tableName;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 业务描述
     */
    private String description;

    /**
     * 是否对查询开放
     */
    private Boolean isVisible;

    /**
     * 主键字段名
     */
    private String primaryKey;

    /**
     * 额外属性(JSON格式)
     */
    private String properties;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
