package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QueryLog {
    private Long id;
    private Long userId;
    private Long conversationId;
    private String question;
    private String intent;
    private String intentJson;
    private String skeleton;
    private Long templateId;
    private Boolean isFromTemplate;
    private String generatedSql;
    private Boolean executionSuccess;
    private Long executionTime;
    private Integer resultCount;
    private Integer rating;
    private java.math.BigDecimal score;
    private Boolean isLabeled;
    private Long datasourceId;
    private Boolean satisfied;  // 用户是否满意（NULL=未评价，TRUE=满意，FALSE=不满意）
    private Long retryFromId;  // 重试来源的query_log ID
    private String sourceType;  // 来源类型（user_template, system_template, bfs）
    private Long sourceTemplateId;  // 来源模板ID
    private String errorMessage;  // SQL执行错误信息
    private LocalDateTime createdAt;
}
