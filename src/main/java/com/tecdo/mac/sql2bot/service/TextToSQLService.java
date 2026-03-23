package com.tecdo.mac.sql2bot.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tecdo.mac.sql2bot.domain.Conversation;
import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import com.tecdo.mac.sql2bot.dto.SqlStep;
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

    @org.springframework.beans.factory.annotation.Value("${schema.search.bfs-retry-top-k:20}")
    private int schemaSearchBfsRetryTopK;

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

            // 4. 获取数据源ID
            Long datasourceId = null;

            // 5. 路径3：用户不满意，直接走高召回 BFS 路径
            if (Boolean.FALSE.equals(request.getSatisfied()) && request.getRetryQueryLogId() != null) {
                log.info("用户不满意，触发BFS高召回重试: queryLogId={}", request.getRetryQueryLogId());
                String sql = generateSQLByBFSWithHighRecall(request.getQuestion(), datasourceId);
                return executeAndRespond(request, conversationId, datasourceId, sql,
                    null, false, 0.0, startTime);
            }

            // 6. 路径1：RAG 模板检索（高准确性）
            QueryTemplate matchedTemplate = null;
            double templateSimilarity = 0.0;
            log.info("开始RAG模板检索: question={}", request.getQuestion());
            try {
                List<TemplateVectorSearchService.TemplateSearchResult> templateResults =
                    templateVectorSearchService.searchSimilarTemplates(
                        request.getQuestion(), datasourceId, MAX_TEMPLATE_CANDIDATES);
                if (!templateResults.isEmpty()) {
                    TemplateVectorSearchService.TemplateSearchResult bestMatch = templateResults.get(0);
                    templateSimilarity = bestMatch.getSimilarity();
                    QueryTemplate candidate = queryTemplateService.getById(bestMatch.getMeta().getTemplateId());
                    if (candidate != null) {
                        matchedTemplate = candidate;
                        log.info("RAG模板匹配成功: templateId={}, similarity={}",
                                matchedTemplate.getId(), templateSimilarity);
                    }
                } else {
                    log.info("未找到符合条件的SQL模板，降级到路径2");
                }
            } catch (Exception e) {
                log.error("RAG模板检索失败，降级到路径2", e);
            }

            if (matchedTemplate != null) {
                try {
                    String parameterPrompt = buildParameterGenerationPrompt(matchedTemplate, request.getQuestion());
                    String parameterResponse = aiService.generateParameters(parameterPrompt);
                    String sql = templateParameterService.fillTemplateWithLLMParameters(matchedTemplate, parameterResponse);
                    Long templateId = matchedTemplate.getId();
                    queryTemplateService.incrementUsageCount(templateId);
                    log.info("路径1 模板填充成功: templateId={}, sql={}", templateId, sql);
                    return executeAndRespond(request, conversationId, datasourceId, sql,
                        templateId, true, templateSimilarity, startTime);
                } catch (Exception e) {
                    log.error("路径1 模板填充失败，降级到路径2", e);
                }
            }

            // 7. 路径2：Schema RAG + BFS扩表 + LLM
            log.info("进入路径2: Schema RAG + BFS扩表 + LLM");
            String sql = generateSQLByBFSNormal(request.getQuestion(), datasourceId);
            return executeAndRespond(request, conversationId, datasourceId, sql,
                null, false, 0.0, startTime);

        } catch (Exception e) {
            log.error("Failed to process query", e);
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
                    step.setSqlTemplate(item.path("sql_template").asText(null));
                    step.setDatasourceId(item.has("datasource_id") && !item.get("datasource_id").isNull()
                        ? item.get("datasource_id").asLong() : null);
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
            Long templateId, boolean isFromTemplate,
            double templateSimilarity, long startTime) {

        if (steps == null || steps.isEmpty()) {
            String err = "执行计划为空";
            if (conversationId != null) messageService.saveErrorMessage(conversationId, err);
            return QueryResponse.error(err);
        }

        String explanation = isFromTemplate ? "通过模板匹配生成查询" : "通过BFS表发现生成查询";
        List<Map<String, Object>> prevResult = null;
        String lastFilledSql = null;
        Long lastDatasourceId = null;

        for (SqlStep step : steps) {
            // 获取参考示例
            QueryLog example = null;
            try {
                example = queryLogService.getBestRecentExample(step.getDatasourceId());
            } catch (Exception e) {
                log.warn("获取参考示例失败，跳过: {}", e.getMessage());
            }

            // LLM 填充参数
            String filledSql;
            try {
                String prompt = buildParamFillingPrompt(question, step.getSqlTemplate(), prevResult, example);
                String llmResp = aiService.generateSQL(prompt, "请填充SQL模板中的参数。");
                filledSql = aiService.extractSQL(llmResp);
                if (filledSql == null || filledSql.isBlank()) {
                    filledSql = llmResp.trim(); // fallback: use raw response
                }
            } catch (Exception e) {
                log.error("LLM参数填充失败: step={}", step.getSqlTemplate(), e);
                String err = "SQL参数填充失败: " + e.getMessage();
                if (conversationId != null) messageService.saveErrorMessage(conversationId, err);
                return QueryResponse.error(err);
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
            long execStart = System.currentTimeMillis();
            try {
                prevResult = queryExecutorService.executeQuery(datasourceId, filledSql);
                lastFilledSql = filledSql;
                lastDatasourceId = datasourceId;
                log.info("步骤执行成功: sql={}, rows={}", filledSql, prevResult.size());
            } catch (Exception e) {
                log.error("步骤执行失败: sql={}", filledSql, e);
                // 记录失败日志
                try {
                    QueryLog queryLog = new QueryLog();
                    queryLog.setUserId(request.getUserId());
                    queryLog.setConversationId(conversationId);
                    queryLog.setQuestion(question);
                    queryLog.setTemplateId(templateId);
                    queryLog.setIsFromTemplate(isFromTemplate);
                    queryLog.setGeneratedSql(filledSql);
                    queryLog.setExecutionSuccess(false);
                    queryLog.setExecutionTime(System.currentTimeMillis() - execStart);
                    queryLog.setDatasourceId(datasourceId);
                    queryLogService.logQuery(queryLog);
                } catch (Exception logEx) {
                    log.warn("记录失败日志异常", logEx);
                }
                String err = "SQL执行失败: " + e.getMessage();
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
            queryLog.setTemplateId(templateId);
            queryLog.setIsFromTemplate(isFromTemplate);
            queryLog.setGeneratedSql(lastFilledSql);
            queryLog.setExecutionSuccess(true);
            queryLog.setResultCount(prevResult.size());
            queryLog.setDatasourceId(lastDatasourceId);
            queryLog.setExecutionTime(System.currentTimeMillis() - startTime);
            queryLogId = queryLogService.logQuery(queryLog);
        } catch (Exception e) {
            log.warn("记录查询日志失败（不影响查询结果）", e);
        }

        if (conversationId != null) {
            messageService.saveAssistantMessage(conversationId, explanation, lastFilledSql, prevResult);
        }

        long totalTime = System.currentTimeMillis() - startTime;
        QueryResponse response = QueryResponse.success(conversationId, lastFilledSql, explanation, prevResult, totalTime);
        response.setQueryLogId(queryLogId);
        response.setTemplateId(templateId);
        response.setFromTemplate(isFromTemplate);
        response.setTemplateSimilarity(templateSimilarity);
        return response;
    }

    private QueryResponse executeAndRespond(QueryRequest request, Long conversationId,
            Long datasourceId, String sql, Long templateId,
            boolean isFromTemplate, double templateSimilarity, long startTime) {

        if (sql == null) {
            String errMsg = "SQL生成失败，无法执行查询";
            if (conversationId != null) messageService.saveErrorMessage(conversationId, errMsg);
            return QueryResponse.error(errMsg);
        }

        // 推断数据源
        if (datasourceId == null) {
            datasourceId = semanticContextService.inferDataSourceFromSQL(sql);
            if (datasourceId == null) {
                String errMsg = "无法从 SQL 中推断出数据源，请检查表名是否正确";
                if (conversationId != null) messageService.saveErrorMessage(conversationId, errMsg);
                return QueryResponse.error(errMsg);
            }
        }

        String explanation = isFromTemplate ? "通过模板匹配生成查询" : "通过BFS表发现生成查询";

        long executionStart = System.currentTimeMillis();
        boolean executionSuccess = false;
        int resultCount = 0;
        List<Map<String, Object>> data = null;
        String errorMessage = null;

        try {
            data = queryExecutorService.executeQuery(datasourceId, sql);
            resultCount = data.size();
            executionSuccess = true;
            log.info("SQL执行成功: resultCount={}", resultCount);
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("SQL执行失败", e);
        }

        long executionTime = System.currentTimeMillis() - executionStart;

        Long queryLogId = null;
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
            queryLogId = queryLogService.logQuery(queryLog);
        } catch (Exception e) {
            log.warn("记录查询日志失败（不影响查询结果）", e);
        }

        if (!executionSuccess) {
            if (conversationId != null) messageService.saveErrorMessage(conversationId, errorMessage);
            return QueryResponse.error("SQL 执行失败: " + errorMessage);
        }

        long totalTime = System.currentTimeMillis() - startTime;
        if (conversationId != null) {
            messageService.saveAssistantMessage(conversationId, explanation, sql, data);
        }

        QueryResponse response = QueryResponse.success(conversationId, sql, explanation, data, totalTime);
        response.setQueryLogId(queryLogId);
        response.setTemplateId(templateId);
        response.setFromTemplate(isFromTemplate);
        response.setTemplateSimilarity(templateSimilarity);
        return response;
    }

    /**
     * 路径2：正常召回 + 相似度过滤 + BFS扩表 + LLM
     */
    private String generateSQLByBFSNormal(String question, Long datasourceId) {
        List<SchemaVectorStoreService.SchemaSearchResult> schemaResults =
            schemaVectorStoreService.searchSchemas(question, datasourceId, schemaSearchTopK);
        Set<Long> seedModelIds = schemaResults.stream()
            .filter(r -> r.getScore() >= schemaSearchSimilarityThreshold)
            .filter(r -> r.getMeta() != null && r.getMeta().getModelId() != null)
            .map(r -> r.getMeta().getModelId())
            .collect(java.util.stream.Collectors.toSet());
        if (seedModelIds.isEmpty()) {
            log.warn("路径2: Schema RAG 无种子表，降级到语义上下文");
            try {
                String sysPrompt = semanticContextService.generateSystemPrompt(datasourceId, question);
                String aiResp = aiService.generateSQL(sysPrompt,
                    semanticContextService.generateUserPrompt(question));
                return aiService.extractSQL(aiResp);
            } catch (Exception e) {
                log.error("路径2 语义上下文降级失败", e);
                return null;
            }
        }
        Set<Long> allModelIds = expandByBFS(seedModelIds, 2);
        return generateSQLWithBFSContext(question, allModelIds, datasourceId);
    }

    /**
     * 路径3：高召回（不做相似度过滤）+ BFS扩表 + LLM
     */
    private String generateSQLByBFSWithHighRecall(String question, Long datasourceId) {
        List<SchemaVectorStoreService.SchemaSearchResult> schemaResults =
            schemaVectorStoreService.searchSchemas(question, datasourceId, schemaSearchBfsRetryTopK);
        Set<Long> seedModelIds = schemaResults.stream()
            .filter(r -> r.getMeta() != null && r.getMeta().getModelId() != null)
            .map(r -> r.getMeta().getModelId())
            .collect(java.util.stream.Collectors.toSet());
        if (seedModelIds.isEmpty()) {
            log.warn("路径3: 高召回 Schema RAG 无结果");
            return null;
        }
        Set<Long> allModelIds = expandByBFS(seedModelIds, 2);
        return generateSQLWithBFSContext(question, allModelIds, datasourceId);
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
