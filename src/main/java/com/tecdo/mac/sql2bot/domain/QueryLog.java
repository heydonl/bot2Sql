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
    private LocalDateTime createdAt;
}
