package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.dto.QueryLogFilter;
import com.tecdo.mac.sql2bot.dto.QueryLogStats;
import com.tecdo.mac.sql2bot.service.QueryLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 查询日志管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/query-logs")
@RequiredArgsConstructor
public class QueryLogController {

    private final QueryLogService queryLogService;

    /**
     * 查询日志列表
     */
    @GetMapping
    public Result<List<QueryLog>> list(QueryLogFilter filter) {
        try {
            List<QueryLog> logs = queryLogService.findByFilter(filter);
            return Result.success(logs);
        } catch (Exception e) {
            log.error("查询日志列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取日志详情
     */
    @GetMapping("/{id}")
    public Result<QueryLog> getById(@PathVariable Long id) {
        try {
            QueryLog queryLog = queryLogService.findById(id);
            if (queryLog == null) {
                return Result.error(404, "查询日志不存在: id=" + id);
            }
            return Result.success(queryLog);
        } catch (Exception e) {
            log.error("获取查询日志详情失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取查询统计
     */
    @GetMapping("/stats")
    public Result<QueryLogStats> getStats() {
        try {
            QueryLogStats stats = queryLogService.getStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取查询统计失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取意图分布统计
     */
    @GetMapping("/intent-distribution")
    public Result<Map<String, Long>> getIntentDistribution() {
        try {
            Map<String, Long> distribution = queryLogService.getIntentDistribution();
            return Result.success(distribution);
        } catch (Exception e) {
            log.error("获取意图分布统计失败", e);
            return Result.error(e.getMessage());
        }
    }
}
