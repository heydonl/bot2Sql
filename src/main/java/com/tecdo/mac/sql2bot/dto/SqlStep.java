package com.tecdo.mac.sql2bot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SQL 执行步骤
 */
@Data
@NoArgsConstructor
public class SqlStep {
    /**
     * 步骤ID（如 step1, step2）
     */
    private String id;

    /**
     * SQL 模板（可能包含 {{param}} 占位符）
     */
    private String sqlTemplate;

    /**
     * 数据源 ID（null 表示从 SQL 推断）
     */
    private Long datasourceId;

    /**
     * 涉及的表信息列表
     */
    private List<TableInfo> tables;

    /**
     * 参数映射（用于参数填充）
     */
    private Map<String, Object> params;

    /**
     * 向后兼容的构造函数
     */
    public SqlStep(String sqlTemplate, Long datasourceId) {
        this.sqlTemplate = sqlTemplate;
        this.datasourceId = datasourceId;
    }

    /**
     * 表信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableInfo {
        /**
         * 表名
         */
        private String name;

        /**
         * 数据库名
         */
        private String database;
    }
}
