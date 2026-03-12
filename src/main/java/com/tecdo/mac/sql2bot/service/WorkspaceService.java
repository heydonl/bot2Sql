package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.Workspace;
import com.tecdo.mac.sql2bot.mapper.WorkspaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 工作区服务层
 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;

    /**
     * 获取所有工作区（按优先级排序）
     */
    public List<Workspace> getAllWorkspaces() {
        return workspaceMapper.selectAll();
    }

    /**
     * 获取活跃的工作区（按优先级排序）
     */
    public List<Workspace> getActiveWorkspaces() {
        return workspaceMapper.selectActive();
    }

    /**
     * 根据ID获取工作区
     */
    public Workspace getWorkspaceById(Long id) {
        return workspaceMapper.selectById(id);
    }

    /**
     * 创建工作区
     */
    public Workspace createWorkspace(Workspace workspace) {
        // 设置默认值
        if (workspace.getPriority() == null) {
            workspace.setPriority(0);
        }
        if (workspace.getIsActive() == null) {
            workspace.setIsActive(true);
        }

        workspaceMapper.insert(workspace);
        return workspace;
    }

    /**
     * 更新工作区
     */
    public Workspace updateWorkspace(Long id, Workspace workspace) {
        workspace.setId(id);
        workspaceMapper.update(workspace);
        return workspaceMapper.selectById(id);
    }

    /**
     * 删除工作区
     */
    public void deleteWorkspace(Long id) {
        workspaceMapper.deleteById(id);
    }

    /**
     * 更新工作区状态
     */
    public void updateWorkspaceStatus(Long id, Boolean isActive) {
        workspaceMapper.updateStatus(id, isActive);
    }

    /**
     * 获取最高优先级的活跃工作区
     */
    public Workspace getHighestPriorityWorkspace() {
        List<Workspace> activeWorkspaces = getActiveWorkspaces();
        return activeWorkspaces.isEmpty() ? null : activeWorkspaces.get(0);
    }
}