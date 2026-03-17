package com.tecdo.mac.sql2bot.dto;

import lombok.Data;
import java.util.Map;

@Data
public class QueryLogStats {
    private Long totalQueries;
    private Long successQueries;
    private Long failedQueries;
    private Long templateUsageCount;
    private Long llmGenerationCount;
    private Double averageExecutionTime;
    private Double averageRating;
    private Long labeledCount;
    private Long unlabeledCount;
    private Map<String, Long> intentDistribution;
}
