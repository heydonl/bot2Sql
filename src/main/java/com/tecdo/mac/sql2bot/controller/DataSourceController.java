package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.DataSource;
import com.tecdo.mac.sql2bot.dto.ImportResult;
import com.tecdo.mac.sql2bot.dto.TableInfo;
import com.tecdo.mac.sql2bot.service.DataSourceService;
import com.tecdo.mac.sql2bot.service.SchemaDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final SchemaDiscoveryService schemaDiscoveryService;

    /**
     * 创建数据源
     */
    @PostMapping
    public Result<DataSource> create(@RequestBody DataSource dataSource) {
        try {
            DataSource created = dataSourceService.create(dataSource);
            return Result.success(created);
        } catch (Exception e) {
            log.error("Failed to create datasource", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 测试数据源连接
     */
    @PostMapping("/test")
    public Result<Boolean> testConnection(@RequestBody DataSource dataSource) {
        try {
            boolean success = dataSourceService.testConnection(dataSource);
            return Result.success(success);
        } catch (Exception e) {
            log.error("Failed to test connection", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询所有数据源
     */
    @GetMapping
    public Result<List<DataSource>> listAll() {
        try {
            List<DataSource> list = dataSourceService.listAll();
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list datasources", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据ID查询数据源
     */
    @GetMapping("/{id}")
    public Result<DataSource> getById(@PathVariable Long id) {
        try {
            DataSource dataSource = dataSourceService.getById(id);
            if (dataSource == null) {
                return Result.error(404, "DataSource not found");
            }
            return Result.success(dataSource);
        } catch (Exception e) {
            log.error("Failed to get datasource", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新数据源
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DataSource dataSource) {
        try {
            dataSource.setId(id);
            dataSourceService.update(dataSource);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to update datasource", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除数据源
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            dataSourceService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to delete datasource", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 发现数据源中的所有表结构
     */
    @GetMapping("/{id}/discover")
    public Result<List<TableInfo>> discoverTables(@PathVariable Long id) {
        try {
            List<TableInfo> tables = schemaDiscoveryService.discoverTables(id);
            return Result.success(tables);
        } catch (Exception e) {
            log.error("Failed to discover tables", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 导入表结构到语义模型
     */
    @PostMapping("/{id}/import")
    public Result<ImportResult> importTables(@PathVariable Long id,
                                              @RequestBody(required = false) List<String> tableNames) {
        try {
            ImportResult result = schemaDiscoveryService.importTables(id, tableNames);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to import tables", e);
            return Result.error(e.getMessage());
        }
    }
}
