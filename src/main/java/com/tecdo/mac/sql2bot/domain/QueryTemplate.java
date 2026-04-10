package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class QueryTemplate {
    private Long id;
    private String question;
    private String generatedSql;
    private Long datasourceId;
    private BigDecimal score;
    private Integer usageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
