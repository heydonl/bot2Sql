package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 查询步骤执行日志
 */
@Data
public class QueryStepLog {
    private Long id;
    private Long queryLogId;
    private String stepId;
    private Integer stepIndex;
    private String sqlTemplate;
    private String filledSql;
    private Long datasourceId;
    private Boolean executionSuccess;
    private Integer resultCount;
    private Long executionTime;
    private String errorMessage;
    private LocalDateTime createTime;
}
