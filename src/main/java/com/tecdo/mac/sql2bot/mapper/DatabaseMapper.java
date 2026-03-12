package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.Database;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据库 Mapper
 */
@Mapper
public interface DatabaseMapper {

    /**
     * 插入数据库记录
     */
    void insert(Database database);

    /**
     * 根据ID查询
     */
    Database selectById(@Param("id") Long id);

    /**
     * 根据数据源ID查询所有数据库
     */
    List<Database> selectByDatasourceId(@Param("datasourceId") Long datasourceId);

    /**
     * 根据数据源ID和数据库名查询
     */
    Database selectByDatasourceIdAndName(@Param("datasourceId") Long datasourceId,
                                          @Param("databaseName") String databaseName);

    /**
     * 根据数据源ID和数据库名列表批量查询
     */
    List<Database> selectByDatasourceIdAndNames(@Param("datasourceId") Long datasourceId,
                                                @Param("databaseNames") List<String> databaseNames);

    /**
     * 批量插入数据库记录
     */
    void batchInsert(@Param("databases") List<Database> databases);

    /**
     * 更新数据库记录
     */
    void update(Database database);

    /**
     * 删除数据库记录
     */
    void deleteById(@Param("id") Long id);
}
