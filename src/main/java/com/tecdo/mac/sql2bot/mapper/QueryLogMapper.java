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

    com.tecdo.mac.sql2bot.domain.QueryLog selectBestByTemplateId(@Param("templateId") Long templateId);

    QueryLog findBestExampleByTemplateId(@Param("templateId") Long templateId);

    List<QueryLog> selectBestRecentExample(
        @Param("datasourceId") Long datasourceId,
        @Param("limit") int limit
    );

    int updateSatisfied(@Param("id") Long id, @Param("satisfied") Boolean satisfied);

    QueryLog findByQuestionAndSql(@Param("question") String question,
                                  @Param("sql") String sql,
                                  @Param("datasourceId") Long datasourceId);

    /**
     * 查询最近的失败案例（执行失败或用户不满意）
     */
    List<QueryLog> selectRecentFailedCases(@Param("limit") int limit);

    /**
     * 查询指定会话中的失败案例（执行失败或用户不满意）
     */
    List<QueryLog> selectFailedCasesByConversation(@Param("conversationId") Long conversationId, @Param("limit") int limit);
}
