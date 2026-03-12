package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.Model;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模型 Mapper
 */
@Mapper
public interface ModelMapper {

    /**
     * 插入模型
     */
    int insert(Model model);

    /**
     * 批量插入模型
     */
    int batchInsert(@Param("models") List<Model> models);

    /**
     * 根据ID删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新模型
     */
    int update(Model model);

    /**
     * 根据ID查询
     */
    Model selectById(@Param("id") Long id);

    /**
     * 查询所有模型
     */
    List<Model> selectAll();

    /**
     * 根据数据源ID查询
     */
    List<Model> selectByDatasourceId(@Param("datasourceId") Long datasourceId);

    /**
     * 根据数据源ID和表名查询
     */
    Model selectByDatasourceIdAndTableName(@Param("datasourceId") Long datasourceId,
                                           @Param("tableName") String tableName);

    /**
     * 查询可见的模型
     */
    List<Model> selectVisible();
}
