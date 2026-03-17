package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.TemplateRating;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TemplateRatingMapper {
    int insert(TemplateRating rating);

    TemplateRating selectById(@Param("id") Long id);

    TemplateRating selectByTemplateAndUser(
        @Param("templateId") Long templateId,
        @Param("userId") Long userId
    );

    int updateScore(
        @Param("templateId") Long templateId,
        @Param("userId") Long userId,
        @Param("score") int score
    );

    int deleteById(@Param("id") Long id);
}
