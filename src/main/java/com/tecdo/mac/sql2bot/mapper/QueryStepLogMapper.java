package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.QueryStepLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QueryStepLogMapper {
    void insert(QueryStepLog stepLog);
    void batchInsert(@Param("stepLogs") List<QueryStepLog> stepLogs);
    List<QueryStepLog> findByQueryLogId(@Param("queryLogId") Long queryLogId);
}
