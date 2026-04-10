package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QueryTemplateMapper {

    int insert(QueryTemplate template);

    QueryTemplate selectById(@Param("id") Long id);

    QueryTemplate selectBySkeleton(@Param("skeleton") String skeleton);

    List<QueryTemplate> selectList(
        @Param("intent") String intent,
        @Param("entity") String entity,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    int count(
        @Param("intent") String intent,
        @Param("entity") String entity
    );

    int updateRating(
        @Param("id") Long id,
        @Param("newScore") int newScore
    );

    int incrementUsageCount(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    List<QueryTemplate> selectAll();

    List<QueryTemplate> selectTopRated(@Param("limit") int limit);

    int updateById(QueryTemplate template);

    QueryTemplateStats getStats();

    class QueryTemplateStats {
        public Long totalTemplates;
        public Double averageScore;
        public Long totalUsageCount;
        public Long totalRatingCount;
    }
}
