package com.tecdo.mac.sql2bot.dto;

import lombok.Data;
import java.util.List;

/**
 * 表信息辅助类，用于解析intent_sql中的tables数组和数据库发现
 */
@Data
public class TableInfo {
    private String name;
    private String database;

    // 数据库发现相关字段
    private String tableName;
    private String tableComment;
    private String tableType;
    private String databaseName;
    private List<ColumnInfo> columns;

    public TableInfo() {}

    public TableInfo(String name, String database) {
        this.name = name;
        this.database = database;
    }
}