package com.tecdo.mac.sql2bot.dto;

import lombok.Data;

@Data
public class TemplateStats {
    private Long totalTemplates;
    private Double averageScore;
    private Long totalUsageCount;
    private Long totalRatingCount;
}
