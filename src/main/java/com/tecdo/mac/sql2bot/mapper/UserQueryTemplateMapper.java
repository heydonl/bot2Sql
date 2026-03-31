package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.UserQueryTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserQueryTemplateMapper {
    UserQueryTemplate findByQuestionAndSql(@Param("question") String question,
                                           @Param("sql") String sql);

    int insert(UserQueryTemplate template);

    UserQueryTemplate findById(@Param("id") Long id);

    int updateScoreOnSatisfied(@Param("id") Long id);

    int updateScoreOnUnsatisfied(@Param("id") Long id);
}
