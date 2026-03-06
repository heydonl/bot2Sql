package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 术语库实体
 */
@Data
public class Glossary {
    private Long id;
    private String term;           // 术语
    private String definition;     // 定义
    private String synonyms;       // 同义词（JSON 数组）
    private String category;       // 分类
    private String examples;       // 使用示例
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
