package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.Database;
import com.tecdo.mac.sql2bot.service.DatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据库管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/databases")
@RequiredArgsConstructor
public class DatabaseController {

    private final DatabaseService databaseService;

    /**
     * 根据数据源ID查询所有数据库
     */
    @GetMapping("/datasource/{datasourceId}")
    public Result<List<Database>> listByDatasourceId(@PathVariable Long datasourceId) {
        try {
            List<Database> databases = databaseService.listByDatasourceId(datasourceId);
            return Result.success(databases);
        } catch (Exception e) {
            log.error("Failed to list databases", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据ID查询数据库
     */
    @GetMapping("/{id}")
    public Result<Database> getById(@PathVariable Long id) {
        try {
            Database database = databaseService.getById(id);
            if (database == null) {
                return Result.error(404, "Database not found");
            }
            return Result.success(database);
        } catch (Exception e) {
            log.error("Failed to get database", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新数据库
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Database database) {
        try {
            database.setId(id);
            databaseService.update(database);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to update database", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除数据库
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            databaseService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to delete database", e);
            return Result.error(e.getMessage());
        }
    }
}
