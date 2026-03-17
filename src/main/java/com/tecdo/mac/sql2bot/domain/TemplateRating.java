package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TemplateRating {
    private Long id;
    private Long templateId;
    private Long userId;
    private Integer score;
    private Long conversationId;
    private LocalDateTime createdAt;
}
