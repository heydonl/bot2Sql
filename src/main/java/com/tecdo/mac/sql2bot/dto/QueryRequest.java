package com.tecdo.mac.sql2bot.dto;

import lombok.Data;

/**
 * 查询请求 DTO
 */
@Data
public class QueryRequest {

    /**
     * 数据源ID（可选，不指定则自动选择）
     */
    private Long datasourceId;

    /**
     * 自然语言问题
     */
    private String question;

    /**
     * 会话ID（可选，用于多轮对话）
     */
    private Long conversationId;

    /**
     * 用户ID（必填，用于会话隔离）
     */
    private Long userId;

    /**
     * 是否创建新会话（默认 false）
     */
    private Boolean createNewConversation;

    /**
     * 不满意重试时传入，指向上次查询的 queryLogId
     */
    private Long retryQueryLogId;

    /**
     * 用户是否满意上次结果（false 触发 BFS 重试路径）
     */
    private Boolean satisfied;
}
