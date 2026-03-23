# Multi-Step SQL Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable sequential multi-step SQL execution across multiple datasources with LLM-guided parameter filling and result chaining.

**Architecture:** Refactor `TextToSQLService#processQuery` to parse SQL templates as JSON arrays of steps, execute each step sequentially with LLM parameter filling, pass results forward as context, and support both template-based (Path 1) and BFS-generated (Path 2/3) execution plans.

**Tech Stack:** Spring Boot 4, MyBatis, Redis KNN, Claude API, Java 17

---

## File Structure

**New files:**
- `src/main/java/com/tecdo/mac/sql2bot/dto/SqlStep.java` - DTO for SQL execution step

**Modified files:**
- `src/main/java/com/tecdo/mac/sql2bot/domain/QueryTemplate.java` - Remove datasourceId
- `src/main/java/com/tecdo/mac/sql2bot/dto/QueryRequest.java` - Remove datasourceId
- `src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java` - Add executePlan, refactor processQuery
- `src/main/java/com/tecdo/mac/sql2bot/service/TemplateVectorSearchService.java` - Fix indexTemplate NPE, remove TemplateMeta.datasourceId
- `src/main/java/com/tecdo/mac/sql2bot/service/ConversationManagementService.java` - Pass null for datasourceId
- `src/main/java/com/tecdo/mac/sql2bot/service/QueryLogService.java` - Add getBestRecentExample
- `src/main/java/com/tecdo/mac/sql2bot/mapper/QueryLogMapper.java` - Add selectBestRecentExample
- `src/main/resources/mapper/QueryLogMapper.xml` - Add selectBestRecentExample SQL
- `src/main/resources/mapper/QueryTemplateMapper.xml` - Remove datasourceId, add missing SQLs
- `src/main/java/com/tecdo/mac/sql2bot/mapper/QueryTemplateMapper.java` - No param changes needed
- `src/main/resources/schema.sql` - Remove datasource_id from query_template
- `src/main/resources/schema-updates.sql` - Add ALTER TABLE DROP COLUMN

---

