package com.tecdo.mac.sql2bot.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tecdo.mac.sql2bot.domain.Conversation;
import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import com.tecdo.mac.sql2bot.dto.intent.IntentAnalysisRequest;
import com.tecdo.mac.sql2bot.dto.intent.IntentAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 增强版Text-to-SQL服务
 * 实现基于RAG的SQL模板检索机制，支持用户反馈和动态模板生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedTextToSQLService {

    private final AIService aiService;
    private final SemanticContextService semanticContextService;
    private final QueryExecutorService queryExecutorService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final IntentAnalysisService intentAnalysisService;
    private final QueryTemplateService queryTemplateService;
    private final TemplateParameterService templateParameterService;
    private final QueryLogService queryLogService;
    private final TemplateVectorStoreService templateVectorStoreService;
    private final VectorRAGService vectorRAGService;
    private final ObjectMapper objectMapper;

    /**
     * 处理自然语言查询（新的RAG流程）
     */
    public QueryResponse processQuery(QueryRequest request) {
        try {
            long startTime = System.currentTimeMillis();

            // 1. 验证用户ID
            if (request.getUserId() == null) {
                return QueryResponse.error("用户ID不能为空");
            }

            // 2. 处理会话
            Long conversationId = handleConversation(request);

            // 3. 保存用户消息
            if (conversationId != null) {
                messageService.saveUserMessage(conversationId, request.getQuestion());
            }

            // 4. 第一阶段：基于RAG的SQL模板检索
            QueryTemplate matchedTemplate = null;
            double templateSimilarity = 0.0;

            log.info("开始RAG模板检索: question={}", request.getQuestion());

            List<TemplateVectorStoreService.TemplateSearchResult> templateResults =
                templateVectorStoreService.searchSimilarTemplates(
                    request.getQuestion(),
                    null,
                    5 // 检索前5个最相似的模板
                );

            if (!templateResults.isEmpty()) {
                TemplateVectorStoreService.TemplateSearchResult bestMatch = templateResults.get(0);
                templateSimilarity = bestMatch.getSimilarity();

                // 设置相似度阈值，只有足够相似的模板才使用
                if (templateSimilarity > 0.7) { // 可配置的阈值
                    matchedTemplate = queryTemplateService.getById(bestMatch.getMeta().getTemplateId());
                    log.info("找到匹配模板: templateId={}, similarity={}",
                            matchedTemplate.getId(), templateSimilarity);
                } else {
                    log.info("模板相似度不足: similarity={}, 阈值=0.7", templateSimilarity);
                }
            }

            String sql = null;
            String explanation = null;
            Long templateId = null;
            boolean isFromTemplate = false;

            if (matchedTemplate != null) {
                // 5a. 使用匹配的模板生成SQL
                try {
                    // 进行意图分析以获取参数值
                    IntentAnalysisRequest intentRequest = new IntentAnalysisRequest();
                    intentRequest.setQuestion(request.getQuestion());
                    IntentAnalysisResponse intentResponse = intentAnalysisService.analyzeIntent(intentRequest);

                    // 使用LLM生成模板参数值
                    sql = templateParameterService.fillTemplate(matchedTemplate, intentResponse);
                    templateId = matchedTemplate.getId();
                    isFromTemplate = true;
                    explanation = "基于相似模板生成查询（相似度: " + String.format("%.2f", templateSimilarity) + "）";

                    // 增加模板使用次数
                    queryTemplateService.incrementUsageCount(templateId);

                    log.info("模板填充成功: templateId={}, sql={}", templateId, sql);

                } catch (Exception e) {
                    log.error("模板填充失败，降级到LLM生成", e);
                    matchedTemplate = null;
                }
            }

            if (sql == null) {
                // 5b. 降级到传统LLM生成
                log.info("使用传统LLM生成SQL");

                Long datasourceId = null;
                String systemPrompt = semanticContextService.generateSystemPrompt(datasourceId, request.getQuestion());

                String conversationContext = "";
                if (conversationId != null) {
                    conversationContext = messageService.buildConversationContext(conversationId, 5);
                }

                String userPrompt = semanticContextService.generateUserPrompt(request.getQuestion());
                if (!conversationContext.isEmpty()) {
                    userPrompt = conversationContext + "\n\n" + userPrompt;
                }

                String aiResponse = aiService.generateSQL(systemPrompt, userPrompt);
                sql = aiService.extractSQL(aiResponse);
                explanation = extractExplanation(aiResponse);

                log.info("LLM生成SQL: {}", sql);
            }

            // 6. 推断数据源（如果需要）
            Long datasourceId = null;
            if (datasourceId == null) {
                datasourceId = semanticContextService.inferDataSourceFromSQL(sql);
                if (datasourceId == null) {
                    throw new IllegalStateException("无法从SQL中推断出数据源，请检查表名是否正确");
                }
                log.info("自动检测到数据源: {}", datasourceId);
            }

            // 7. 执行SQL
            long executionStart = System.currentTimeMillis();
            boolean executionSuccess = false;
            int resultCount = 0;
            List<Map<String, Object>> data = null;
            String errorMessage = null;

            try {
                log.info("执行SQL查询: datasourceId={}", datasourceId);
                data = queryExecutorService.executeQuery(datasourceId, sql);
                resultCount = data.size();
                executionSuccess = true;
            } catch (Exception e) {
                errorMessage = e.getMessage();
                log.error("SQL执行失败", e);
            }

            long executionTime = System.currentTimeMillis() - executionStart;

            // 8. 记录查询日志
            Long queryLogId = logQuery(request, conversationId, templateId, isFromTemplate,
                    sql, executionSuccess, executionTime, resultCount, datasourceId);

            // 9. 如果执行失败，抛出异常
            if (!executionSuccess) {
                throw new RuntimeException("SQL执行失败: " + errorMessage);
            }

            // 10. 保存助手消息
            if (conversationId != null) {
                messageService.saveAssistantMessage(conversationId, explanation, sql, data);
            }

            long totalTime = System.currentTimeMillis() - startTime;

            // 11. 构建响应（包含反馈相关信息）
            QueryResponse response = QueryResponse.success(conversationId, sql, explanation, data, totalTime);
            response.setQueryLogId(queryLogId);
            response.setTemplateId(templateId);
            response.setFromTemplate(isFromTemplate);
            response.setTemplateSimilarity(templateSimilarity);

            return response;

        } catch (Exception e) {
            log.error("处理查询失败", e);

            // 保存错误消息
            if (request.getConversationId() != null) {
                messageService.saveErrorMessage(request.getConversationId(), e.getMessage());
            }

            return QueryResponse.error(e.getMessage());
        }
    }

    /**
     * 处理会话逻辑
     */
    private Long handleConversation(QueryRequest request) {
        Long conversationId = request.getConversationId();

        // 如果指定了会话ID，验证会话是否属于该用户
        if (conversationId != null) {
            if (!conversationService.belongsToUser(conversationId, request.getUserId())) {
                throw new IllegalArgumentException("无权访问该会话");
            }
        } else if (Boolean.TRUE.equals(request.getCreateNewConversation())) {
            // 创建新会话
            Conversation conversation = conversationService.create(
                request.getUserId(),
                generateConversationTitle(request.getQuestion()),
                null
            );
            conversationId = conversation.getId();
            log.info("创建新会话: id={}, userId={}", conversationId, request.getUserId());
        }

        return conversationId;
    }

    /**
     * 记录查询日志
     */
    private Long logQuery(QueryRequest request, Long conversationId, Long templateId,
                         boolean isFromTemplate, String sql, boolean executionSuccess,
                         long executionTime, int resultCount, Long datasourceId) {
        try {
            QueryLog queryLog = new QueryLog();
            queryLog.setUserId(request.getUserId());
            queryLog.setConversationId(conversationId);
            queryLog.setQuestion(request.getQuestion());
            queryLog.setTemplateId(templateId);
            queryLog.setIsFromTemplate(isFromTemplate);
            queryLog.setGeneratedSql(sql);
            queryLog.setExecutionSuccess(executionSuccess);
            queryLog.setExecutionTime(executionTime);
            queryLog.setResultCount(resultCount);
            queryLog.setDatasourceId(datasourceId);

            return queryLogService.logQuery(queryLog);
        } catch (Exception e) {
            log.warn("记录查询日志失败（不影响查询结果）", e);
            return null;
        }
    }

    /**
     * 从AI响应中提取解释
     */
    private String extractExplanation(String response) {
        // 移除SQL代码块
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
     * 验证生成的SQL
     */
    public boolean validateSQL(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }

        String upperSQL = sql.toUpperCase().trim();
        return upperSQL.startsWith("SELECT");
    }
}