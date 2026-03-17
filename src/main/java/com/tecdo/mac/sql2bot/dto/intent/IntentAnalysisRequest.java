package com.tecdo.mac.sql2bot.dto.intent;

import lombok.Data;

/**
 * 意图分析请求
 */
@Data
public class IntentAnalysisRequest {

    /**
     * 用户的自然语言问题
     */
    private String question;

    /**
     * 当前日期（用于相对时间转换，格式：YYYY-MM-DD）
     */
    private String currentDate;

    /**
     * 是否返回骨架格式（默认 true）
     */
    private Boolean includeSkeleton = true;
}
