package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 工作区实体
 */
@Data
public class Workspace {
    private Long id;
    private String name;
    private String description;
    private Integer priority;
    private String layoutData; // JSON格式存储工作区布局
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}