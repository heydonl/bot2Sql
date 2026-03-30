package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryStepLog;
import com.tecdo.mac.sql2bot.mapper.QueryStepLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryStepLogService {

    private final QueryStepLogMapper queryStepLogMapper;

    public void logStep(QueryStepLog stepLog) {
        queryStepLogMapper.insert(stepLog);
        log.debug("记录步骤日志: queryLogId={}, stepId={}, success={}",
            stepLog.getQueryLogId(), stepLog.getStepId(), stepLog.getExecutionSuccess());
    }

    public void logSteps(List<QueryStepLog> stepLogs) {
        if (stepLogs != null && !stepLogs.isEmpty()) {
            queryStepLogMapper.batchInsert(stepLogs);
            log.debug("批量记录步骤日志: count={}", stepLogs.size());
        }
    }

    public List<QueryStepLog> getByQueryLogId(Long queryLogId) {
        return queryStepLogMapper.findByQueryLogId(queryLogId);
    }
}
