package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserQueryTemplate {
    private Long id;
    private String question;
    private String generatedSql;
    private Long datasourceId;
    private Integer totalScore;
    private Integer ratingCount;
    private BigDecimal avgScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
