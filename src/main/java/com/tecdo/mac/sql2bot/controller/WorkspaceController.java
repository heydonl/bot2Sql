package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.Workspace;
import com.tecdo.mac.sql2bot.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作区管理控制器
 */
@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    /**
     * 获取所有工作区
     */
    @GetMapping
    public Result<List<Workspace>> getAllWorkspaces() {
        List<Workspace> workspaces = workspaceService.getAllWorkspaces();
        return Result.success(workspaces);
    }

    /**
     * 获取活跃的工作区
     */
    @GetMapping("/active")
    public Result<List<Workspace>> getActiveWorkspaces() {
        List<Workspace> workspaces = workspaceService.getActiveWorkspaces();
        return Result.success(workspaces);
    }

    /**
     * 获取最高优先级的工作区
     */
    @GetMapping("/highest-priority")
    public Result<Workspace> getHighestPriorityWorkspace() {
        Workspace workspace = workspaceService.getHighestPriorityWorkspace();
        return Result.success(workspace);
    }

    /**
     * 根据ID获取工作区
     */
    @GetMapping("/{id}")
    public Result<Workspace> getWorkspaceById(@PathVariable Long id) {
        Workspace workspace = workspaceService.getWorkspaceById(id);
        if (workspace == null) {
            return Result.error("工作区不存在");
        }
        return Result.success(workspace);
    }

    /**
     * 创建工作区
     */
    @PostMapping
    public Result<Workspace> createWorkspace(@RequestBody Workspace workspace) {
        Workspace created = workspaceService.createWorkspace(workspace);
        return Result.success(created);
    }

    /**
     * 更新工作区
     */
    @PutMapping("/{id}")
    public Result<Workspace> updateWorkspace(@PathVariable Long id, @RequestBody Workspace workspace) {
        Workspace updated = workspaceService.updateWorkspace(id, workspace);
        return Result.success(updated);
    }

    /**
     * 删除工作区
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteWorkspace(@PathVariable Long id) {
        workspaceService.deleteWorkspace(id);
        return Result.success();
    }

    /**
     * 更新工作区状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateWorkspaceStatus(@PathVariable Long id, @RequestParam Boolean isActive) {
        workspaceService.updateWorkspaceStatus(id, isActive);
        return Result.success();
    }
}