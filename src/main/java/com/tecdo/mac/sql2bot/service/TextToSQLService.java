package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.Conversation;
import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Text-to-SQL 核心服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextToSQLService {

    private final AIService aiService;
    private final SemanticContextService semanticContextService;
    private final QueryExecutorService queryExecutorService;
    private final ConversationService conversationService;
    private final MessageService messageService;

    /**
     * 处理自然语言查询（支持会话隔离和多轮对话）
     */
    public QueryResponse processQuery(QueryRequest request) {
        try {
            long startTime = System.currentTimeMillis();

            // 1. 验证用户ID
            if (request.getUserId() == null) {
                return QueryResponse.error("用户ID不能为空");
            }

            // 2. 处理会话
            Long conversationId = request.getConversationId();

            // 如果指定了会话ID，验证会话是否属于该用户
            if (conversationId != null) {
                if (!conversationService.belongsToUser(conversationId, request.getUserId())) {
                    return QueryResponse.error("无权访问该会话");
                }
            } else if (Boolean.TRUE.equals(request.getCreateNewConversation())) {
                // 创建新会话
                Conversation conversation = conversationService.create(
                    request.getUserId(),
                    generateConversationTitle(request.getQuestion()),
                    request.getDatasourceId()
                );
                conversationId = conversation.getId();
                log.info("Created new conversation: id={}, userId={}", conversationId, request.getUserId());
            }

            // 3. 保存用户消息
            if (conversationId != null) {
                messageService.saveUserMessage(conversationId, request.getQuestion());
            }

            // 4. 生成语义上下文（使用 RAG 检索相关表和字段）
            Long datasourceId = request.getDatasourceId();
            if (datasourceId == null) {
                log.info("No datasource specified, will auto-detect from all available datasources");
            } else {
                log.info("Generating semantic context for datasource: {}", datasourceId);
            }

            String systemPrompt = semanticContextService.generateSystemPrompt(datasourceId, request.getQuestion());

            // 5. 如果有会话历史，添加对话上下文
            String conversationContext = "";
            if (conversationId != null) {
                conversationContext = messageService.buildConversationContext(conversationId, 5);
            }

            String userPrompt = semanticContextService.generateUserPrompt(request.getQuestion());
            if (!conversationContext.isEmpty()) {
                userPrompt = conversationContext + "\n\n" + userPrompt;
            }

            // 6. 调用 AI 生成 SQL
            log.info("Calling AI service to generate SQL for question: {}", request.getQuestion());
            String aiResponse = aiService.generateSQL(systemPrompt, userPrompt);
            log.debug("AI response: {}", aiResponse);

            // 7. 提取 SQL 语句
            String sql = aiService.extractSQL(aiResponse);
            log.info("Generated SQL: {}", sql);

            // 8. 如果未指定数据源，从 SQL 中推断
            if (datasourceId == null) {
                datasourceId = semanticContextService.inferDataSourceFromSQL(sql);
                if (datasourceId == null) {
                    throw new IllegalStateException("无法从 SQL 中推断出数据源，请检查表名是否正确");
                }
                log.info("Auto-detected datasource: {}", datasourceId);
            }

            // 9. 提取解释
            String explanation = extractExplanation(aiResponse);

            // 10. 执行 SQL 查询
            log.info("Executing SQL query on datasource: {}", datasourceId);
            List<Map<String, Object>> data = queryExecutorService.executeQuery(datasourceId, sql);

            long executionTime = System.currentTimeMillis() - startTime;

            // 11. 保存助手消息
            if (conversationId != null) {
                messageService.saveAssistantMessage(conversationId, explanation, sql, data);
            }

            // 12. 返回结果
            return QueryResponse.success(conversationId, sql, explanation, data, executionTime);

        } catch (Exception e) {
            log.error("Failed to process query", e);

            // 保存错误消息
            if (request.getConversationId() != null) {
                messageService.saveErrorMessage(request.getConversationId(), e.getMessage());
            }

            return QueryResponse.error(e.getMessage());
        }
    }

    /**
     * 从 AI 响应中提取解释
     */
    private String extractExplanation(String response) {
        // 移除 SQL 代码块
        String explanation = response;

        if (response.contains("```sql")) {
            int start = response.indexOf("```sql");
            int end = response.indexOf("```", start + 6);
            if (end > start) {
                explanation = response.substring(0, start) + response.substring(end + 3);
            }
        } else if (response.contains("```")) {
            int start = response.indexOf("```");
            int end = response.indexOf("```", start + 3);
            if (end > start) {
                explanation = response.substring(0, start) + response.substring(end + 3);
            }
        }

        return explanation.trim();
    }

    /**
     * 根据问题生成会话标题
     */
    private String generateConversationTitle(String question) {
        // 简单截取前30个字符作为标题
        if (question.length() <= 30) {
            return question;
        }
        return question.substring(0, 27) + "...";
    }

    /**
     * 验证生成的 SQL
     */
    public boolean validateSQL(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }

        String upperSQL = sql.toUpperCase().trim();
        return upperSQL.startsWith("SELECT");
    }
}
