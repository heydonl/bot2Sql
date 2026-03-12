package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.Relationship;
import com.tecdo.mac.sql2bot.service.RelationshipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 关系管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/relationships")
@RequiredArgsConstructor
public class RelationshipController {

    private final RelationshipService relationshipService;

    /**
     * 创建关系
     */
    @PostMapping
    public Result<Relationship> create(@RequestBody Relationship relationship) {
        try {
            Relationship created = relationshipService.create(relationship);
            return Result.success(created);
        } catch (Exception e) {
            log.error("Failed to create relationship", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询所有关系
     */
    @GetMapping
    public Result<List<Relationship>> listAll() {
        try {
            List<Relationship> list = relationshipService.listAll();
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list relationships", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据模型ID查询所有相关关系
     */
    @GetMapping("/model/{modelId}")
    public Result<List<Relationship>> listByModelId(@PathVariable Long modelId) {
        try {
            List<Relationship> list = relationshipService.listByModelId(modelId);
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list relationships by model", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据工作区ID查询关系
     */
    @GetMapping("/workspace/{workspaceId}")
    public Result<List<Relationship>> listByWorkspaceId(@PathVariable Long workspaceId) {
        try {
            List<Relationship> list = relationshipService.listByWorkspaceId(workspaceId);
            return Result.success(list);
        } catch (Exception e) {
            log.error("Failed to list relationships by workspace", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 根据ID查询关系
     */
    @GetMapping("/{id}")
    public Result<Relationship> getById(@PathVariable Long id) {
        try {
            Relationship relationship = relationshipService.getById(id);
            if (relationship == null) {
                return Result.error(404, "Relationship not found");
            }
            return Result.success(relationship);
        } catch (Exception e) {
            log.error("Failed to get relationship", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新关系
     */
    @PutMapping("/{id}")
    public Result<Relationship> update(@PathVariable Long id, @RequestBody Relationship relationship) {
        try {
            relationship.setId(id);
            relationshipService.update(relationship);
            // 返回更新后的关系数据
            Relationship updated = relationshipService.getById(id);
            return Result.success(updated);
        } catch (Exception e) {
            log.error("Failed to update relationship", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除关系
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            relationshipService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("Failed to delete relationship", e);
            return Result.error(e.getMessage());
        }
    }
}
