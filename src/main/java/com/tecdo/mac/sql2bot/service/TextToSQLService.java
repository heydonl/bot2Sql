package com.tecdo.mac.sql2bot.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tecdo.mac.sql2bot.domain.Conversation;
import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.domain.QueryStepLog;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.domain.UserQueryTemplate;
import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import com.tecdo.mac.sql2bot.dto.SqlStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final QueryTemplateService queryTemplateService;
    private final TemplateParameterService templateParameterService;
    private final QueryLogService queryLogService;
    private final SchemaVectorStoreService schemaVectorStoreService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final ModelService modelService;
    private final RelationshipService relationshipService;
    private final ColumnDefinitionService columnDefinitionService;
    private final DataSourceService dataSourceService;
    private final DatabaseService databaseService;
    private final QueryStepLogService queryStepLogService;
    private final UserQueryTemplateService userQueryTemplateService;
    private final TemplateVectorStoreService templateVectorStoreService;

    // RAG模板检索配置
    private static final int MAX_TEMPLATE_CANDIDATES = 5;

    // Schema 向量检索配置
    @org.springframework.beans.factory.annotation.Value("${schema.search.top-k:10}")
    private int schemaSearchTopK;

    @org.springframework.beans.factory.annotation.Value("${schema.search.similarity-threshold:0.5}")
    private double schemaSearchSimilarityThreshold;

    @org.springframework.beans.factory.annotation.Value("${schema.search.bfs-retry-top-k:20}")
    private int schemaSearchBfsRetryTopK;

    @org.springframework.beans.factory.annotation.Value("${template.search.similarity-threshold:0.6}")
    private double templateSearchSimilarityThreshold;

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
            if (conversationId != null) {
                if (!conversationService.belongsToUser(conversationId, request.getUserId())) {
                    return QueryResponse.error("无权访问该会话");
                }
            } else if (Boolean.TRUE.equals(request.getCreateNewConversation())) {
                Conversation conversation = conversationService.create(
                    request.getUserId(),
                    generateConversationTitle(request.getQuestion()),
                    null
                );
                conversationId = conversation.getId();
                log.info("Created new conversation: id={}, userId={}", conversationId, request.getUserId());
            }

            // 3. 保存用户消息
            if (conversationId != null) {
                messageService.saveUserMessage(conversationId, request.getQuestion());
            }

            // 4. 处理用户反馈
            if (request.getSatisfied() != null && request.getRetryQueryLogId() != null) {
                handleUserFeedback(request);

                if (Boolean.TRUE.equals(request.getSatisfied())) {
                    QueryResponse response = new QueryResponse();
                    response.setSuccess(true);
                    response.setExplanation("感谢您的反馈");
                    return response;
                }
                // 不满意，继续执行后续流程生成新答案
            }

            // 5. 路径3：用户不满意，直接走高召回 BFS 路径
            if (Boolean.FALSE.equals(request.getSatisfied()) && request.getRetryQueryLogId() != null) {
                // 降低来源用户模板评分
                QueryLog retryLog = queryLogService.findById(request.getRetryQueryLogId());
                if (retryLog != null && "user_template".equals(retryLog.getSourceType())) {
                    userQueryTemplateService.updateScoreOnUnsatisfied(retryLog.getSourceTemplateId());
                }
                log.info("用户不满意，触发BFS高召回重试: queryLogId={}", request.getRetryQueryLogId());
                List<SqlStep> steps = generatePlanByBFSWithHighRecall(request.getQuestion(), request.getRetryQueryLogId());
                return executePlan(request.getQuestion(), steps, conversationId, request,
                    null, "bfs", 0.0, startTime);
            }

            // 6. 路径1：RAG 模板检索（高准确性）- 支持用户模板和系统模板
            QueryTemplate matchedTemplate = null;
            UserQueryTemplate matchedUserTemplate = null;
            double templateSimilarity = 0.0;
            log.info("开始RAG模板检索: question={}", request.getQuestion());

            // 1. 先搜索系统模板（query_template）
            QueryTemplate matchedSystemTemplate = null;
            try {
                List<TemplateVectorStoreService.TemplateSearchResult> systemTemplateResults =
                    templateVectorStoreService.searchSystemTemplates(
                        request.getQuestion(), null, MAX_TEMPLATE_CANDIDATES);
                if (!systemTemplateResults.isEmpty()) {
                    TemplateVectorStoreService.TemplateSearchResult bestMatch = systemTemplateResults.get(0);
                    templateSimilarity = bestMatch.getSimilarity();
                    if (templateSimilarity >= templateSearchSimilarityThreshold) {
                        QueryTemplate candidate = queryTemplateService.getById(bestMatch.getMeta().getTemplateId());
                        if (candidate != null) {
                            matchedSystemTemplate = candidate;
                            log.info("系统模板匹配成功: templateId={}, similarity={}",
                                    matchedSystemTemplate.getId(), templateSimilarity);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("系统模板检索失败", e);
            }

            // 2. 如果系统模板未匹配，再搜索用户模板（user_query_template）
            if (matchedSystemTemplate == null && matchedUserTemplate == null) {
                try {
                    List<TemplateVectorStoreService.TemplateSearchResult> userTemplateResults =
                        templateVectorStoreService.searchUserTemplates(
                            request.getQuestion(), null, MAX_TEMPLATE_CANDIDATES);
                    if (!userTemplateResults.isEmpty()) {
                        TemplateVectorStoreService.TemplateSearchResult bestMatch = userTemplateResults.get(0);
                        templateSimilarity = bestMatch.getSimilarity();
                        if (templateSimilarity >= templateSearchSimilarityThreshold) {
                            UserQueryTemplate candidate = userQueryTemplateService.findById(bestMatch.getMeta().getTemplateId());
                            if (candidate != null) {
                                matchedUserTemplate = candidate;
                                log.info("用户模板匹配成功: userTemplateId={}, similarity={}",
                                        matchedUserTemplate.getId(), templateSimilarity);
                            }
                        } else {
                            log.info("模板相似度不足，降级到路径2: similarity={}, threshold={}",
                                templateSimilarity, templateSearchSimilarityThreshold);
                        }
                    } else {
                        log.info("未找到符合条件的模板，降级到路径2");
                    }
                } catch (Exception e) {
                    log.error("用户模板检索失败，降级到路径2", e);
                }
            }

            // 处理用户模板匹配
            if (matchedUserTemplate != null) {
                String prompt = buildUserTemplateParameterPrompt(matchedUserTemplate, request.getQuestion());
                QueryResponse resp = executeTemplatePath1(prompt, matchedUserTemplate.getId(), "user_template",
                        templateSimilarity, request, conversationId, startTime);
                if (resp != null) return resp;
            }

            // 处理系统模板匹配
            if (matchedTemplate != null) {
                String prompt = buildTemplateParameterPrompt(matchedTemplate, request.getQuestion());
                QueryResponse resp = executeTemplatePath1(prompt, matchedTemplate.getId(), "system_template",
                        templateSimilarity, request, conversationId, startTime);
                if (resp != null) return resp;
            }

            // 7. 路径2：Schema RAG + BFS扩表 + LLM
            log.info("进入路径2: Schema RAG + BFS扩表 + LLM");
            List<SqlStep> steps = generatePlanByBFSNormal(request.getQuestion(), request.getRetryQueryLogId());
            return executePlan(request.getQuestion(), steps, conversationId, request,
                null, "bfs", 0.0, startTime);

        } catch (Exception e) {
            log.error("Failed to process query", e);
            if (request.getConversationId() != null) {
                messageService.saveErrorMessage(request.getConversationId(), e.getMessage());
            }
            return QueryResponse.error(e.getMessage());
        }
    }

    /**
     * 处理用户反馈
     */
    private void handleUserFeedback(QueryRequest request) {
        Long queryLogId = request.getRetryQueryLogId();
        Boolean satisfied = request.getSatisfied();

        QueryLog queryLog = queryLogService.findById(queryLogId);
        if (queryLog == null) {
            throw new IllegalArgumentException("查询记录不存在: id=" + queryLogId);
        }

        if (queryLog.getSatisfied() != null) {
            if (Boolean.TRUE.equals(queryLog.getSatisfied())) {
                // 已满意的不允许再评价
                throw new IllegalStateException("该查询已评价为满意，无法再次评价: id=" + queryLogId);
            }
            // 已不满意的允许继续走重试流程，跳过重复写入
            log.info("该查询已评价为不满意，跳过重复写入，继续重试: id={}", queryLogId);
            return;
        }

        queryLogService.updateSatisfied(queryLogId, satisfied);

        // 来自系统模板的查询不沉淀为用户模板
        if ("system_template".equals(queryLog.getSourceType())) {
            return;
        }

        String question = queryLog.getQuestion();
        String sql = queryLog.getGeneratedSql();

        if (Boolean.TRUE.equals(satisfied)) {
            UserQueryTemplate existing = userQueryTemplateService.findByQuestionAndSql(question, sql);

            if (existing == null) {
                try {
                    UserQueryTemplate newTemplate = userQueryTemplateService.create(question, sql, queryLog.getDatasourceId());
                    try {
                        templateVectorStoreService.indexUserTemplate(newTemplate);
                    } catch (Exception e) {
                        log.error("用户模板向量索引失败，不影响反馈记录: id={}", newTemplate.getId(), e);
                    }
                } catch (Exception e) {
                    // 并发场景下可能触发唯一索引冲突，此时查找已存在的记录并更新评分
                    log.warn("创建用户模板时发生冲突（可能是并发），尝试更新已有记录: question={}", question);
                    UserQueryTemplate conflicted = userQueryTemplateService.findByQuestionAndSql(question, sql);
                    if (conflicted != null) {
                        userQueryTemplateService.updateScoreOnSatisfied(conflicted.getId());
                    }
                }
            } else {
                userQueryTemplateService.updateScoreOnSatisfied(existing.getId());
            }
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

        prompt.append("## SQL模板\n").append(template.getGeneratedSql()).append("\n\n");

        // 添加历史查询示例
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
     * 解析 sqlTemplate 字段为 SqlStep 列表
     * 支持 JSON 数组格式和旧版单条 SQL 字符串（向后兼容）
     */
    private List<SqlStep> parseSqlSteps(String sqlTemplate) {
        if (sqlTemplate == null || sqlTemplate.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(sqlTemplate);
            if (node.isArray()) {
                List<SqlStep> steps = new ArrayList<>();
                for (JsonNode item : node) {
                    SqlStep step = new SqlStep();
                    step.setId(item.path("id").asText(null));
                    step.setSqlTemplate(item.path("sql_template").asText(null));
                    step.setDatasourceId(item.has("datasource_id") && !item.get("datasource_id").isNull()
                        ? item.get("datasource_id").asLong() : null);
                    // 解析 tables
                    JsonNode tablesNode = item.path("tables");
                    if (tablesNode.isArray()) {
                        List<SqlStep.TableInfo> tables = new ArrayList<>();
                        for (JsonNode tableNode : tablesNode) {
                            SqlStep.TableInfo tableInfo = new SqlStep.TableInfo();
                            tableInfo.setName(tableNode.path("name").asText());
                            tableInfo.setDatabase(tableNode.path("database").asText());
                            tables.add(tableInfo);
                        }
                        step.setTables(tables);
                    }
                    // 解析 params
                    JsonNode paramsNode = item.path("params");
                    if (paramsNode.isObject()) {
                        Map<String, Object> params = new HashMap<>();
                        paramsNode.properties().forEach(entry -> {
                            JsonNode val = entry.getValue();
                            if (val.isNumber()) {
                                params.put(entry.getKey(), val.numberValue());
                            } else {
                                params.put(entry.getKey(), val.asText());
                            }
                        });
                        step.setParams(params);
                    }
                    steps.add(step);
                }
                return steps.isEmpty() ? null : steps;
            }
        } catch (Exception ignored) {}
        // 向后兼容：旧版单条 SQL 字符串
        String upper = sqlTemplate.trim().toUpperCase();
        if (upper.startsWith("SELECT") || upper.startsWith("WITH")) {
            SqlStep step = new SqlStep(sqlTemplate, null);
            return List.of(step);
        }
        return null;
    }

    /**
     * 构建参数填充 Prompt（per-step）
     */
    private String buildParamFillingPrompt(String question, String sqlTemplate,
                                            List<Map<String, Object>> prevResult,
                                            QueryLog example) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个SQL参数填充专家。根据以下信息，将SQL模板中的{{param}}占位符替换为具体值。\n\n");
        sb.append("SQL模板:\n").append(sqlTemplate).append("\n\n");

        if (prevResult != null && !prevResult.isEmpty()) {
            List<Map<String, Object>> truncated = prevResult.size() > 100
                ? prevResult.subList(0, 100) : prevResult;
            try {
                sb.append("上一步查询结果（最多100行）:\n")
                  .append(objectMapper.writeValueAsString(truncated)).append("\n\n");
            } catch (Exception e) {
                sb.append("上一步查询结果: (序列化失败)\n\n");
            }
        } else {
            sb.append("上一步查询结果: 无\n\n");
        }

        if (example != null) {
            sb.append("参考示例（高评分历史问答）:\n");
            sb.append("问题: ").append(example.getQuestion()).append("\n");
            sb.append("SQL: ").append(example.getGeneratedSql()).append("\n\n");
        }

        sb.append("用户问题:\n").append(question).append("\n\n");
        sb.append("要求：只返回填充完成的SQL语句，不要额外解释。");
        return sb.toString();
    }

    /**
     * 顺序执行多步骤 SQL 计划
     */
    private QueryResponse executePlan(String question, List<SqlStep> steps,
            Long conversationId, QueryRequest request,
            Long sourceTemplateId, String sourceType,
            double templateSimilarity, long startTime) {
        return executePlanWithRetry(question, steps, conversationId, request,
            sourceTemplateId, sourceType, templateSimilarity, startTime, 0, null);
    }

    private QueryResponse executePlanWithRetry(String question, List<SqlStep> steps,
            Long conversationId, QueryRequest request,
            Long sourceTemplateId, String sourceType,
            double templateSimilarity, long startTime, int retryCount, String lastError) {

        final int MAX_RETRIES = 10;

        if (steps == null || steps.isEmpty()) {
            String err = "执行计划为空";
            if (conversationId != null) messageService.saveErrorMessage(conversationId, err);
            return QueryResponse.error(err);
        }

        String explanation = "system_template".equals(sourceType) ? "通过系统模板匹配生成查询" :
                             "user_template".equals(sourceType) ? "通过用户模板匹配生成查询" :
                             "通过BFS表发现生成查询";
        List<Map<String, Object>> prevResult = null;
        String lastFilledSql = null;
        Long lastDatasourceId = null;
        List<QueryStepLog> stepLogs = new ArrayList<>();
        Map<String, List<Map<String, Object>>> allStepResults = new HashMap<>();

        for (int i = 0; i < steps.size(); i++) {
            SqlStep step = steps.get(i);
            long stepStartTime = System.currentTimeMillis();
            QueryStepLog stepLog = new QueryStepLog();
            stepLog.setStepId(step.getId() != null ? step.getId() : "step" + (i + 1));
            stepLog.setStepIndex(i);
            stepLog.setSqlTemplate(step.getSqlTemplate());
            stepLog.setDatasourceId(step.getDatasourceId());

            // 参数填充：优先使用 params 直接替换，否则走 LLM
            String filledSql;
            if (step.getParams() != null && !step.getParams().isEmpty()) {
                filledSql = replacePlaceholders(step, allStepResults);
            } else {
                // 降级到 LLM 参数填充
                QueryLog example = null;
                try {
                    example = queryLogService.getBestRecentExample(step.getDatasourceId());
                } catch (Exception e) {
                    log.warn("获取参考示例失败，跳过: {}", e.getMessage());
                }
                try {
                    String prompt = buildParamFillingPrompt(question, step.getSqlTemplate(), prevResult, example);
                    String llmResp = aiService.generateSQL(prompt, "请填充SQL模板中的参数。");
                    filledSql = aiService.extractSQL(llmResp);
                    if (filledSql == null || filledSql.isBlank()) {
                        filledSql = llmResp.trim();
                    }
                } catch (Exception e) {
                    log.error("LLM参数填充失败: step={}", step.getSqlTemplate(), e);
                    String err = "SQL参数填充失败: " + e.getMessage();
                    if (conversationId != null) messageService.saveErrorMessage(conversationId, err);
                    return QueryResponse.error(err);
                }
            }

            // 推断数据源
            Long datasourceId = step.getDatasourceId();
            if (datasourceId == null) {
                datasourceId = semanticContextService.inferDataSourceFromSQL(filledSql);
            }
            if (datasourceId == null) {
                String err = "无法推断数据源，请检查SQL中的表名";
                if (conversationId != null) messageService.saveErrorMessage(conversationId, err);
                return QueryResponse.error(err);
            }

            // 执行 SQL
            try {
                prevResult = queryExecutorService.executeQuery(datasourceId, filledSql);
                lastFilledSql = filledSql;
                lastDatasourceId = datasourceId;
                log.info("步骤执行成功: sql={}, rows={}", filledSql, prevResult.size());

                allStepResults.put(stepLog.getStepId(), prevResult);
                stepLog.setFilledSql(filledSql);
                stepLog.setExecutionSuccess(true);
                stepLog.setResultCount(prevResult.size());
                stepLog.setExecutionTime(System.currentTimeMillis() - stepStartTime);
                stepLogs.add(stepLog);
            } catch (Exception e) {
                log.error("步骤执行失败: sql={}, retryCount={}", filledSql, retryCount, e);

                stepLog.setFilledSql(filledSql);
                stepLog.setExecutionSuccess(false);
                stepLog.setErrorMessage(e.getMessage());
                stepLog.setExecutionTime(System.currentTimeMillis() - stepStartTime);
                stepLogs.add(stepLog);

                // 记录失败日志
                Long failedQueryLogId = null;
                try {
                    QueryLog queryLog = new QueryLog();
                    queryLog.setUserId(request.getUserId());
                    queryLog.setConversationId(conversationId);
                    queryLog.setQuestion(question);
                    queryLog.setTemplateId(sourceTemplateId);
                    queryLog.setIsFromTemplate(sourceTemplateId != null);
                    queryLog.setSourceType(sourceType);
                    queryLog.setSourceTemplateId(sourceTemplateId);
                    queryLog.setRetryFromId(request.getRetryQueryLogId());
                    queryLog.setGeneratedSql(filledSql);
                    queryLog.setExecutionSuccess(false);
                    queryLog.setExecutionTime(System.currentTimeMillis() - startTime);
                    queryLog.setDatasourceId(datasourceId);
                    queryLog.setErrorMessage(e.getMessage());
                    failedQueryLogId = queryLogService.logQuery(queryLog);

                    if (failedQueryLogId != null) {
                        final Long finalFailedQueryLogId = failedQueryLogId;
                        stepLogs.forEach(sl -> sl.setQueryLogId(finalFailedQueryLogId));
                        try { queryStepLogService.logSteps(stepLogs); } catch (Exception ex) { log.warn("记录步骤日志失败", ex); }
                    }
                } catch (Exception logEx) {
                    log.warn("记录失败日志异常", logEx);
                }

                // 如果未达到最大重试次数，自动重试
                if (retryCount < MAX_RETRIES) {
                    log.info("SQL执行失败，尝试第 {} 次重试，错误信息: {}", retryCount + 1, e.getMessage());

                    // 使用失败的 queryLogId 重新生成 SQL
                    try {
                        List<SqlStep> newSteps = generatePlanByBFSNormal(question, failedQueryLogId);
                        if (newSteps != null && !newSteps.isEmpty()) {
                            return executePlanWithRetry(question, newSteps, conversationId, request,
                                sourceTemplateId, sourceType, templateSimilarity, startTime, retryCount + 1, e.getMessage());
                        }
                    } catch (Exception retryEx) {
                        log.error("重试生成SQL失败", retryEx);
                    }
                }

                // 达到最大重试次数或重试失败，返回错误
                String err = "SQL执行失败（已重试" + retryCount + "次）: " + e.getMessage();
                if (conversationId != null) messageService.saveErrorMessage(conversationId, err);
                return QueryResponse.error(err);
            }
        }

        // 记录成功日志
        Long queryLogId = null;
        try {
            QueryLog queryLog = new QueryLog();
            queryLog.setUserId(request.getUserId());
            queryLog.setConversationId(conversationId);
            queryLog.setQuestion(question);
            queryLog.setTemplateId(sourceTemplateId);
            queryLog.setIsFromTemplate(sourceTemplateId != null);
            queryLog.setSourceType(sourceType);
            queryLog.setSourceTemplateId(sourceTemplateId);
            queryLog.setRetryFromId(request.getRetryQueryLogId());
            queryLog.setGeneratedSql(lastFilledSql);
            queryLog.setExecutionSuccess(true);
            queryLog.setResultCount(prevResult.size());
            queryLog.setDatasourceId(lastDatasourceId);
            queryLog.setExecutionTime(System.currentTimeMillis() - startTime);
            queryLogId = queryLogService.logQuery(queryLog);
        } catch (Exception e) {
            log.warn("记录查询日志失败（不影响查询结果）", e);
        }

        if (queryLogId != null) {
            final Long finalQueryLogId = queryLogId;
            stepLogs.forEach(sl -> sl.setQueryLogId(finalQueryLogId));
            try { queryStepLogService.logSteps(stepLogs); } catch (Exception e) { log.warn("记录步骤日志失败", e); }
        }

        if (conversationId != null) {
            messageService.saveAssistantMessage(conversationId, explanation, lastFilledSql, prevResult);
        }

        long totalTime = System.currentTimeMillis() - startTime;
        QueryResponse response = QueryResponse.success(conversationId, lastFilledSql, explanation, prevResult, totalTime);
        response.setQueryLogId(queryLogId);
        response.setTemplateId(sourceTemplateId);
        response.setFromTemplate(sourceTemplateId != null);
        response.setTemplateSimilarity(templateSimilarity);
        return response;
    }

    /**
     * 路径2：正常召回 + 相似度过滤 + BFS扩表 + LLM 生成执行计划
     */
    private List<SqlStep> generatePlanByBFSNormal(String question, Long retryQueryLogId) {
        List<SchemaVectorStoreService.SchemaSearchResult> schemaResults =
            schemaVectorStoreService.searchSchemas(question, null, schemaSearchTopK);
        Set<Long> seedModelIds = schemaResults.stream()
            .filter(r -> r.getScore() >= schemaSearchSimilarityThreshold)
            .filter(r -> r.getMeta() != null && r.getMeta().getModelId() != null)
            .map(r -> r.getMeta().getModelId())
            .collect(java.util.stream.Collectors.toSet());
        if (seedModelIds.isEmpty()) {
            log.warn("路径2: Schema RAG 无种子表，降级到语义上下文");
            try {
                String sysPrompt = semanticContextService.generateSystemPrompt(null, question);
                String aiResp = aiService.generateSQL(sysPrompt,
                    semanticContextService.generateUserPrompt(question));
                String sql = aiService.extractSQL(aiResp);
                return sql != null ? List.of(new SqlStep(sql, null)) : null;
            } catch (Exception e) {
                log.error("路径2 语义上下文降级失败", e);
                return null;
            }
        }
        Set<Long> allModelIds = expandByBFS(seedModelIds, 2);
        return generatePlanWithBFSContext(question, allModelIds, retryQueryLogId);
    }

    /**
     * 路径3：高召回（不做相似度过滤）+ BFS扩表 + LLM 生成执行计划
     */
    private List<SqlStep> generatePlanByBFSWithHighRecall(String question, Long retryQueryLogId) {
        List<SchemaVectorStoreService.SchemaSearchResult> schemaResults =
            schemaVectorStoreService.searchSchemas(question, null, schemaSearchBfsRetryTopK);
        Set<Long> seedModelIds = schemaResults.stream()
            .filter(r -> r.getMeta() != null && r.getMeta().getModelId() != null)
            .map(r -> r.getMeta().getModelId())
            .collect(java.util.stream.Collectors.toSet());
        if (seedModelIds.isEmpty()) {
            log.warn("路径3: 高召回 Schema RAG 无结果");
            return null;
        }
        Set<Long> allModelIds = expandByBFS(seedModelIds, 2);
        return generatePlanWithBFSContext(question, allModelIds, retryQueryLogId);
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
     * 根据 modelId 集合构建完整上下文，调用 LLM 生成多步骤执行计划
     */
    private List<SqlStep> generatePlanWithBFSContext(String question, Set<Long> modelIds, Long retryQueryLogId) {
        try {
            List<com.tecdo.mac.sql2bot.domain.Model> models = new ArrayList<>();
            Map<Long, List<com.tecdo.mac.sql2bot.domain.ColumnDefinition>> columnsMap = new HashMap<>();
            for (Long modelId : modelIds) {
                com.tecdo.mac.sql2bot.domain.Model model = modelService.getById(modelId);
                if (model == null) continue;
                models.add(model);
                columnsMap.put(modelId, columnDefinitionService.getByModelId(modelId));
            }

            // 只保留两端都在 models 列表中的关系
            Set<Long> validModelIds = models.stream()
                .map(com.tecdo.mac.sql2bot.domain.Model::getId)
                .collect(java.util.stream.Collectors.toSet());

            List<com.tecdo.mac.sql2bot.domain.Relationship> relationships =
                relationshipService.listAll().stream()
                    .filter(r -> validModelIds.contains(r.getFromModelId())
                              && validModelIds.contains(r.getToModelId()))
                    .collect(java.util.stream.Collectors.toList());

            // 获取系统模板作为参考示例
            String templateExamples = buildExamplesFromQueryTemplates();

            StringBuilder systemPrompt = new StringBuilder();
            systemPrompt.append("你是一个SQL生成专家。根据以下数据库结构，为用户问题生成多步骤SQL执行计划。\n\n");

            systemPrompt.append("## 数据库表结构\n");
            for (com.tecdo.mac.sql2bot.domain.Model m : models) {
                systemPrompt.append("### ").append(m.getTableName());
                if (m.getDisplayName() != null) systemPrompt.append(" (").append(m.getDisplayName()).append(")");
                // 写出所属数据库名，方便 LLM 知道表归属
                if (m.getDatabaseId() != null) {
                    com.tecdo.mac.sql2bot.domain.Database db = databaseService.getById(m.getDatabaseId());
                    if (db != null && db.getDatabaseName() != null) {
                        systemPrompt.append(" [数据库: ").append(db.getDatabaseName()).append("]");
                    }
                }
                systemPrompt.append(" (datasource_id: ").append(m.getDatasourceId()).append(")\n");
                if (m.getDescription() != null) systemPrompt.append("描述: ").append(m.getDescription()).append("\n");
                systemPrompt.append("字段:\n");
                List<com.tecdo.mac.sql2bot.domain.ColumnDefinition> cols = columnsMap.get(m.getId());
                if (cols != null) {
                    for (com.tecdo.mac.sql2bot.domain.ColumnDefinition col : cols) {
                        systemPrompt.append("- ").append(m.getTableName()).append(".").append(col.getColumnName());
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

                // 从 joinCondition JSON 中解析字段名
                String fromColumn = "unknown";
                String toColumn = "unknown";
                try {
                    if (rel.getJoinCondition() != null && !rel.getJoinCondition().isEmpty()) {
                        JsonNode joinConditions = objectMapper.readTree(rel.getJoinCondition());
                        if (joinConditions.isArray() && joinConditions.size() > 0) {
                            JsonNode firstCondition = joinConditions.get(0);
                            fromColumn = firstCondition.has("from") ? firstCondition.get("from").asText() : "unknown";
                            toColumn = firstCondition.has("to") ? firstCondition.get("to").asText() : "unknown";
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析 joinCondition 失败: {}", rel.getJoinCondition(), e);
                }

                systemPrompt.append("- ").append(fromName).append(".").append(fromColumn)
                      .append(" -> ").append(toName).append(".").append(toColumn)
                      .append(" (").append(rel.getRelationshipType()).append(")\n");
            }
            systemPrompt.append("\n");

            if (templateExamples != null && !templateExamples.trim().isEmpty()) {
                systemPrompt.append("## 参考示例\n").append(templateExamples).append("\n\n");
            }

            // 添加失败案例作为负面示例（当前会话中的所有失败案例）
            if (retryQueryLogId != null) {
                try {
                    // 获取当前失败的查询记录，从中提取会话ID
                    QueryLog currentFailedCase = queryLogService.findById(retryQueryLogId);
                    if (currentFailedCase != null && currentFailedCase.getConversationId() != null) {
                        // 获取当前会话中的所有失败案例
                        List<QueryLog> failedCases = queryLogService.getFailedCasesByConversation(
                            currentFailedCase.getConversationId(), 10);

                        if (!failedCases.isEmpty()) {
                            systemPrompt.append("## 错误案例（请避免犯同样的错误）\n");
                            systemPrompt.append("以下是本次会话中的失败尝试，请分析错误原因并避免重复：\n\n");

                            for (int i = 0; i < failedCases.size(); i++) {
                                QueryLog failedCase = failedCases.get(i);
                                systemPrompt.append("### 错误案例 ").append(i + 1).append("\n");
                                systemPrompt.append("问题: ").append(failedCase.getQuestion()).append("\n");
                                systemPrompt.append("错误的SQL: ").append(failedCase.getGeneratedSql()).append("\n");

                                if (failedCase.getExecutionSuccess() != null && !failedCase.getExecutionSuccess()) {
                                    systemPrompt.append("错误原因: SQL执行失败");
                                    if (failedCase.getErrorMessage() != null) {
                                        systemPrompt.append("，数据库报错: ").append(failedCase.getErrorMessage());
                                    }
                                    systemPrompt.append("\n");
                                } else if (Boolean.FALSE.equals(failedCase.getSatisfied())) {
                                    systemPrompt.append("错误原因: 用户反馈不满意\n");
                                }
                                systemPrompt.append("\n");
                            }

                            systemPrompt.append("注意: 请仔细分析上述错误案例，避免生成类似的错误SQL。")
                                      .append("特别注意字段归属、表名使用、数据类型匹配等问题。\n\n");

                            log.info("已添加会话失败案例到Prompt: conversationId={}, failedCasesCount={}",
                                currentFailedCase.getConversationId(), failedCases.size());
                        }
                    }
                } catch (Exception e) {
                    log.warn("获取会话失败案例失败，跳过: {}", e.getMessage());
                }
            }

            systemPrompt.append("## 输出要求\n");
            systemPrompt.append("返回JSON数组，每个元素格式: {\"sql_template\": \"...\", \"params\": {...}, \"datasource_id\": N}\n");
            systemPrompt.append("- 使用 {{param_name}} 表示依赖用户输入或上一步查询结果的动态值\n");
            systemPrompt.append("- params 对象包含所有参数的默认值或示例值\n");
            systemPrompt.append("- datasource_id 必须与表所属的数据源一致\n");
            systemPrompt.append("- 如只需一步，也返回只含一个元素的数组\n");
            systemPrompt.append("- 只返回JSON数组，不要额外解释\n");
            systemPrompt.append("- 字段归属约束：每个字段唯一属于其所在的表，SQL中必须使用 表名.字段名 的形式引用字段，禁止跨表混用字段\n");

            log.info("BFS上下文Prompt已添加字段归属约束");
            String llmResponse = aiService.generateSQL(systemPrompt.toString(), question);

            // 提取 JSON 数组
            String jsonStr = extractJsonBlock(llmResponse);
            if (jsonStr == null) {
                // 尝试直接解析整个响应
                jsonStr = llmResponse.trim();
            }

            List<SqlStep> steps = parseSqlSteps(jsonStr);
            if (steps == null || steps.isEmpty()) {
                log.error("BFS上下文LLM生成执行计划失败，无法解析JSON: {}", llmResponse);
                return null;
            }
            log.info("BFS上下文LLM生成执行计划成功: steps={}", steps.size());
            return steps;
        } catch (Exception e) {
            log.error("BFS上下文LLM生成执行计划失败: question={}", question, e);
            return null;
        }
    }

    /**
     * 模板参数填充方法
     */
    private String fillTemplateParameters(String template, String userQuestion, String exampleContext) {
        String prompt = buildParamFillingPrompt(template, userQuestion, exampleContext);
        return aiService.generateSQL(prompt, "请填充SQL模板中的参数。");
    }

    /**
     * 构建参数填充提示词
     */
    private String buildParamFillingPrompt(String template, String userQuestion, String exampleContext) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个SQL参数填充专家。根据以下信息，将SQL模板中的{{param}}占位符替换为具体值。\n\n");

        prompt.append("## SQL模板\n").append(template).append("\n\n");

        if (exampleContext != null && !exampleContext.trim().isEmpty()) {
            prompt.append("## 示例上下文\n").append(exampleContext).append("\n\n");
        }

        prompt.append("## 用户问题\n").append(userQuestion).append("\n\n");

        prompt.append("## 要求\n");
        prompt.append("请分析用户问题，为模板中的每个参数生成合适的值。\n");
        prompt.append("只返回填充完成的SQL语句，不要额外解释。");

        return prompt.toString();
    }

    /**
     * 示例上下文构建方法
     */
    private String buildExampleContext(Long templateId) {
        try {
            QueryLog example = queryLogService.getBestExampleByTemplateId(templateId);
            if (example == null) {
                return "";
            }

            // 解析intent_sql获取表信息
            List<TableInfo> tables = parseIntentSqlTables(example.getIntentJson());

            // 构建上下文信息
            StringBuilder context = new StringBuilder();
            context.append("示例问题: ").append(example.getQuestion()).append("\n");
            context.append("相关表信息:\n");

            for (TableInfo tableInfo : tables) {
                com.tecdo.mac.sql2bot.domain.Model model = modelService.getByDatabaseAndTableName(
                    tableInfo.getDatabase(), tableInfo.getName());
                if (model != null) {
                    context.append("- 表: ").append(model.getTableName())
                           .append(", 描述: ").append(model.getDescription()).append("\n");
                }
            }

            return context.toString();
        } catch (Exception e) {
            log.error("构建示例上下文失败: templateId={}", templateId, e);
            return "";
        }
    }

    /**
     * Intent SQL解析方法
     */
    private List<TableInfo> parseIntentSqlTables(String intentSql) {
        List<TableInfo> tables = new ArrayList<>();
        try {
            if (intentSql == null || intentSql.trim().isEmpty()) {
                return tables;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(intentSql);
            JsonNode tablesNode = root.get("tables");

            if (tablesNode != null && tablesNode.isArray()) {
                for (JsonNode tableNode : tablesNode) {
                    String name = tableNode.get("name").asText();
                    String database = tableNode.get("database").asText();
                    tables.add(new TableInfo(name, database));
                }
            }
        } catch (Exception e) {
            log.error("解析intent_sql失败: {}", e.getMessage());
        }
        return tables;
    }

    /**
     * 表信息内部类
     */
    private static class TableInfo {
        private final String name;
        private final String database;

        public TableInfo(String name, String database) {
            this.name = name;
            this.database = database;
        }

        public String getName() {
            return name;
        }

        public String getDatabase() {
            return database;
        }
    }

    /**
     * 解析 intent_json/intent_sql JSON 为 SqlStep 列表（用于构建示例上下文）
     */
    private List<SqlStep> parseIntentSql(String intentSqlJson) {
        try {
            JsonNode stepsArray = objectMapper.readTree(intentSqlJson);
            List<SqlStep> steps = new ArrayList<>();
            for (JsonNode stepNode : stepsArray) {
                SqlStep step = new SqlStep();
                step.setId(stepNode.path("id").asText(null));
                step.setDatasourceId(stepNode.path("datasource_id").asLong(0));
                JsonNode tablesNode = stepNode.path("tables");
                if (tablesNode.isArray()) {
                    List<SqlStep.TableInfo> tables = new ArrayList<>();
                    for (JsonNode tableNode : tablesNode) {
                        SqlStep.TableInfo tableInfo = new SqlStep.TableInfo();
                        tableInfo.setName(tableNode.path("name").asText());
                        tableInfo.setDatabase(tableNode.path("database").asText());
                        tables.add(tableInfo);
                    }
                    step.setTables(tables);
                }
                steps.add(step);
            }
            return steps;
        } catch (Exception e) {
            log.warn("解析 intent_sql 失败: {}", intentSqlJson, e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 构建示例的完整上下文（数据源、数据库、表、字段）供路径1使用
     */
    private String buildExampleContextForTemplate(List<SqlStep> intentSteps) {
        StringBuilder context = new StringBuilder();
        Set<Long> processedDataSources = new HashSet<>();
        Set<String> processedTables = new HashSet<>();

        for (SqlStep step : intentSteps) {
            Long dataSourceId = step.getDatasourceId();
            if (dataSourceId != null && dataSourceId > 0 && !processedDataSources.contains(dataSourceId)) {
                try {
                    com.tecdo.mac.sql2bot.domain.DataSource ds = dataSourceService.getById(dataSourceId);
                    if (ds != null) {
                        context.append("### 数据源: ").append(ds.getName()).append("\n");
                        context.append("- **ID**: ").append(ds.getId()).append("\n\n");
                        processedDataSources.add(dataSourceId);
                    }
                } catch (Exception e) {
                    log.warn("获取数据源失败: dataSourceId={}", dataSourceId, e);
                }
            }

            if (step.getTables() != null) {
                for (SqlStep.TableInfo tableInfo : step.getTables()) {
                    String tableKey = dataSourceId + "." + tableInfo.getDatabase() + "." + tableInfo.getName();
                    if (!processedTables.contains(tableKey)) {
                        try {
                            com.tecdo.mac.sql2bot.domain.Model model =
                                modelService.getByDatabaseAndTableName(tableInfo.getDatabase(), tableInfo.getName());
                            if (model != null) {
                                context.append("#### 表: ").append(model.getTableName()).append("\n");
                                if (model.getDescription() != null) {
                                    context.append("**描述**: ").append(model.getDescription()).append("\n");
                                }
                                List<com.tecdo.mac.sql2bot.domain.ColumnDefinition> columns =
                                    columnDefinitionService.getByModelId(model.getId());
                                context.append("**字段**:\n");
                                for (com.tecdo.mac.sql2bot.domain.ColumnDefinition col : columns) {
                                    context.append("- `").append(col.getColumnName()).append("`");
                                    if (col.getColumnType() != null) context.append(" (").append(col.getColumnType()).append(")");
                                    if (col.getDescription() != null) context.append(": ").append(col.getDescription());
                                    context.append("\n");
                                }
                                context.append("\n");
                                processedTables.add(tableKey);
                            }
                        } catch (Exception e) {
                            log.warn("获取表信息失败: {}.{}", tableInfo.getDatabase(), tableInfo.getName(), e);
                        }
                    }
                }
            }
        }
        return context.toString();
    }

    /**
     * 构建路径1的参数填充 Prompt（包含完整示例上下文）
     */
    private String buildTemplateParameterPrompt(QueryTemplate template, String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 角色\n");
        prompt.append("你是一名 SQL 专家，擅长根据用户问题生成多步骤的 SQL 执行计划。\n\n");
        prompt.append("# 任务\n");
        prompt.append("你将获得一组预定义的 SQL 模板（JSON 数组形式），每个模板包含一个 SQL 片段、参数说明等信息。\n");
        prompt.append("你需要根据用户的最新问题，从模板中选出合适的步骤，并为每个步骤填充具体的参数值（params 字段）。\n\n");
        prompt.append("# 模板说明\n");
        prompt.append("- 模板中的 sql_template 包含占位符 {{xxx}}，你需要在 params 中写出实际值\n");
        prompt.append("- 如果某个参数依赖前一步的输出（如 {{step1.字段名}}），params 中填写 \"step1.字段名\" 即可\n");
        prompt.append("- 时间范围类参数需根据用户描述进行合理转换\n\n");

        try {
            QueryLog example = queryLogService.getBestExampleByTemplateId(template.getId());
            if (example != null && example.getIntentJson() != null) {
                List<SqlStep> intentSteps = parseIntentSql(example.getIntentJson());
                if (!intentSteps.isEmpty()) {
                    String exampleContext = buildExampleContextForTemplate(intentSteps);
                    prompt.append("# 示例\n");
                    prompt.append("**用户问题**: ").append(example.getQuestion()).append("\n\n");
                    if (!exampleContext.isBlank()) {
                        prompt.append(exampleContext).append("\n");
                    }
                    if (example.getGeneratedSql() != null) {
                        prompt.append("**预期输出**:\n```json\n").append(example.getGeneratedSql()).append("\n```\n\n");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取模板示例失败: templateId={}", template.getId(), e);
        }

        prompt.append("用户问题: ").append(question).append("\n");
        prompt.append("初始模板:\n```json\n").append(template.getGeneratedSql()).append("\n```\n\n");
        prompt.append("请返回填充了 params 字段的完整 JSON 数组，用 ```json ``` 包裹。\n");
        return prompt.toString();
    }

    /**
     * 构建路径1的用户模板参数填充 Prompt
     */
    private String buildUserTemplateParameterPrompt(UserQueryTemplate template, String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 角色\n");
        prompt.append("你是一名 SQL 专家，擅长根据用户问题生成多步骤的 SQL 执行计划。\n\n");
        prompt.append("# 任务\n");
        prompt.append("你将获得一个历史问题及其对应的 SQL 执行计划（JSON 数组），请参考该示例，根据新问题生成适配的 SQL 执行计划。\n\n");
        prompt.append("# 示例\n");
        prompt.append("**历史问题**: ").append(template.getQuestion()).append("\n");
        prompt.append("**历史SQL计划**:\n```json\n").append(template.getGeneratedSql()).append("\n```\n\n");
        prompt.append("# 当前问题\n");
        prompt.append("用户问题: ").append(question).append("\n\n");
        prompt.append("请参考历史SQL计划，生成适配当前问题的完整 JSON 数组，用 ```json ``` 包裹。\n");
        return prompt.toString();
    }

    /**
     * 路径1公共执行逻辑：LLM参数填充 → 解析 → executePlan，失败则返回 null 降级
     */
    private QueryResponse executeTemplatePath1(String prompt, Long templateId, String sourceType,
            double similarity, QueryRequest request, Long conversationId, long startTime) {
        try {
            String llmResp = aiService.generateSQL(prompt, "请根据模板和问题生成参数填充的SQL执行计划。");
            String jsonStr = extractJsonBlock(llmResp);
            if (jsonStr == null) jsonStr = llmResp.trim();
            List<SqlStep> steps = parseSqlSteps(jsonStr);
            if (steps == null) {
                log.warn("路径1 LLM参数填充解析失败，降级到路径2: sourceType={}, templateId={}", sourceType, templateId);
                return null;
            }
            log.info("路径1 参数填充成功: sourceType={}, templateId={}, steps={}", sourceType, templateId, steps.size());
            return executePlan(request.getQuestion(), steps, conversationId, request,
                    templateId, sourceType, similarity, startTime);
        } catch (Exception e) {
            log.error("路径1 处理失败，降级到路径2: sourceType={}, templateId={}", sourceType, templateId, e);
            return null;
        }
    }

    /**
     * 替换 SQL 模板中的占位符
     * 支持简单参数 {{param}} 和步骤引用 {{step1.field}}
     */
    private String replacePlaceholders(SqlStep step, Map<String, List<Map<String, Object>>> stepResults) {
        String sql = step.getSqlTemplate();
        if (sql == null || step.getParams() == null || step.getParams().isEmpty()) {
            return sql;
        }
        for (Map.Entry<String, Object> entry : step.getParams().entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            Object paramValue = entry.getValue();
            if (paramValue == null) continue;

            String valueStr = paramValue.toString();
            // 检查是否是步骤引用（格式：stepId.fieldName，stepId 必须在 stepResults 中存在）
            boolean isStepRef = false;
            if (valueStr.contains(".")) {
                String[] parts = valueStr.split("\\.", 2);
                String refStepId = parts[0];
                if (stepResults.containsKey(refStepId)) {
                    isStepRef = true;
                    String fieldName = parts[1];
                    List<Map<String, Object>> stepResult = stepResults.get(refStepId);
                    if (stepResult != null && !stepResult.isEmpty()) {
                        String inClause = stepResult.stream()
                            .map(row -> row.get(fieldName))
                            .filter(v -> v != null)
                            .map(v -> "'" + v.toString().replace("'", "''") + "'")
                            .collect(Collectors.joining(", "));
                        if (!inClause.isEmpty()) {
                            sql = sql.replace(placeholder, inClause);
                        } else {
                            log.warn("步骤引用 IN 子句为空: refStepId={}, field={}", refStepId, fieldName);
                        }
                    } else {
                        log.warn("步骤引用未找到结果: refStepId={}, field={}", refStepId, fieldName);
                    }
                }
            }
            if (!isStepRef) {
                if (paramValue instanceof Number) {
                    sql = sql.replace(placeholder, valueStr);
                } else {
                    sql = sql.replace(placeholder, "'" + valueStr.replace("'", "''") + "'");
                }
            }
        }
        return sql;
    }

    /**
     * 从 query_template 表构建参考示例
     */
    private String buildExamplesFromQueryTemplates() {
        try {
            // 获取评分较高的系统模板作为示例
            List<QueryTemplate> templates = queryTemplateService.getTopRatedTemplates(3);
            if (templates.isEmpty()) {
                return "";
            }

            StringBuilder examples = new StringBuilder();
            examples.append("## 参考示例\n");

            for (int i = 0; i < templates.size(); i++) {
                QueryTemplate template = templates.get(i);
                examples.append("### 示例 ").append(i + 1).append("\n");
                examples.append("问题: ").append(template.getQuestion()).append("\n");
                examples.append("SQL: ").append(template.getGeneratedSql()).append("\n\n");
            }

            return examples.toString();
        } catch (Exception e) {
            log.warn("构建参考示例失败", e);
            return "";
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
