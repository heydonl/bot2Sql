package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.service.ModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    /**
     * 创建模型
     */
    @PostMapping
    public Result<Model> create(@RequestBody Model model) {
        try {
            Model created = modelService.create(model);
            return Result.success(created);
        } catch (Exception e) {
            log.error("Failed to create model", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询所有模型
     */
    @GetMapping
    public Result<List<Model>> listAll() {
        try {
            List<Model> list = modelService.listAll();
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list models", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据数据源ID查询模型
     */
    @GetMapping("/datasource/{datasourceId}")
    public Result<List<Model>> listByDatasourceId(@PathVariable Long datasourceId) {
        try {
            List<Model> list = modelService.listByDatasourceId(datasourceId);
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list models by datasource", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询可见的模型
     */
    @GetMapping("/visible")
    public Result<List<Model>> listVisible() {
        try {
            List<Model> list = modelService.listVisible();
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list visible models", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据ID查询模型
     */
    @GetMapping("/{id}")
    public Result<Model> getById(@PathVariable Long id) {
        try {
            Model model = modelService.getById(id);
            if (model == null) {
                return Result.error(404, "Model not found");
            }
            return Result.success(model);
        } catch (Exception e) {
            log.error("Failed to get model", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新模型
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Model model) {
        try {
            model.setId(id);
            modelService.update(model);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to update model", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            modelService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to delete model", e);
            return Result.error(e.getMessage());
        }
    }
}
