package com.tecdo.mac.sql2bot.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 查询响应 DTO
 */
@Data
public class QueryResponse {

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 生成的 SQL 语句
     */
    private String sql;

    /**
     * SQL 解释（自然语言）
     */
    private String explanation;

    /**
     * 查询结果数据
     */
    private List<Map<String, Object>> data;

    /**
     * 结果行数
     */
    private Integer rowCount;

    /**
     * 执行时间（毫秒）
     */
    private Long executionTime;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    public static QueryResponse success(Long conversationId, String sql, String explanation,
                                       List<Map<String, Object>> data, long executionTime) {
        QueryResponse response = new QueryResponse();
        response.setSuccess(true);
        response.setConversationId(conversationId);
        response.setSql(sql);
        response.setExplanation(explanation);
        response.setData(data);
        response.setRowCount(data.size());
        response.setExecutionTime(executionTime);
        return response;
    }

    public static QueryResponse error(String errorMessage) {
        QueryResponse response = new QueryResponse();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
