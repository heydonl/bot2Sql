package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.Workspace;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 工作区数据访问层
 */
@Mapper
public interface WorkspaceMapper {

    @Select("SELECT * FROM workspace ORDER BY priority DESC, created_at DESC")
    List<Workspace> selectAll();

    @Select("SELECT * FROM workspace WHERE id = #{id}")
    Workspace selectById(Long id);

    @Select("SELECT * FROM workspace WHERE is_active = 1 ORDER BY priority DESC, created_at DESC")
    List<Workspace> selectActive();

    @Insert("INSERT INTO workspace (name, description, priority, layout_data, is_active, created_at, updated_at) " +
            "VALUES (#{name}, #{description}, #{priority}, #{layoutData}, #{isActive}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Workspace workspace);

    @Update("UPDATE workspace SET name = #{name}, description = #{description}, " +
            "priority = #{priority}, layout_data = #{layoutData}, is_active = #{isActive}, " +
            "updated_at = NOW() WHERE id = #{id}")
    int update(Workspace workspace);

    @Delete("DELETE FROM workspace WHERE id = #{id}")
    int deleteById(Long id);

    @Update("UPDATE workspace SET is_active = #{isActive}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(Long id, Boolean isActive);
}