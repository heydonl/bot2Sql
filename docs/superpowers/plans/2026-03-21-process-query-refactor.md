# processQuery 重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按照 newpr.md 需求，重构 `TextToSQLService#processQuery`，实现三条查询路径：模板命中、无模板降级（+BFS扩表）、用户不满意重试（+高召回BFS）。

**Architecture:**
三条路径共用 BFS 扩表逻辑，差异只在种子表的 topK 参数。BFS 扩表逻辑提取为 `TextToSQLService` 私有方法，直接使用 `SchemaVectorStoreService`（Redis KNN），不依赖旧的 `BFSTableDiscoveryService`。用户不满意重试通过 `QueryRequest` 携带 `retryQueryLogId + satisfied=false` 触发。原有 schema-based JSON 模板解析逻辑（`sql_template + parameters`）有意废弃，统一走 BFS 路径。

**Tech Stack:** Spring Boot 4, MyBatis, Redis KNN（SchemaVectorStoreService），Claude API（AIService），Jackson

---

## 文件变更清单

| 文件 | 操作 | 职责 |
|------|------|------|
| `dto/QueryRequest.java` | 修改 | 新增 `retryQueryLogId`、`satisfied` 字段 |
| `service/TextToSQLService.java` | 修改 | 重构三条路径 + 私有 BFS 方法 |
| `resources/application.properties` | 修改 | 新增 `schema.search.bfs-retry-top-k` 配置 |

---

## Task 1: 扩展 QueryRequest，支持不满意重试参数

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/dto/QueryRequest.java`

- [ ] **Step 1: 在 `QueryRequest` 中新增两个字段**

```java
/**
 * 不满意重试时传入，指向上次查询的 queryLogId
 */
private Long retryQueryLogId;

/**
 * 用户是否满意上次结果（false 触发 BFS 重试路径）
 */
private Boolean satisfied;
```

- [ ] **Step 2: 确认编译通过**

```bash
./mvnw compile -q
```
期望：BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/dto/QueryRequest.java
git commit -m "feat(dto): 扩展 QueryRequest 支持不满意重试参数"
```

---

## Task 2: 在 TextToSQLService 中添加 BFS 扩表私有方法

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java`

注意：`TextToSQLService` 使用 `@RequiredArgsConstructor`，新增的 `final` 字段会自动加入构造函数，无需手动修改构造函数。`ModelService`、`RelationshipService`、`ColumnDefinitionService`、`IntentFewShotService` 均已是 Spring Bean。

- [ ] **Step 1: 在文件顶部 import 区补充缺失的 `java.util.*` 类型**

在现有 `import java.util.List;` 和 `import java.util.Map;` 之后追加：

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
```

- [ ] **Step 2: 在 `TextToSQLService` 字段声明区追加四个依赖**

```java
private final ModelService modelService;
private final RelationshipService relationshipService;
private final ColumnDefinitionService columnDefinitionService;
private final IntentFewShotService intentFewShotService;
```

- [ ] **Step 3: 添加 BFS 核心扩表方法**

在 `TextToSQLService` 末尾（`validateSQL` 之前）添加：

```java
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
```

- [ ] **Step 4: 添加「BFS建表上下文并调用LLM生成SQL」方法**

`generateSQLWithBFSContext` 将完整上下文作为 system prompt，user prompt 固定为"请根据以上信息生成SQL"，避免 question 重复传入：

```java
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
```

- [ ] **Step 5: 编译确认**

```bash
./mvnw compile -q
```
期望：BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java
git commit -m "feat(service): TextToSQLService 添加 BFS 扩表方法和 BFS 上下文 SQL 生成方法"
```

---

## Task 3: 重构 processQuery 三条路径

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java`
- Modify: `src/main/resources/application.properties`

**三条路径：**
1. `satisfied != false` 且 RAG 找到模板 → 模板填充路径
2. `satisfied != false` 且 RAG 未找到模板 → Schema RAG（正常召回+相似度过滤）+ BFS扩表 + LLM
3. `satisfied == false` → Schema RAG（高召回，不过滤）+ BFS扩表 + LLM

**注意：** 原有 schema-based JSON 模板解析逻辑（`sql_template + parameters`）有意废弃，统一走 BFS 路径。

- [ ] **Step 1: 在 `application.properties` 新增高召回配置**

```properties
# 不满意重试时的高召回 topK（不做相似度过滤）
schema.search.bfs-retry-top-k=20
```

- [ ] **Step 2: 在 TextToSQLService 注入新配置**

```java
@org.springframework.beans.factory.annotation.Value("${schema.search.bfs-retry-top-k:20}")
private int schemaSearchBfsRetryTopK;
```

- [ ] **Step 3: 添加 `executeAndRespond` 私有方法**

执行失败时先保存错误消息再返回错误响应，与原逻辑一致：

```java
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
```

- [ ] **Step 4: 添加路径2和路径3的私有路由方法**

```java
/**
 * 路径2：正常召回 + 相似度过滤 + BFS扩表 + LLM
 * datasourceId 可为 null（搜索所有数据源）
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
 * datasourceId 可为 null（搜索所有数据源）
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
```

- [ ] **Step 5: 重写 `processQuery` 方法体**

用以下代码完整替换 `processQuery` 方法体（保留方法签名不变）：

```java
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
```

- [ ] **Step 6: 编译确认**

```bash
./mvnw compile -q
```
期望：BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java \
        src/main/resources/application.properties
git commit -m "feat(service): 重构 processQuery 三条路径，路径2/3 通过 BFS 扩表生成 SQL"
```

---

## Task 4: 冒烟测试

- [ ] **Step 1: 启动服务（先关闭旧进程）**

```bash
lsof -ti:8082 | xargs kill -9 2>/dev/null || true
```

然后手动运行：`./mvnw spring-boot:run`，等待日志出现 "Started Sql2botApplication"

- [ ] **Step 2: 测试路径1（模板命中）**

```bash
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"question":"查询广告主12345的消费","datasourceId":1}'
```
期望：返回 `"fromTemplate": true`

- [ ] **Step 3: 测试路径2（无模板降级+BFS）**

```bash
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"question":"一个完全陌生的问题RAG无法匹配任何模板","datasourceId":1}'
```
期望：返回 `"fromTemplate": false`，日志中出现 "BFS扩展完成"

- [ ] **Step 4: 测试路径3（不满意重试）**

```bash
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"question":"同一问题","datasourceId":1,"satisfied":false,"retryQueryLogId":1}'
```
期望：日志中出现 "用户不满意，触发BFS高召回重试"
