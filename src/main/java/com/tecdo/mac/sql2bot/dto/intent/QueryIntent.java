package com.tecdo.mac.sql2bot.dto.intent;

/**
 * 查询意图类型
 */
public enum QueryIntent {
    /**
     * 单指标查询（如工单数、消耗金额）
     */
    SINGLE_METRIC_QUERY,

    /**
     * 对比查询（两个时间段对比）
     */
    COMPARISON_QUERY,

    /**
     * 排名查询（如 top N）
     */
    RANKING_QUERY,

    /**
     * 趋势查询（如月度趋势）
     */
    TREND_QUERY,

    /**
     * 明细查询（列出符合条件的记录）
     */
    DETAIL_QUERY,

    /**
     * 其他不属于以上类型的查询
     */
    OTHER
}
