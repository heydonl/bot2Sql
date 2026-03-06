package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 提示词模板实体
 */
@Data
public class PromptTemplate {
    private Long id;
    private String name;
    private String description;
    private String template;
    private String category; // system, user, example
    private Boolean isActive;
    private Integer priority; // 优先级，数字越大优先级越高
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
