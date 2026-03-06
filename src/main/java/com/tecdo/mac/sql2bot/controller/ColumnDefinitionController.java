package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.service.ColumnDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字段定义管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/columns")
@RequiredArgsConstructor
public class ColumnDefinitionController {

    private final ColumnDefinitionService columnDefinitionService;

    /**
     * 创建字段定义
     */
    @PostMapping
    public Result<ColumnDefinition> create(@RequestBody ColumnDefinition columnDefinition) {
        try {
            ColumnDefinition created = columnDefinitionService.create(columnDefinition);
            return Result.success(created);
        } catch (Exception e) {
            log.error("Failed to create column", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量创建字段定义
     */
    @PostMapping("/batch")
    public Result<Void> batchCreate(@RequestBody List<ColumnDefinition> columns) {
        try {
            columnDefinitionService.batchCreate(columns);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to batch create columns", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据模型ID查询所有字段
     */
    @GetMapping("/model/{modelId}")
    public Result<List<ColumnDefinition>> listByModelId(@PathVariable Long modelId) {
        try {
            List<ColumnDefinition> list = columnDefinitionService.listByModelId(modelId);
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list columns by model", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据字段类型查询
     */
    @GetMapping("/model/{modelId}/type/{columnType}")
    public Result<List<ColumnDefinition>> listByColumnType(@PathVariable Long modelId,
                                                            @PathVariable String columnType) {
        try {
            List<ColumnDefinition> list = columnDefinitionService.listByColumnType(modelId, columnType);
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list columns by type", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据ID查询字段
     */
    @GetMapping("/{id}")
    public Result<ColumnDefinition> getById(@PathVariable Long id) {
        try {
            ColumnDefinition column = columnDefinitionService.getById(id);
            if (column == null) {
                return Result.error(404, "Column not found");
            }
            return Result.success(column);
        } catch (Exception e) {
            log.error("Failed to get column", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新字段定义
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ColumnDefinition columnDefinition) {
        try {
            columnDefinition.setId(id);
            columnDefinitionService.update(columnDefinition);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to update column", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除字段定义
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            columnDefinitionService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to delete column", e);
            return Result.error(e.getMessage());
        }
    }
}
