package com.tecdo.mac.sql2bot.dto;

import lombok.Data;
import java.util.List;

/**
 * 表信息 DTO
 */
@Data
public class TableInfo {

    /**
     * 数据库名
     */
    private String databaseName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 表注释
     */
    private String tableComment;

    /**
     * 表类型（TABLE, VIEW）
     */
    private String tableType;

    /**
     * 字段列表
     */
    private List<ColumnInfo> columns;
}
