package com.tecdo.mac.sql2bot.dto;

import lombok.Data;

/**
 * 表信息辅助类，用于解析intent_sql中的tables数组
 */
@Data
public class TableInfo {
    private String name;
    private String database;

    public TableInfo() {}

    public TableInfo(String name, String database) {
        this.name = name;
        this.database = database;
    }
}