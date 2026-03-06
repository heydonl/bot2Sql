package com.tecdo.mac.sql2bot.dto;

import lombok.Data;

/**
 * 字段信息 DTO
 */
@Data
public class ColumnInfo {

    /**
     * 字段名
     */
    private String columnName;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 字段注释
     */
    private String columnComment;

    /**
     * 是否可为空
     */
    private Boolean isNullable;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 是否主键
     */
    private Boolean isPrimaryKey;

    /**
     * 字段长度
     */
    private Integer columnSize;
}
