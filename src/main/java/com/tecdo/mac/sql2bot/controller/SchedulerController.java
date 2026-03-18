package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.scheduler.SchemaIndexScheduler;
import com.tecdo.mac.sql2bot.scheduler.SchemaIndexService;
import com.tecdo.mac.sql2bot.service.SchemaVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Schema 索引管理接口
 * 提供全量索引、增量索引、定时任务启停及状态查询功能
 */
@Slf4j
@RestController
@RequestMapping("/api/scheduler/schema-index")
@RequiredArgsConstructor
public class SchedulerController {

    private final SchemaIndexScheduler schemaIndexScheduler;
    private final SchemaIndexService schemaIndexService;
    private final SchemaVectorStoreService schemaVectorStoreService;

    /** 手动触发全量索引 */
    @PostMapping("/full")
    public Result<String> fullIndex() {
        log.info("手动触发全量索引");
        schemaIndexService.fullIndex();
        return Result.success("全量索引完成");
    }

    /** 手动触发增量索引 */
    @PostMapping("/incremental")
    public Result<String> incrementalIndex() {
        log.info("手动触发增量索引");
        schemaIndexService.incrementalIndex();
        return Result.success("增量索引完成");
    }

    /** 启动定时索引任务 */
    @PostMapping("/start")
    public Result<String> start() {
        schemaIndexScheduler.start();
        return Result.success("定时任务已启动");
    }

    /** 停止定时索引任务 */
    @PostMapping("/stop")
    public Result<String> stop() {
        schemaIndexScheduler.stop();
        return Result.success("定时任务已停止");
    }

    /** 查询索引状态 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", schemaIndexScheduler.isRunning());
        status.put("indexCount", schemaVectorStoreService.getIndexCount());
        status.put("lastIndexTime", schemaIndexService.getLastIndexTime());
        return Result.success(status);
    }
}
