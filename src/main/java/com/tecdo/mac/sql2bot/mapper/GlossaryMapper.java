package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.Glossary;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 术语库 Mapper
 */
@Mapper
public interface GlossaryMapper {

    @Select("SELECT * FROM glossary ORDER BY term ASC")
    List<Glossary> selectAll();

    @Select("SELECT * FROM glossary WHERE is_active = true ORDER BY term ASC")
    List<Glossary> selectActive();

    @Select("SELECT * FROM glossary WHERE category = #{category} ORDER BY term ASC")
    List<Glossary> selectByCategory(String category);

    @Select("SELECT * FROM glossary WHERE id = #{id}")
    Glossary selectById(Long id);

    @Select("SELECT * FROM glossary WHERE term LIKE CONCAT('%', #{keyword}, '%') " +
            "OR definition LIKE CONCAT('%', #{keyword}, '%') ORDER BY term ASC")
    List<Glossary> search(String keyword);

    @Insert("INSERT INTO glossary (term, definition, synonyms, category, examples, is_active, created_at, updated_at) " +
            "VALUES (#{term}, #{definition}, #{synonyms}, #{category}, #{examples}, #{isActive}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Glossary glossary);

    @Update("UPDATE glossary SET term = #{term}, definition = #{definition}, " +
            "synonyms = #{synonyms}, category = #{category}, examples = #{examples}, " +
            "is_active = #{isActive}, updated_at = NOW() WHERE id = #{id}")
    void update(Glossary glossary);

    @Delete("DELETE FROM glossary WHERE id = #{id}")
    void deleteById(Long id);
}
