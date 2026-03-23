package com.tecdo.mac.sql2bot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 执行步骤
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlStep {
    /**
     * SQL 模板（可能包含 {{param}} 占位符）
     */
    private String sqlTemplate;

    /**
     * 数据源 ID（null 表示从 SQL 推断）
     */
    private Long datasourceId;
}
