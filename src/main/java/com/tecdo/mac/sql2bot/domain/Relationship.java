package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 表关系实体
 */
@Data
public class Relationship {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关系名称
     */
    private String name;

    /**
     * 源模型ID
     */
    private Long fromModelId;

    /**
     * 目标模型ID
     */
    private Long toModelId;

    /**
     * 源字段名
     */
    private String fromColumn;

    /**
     * 目标字段名
     */
    private String toColumn;

    /**
     * 关系类型: one_to_one, one_to_many, many_to_many
     */
    private String joinType;

    /**
     * 关系类型别名（兼容性）
     */
    public String getRelationshipType() {
        return joinType;
    }

    public void setRelationshipType(String relationshipType) {
        this.joinType = relationshipType;
    }

    /**
     * JOIN条件(JSON格式)
     */
    private String joinCondition;

    /**
     * 关系描述
     */
    private String description;

    /**
     * 工作区ID
     */
    private Long workspaceId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