### Task 1: Create SqlStep DTO

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/dto/SqlStep.java`

- [ ] **Step 1: Create SqlStep class**

```java
package com.tecdo.mac.sql2bot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 执行步骤
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlStep {
    /**
     * SQL 模板（可能包含 {{param}} 占位符）
     */
    private String sqlTemplate;

    /**
     * 数据源 ID（null 表示从 SQL 推断）
     */
    private Long datasourceId;
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/dto/SqlStep.java
git commit -m "feat: add SqlStep DTO for multi-step SQL execution"
```

---

### Task 2: Add QueryLog best recent example query

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/mapper/QueryLogMapper.java`
- Modify: `src/main/resources/mapper/QueryLogMapper.xml`
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/QueryLogService.java`

- [ ] **Step 1: Add method to QueryLogMapper interface**

```java
// Add to QueryLogMapper.java after line 52
QueryLog selectBestRecentExample(
    @Param("datasourceId") Long datasourceId,
    @Param("limit") int limit
);
```

- [ ] **Step 2: Add SQL to QueryLogMapper.xml**

```xml
<!-- Add before </mapper> closing tag -->
<select id="selectBestRecentExample" resultMap="BaseResultMap">
    SELECT * FROM query_log
    WHERE execution_success = TRUE
      AND rating >= 4
      AND generated_sql IS NOT NULL
      <if test="datasourceId != null">
          AND datasource_id = #{datasourceId}
      </if>
    ORDER BY rating DESC, created_at DESC
    LIMIT #{limit}
</select>
```

- [ ] **Step 3: Add method to QueryLogService**

```java
// Add to QueryLogService.java
public QueryLog getBestRecentExample(Long datasourceId) {
    List<QueryLog> results = queryLogMapper.selectBestRecentExample(datasourceId, 1);
    return results.isEmpty() ? null : results.get(0);
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/mapper/QueryLogMapper.java src/main/resources/mapper/QueryLogMapper.xml src/main/java/com/tecdo/mac/sql2bot/service/QueryLogService.java
git commit -m "feat: add QueryLog best recent example query for param-filling prompts"
```

---

### Task 3: Remove datasourceId from QueryTemplate

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/domain/QueryTemplate.java:22`

- [ ] **Step 1: Remove datasourceId field**

Remove line 22:
```java
private Long datasourceId;
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/domain/QueryTemplate.java
git commit -m "refactor: remove datasourceId from QueryTemplate domain"
```

---

### Task 4: Remove datasourceId from QueryRequest

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/dto/QueryRequest.java:14`

- [ ] **Step 1: Remove datasourceId field and comment**

Remove lines 11-14:
```java
/**
 * 数据源ID（可选，不指定则自动选择）
 */
private Long datasourceId;
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/dto/QueryRequest.java
git commit -m "refactor: remove datasourceId from QueryRequest DTO"
```

---

### Task 5: Fix TemplateVectorSearchService datasourceId handling

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/TemplateVectorSearchService.java:170,321,352`

- [ ] **Step 1: Fix indexTemplate NPE (line 170)**

Replace:
```java
String.valueOf(template.getDatasourceId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)
```

With:
```java
"0".getBytes(java.nio.charset.StandardCharsets.UTF_8)
```

- [ ] **Step 2: Remove datasourceId from TemplateMeta (line 352)**

Remove line 352:
```java
private Long datasourceId;
```

- [ ] **Step 3: Remove setDatasourceId call in buildTemplateMeta (line 321)**

Remove line 321:
```java
meta.setDatasourceId(template.getDatasourceId());
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/TemplateVectorSearchService.java
git commit -m "fix: handle null datasourceId in TemplateVectorSearchService"
```

---

### Task 6: Update ConversationManagementService

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/ConversationManagementService.java:40`

- [ ] **Step 1: Pass null for datasourceId**

Replace line 40:
```java
request.getDatasourceId()
```

With:
```java
null
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/ConversationManagementService.java
git commit -m "refactor: pass null datasourceId in ConversationManagementService"
```

---

### Task 7: Update QueryTemplateMapper.xml

**Files:**
- Modify: `src/main/resources/mapper/QueryTemplateMapper.xml`

- [ ] **Step 1: Remove datasource_id from resultMap (line 20)**

Remove:
```xml
<result column="datasource_id" property="datasourceId"/>
```

- [ ] **Step 2: Remove datasource_id from INSERT (lines 31, 36)**

Replace INSERT columns:
```xml
<insert id="insert" parameterType="com.tecdo.mac.sql2bot.domain.QueryTemplate"
        useGeneratedKeys="true" keyProperty="id">
    INSERT INTO query_template (
        skeleton, sql_template, entity, intent,
        supported_dimensions, supported_metrics, parameters,
        example_question, example_intent_json,
        score, rating_count, usage_count
    ) VALUES (
        #{skeleton}, #{sqlTemplate}, #{entity}, #{intent},
        #{supportedDimensions}, #{supportedMetrics}, #{parameters},
        #{exampleQuestion}, #{exampleIntentJson},
        #{score}, #{ratingCount}, #{usageCount}
    )
</insert>
```

- [ ] **Step 3: Add missing selectAll SQL**

```xml
<select id="selectAll" resultMap="BaseResultMap">
    SELECT * FROM query_template ORDER BY score DESC, usage_count DESC
</select>
```

- [ ] **Step 4: Add missing updateById SQL**

```xml
<update id="updateById" parameterType="com.tecdo.mac.sql2bot.domain.QueryTemplate">
    UPDATE query_template
    <set>
        <if test="skeleton != null">skeleton = #{skeleton},</if>
        <if test="sqlTemplate != null">sql_template = #{sqlTemplate},</if>
        <if test="entity != null">entity = #{entity},</if>
        <if test="intent != null">intent = #{intent},</if>
        <if test="supportedDimensions != null">supported_dimensions = #{supportedDimensions},</if>
        <if test="supportedMetrics != null">supported_metrics = #{supportedMetrics},</if>
        <if test="parameters != null">parameters = #{parameters},</if>
        <if test="exampleQuestion != null">example_question = #{exampleQuestion},</if>
        <if test="exampleIntentJson != null">example_intent_json = #{exampleIntentJson},</if>
        updated_at = CURRENT_TIMESTAMP
    </set>
    WHERE id = #{id}
</update>
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/mapper/QueryTemplateMapper.xml
git commit -m "refactor: remove datasource_id from QueryTemplateMapper, add missing selectAll/updateById"
```

---

### Task 8: Update schema.sql and schema-updates.sql

**Files:**
- Modify: `src/main/resources/schema.sql:223`
- Modify: `src/main/resources/schema-updates.sql`

- [ ] **Step 1: Remove datasource_id from CREATE TABLE query_template in schema.sql**

Remove line 223:
```sql
datasource_id BIGINT COMMENT '关联数据源',
```

- [ ] **Step 2: Add migration to schema-updates.sql**

Append to `src/main/resources/schema-updates.sql`:
```sql
-- 2026-03-23: drop datasource_id from query_template (now embedded in sql_template JSON array)
ALTER TABLE query_template DROP COLUMN IF EXISTS datasource_id;
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/schema.sql src/main/resources/schema-updates.sql
git commit -m "refactor: drop datasource_id column from query_template table"
```

---

### Task 9: Add executePlan to TextToSQLService

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java`

This task adds the `executePlan` method and the `parseSqlSteps` helper. The existing `executeAndRespond` method is replaced.

- [ ] **Step 1: Add import for SqlStep and List at top of file (already imported java.util.List)**

Verify `import com.tecdo.mac.sql2bot.dto.SqlStep;` is present. Add if missing.

- [ ] **Step 2: Add parseSqlSteps helper method**

Add after `generateConversationTitle` method:

```java
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
```

- [ ] **Step 3: Add buildParamFillingPrompt helper method**

```java
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
```

- [ ] **Step 4: Add executePlan method (replaces executeAndRespond)**

```java
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
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java
git commit -m "feat: add executePlan, parseSqlSteps, buildParamFillingPrompt to TextToSQLService"
```

---

### Task 10: Refactor processQuery and generateSQLWithBFSContext

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java`

- [ ] **Step 1: Update processQuery — remove datasourceId from request, update conversation creation**

Replace the conversation creation block (lines 83-91):
```java
} else if (Boolean.TRUE.equals(request.getCreateNewConversation())) {
    Conversation conversation = conversationService.create(
        request.getUserId(),
        generateConversationTitle(request.getQuestion()),
        null   // datasourceId removed from request
    );
    conversationId = conversation.getId();
    log.info("Created new conversation: id={}, userId={}", conversationId, request.getUserId());
}
```

- [ ] **Step 2: Remove datasourceId local variable (line 99)**

Remove:
```java
Long datasourceId = request.getDatasourceId();
```

- [ ] **Step 3: Update Path 3 — remove datasourceId parameter**

Replace:
```java
String sql = generateSQLByBFSWithHighRecall(request.getQuestion(), datasourceId);
return executeAndRespond(request, conversationId, datasourceId, sql,
    null, false, 0.0, startTime);
```

With:
```java
List<SqlStep> steps = generatePlanByBFSWithHighRecall(request.getQuestion());
return executePlan(request.getQuestion(), steps, conversationId, request,
    null, false, 0.0, startTime);
```

- [ ] **Step 4: Update Path 1 — use parseSqlSteps and executePlan**

Replace the `matchedTemplate != null` block:
```java
if (matchedTemplate != null) {
    try {
        List<SqlStep> steps = parseSqlSteps(matchedTemplate.getSqlTemplate());
        if (steps == null) {
            log.warn("路径1 模板解析失败，降级到路径2: templateId={}", matchedTemplate.getId());
        } else {
            queryTemplateService.incrementUsageCount(matchedTemplate.getId());
            log.info("路径1 模板解析成功: templateId={}, steps={}", matchedTemplate.getId(), steps.size());
            return executePlan(request.getQuestion(), steps, conversationId, request,
                matchedTemplate.getId(), true, templateSimilarity, startTime);
        }
    } catch (Exception e) {
        log.error("路径1 处理失败，降级到路径2", e);
    }
}
```

- [ ] **Step 5: Update Path 2 — use generatePlanByBFSNormal**

Replace:
```java
String sql = generateSQLByBFSNormal(request.getQuestion(), datasourceId);
return executeAndRespond(request, conversationId, datasourceId, sql,
    null, false, 0.0, startTime);
```

With:
```java
List<SqlStep> steps = generatePlanByBFSNormal(request.getQuestion());
return executePlan(request.getQuestion(), steps, conversationId, request,
    null, false, 0.0, startTime);
```

- [ ] **Step 6: Update searchSimilarTemplates call — pass null for datasourceId**

Replace:
```java
templateVectorSearchService.searchSimilarTemplates(
    request.getQuestion(), datasourceId, MAX_TEMPLATE_CANDIDATES);
```

With:
```java
templateVectorSearchService.searchSimilarTemplates(
    request.getQuestion(), null, MAX_TEMPLATE_CANDIDATES);
```

- [ ] **Step 7: Rename generateSQLByBFSNormal → generatePlanByBFSNormal, return List\<SqlStep\>**

Replace the entire `generateSQLByBFSNormal` method:

```java
/**
 * 路径2：正常召回 + 相似度过滤 + BFS扩表 + LLM 生成执行计划
 */
private List<SqlStep> generatePlanByBFSNormal(String question) {
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
    return generatePlanWithBFSContext(question, allModelIds);
}
```

- [ ] **Step 8: Rename generateSQLByBFSWithHighRecall → generatePlanByBFSWithHighRecall, return List\<SqlStep\>**

Replace the entire `generateSQLByBFSWithHighRecall` method:

```java
/**
 * 路径3：高召回（不做相似度过滤）+ BFS扩表 + LLM 生成执行计划
 */
private List<SqlStep> generatePlanByBFSWithHighRecall(String question) {
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
    return generatePlanWithBFSContext(question, allModelIds);
}
```

- [ ] **Step 9: Rename generateSQLWithBFSContext → generatePlanWithBFSContext, return List\<SqlStep\>**

Replace the entire `generateSQLWithBFSContext` method:

```java
/**
 * 根据 modelId 集合构建完整上下文，调用 LLM 生成多步骤执行计划
 */
private List<SqlStep> generatePlanWithBFSContext(String question, Set<Long> modelIds) {
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

        String fewShot = intentFewShotService.getFewShotExamples(null, question);

        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("你是一个SQL生成专家。根据以下数据库结构，为用户问题生成多步骤SQL执行计划。\n\n");

        systemPrompt.append("## 数据库表结构\n");
        for (com.tecdo.mac.sql2bot.domain.Model m : models) {
            systemPrompt.append("### ").append(m.getTableName());
            if (m.getDisplayName() != null) systemPrompt.append(" (").append(m.getDisplayName()).append(")");
            systemPrompt.append(" (datasource_id: ").append(m.getDatasourceId()).append(")\n");
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

        systemPrompt.append("## 输出要求\n");
        systemPrompt.append("返回JSON数组，每个元素格式: {\"sql_template\": \"...\", \"datasource_id\": N}\n");
        systemPrompt.append("- 使用 {{param_name}} 表示依赖用户输入或上一步查询结果的动态值\n");
        systemPrompt.append("- datasource_id 必须与表所属的数据源一致\n");
        systemPrompt.append("- 如只需一步，也返回只含一个元素的数组\n");
        systemPrompt.append("- 只返回JSON数组，不要额外解释\n");

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
```

- [ ] **Step 10: Remove old executeAndRespond method and TEMPLATE_SIMILARITY_THRESHOLD constant**

Delete the entire `executeAndRespond` method (lines 289-363) and the constant:
```java
private static final double TEMPLATE_SIMILARITY_THRESHOLD = 0.7;
```

- [ ] **Step 11: Verify the file compiles — check for any remaining references to removed methods/fields**

Search for any remaining calls to `executeAndRespond`, `generateSQLByBFSNormal`, `generateSQLByBFSWithHighRecall`, `generateSQLWithBFSContext`, `request.getDatasourceId()`, `TEMPLATE_SIMILARITY_THRESHOLD`.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java
git commit -m "feat: refactor processQuery to multi-step SQL execution with executePlan"
```

---

### Task 11: Verify build compiles

- [ ] **Step 1: Run Maven compile**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS with no errors.

If errors appear, fix them before proceeding. Common issues:
- Missing imports for `SqlStep`
- Remaining references to removed `datasourceId` fields
- `selectBestRecentExample` returning `List<QueryLog>` — ensure `QueryLogMapper.selectBestRecentExample` returns `List<QueryLog>` (not single object)

- [ ] **Step 2: Commit any compile fixes**

```bash
git add -A
git commit -m "fix: resolve compile errors after multi-step SQL refactor"
```

---

