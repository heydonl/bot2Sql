package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 查询反馈实体
 * 用于记录用户对查询结果的满意度评分
 */
@Data
public class QueryFeedback {
    private Long id;
    private Long userId;
    private Long conversationId;
    private Long queryLogId;
    private Long templateId;
    private String question;
    private String generatedSql;
    private Integer rating; // 1=满意, 0=不满意
    private String feedbackReason; // 不满意的原因
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}