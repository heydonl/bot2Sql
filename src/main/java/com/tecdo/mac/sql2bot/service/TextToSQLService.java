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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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
    private final EmbeddingService embeddingService;
    private final TemplateVectorSearchService templateVectorSearchService;
    private final ObjectMapper objectMapper;
    private final ModelService modelService;
    private final RelationshipService relationshipService;
    private final ColumnDefinitionService columnDefinitionService;
    private final IntentFewShotService intentFewShotService;

    // RAG模板检索配置
    private static final double TEMPLATE_SIMILARITY_THRESHOLD = 0.7;
    private static final int MAX_TEMPLATE_CANDIDATES = 5;

    // Schema 向量检索配置
    @org.springframework.beans.factory.annotation.Value("${schema.search.top-k:10}")
    private int schemaSearchTopK;

    @org.springframework.beans.factory.annotation.Value("${schema.search.similarity-threshold:0.5}")
    private double schemaSearchSimilarityThreshold;

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

            // 4. 获取数据源ID
            Long datasourceId = request.getDatasourceId();

            // 5. 基于RAG的SQL模板检索（需求文档第一阶段）
            QueryTemplate matchedTemplate = null;
            double templateSimilarity = 0.0;

            log.info("开始RAG模板检索: question={}", request.getQuestion());

            try {
                // 5a. 用户自然语言提问 -> embedding -> 向量检索相似SQL模板
                List<TemplateVectorSearchService.TemplateSearchResult> templateResults =
                    templateVectorSearchService.searchSimilarTemplates(
                        request.getQuestion(),
                        datasourceId,
                        MAX_TEMPLATE_CANDIDATES
                    );

                if (!templateResults.isEmpty()) {
                    TemplateVectorSearchService.TemplateSearchResult bestMatch = templateResults.get(0);
                    templateSimilarity = bestMatch.getSimilarity();

                    // 直接使用过滤后的模板结果（过滤逻辑已在TemplateVectorSearchService中完成）
                    QueryTemplate candidateTemplate = queryTemplateService.getById(bestMatch.getMeta().getTemplateId());

                    if (candidateTemplate != null) {
                        matchedTemplate = candidateTemplate;
                        log.info("RAG模板匹配成功: templateId={}, similarity={}",
                                matchedTemplate.getId(), templateSimilarity);
                    }
                } else {
                    log.info("未找到符合条件的SQL模板");
                }

            } catch (Exception e) {
                log.error("RAG模板检索失败，降级到LLM生成", e);
            }

            // 6. 生成 SQL（模板填充或 LLM 生成）
            String sql;
            String aiResponse = null;
            Long templateId = null;
            boolean isFromTemplate = false;

            if (matchedTemplate != null) {
                // 6a. 使用LLM生成参数值并填充模板
                try {
                    // 调用LLM生成参数值
                    String parameterPrompt = buildParameterGenerationPrompt(matchedTemplate, request.getQuestion());
                    String parameterResponse = aiService.generateParameters(parameterPrompt);

                    // 解析参数并填充模板
                    sql = templateParameterService.fillTemplateWithLLMParameters(matchedTemplate, parameterResponse);
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

                // 6b-1. 基于用户提问进行语义检索相关表结构
                String schemaContext = null;
                try {
                    List<SchemaVectorStoreService.SchemaSearchResult> schemaResults =
                        schemaVectorStoreService.searchSchemas(request.getQuestion(), datasourceId, schemaSearchTopK);
                    List<SchemaVectorStoreService.SchemaSearchResult> filtered = schemaResults.stream()
                            .filter(r -> r.getScore() >= schemaSearchSimilarityThreshold)
                            .collect(java.util.stream.Collectors.toList());
                    if (!filtered.isEmpty()) {
                        schemaContext = buildSchemaContext(filtered);
                        log.info("骨架检索到 {} 个相关表（相似度阈值 {}）: {}", filtered.size(), schemaSearchSimilarityThreshold,
                                filtered.stream()
                                        .map(r -> r.getMeta() != null ? r.getMeta().getTableName() : "null")
                                        .collect(java.util.stream.Collectors.joining(", ")));
                    }
                } catch (Exception e) {
                    log.error("骨架检索失败，降级到关键词 RAG", e);
                }

                String systemPrompt;
                if (schemaContext != null) {
                    systemPrompt = buildSchemaBasedPrompt(request.getQuestion(), schemaContext);
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
                log.info("systemPrompt: {}", systemPrompt);
                log.info("userPrompt: {}", userPrompt);
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

                                // 使用LLM生成参数值并填充动态模板
                                String parameterPrompt = buildParameterGenerationPrompt(dynamicTemplate, request.getQuestion());
                                String parameterResponse = aiService.generateParameters(parameterPrompt);
                                String filledSql = templateParameterService.fillTemplateWithLLMParameters(dynamicTemplate, parameterResponse);
                                if (filledSql != null && !filledSql.isEmpty()) {
                                    sql = filledSql;
                                    log.info("Schema-based 模板填充成功: {}", sql);
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

            // 10. 记录查询日志（包含模板相似度信息）
            Long queryLogId = null;
            try {
                QueryLog queryLog = new QueryLog();
                queryLog.setUserId(request.getUserId());
                queryLog.setConversationId(conversationId);
                queryLog.setQuestion(request.getQuestion());
                queryLog.setIntent(null); // 不再使用意图分析
                queryLog.setIntentJson(null); // 不再使用意图分析
                queryLog.setSkeleton(null); // 不再使用骨架
                queryLog.setTemplateId(templateId);
                queryLog.setIsFromTemplate(isFromTemplate);
                queryLog.setGeneratedSql(sql);
                queryLog.setExecutionSuccess(executionSuccess);
                queryLog.setExecutionTime(executionTime);
                queryLog.setResultCount(resultCount);
                queryLog.setDatasourceId(datasourceId);

                queryLogId = queryLogService.logQuery(queryLog);
                log.info("查询日志记录成功: queryLogId={}", queryLogId);
            } catch (Exception e) {
                log.warn("记录查询日志失败（不影响查询结果）", e);
            }

            // 11. 如果执行失败，抛出异常
            if (!executionSuccess) {
                throw new RuntimeException("SQL 执行失败: " + errorMessage);
            }

            // 12. 如果是 LLM 生成且执行成功，可以考虑保存为新模板（暂时跳过）
            // 注意：由于移除了意图分析和骨架生成，暂时不保存新模板
            // 后续可以基于用户问题和生成的SQL来创建模板

            long totalTime = System.currentTimeMillis() - startTime;

            // 13. 保存助手消息
            if (conversationId != null) {
                messageService.saveAssistantMessage(conversationId, explanation, sql, data);
            }

            // 14. 返回结果（包含反馈所需信息）
            QueryResponse response = QueryResponse.success(conversationId, sql, explanation, data, totalTime);

            // 添加反馈相关信息，支持需求文档第二阶段的用户反馈机制
            response.setQueryLogId(queryLogId);
            response.setTemplateId(templateId);
            response.setFromTemplate(isFromTemplate);
            response.setTemplateSimilarity(templateSimilarity);

            log.info("查询处理完成: userId={}, fromTemplate={}, similarity={}",
                    request.getUserId(), isFromTemplate, templateSimilarity);

            return response;

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
     * 构建基于 Schema 的 Prompt（简化版，不依赖意图分析）
     */
    private String buildSchemaBasedPrompt(String question, String schemaContext) {
        return "你是一个 SQL 生成专家。根据以下信息生成 SQL 查询：\n\n" +
               "## 用户问题\n" + question + "\n\n" +
               "## 相关表结构\n" + schemaContext +
               "## 要求\n" +
               "1. 生成标准 MySQL SQL\n" +
               "2. 确保 SQL 语法正确\n" +
               "3. 使用合适的 JOIN 和 WHERE 条件\n" +
               "4. 返回用户需要的数据";
    }

    /**
     * 构建参数生成提示词
     */
    private String buildParameterGenerationPrompt(QueryTemplate template, String question) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("根据以下SQL模板和用户问题，生成具体的参数值：\n\n");

        prompt.append("## SQL模板\n").append(template.getSqlTemplate()).append("\n\n");

        prompt.append("## 参数定义\n").append(template.getParameters()).append("\n\n");

        // 添加 few-shot 示例
        try {
            QueryLog example = queryLogService.getBestExampleByTemplateId(template.getId());

            if (example != null) {
                prompt.append("## 示例\n");
                prompt.append("示例问题: ").append(example.getQuestion()).append("\n");
                prompt.append("示例答案: ").append(example.getGeneratedSql()).append("\n\n");
            }
        } catch (Exception e) {
            log.warn("获取模板示例失败: templateId={}", template.getId(), e);
        }

        prompt.append("## 用户问题\n").append(question).append("\n\n");

        prompt.append("## 要求\n");
        prompt.append("请分析用户问题，为模板中的每个参数生成合适的值。\n");
        prompt.append("返回JSON格式的参数值映射，例如：\n");
        prompt.append("{\n");
        prompt.append("  \"advertiserId\": \"12345\",\n");
        prompt.append("  \"startDate\": \"2024-01-01\",\n");
        prompt.append("  \"endDate\": \"2024-01-31\"\n");
        prompt.append("}");

        return prompt.toString();
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
     * 基于种子表执行 BFS 两层扩展，返回所有涉及的 modelId
     */
    private Set<Long> expandByBFS(Set<Long> seedModelIds, int maxDepth) {
        Set<Long> visited = new HashSet<>(seedModelIds);
        Queue<Long> queue = new LinkedList<>(seedModelIds);
        Map<Long, Integer> depthMap = new HashMap<>();
        seedModelIds.forEach(id -> depthMap.put(id, 0));

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            int currentDepth = depthMap.get(current);
            if (currentDepth >= maxDepth) continue;

            List<com.tecdo.mac.sql2bot.domain.Relationship> rels =
                relationshipService.findByModelId(current);
            for (com.tecdo.mac.sql2bot.domain.Relationship rel : rels) {
                Long neighbor = current.equals(rel.getFromModelId())
                    ? rel.getToModelId() : rel.getFromModelId();
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    depthMap.put(neighbor, currentDepth + 1);
                    queue.offer(neighbor);
                }
            }
        }
        log.info("BFS扩展完成: 种子{}个 -> 扩展后{}个表", seedModelIds.size(), visited.size());
        return visited;
    }

    /**
     * 根据 modelId 集合构建完整上下文并调用 LLM 生成 SQL
     */
    private String generateSQLWithBFSContext(String question, Set<Long> modelIds,
                                              Long datasourceId) {
        try {
            List<com.tecdo.mac.sql2bot.domain.Model> models = new ArrayList<>();
            Map<Long, List<com.tecdo.mac.sql2bot.domain.ColumnDefinition>> columnsMap = new HashMap<>();
            for (Long modelId : modelIds) {
                com.tecdo.mac.sql2bot.domain.Model model = modelService.getById(modelId);
                if (model == null) continue;
                models.add(model);
                columnsMap.put(modelId, columnDefinitionService.getByModelId(modelId));
            }

            List<com.tecdo.mac.sql2bot.domain.Relationship> relationships =
                relationshipService.listAll().stream()
                    .filter(r -> modelIds.contains(r.getFromModelId())
                              || modelIds.contains(r.getToModelId()))
                    .collect(java.util.stream.Collectors.toList());

            String fewShot = intentFewShotService.getFewShotExamples(datasourceId, question);

            StringBuilder systemPrompt = new StringBuilder();
            systemPrompt.append("你是一个SQL生成专家。根据以下数据库结构和问答示例，为用户问题生成标准MySQL SQL。\n\n");
            systemPrompt.append("## 用户问题\n").append(question).append("\n\n");

            systemPrompt.append("## 数据库表结构\n");
            for (com.tecdo.mac.sql2bot.domain.Model m : models) {
                systemPrompt.append("### ").append(m.getTableName());
                if (m.getDisplayName() != null) systemPrompt.append(" (").append(m.getDisplayName()).append(")");
                systemPrompt.append("\n");
                if (m.getDescription() != null) systemPrompt.append("描述: ").append(m.getDescription()).append("\n");
                systemPrompt.append("字段:\n");
                List<com.tecdo.mac.sql2bot.domain.ColumnDefinition> cols = columnsMap.get(m.getId());
                if (cols != null) {
                    for (com.tecdo.mac.sql2bot.domain.ColumnDefinition col : cols) {
                        systemPrompt.append("- ").append(col.getColumnName());
                        if (col.getColumnType() != null) systemPrompt.append(" (").append(col.getColumnType()).append(")");
                        if (col.getDisplayName() != null) systemPrompt.append(" - ").append(col.getDisplayName());
                        if (col.getDescription() != null) systemPrompt.append(": ").append(col.getDescription());
                        systemPrompt.append("\n");
                    }
                }
                systemPrompt.append("\n");
            }

            systemPrompt.append("## 表关系\n");
            for (com.tecdo.mac.sql2bot.domain.Relationship rel : relationships) {
                String fromName = models.stream()
                    .filter(m -> m.getId().equals(rel.getFromModelId()))
                    .map(com.tecdo.mac.sql2bot.domain.Model::getTableName)
                    .findFirst().orElse("unknown");
                String toName = models.stream()
                    .filter(m -> m.getId().equals(rel.getToModelId()))
                    .map(com.tecdo.mac.sql2bot.domain.Model::getTableName)
                    .findFirst().orElse("unknown");
                systemPrompt.append("- ").append(fromName).append(".").append(rel.getFromColumn())
                      .append(" -> ").append(toName).append(".").append(rel.getToColumn())
                      .append(" (").append(rel.getRelationshipType()).append(")\n");
            }
            systemPrompt.append("\n");

            if (fewShot != null && !fewShot.trim().isEmpty()) {
                systemPrompt.append("## 参考示例\n").append(fewShot).append("\n\n");
            }

            systemPrompt.append("## 要求\n只返回SQL语句，不要额外解释。确保SQL语法正确，使用合适的JOIN和WHERE条件。\n");

            // system prompt 包含完整上下文，user prompt 只传问题
            String llmResponse = aiService.generateSQL(systemPrompt.toString(), question);
            String sql = aiService.extractSQL(llmResponse);
            log.info("BFS上下文LLM生成SQL成功: {}", sql);
            return sql;
        } catch (Exception e) {
            log.error("BFS上下文LLM生成SQL失败: question={}", question, e);
            return null;
        }
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
