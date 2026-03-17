package com.tecdo.mac.sql2bot.dto.intent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 意图分析响应 - 对应 pr.md 中的 JSON 结构
 */
@Data
public class IntentAnalysisResponse {

    /**
     * 意图类型
     */
    private QueryIntent intent;

    /**
     * 实体类型（如 "work_order"）
     */
    private String entity;

    /**
     * 维度字段列表（如 ["status", "advertisers.name"]）
     */
    private List<String> dimensions;

    /**
     * 指标列表
     */
    private List<MetricDefinition> metrics;

    /**
     * 日期范围（单个时间段查询使用）
     */
    private DateRange dateRanges;

    /**
     * 对比时间段（对比查询使用）
     */
    private List<DateRange> comparisonPeriods;

    /**
     * 维度过滤条件
     */
    private FilterCondition dimensionFilter;

    /**
     * 指标过滤条件
     */
    private FilterCondition metricFilter;

    /**
     * 排序规则
     */
    private List<OrderBy> orderBys;

    /**
     * 限制返回行数
     */
    private Integer limit;

    /**
     * 骨架格式（简化表示）- 不在 JSON 中，由后端生成
     */
    private String skeleton;

    /**
     * 原始 JSON 字符串 - 不在 JSON 中，由后端生成
     */
    private String rawJson;

    /**
     * 指标定义
     */
    @Data
    public static class MetricDefinition {
        /**
         * 字段名（* 表示 COUNT(*)）
         */
        private String field;

        /**
         * 聚合函数（COUNT、SUM、AVG、MAX、MIN）
         */
        private String aggregation;
    }

    /**
     * 日期范围
     */
    @Data
    public static class DateRange {
        /**
         * 开始日期（YYYY-MM-DD）
         */
        private String startDate;

        /**
         * 结束日期（YYYY-MM-DD）
         */
        private String endDate;
    }

    /**
     * 过滤条件
     */
    @Data
    public static class FilterCondition {
        /**
         * 单个过滤器
         */
        private Filter filter;

        /**
         * 逻辑运算符（AND、OR）
         */
        private String logicalOperator;

        /**
         * 子过滤条件列表
         */
        private List<FilterCondition> filters;
    }

    /**
     * 过滤器
     */
    @Data
    public static class Filter {
        /**
         * 字段名
         */
        private String fieldName;

        /**
         * 运算符（=、!=、>、<、>=、<=、IN、NOT IN、LIKE）
         */
        private String operator;

        /**
         * 值
         */
        private String value;
    }

    /**
     * 排序规则
     */
    @Data
    public static class OrderBy {
        /**
         * 字段名（可以是聚合函数如 COUNT(*)）
         */
        private String field;

        /**
         * 排序方向（ASCENDING、DESCENDING）
         */
        private String sortOrder;
    }
}
