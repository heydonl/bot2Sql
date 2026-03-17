package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.QueryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface QueryLogMapper {
    int insert(QueryLog log);

    QueryLog selectById(@Param("id") Long id);

    List<QueryLog> selectList(
        @Param("intent") String intent,
        @Param("isFromTemplate") Boolean isFromTemplate,
        @Param("executionSuccess") Boolean executionSuccess,
        @Param("isLabeled") Boolean isLabeled,
        @Param("minRating") Integer minRating,
        @Param("maxRating") Integer maxRating,
        @Param("userId") Long userId,
        @Param("startDate") String startDate,
        @Param("endDate") String endDate,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    int count(
        @Param("intent") String intent,
        @Param("isFromTemplate") Boolean isFromTemplate,
        @Param("executionSuccess") Boolean executionSuccess,
        @Param("isLabeled") Boolean isLabeled,
        @Param("userId") Long userId
    );

    int updateRating(
        @Param("id") Long id,
        @Param("rating") int rating
    );

    int markAsLabeled(@Param("id") Long id);

    Map<String, Long> getIntentDistribution(
        @Param("startDate") String startDate,
        @Param("endDate") String endDate
    );

    com.tecdo.mac.sql2bot.dto.QueryLogStats getStats();
}
