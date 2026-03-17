package com.tecdo.mac.sql2bot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryLogFilter {
    private String intent;
    private Boolean isFromTemplate;
    private Boolean executionSuccess;
    private Boolean isLabeled;
    private Integer minRating;
    private Integer maxRating;
    private Long userId;
    private String startDate;
    private String endDate;
    private String sortBy;
    private String sortOrder;
}
