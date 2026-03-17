package com.tecdo.mac.sql2bot.dto;

import lombok.Data;

@Data
public class LabelFewShotRequest {
    private String targetIntent;
    private String correctedIntentJson;
    private Long createdBy;
}
