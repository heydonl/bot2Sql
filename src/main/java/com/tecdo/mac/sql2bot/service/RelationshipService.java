package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.Relationship;
import com.tecdo.mac.sql2bot.mapper.RelationshipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 关系服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipService {

    private final RelationshipMapper relationshipMapper;

    /**
     * 创建关系
     */
    @Transactional
    public Relationship create(Relationship relationship) {
        relationshipMapper.insert(relationship);
        log.info("Created relationship: id={}, from={}, to={}",
                relationship.getId(), relationship.getFromModelId(), relationship.getToModelId());
        return relationship;
    }

    /**
     * 根据ID查询
     */
    public Relationship getById(Long id) {
        return relationshipMapper.selectById(id);
    }

    /**
     * 查询所有关系
     */
    public List<Relationship> listAll() {
        return relationshipMapper.selectAll();
    }

    /**
     * 根据模型ID查询所有相关关系
     */
    public List<Relationship> listByModelId(Long modelId) {
        return relationshipMapper.selectByModelId(modelId);
    }

    /**
     * 根据工作区ID查询关系
     */
    public List<Relationship> listByWorkspaceId(Long workspaceId) {
        return relationshipMapper.selectByWorkspaceId(workspaceId);
    }

    /**
     * 更新关系
     */
    @Transactional
    public void update(Relationship relationship) {
        relationshipMapper.update(relationship);
        log.info("Updated relationship: id={}", relationship.getId());
    }

    /**
     * 删除关系
     */
    @Transactional
    public void delete(Long id) {
        relationshipMapper.deleteById(id);
        log.info("Deleted relationship: id={}", id);
    }
}
