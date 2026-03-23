# Multi-Step SQL Execution Design

## Overview

Restructure `TextToSQLService#processQuery` to support multi-step sequential SQL execution across multiple datasources. Each query is decomposed into an ordered list of SQL steps; each step is executed against its designated datasource, and the result is passed as context to the next step.

---

## Data Model Changes

### `QueryTemplate.sqlTemplate`
Changes from a single SQL string to a JSON array string stored in the existing TEXT column:

```json
[
  {"sql_template": "SELECT quota FROM advertiser WHERE id = {{advertiser_id}}", "datasource_id": 1},
  {"sql_template": "SELECT spend FROM report WHERE adv_id = {{advertiser_id}} AND date BETWEEN {{start}} AND {{end}}", "datasource_id": 2}
]
```

**Backward compatibility**: When parsing `sqlTemplate`, first attempt JSON array parse. If that fails (legacy single-SQL string), wrap the raw string as a single-step plan: `SqlStep(sqlTemplate=rawString, datasourceId=null)`. Datasource inferred at execution time via `semanticContextService.inferDataSourceFromSQL`.

### `QueryTemplate` domain class
- Remove field `private Long datasourceId`

### `query_template` DB table
- Drop column `datasource_id` via migration (see Schema Migration section)

### `QueryRequest` DTO
- Remove field `private Long datasourceId`

### `SqlStep` DTO
Standalone class at `com.tecdo.mac.sql2bot.dto.SqlStep`:

```java
@Data
public class SqlStep {
    private String sqlTemplate;   // may contain {{param}} placeholders
    private Long datasourceId;    // null = infer from SQL
}
```

---

## Architecture

### Path 1 — RAG template match

1. Call `templateVectorSearchService.searchSimilarTemplates(question, null, MAX_TEMPLATE_CANDIDATES)` — datasourceId is always `null` now (no longer a request-level concept). The method already handles `null` by searching across all datasources.
2. The existing `TEMPLATE_SIMILARITY_THRESHOLD` check in `TextToSQLService` is **removed**; filtering is delegated to `searchSimilarTemplates` internal threshold.
3. Parse `template.sqlTemplate` JSON string → `List<SqlStep>`:
   - JSON parse success → use result
   - JSON parse fails, value looks like a SQL string (starts with SELECT/WITH) → wrap as `[SqlStep(rawString, null)]`
   - JSON parse fails for other reason → fall back to Path 2
4. If `steps` list is empty → fall back to Path 2.
5. **`TemplateParameterService` is no longer called.** Parameter filling is handled per-step inside `executePlan`.
6. Increment `template.usageCount`.
7. Call `executePlan(question, steps, conversationId, request, templateId, true, templateSimilarity, startTime)`.

### Path 2 — BFS table discovery (normal recall)

1. Schema RAG + BFS 2-level table expansion.
2. Build context: table structures + column definitions + relationships, each table annotated with its `datasourceId` from `Model.datasourceId`.
3. LLM generates JSON array (see Plan-generation prompt below).
4. Parse JSON → `List<SqlStep>`.
5. If JSON parse fails → return error (no further fallback).
6. If `steps` empty → return error.
7. **If seed tables empty** (Schema RAG returns no results above threshold): call `semanticContextService.generateSystemPrompt` + `aiService.generateSQL` as before to get a raw SQL string, then wrap as `[SqlStep(rawSql, null)]`.
8. Call `executePlan(question, steps, conversationId, request, null, false, 0.0, startTime)`.

### Path 3 — BFS high-recall retry (user unsatisfied)

Same as Path 2 but uses `schemaSearchBfsRetryTopK` (no similarity threshold filter). No datasourceId parameter.

---

## `executePlan` Method

### Signature

```java
private QueryResponse executePlan(
    String question,
    List<SqlStep> steps,
    Long conversationId,
    QueryRequest request,
    Long templateId,
    boolean isFromTemplate,
    double templateSimilarity,
    long startTime
)
```

### Logic

```
// Guard: empty steps should not reach here, but defensive check
if steps is empty:
    return QueryResponse.error("执行计划为空")

prevResult = null            // List<Map<String, Object>>
lastFilledSql = null         // String
lastDatasourceId = null      // Long
explanation = isFromTemplate ? "通过模板匹配生成查询" : "通过BFS表发现生成查询"

for each step in steps:
    filledSql = LLM fills params using (question, step.sqlTemplate, prevResult truncated to 100 rows)

    if step.datasourceId != null:
        datasourceId = step.datasourceId
    else:
        datasourceId = semanticContextService.inferDataSourceFromSQL(filledSql)

    if datasourceId == null:
        record QueryLog (lastFilledSql or filledSql if lastFilledSql is null, failure)
        return QueryResponse.error("无法推断数据源，请检查SQL中的表名")

    try:
        result = queryExecutorService.executeQuery(datasourceId, filledSql)
    catch Exception e:
        record QueryLog (filledSql, lastDatasourceId=datasourceId, executionSuccess=false)
        if conversationId != null: messageService.saveErrorMessage(conversationId, e.getMessage())
        return QueryResponse.error("SQL 执行失败: " + e.getMessage())

    prevResult = result
    lastFilledSql = filledSql
    lastDatasourceId = datasourceId

// prevResult cannot be null here (would have exited on error)
record QueryLog (lastFilledSql, lastDatasourceId, prevResult.size(), executionSuccess=true)
if conversationId != null: messageService.saveAssistantMessage(conversationId, explanation, lastFilledSql, prevResult)

response = QueryResponse.success(conversationId, lastFilledSql, explanation, prevResult, totalTime)
response.setQueryLogId(queryLogId)
response.setTemplateId(templateId)
response.setFromTemplate(isFromTemplate)
response.setTemplateSimilarity(templateSimilarity)
return response
```

