package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class QueryTemplate {
    private Long id;
    private String skeleton;
    private String sqlTemplate;
    private String entity;
    private String intent;
    private String supportedDimensions;
    private String supportedMetrics;
    private String parameters;
    private String exampleQuestion;
    private String exampleIntentJson;
    private BigDecimal score;
    private Integer ratingCount;
    private Integer usageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
