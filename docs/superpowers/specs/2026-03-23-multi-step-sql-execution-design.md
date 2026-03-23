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

### `QueryTemplate` domain class
- Remove field `private Long datasourceId`

### `query_template` DB table
- Drop column `datasource_id`

### `QueryRequest` DTO
- Remove field `private Long datasourceId` (datasource is now determined per step, not per request)

---

## Architecture

### New inner class `SqlStep` (inside `TextToSQLService`)

```java
private static class SqlStep {
    String sqlTemplate;  // may contain {{param}} placeholders
    Long datasourceId;
}
```

### Unified execution method `executePlan`

Replaces `executeAndRespond`. Runs the sequential loop:

```
prevResult = null
for each step in steps:
    filledSql = LLM fills params in step.sqlTemplate
                (context: question + step.sqlTemplate + prevResult)
    result = queryExecutorService.executeQuery(step.datasourceId, filledSql)
    prevResult = result
return prevResult + last filledSql to client
```

### `QueryLog` recording
Records the last step's executed SQL and result count.

---

## Query Paths

### Path 1 — RAG template match
1. RAG finds a matching `QueryTemplate`
2. Parse `template.sqlTemplate` JSON string → `List<SqlStep>`
3. If JSON parse fails → fall back to Path 2
4. Call `executePlan(question, steps, ...)`

### Path 2 — BFS table discovery (normal recall)
1. Schema RAG + BFS 2-level table expansion
2. Build context: table structures + column definitions + relationships, each table annotated with its `datasourceId`
3. LLM prompt asks for JSON array output (`[{"sql_template": "...", "datasource_id": N}, ...]`)
4. Parse JSON → `List<SqlStep>`
5. If JSON parse fails → return error (no further fallback)
6. Call `executePlan(question, steps, ...)`

### Path 3 — BFS high-recall retry (user unsatisfied)
Same as Path 2 but uses `schemaSearchBfsRetryTopK` (no similarity threshold filter).

---

## LLM Prompts

### Param-filling prompt (per step in `executePlan`)

```
System:
  SQL template: <step.sqlTemplate>
  Previous step result: <prevResult as JSON, or "none" if first step>
  User question: <question>

User:
  Fill in all {{param}} placeholders. Return only the completed SQL statement.
```

### Plan-generation prompt (Path 2/3, replaces `generateSQLWithBFSContext`)

```
System:
  You are a SQL generation expert.
  ## Table structures
  ### <tableName> (datasource_id: <N>)
  Columns: ...
  ## Relationships
  ...
  ## Few-shot examples
  ...
  ## Requirements
  Return a JSON array of steps. Each step: {"sql_template": "...", "datasource_id": N}.
  Use {{param_name}} for dynamic values that depend on user input or prior results.

User:
  <question>
```

---

## Error Handling

| Situation | Behavior |
|-----------|----------|
| Step SQL execution fails | Abort plan immediately, return error to client |
| LLM JSON parse fails (Path 1) | Fall back to Path 2 |
| LLM JSON parse fails (Path 2/3) | Return error, no further fallback |
| Step returns empty result | Pass empty array as context to next step, continue |
| `datasource_id` not found | Abort plan immediately, return error |
| Single-step plan | Behaves identically to current logic |

---

## Files Changed

| File | Change |
|------|--------|
| `domain/QueryTemplate.java` | Remove `datasourceId` field |
| `dto/QueryRequest.java` | Remove `datasourceId` field |
| `service/TextToSQLService.java` | Add `SqlStep`, `executePlan`, refactor `processQuery`, `generateSQLWithBFSContext` |
| `resources/schema.sql` | Drop `datasource_id` column from `query_template` table |
| `mapper/QueryTemplateMapper.xml` | Remove `datasource_id` from INSERT/SELECT/ResultMap |
| `mapper/QueryTemplateMapper.java` | Remove any `datasourceId` params |

---

## Out of Scope

- Frontend changes (API contract: `datasourceId` in request simply becomes ignored/removed)
- Migration of existing `query_template` records to new JSON format (handled separately)
- Parallel step execution (future enhancement)