### `prevResult` Truncation (LLM prompt only)
When building the param-filling prompt for the next step, truncate `prevResult` to max 100 rows. The full `prevResult` is still passed to `QueryResponse`. Truncation applies only to the LLM prompt input.

---

## LLM Prompts

### Per-step param-filling prompt (inside `executePlan`)

```
System:
  你是一个SQL参数填充专家。根据以下信息，将SQL模板中的{{param}}占位符替换为具体值。

  SQL模板: <step.sqlTemplate>

  上一步查询结果（最多100行）: <prevResult as JSON array, or "无" if first step>

  用户问题: <question>

  要求：只返回填充完成的SQL语句，不要额外解释。

User:
  请填充SQL模板中的参数。
```

### Plan-generation prompt (Path 2/3, replaces `generateSQLWithBFSContext`)

```
System:
  你是一个SQL生成专家。根据以下数据库结构，为用户问题生成多步骤SQL执行计划。

  ## 数据库表结构
  ### <tableName> (datasource_id: <N>)
  字段: ...

  ## 表关系
  ...

  ## 参考示例
  ...

  ## 输出要求
  返回JSON数组，每个元素格式: {"sql_template": "...", "datasource_id": N}
  - 使用 {{param_name}} 表示依赖用户输入或上一步查询结果的动态值
  - datasource_id 必须与表所属的数据源一致
  - 如只需一步，也返回只含一个元素的数组

User:
  <question>
```

---

## `TemplateVectorSearchService` Changes

### `indexTemplate` method
Since `QueryTemplate.datasourceId` is removed, replace:
```java
String.valueOf(template.getDatasourceId()).getBytes(...)
```
with:
```java
"0".getBytes(StandardCharsets.UTF_8)
```
This stores `0` in the Redis Hash `datasource_id` field (effectively "no datasource filter").

### `TemplateMeta` inner class
Remove field `private Long datasourceId` and its corresponding setter call in `buildTemplateMeta`. The field is no longer needed since datasource filtering is done at the Redis Hash level (KNN query), not via meta.

### `searchSimilarTemplates`
No change needed — already handles `null` datasourceId by running unrestricted KNN (`*=>[KNN ...]`).

---

## `ConversationManagementService` Change

`TextToSQLService` and `ConversationManagementService` both call `conversationService.create(userId, title, datasourceId)`. With `QueryRequest.datasourceId` removed, pass `null` as the third argument in both places.

---

## `QueryLog` Recording

In multi-step execution, record the **last successfully executed step's** SQL and datasource. On abort due to execution failure, record the **failing step's** SQL and `executionSuccess=false`. This preserves the current `query_log` schema without changes.

---

## Schema Migration

Since `schema.sql` uses `CREATE TABLE IF NOT EXISTS` (not incremental), the column drop must be applied via the existing `schema-updates.sql` file:

```sql
-- Migration: drop datasource_id from query_template
ALTER TABLE query_template DROP COLUMN IF EXISTS datasource_id;
```

Also update `schema.sql` `CREATE TABLE query_template` definition to remove `datasource_id` column for fresh installs.

---

## Files Changed

| File | Change |
|------|--------|
| `domain/QueryTemplate.java` | Remove `datasourceId` field |
| `dto/QueryRequest.java` | Remove `datasourceId` field |
| `dto/SqlStep.java` | New class |
| `service/TextToSQLService.java` | Add `executePlan`, refactor `processQuery`, update `generateSQLWithBFSContext` to return `List<SqlStep>` |
| `service/TemplateVectorSearchService.java` | `indexTemplate`: write `"0"` for datasourceId; `TemplateMeta`: remove `datasourceId` field |
| `service/ConversationManagementService.java` | Pass `null` as datasourceId in `conversationService.create()` |
| `resources/schema.sql` | Remove `datasource_id` from `CREATE TABLE query_template` |
| `resources/schema-updates.sql` | Add `ALTER TABLE query_template DROP COLUMN IF EXISTS datasource_id` |
| `mapper/QueryTemplateMapper.xml` | Remove `datasource_id` from resultMap, INSERT, SELECT. Note: `selectAll` and `updateById` SQL are missing from this XML (pre-existing gap) — add them during this task |
| `mapper/QueryTemplateMapper.java` | Remove any `datasourceId` params |

---

## Out of Scope

- Frontend changes
- Migration of existing `query_template` records to new JSON array format
- Parallel step execution (future enhancement)
- `TemplateParameterService` is kept in codebase but no longer called from any path
