package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.Relationship;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 关系 Mapper
 */
@Mapper
public interface RelationshipMapper {

    /**
     * 插入关系
     */
    int insert(Relationship relationship);

    /**
     * 根据ID删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新关系
     */
    int update(Relationship relationship);

    /**
     * 根据ID查询
     */
    Relationship selectById(@Param("id") Long id);

    /**
     * 查询所有关系
     */
    List<Relationship> selectAll();

    /**
     * 根据源模型ID查询
     */
    List<Relationship> selectByFromModelId(@Param("fromModelId") Long fromModelId);

    /**
     * 根据目标模型ID查询
     */
    List<Relationship> selectByToModelId(@Param("toModelId") Long toModelId);

    /**
     * 根据模型ID查询所有相关关系（作为源或目标）
     */
    List<Relationship> selectByModelId(@Param("modelId") Long modelId);
}
