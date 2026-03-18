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
    private final IntentAnalysisService intentAnalysisService;
    private final QueryTemplateService queryTemplateService;
    private final TemplateParameterService templateParameterService;
    private final QueryLogService queryLogService;
    private final SchemaVectorStoreService schemaVectorStoreService;
    private final ObjectMapper objectMapper;

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

            // 4. 意图分析
            Long datasourceId = request.getDatasourceId();
            IntentAnalysisRequest intentRequest = new IntentAnalysisRequest();
            intentRequest.setQuestion(request.getQuestion());

            IntentAnalysisResponse intentResponse = null;
            String skeleton = null;
            try {
                intentResponse = intentAnalysisService.analyzeIntent(intentRequest);
                skeleton = intentAnalysisService.convertToSkeleton(intentResponse);
                log.info("意图分析完成: intent={}, skeleton={}", intentResponse.getIntent(), skeleton);
            } catch (Exception e) {
                log.error("意图分析失败，降级到直接 LLM 生成", e);
            }

            // 5. 模板检索和验证
            QueryTemplate matchedTemplate = null;
            if (skeleton != null) {
                QueryTemplate candidateTemplate = queryTemplateService.findTemplate(skeleton);
                if (candidateTemplate != null && queryTemplateService.validateTemplate(candidateTemplate, intentResponse)) {
                    matchedTemplate = candidateTemplate;
                    log.info("模板验证通过: templateId={}", matchedTemplate.getId());
                }
            }

            // 6. 生成 SQL（模板填充或 LLM 生成）
            String sql;
            String aiResponse = null;
            Long templateId = null;
            boolean isFromTemplate = false;

            if (matchedTemplate != null) {
                // 6a. 使用模板填充参数
                try {
                    sql = templateParameterService.fillTemplate(matchedTemplate, intentResponse);
                    templateId = matchedTemplate.getId();
                    isFromTemplate = true;
                    queryTemplateService.incrementUsageCount(templateId);
                    log.info("模板填充成功: templateId={}, sql={}", templateId, sql);
                } catch (Exception e) {
                    log.error("模板填充失败，降级到 LLM 生成", e);
                    matchedTemplate = null;
                    sql = null;
                }
            } else {
                sql = null;
            }

            if (sql == null) {
                // 6b. LLM 生成 SQL
                if (datasourceId == null) {
                    log.info("未指定数据源，将从所有可用数据源中自动检测");
                } else {
                    log.info("为数据源生成语义上下文: {}", datasourceId);
                }

                // 6b-1. 基于骨架字符串进行语义检索相关表结构
                String schemaContext = null;
                if (skeleton != null) {
                    try {
                        List<SchemaVectorStoreService.SchemaSearchResult> schemaResults =
                            schemaVectorStoreService.searchSchemas(skeleton, datasourceId, 10);
                        if (!schemaResults.isEmpty()) {
                            schemaContext = buildSchemaContext(schemaResults);
                            log.info("骨架检索到 {} 个相关表", schemaResults.size());
                        }
                    } catch (Exception e) {
                        log.error("骨架检索失败，降级到关键词 RAG", e);
                    }
                }

                String systemPrompt;
                if (schemaContext != null && intentResponse != null) {
                    systemPrompt = buildSchemaBasedPrompt(intentResponse, schemaContext);
                } else {
                    systemPrompt = semanticContextService.generateSystemPrompt(datasourceId, request.getQuestion());
                }

                String conversationContext = "";
                if (conversationId != null) {
                    conversationContext = messageService.buildConversationContext(conversationId, 5);
                }

                String userPrompt = semanticContextService.generateUserPrompt(request.getQuestion());
                if (!conversationContext.isEmpty()) {
                    userPrompt = conversationContext + "\n\n" + userPrompt;
                }

                log.info("调用 AI 服务生成 SQL: {}", request.getQuestion());
                aiResponse = aiService.generateSQL(systemPrompt, userPrompt);
                log.debug("AI 响应: {}", aiResponse);

                sql = aiService.extractSQL(aiResponse);
                log.info("LLM 生成 SQL: {}", sql);

                // 尝试解析 schema-based LLM 响应（含 sql_template + parameters）
                if (schemaContext != null) {
                    try {
                        String jsonBlock = extractJsonBlock(aiResponse);
                        if (jsonBlock != null) {
                            JsonNode root = objectMapper.readTree(jsonBlock);
                            if (root.has("sql_template") && root.has("parameters")) {
                                QueryTemplate dynamicTemplate = new QueryTemplate();
                                dynamicTemplate.setSqlTemplate(root.get("sql_template").asText());
                                dynamicTemplate.setParameters(root.get("parameters").toString());
                                if (intentResponse != null) {
                                    String filledSql = templateParameterService.fillTemplate(dynamicTemplate, intentResponse);
                                    if (filledSql != null && !filledSql.isEmpty()) {
                                        sql = filledSql;
                                        log.info("Schema-based 模板填充成功: {}", sql);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Schema-based 响应解析失败，使用原始 SQL", e);
                    }
                }
            }

            // 7. 推断数据源
            if (datasourceId == null) {
                datasourceId = semanticContextService.inferDataSourceFromSQL(sql);
                if (datasourceId == null) {
                    throw new IllegalStateException("无法从 SQL 中推断出数据源，请检查表名是否正确");
                }
                log.info("自动检测到数据源: {}", datasourceId);
            }

            // 8. 提取解释（仅 LLM 生成时有 AI 响应）
            String explanation = aiResponse != null ? extractExplanation(aiResponse) : "通过模板匹配生成查询";

            // 9. 执行 SQL
            long executionStart = System.currentTimeMillis();
            boolean executionSuccess = false;
            int resultCount = 0;
            List<Map<String, Object>> data = null;
            String errorMessage = null;

            try {
                log.info("执行 SQL 查询: datasourceId={}", datasourceId);
                data = queryExecutorService.executeQuery(datasourceId, sql);
                resultCount = data.size();
                executionSuccess = true;
            } catch (Exception e) {
                errorMessage = e.getMessage();
                log.error("SQL 执行失败", e);
            }

            long executionTime = System.currentTimeMillis() - executionStart;

            // 10. 记录查询日志
            try {
                QueryLog queryLog = new QueryLog();
                queryLog.setUserId(request.getUserId());
                queryLog.setConversationId(conversationId);
                queryLog.setQuestion(request.getQuestion());
                queryLog.setIntent(intentResponse != null ? intentResponse.getIntent().name() : null);
                queryLog.setIntentJson(intentResponse != null ?
                        objectMapper.writeValueAsString(intentResponse) : null);
                queryLog.setSkeleton(skeleton);
                queryLog.setTemplateId(templateId);
                queryLog.setIsFromTemplate(isFromTemplate);
                queryLog.setGeneratedSql(sql);
                queryLog.setExecutionSuccess(executionSuccess);
                queryLog.setExecutionTime(executionTime);
                queryLog.setResultCount(resultCount);
                queryLog.setDatasourceId(datasourceId);

                queryLogService.logQuery(queryLog);
            } catch (Exception e) {
                log.warn("记录查询日志失败（不影响查询结果）", e);
            }

            // 11. 如果执行失败，抛出异常
            if (!executionSuccess) {
                throw new RuntimeException("SQL 执行失败: " + errorMessage);
            }

            // 12. 如果是 LLM 生成且执行成功，保存为新模板
            if (!isFromTemplate && intentResponse != null && skeleton != null) {
                try {
                    queryTemplateService.saveGeneratedTemplate(
                            skeleton,
                            sql,
                            intentResponse.getIntent().name(),
                            intentResponse.getEntity(),
                            intentResponse.getDimensions(),
                            intentResponse.getMetrics(),
                            datasourceId
                    );
                    log.info("新模板已保存: skeleton={}", skeleton);
                } catch (Exception e) {
                    log.warn("保存模板失败（不影响查询结果）", e);
                }
            }

            long totalTime = System.currentTimeMillis() - startTime;

            // 13. 保存助手消息
            if (conversationId != null) {
                messageService.saveAssistantMessage(conversationId, explanation, sql, data);
            }

            // 14. 返回结果
            return QueryResponse.success(conversationId, sql, explanation, data, totalTime);

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
     * 构建 Schema 上下文字符串
     */
    private String buildSchemaContext(List<SchemaVectorStoreService.SchemaSearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (SchemaVectorStoreService.SchemaSearchResult result : results) {
            SchemaVectorStoreService.SchemaMeta meta = result.getMeta();
            sb.append("表名: ").append(meta.getTableName());
            if (meta.getDisplayName() != null) sb.append("（").append(meta.getDisplayName()).append("）");
            sb.append("\n");
            if (meta.getDatasourceName() != null) sb.append("数据源: ").append(meta.getDatasourceName()).append("\n");
            sb.append("字段:\n");
            if (meta.getColumns() != null) {
                for (SchemaVectorStoreService.SchemaMeta.ColumnInfo col : meta.getColumns()) {
                    sb.append("- ").append(col.getColumnName());
                    if (col.getColumnType() != null) sb.append(" (").append(col.getColumnType()).append(")");
                    if (col.getDisplayName() != null) sb.append(" - ").append(col.getDisplayName());
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建基于 Schema 的 Prompt
     */
    private String buildSchemaBasedPrompt(IntentAnalysisResponse intentResponse, String schemaContext) {
        String intentJson;
        try {
            intentJson = objectMapper.writeValueAsString(intentResponse);
        } catch (Exception e) {
            intentJson = intentResponse.toString();
        }
        return "你是一个 SQL 生成专家。根据以下信息生成 SQL 骨架和参数定义：\n\n" +
               "## 用户意图\n" + intentJson + "\n\n" +
               "## 相关表结构\n" + schemaContext +
               "## 要求\n" +
               "1. 生成标准 MySQL SQL，所有参数使用 {{paramName}} 占位符\n" +
               "2. 同时输出参数定义列表，说明每个参数对应意图 JSON 的哪个字段和类型\n" +
               "3. 使用以下 JSON 格式输出，用 ```json ``` 代码块包裹：\n\n" +
               "{\n" +
               "  \"sql_template\": \"SELECT ... WHERE advertiser_id = {{advertiserId}}\",\n" +
               "  \"parameters\": [\n" +
               "    { \"name\": \"advertiserId\", \"source\": \"entity.dimensionFilter.conditions[0].value\", \"type\": \"NUMBER\" }\n" +
               "  ]\n" +
               "}";
    }

    /**
     * 从文本中提取 ```json ``` 代码块内容
     */
    private String extractJsonBlock(String text) {
        if (text == null) return null;
        int start = text.indexOf("```json");
        if (start == -1) return null;
        start = text.indexOf("\n", start) + 1;
        int end = text.indexOf("```", start);
        if (end == -1) return null;
        return text.substring(start, end).trim();
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
