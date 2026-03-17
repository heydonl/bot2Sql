package com.tecdo.mac.sql2bot.dto;

import lombok.Data;

@Data
public class RateQueryRequest {
    private Long queryLogId;
    private Long templateId;
    private Long userId;
    private Integer score;
    private Long conversationId;
}
