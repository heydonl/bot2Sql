package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据源 Mapper
 */
@Mapper
public interface DataSourceMapper {

    /**
     * 插入数据源
     */
    int insert(DataSource dataSource);

    /**
     * 根据ID删除
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新数据源
     */
    int update(DataSource dataSource);

    /**
     * 根据ID查询
     */
    DataSource selectById(@Param("id") Long id);

    /**
     * 查询所有数据源
     */
    List<DataSource> selectAll();

    /**
     * 根据名称查询
     */
    DataSource selectByName(@Param("name") String name);

    /**
     * 根据状态查询
     */
    List<DataSource> selectByStatus(@Param("status") String status);
}
