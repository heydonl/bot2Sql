package com.tecdo.mac.sql2bot.dto;

import lombok.Data;

/**
 * 表导入请求
 */
@Data
public class TableImportRequest {
    /**
     * 表名
     */
    private String tableName;

    /**
     * 数据库名
     */
    private String databaseName;
}
