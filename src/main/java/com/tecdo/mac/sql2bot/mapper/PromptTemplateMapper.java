package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.PromptTemplate;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 提示词模板 Mapper
 */
@Mapper
public interface PromptTemplateMapper {

    @Select("SELECT * FROM prompt_template ORDER BY priority DESC, created_at DESC")
    List<PromptTemplate> selectAll();

    @Select("SELECT * FROM prompt_template WHERE is_active = true ORDER BY priority DESC")
    List<PromptTemplate> selectActive();

    @Select("SELECT * FROM prompt_template WHERE category = #{category} ORDER BY priority DESC")
    List<PromptTemplate> selectByCategory(String category);

    @Select("SELECT * FROM prompt_template WHERE id = #{id}")
    PromptTemplate selectById(Long id);

    @Insert("INSERT INTO prompt_template (name, description, template, category, is_active, priority, created_at, updated_at) " +
            "VALUES (#{name}, #{description}, #{template}, #{category}, #{isActive}, #{priority}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(PromptTemplate promptTemplate);

    @Update("UPDATE prompt_template SET name = #{name}, description = #{description}, " +
            "template = #{template}, category = #{category}, is_active = #{isActive}, " +
            "priority = #{priority}, updated_at = NOW() WHERE id = #{id}")
    void update(PromptTemplate promptTemplate);

    @Delete("DELETE FROM prompt_template WHERE id = #{id}")
    void deleteById(Long id);
}
