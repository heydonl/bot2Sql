package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class IntentFewShot {
    private Long id;
    private String intent;
    private String question;
    private String intentJson;
    private String skeleton;
    private Boolean isActive;
    private Long datasourceId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
