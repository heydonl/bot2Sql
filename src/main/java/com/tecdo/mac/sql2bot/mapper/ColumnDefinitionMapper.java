package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 字段定义 Mapper
 */
@Mapper
public interface ColumnDefinitionMapper {

    /**
     * 插入字段定义
     */
    int insert(ColumnDefinition columnDefinition);

    /**
     * 根据ID删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据模型ID删除所有字段
     */
    int deleteByModelId(@Param("modelId") Long modelId);

    /**
     * 更新字段定义
     */
    int update(ColumnDefinition columnDefinition);

    /**
     * 根据ID查询
     */
    ColumnDefinition selectById(@Param("id") Long id);

    /**
     * 根据模型ID查询所有字段
     */
    List<ColumnDefinition> selectByModelId(@Param("modelId") Long modelId);

    /**
     * 根据模型ID和字段名查询
     */
    ColumnDefinition selectByModelIdAndColumnName(@Param("modelId") Long modelId,
                                                   @Param("columnName") String columnName);

    /**
     * 根据字段类型查询
     */
    List<ColumnDefinition> selectByColumnType(@Param("modelId") Long modelId,
                                               @Param("columnType") String columnType);

    /**
     * 根据字段名查找所有匹配的字段定义（跨表）
     */
    List<ColumnDefinition> selectByColumnName(@Param("columnName") String columnName);
}
