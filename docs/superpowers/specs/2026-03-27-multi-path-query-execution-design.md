# 多路径查询执行架构设计文档

## 文档信息

- **创建日期**: 2026-03-27
- **版本**: 1.0
- **作者**: SQL2Bot 开发团队
- **目标读者**: 开发团队成员、架构师、产品经理

## 1. 概述

### 1.1 背景

SQL2Bot 系统需要将用户的自然语言问题转换为 SQL 查询并执行。为了提高查询准确性和召回率，系统设计了三条互补的查询路径：

1. **路径1（RAG模板匹配）**: 高准确性，适用于已知问题模式
2. **路径2（BFS正常召回）**: 平衡准确性和召回率，适用于常规查询
3. **路径3（BFS高召回重试）**: 高召回率，适用于用户不满意后的重试

### 1.2 核心目标

- 提供多层次的查询策略，从高精度到高召回逐步降级
- 支持多步骤 SQL 执行计划，处理复杂查询场景
- 通过用户反馈持续优化查询质量

### 1.3 技术架构

```
用户问题
    ↓
processQuery (入口)
    ↓
├─ 路径1: RAG模板检索 → LLM参数填充 → 多步SQL执行
├─ 路径2: Schema RAG + BFS正常召回扩表 → LLM生成计划 → 多步SQL执行
└─ 路径3: Schema RAG + BFS高召回扩表 → LLM生成计划 → 多步SQL执行
    ↓
返回查询结果
```

## 2. 核心流程设计

### 2.1 主流程：processQuery

`TextToSQLService#processQuery` 是整个查询处理的入口方法。

#### 2.1.1 流程图

```
开始
  ↓
验证用户ID
  ↓
处理会话（创建/验证）
  ↓
保存用户消息
  ↓
用户满意？ ─Yes→ 将问答对存入向量索引 → 继续
  ↓ No
用户不满意？ ─Yes→ 触发路径3（BFS高召回）→ 执行计划 → 返回结果
  ↓ No
路径1: RAG模板检索
  ↓
找到模板？ ─Yes→ 构建Prompt（模板+问题+示例）→ LLM生成参数填充的SQL模板 → 解析为SqlStep → 执行计划 → 返回结果
  ↓ No
路径2: Schema RAG + BFS扩表
  ↓
生成执行计划
  ↓
执行计划
  ↓
返回结果
```

#### 2.1.2 关键步骤说明

**步骤1: 用户满意反馈处理**
```java
if (Boolean.TRUE.equals(request.getSatisfied()) && request.getRetryQueryLogId() != null) {
    QueryLog queryLog = queryLogService.findById(request.getRetryQueryLogId());
    schemaVectorStoreService.indexQuestionAnswer(
        queryLog.getId(),
        queryLog.getQuestion(),
        queryLog.getGeneratedSql()
    );
}
```
- 当用户标记查询结果满意时，将问答对存入向量索引
- 用于后续的 RAG 模板检索和 Schema 检索

**步骤2: 用户不满意触发高召回重试**
```java
if (Boolean.FALSE.equals(request.getSatisfied()) && request.getRetryQueryLogId() != null) {
    List<SqlStep> steps = generatePlanByBFSWithHighRecall(request.getQuestion());
    return executePlan(...);
}
```
- 直接跳转到路径3，使用更高的召回率（TopK=20，无相似度过滤）
- 扩大搜索范围，提高找到正确表的概率

### 2.2 路径1：RAG模板匹配

#### 2.2.1 流程图

```
用户问题
  ↓
向量化问题文本
  ↓
在模板向量库中检索（TopK=5）
  ↓
找到相似模板？
  ↓ Yes
构建Prompt（模板+问题+示例）
  ↓
LLM生成参数填充的SQL模板
  ↓
解析为 SqlStep 列表
  ↓
调用 executePlan 执行
  ↓
返回结果

  ↓ No（未找到或解析失败）
降级到路径2
```

#### 2.2.2 实现细节

**模板检索**
```java
List<TemplateSearchResult> templateResults =
    templateVectorSearchService.searchSimilarTemplates(
        request.getQuestion(), null, MAX_TEMPLATE_CANDIDATES);
```
- 使用 `TemplateVectorSearchService` 进行向量相似度搜索
- `MAX_TEMPLATE_CANDIDATES = 5`，返回最相似的5个模板
- 选择相似度最高的模板作为候选

**LLM参数填充**
根据 newpr1.md 备注2，路径1的核心是将找到的模板给到LLM生成参数填充的SQL模板：

```java
// 构建参数填充Prompt（包含模板、问题、示例）
String prompt = buildTemplateParameterPrompt(matchedTemplate, request.getQuestion());
String llmResponse = aiService.generateSQL(prompt, "请根据模板和问题生成参数填充的SQL。");
String filledTemplateJson = extractJsonBlock(llmResponse);
```

**示例数据获取**
示例问答需要从数据库动态获取：

```java
private String buildTemplateParameterPrompt(QueryTemplate template, String question) {
    StringBuilder prompt = new StringBuilder();

    // 1. 基础Prompt结构
    prompt.append("# 角色\n你是一名 SQL 专家...\n\n");

    // 2. 从query_log获取示例问答
    QueryLog example = queryLogService.getBestExampleByTemplateId(template.getId());
    if (example != null) {
        // 3. 解析intent_sql JSON结构
        List<SqlStep> intentSteps = parseIntentSql(example.getIntentSql());

        // 4. 构建示例的完整上下文
        String exampleContext = buildExampleContext(intentSteps);

        prompt.append("# 示例\n");
        prompt.append("**用户问题**\n").append(example.getQuestion()).append("\n\n");
        prompt.append(exampleContext).append("\n\n");
        prompt.append("**预期输出**\n").append(example.getGeneratedSql()).append("\n\n");
    }

    // 5. 当前用户问题和模板
    prompt.append("用户问题: ").append(question).append("\n");
    prompt.append("初始模板: ").append(template.getSqlTemplate()).append("\n");

    return prompt.toString();
}

private String buildExampleContext(List<SqlStep> intentSteps) {
    StringBuilder context = new StringBuilder();
    Set<Long> processedDataSources = new HashSet<>();
    Set<String> processedTables = new HashSet<>();

    for (SqlStep step : intentSteps) {
        Long dataSourceId = step.getDatasourceId();

        // 处理数据源信息（避免重复）
        if (dataSourceId != null && !processedDataSources.contains(dataSourceId)) {
            DataSource dataSource = dataSourceService.getById(dataSourceId);
            context.append("### 数据源: ").append(dataSource.getName()).append("\n");
            context.append("- **ID**: ").append(dataSource.getId()).append("\n");
            context.append("- **类型**: ").append(dataSource.getType()).append("\n\n");
            processedDataSources.add(dataSourceId);
        }

        // 处理表信息
        if (step.getTables() != null) {
            for (TableInfo tableInfo : step.getTables()) {
                String tableKey = step.getDatasourceId() + "." + tableInfo.getDatabase() + "." + tableInfo.getName();

                if (!processedTables.contains(tableKey)) {
                    // 根据database和table name查找Model
                    Model model = modelService.getByDatabaseAndTableName(
                        tableInfo.getDatabase(), tableInfo.getName());

                    if (model != null) {
                        Database database = databaseService.getById(model.getDatabaseId());

                        context.append("#### 数据库: ").append(database.getName()).append("\n\n");
                        context.append("**相关的表**:\n");
                        context.append("##### 表: ").append(model.getTableName()).append("\n");
                        context.append("**描述**: ").append(model.getDescription()).append("\n");
                        context.append("**主键**: ").append(model.getPrimaryKey()).append("\n\n");

                        // 获取字段信息
                        List<ColumnDefinition> columns = columnDefinitionService.getByModelId(model.getId());
                        context.append("**相关字段**:\n");
                        for (ColumnDefinition col : columns) {
                            context.append("- `").append(col.getColumnName()).append("` (")
                                   .append(col.getColumnType()).append(")");
                            if (col.getDescription() != null) {
                                context.append(": ").append(col.getDescription());
                            }
                            context.append(" [").append(col.getDimensionType()).append("]\n");
                        }
                        context.append("\n");

                        processedTables.add(tableKey);
                    }
                }
            }
        }
    }

    // 获取表关系信息（基于所有涉及的表）
    List<String> allTableNames = intentSteps.stream()
        .filter(step -> step.getTables() != null)
        .flatMap(step -> step.getTables().stream())
        .map(TableInfo::getName)
        .distinct()
        .collect(Collectors.toList());

    if (allTableNames.size() > 1) {
        List<Relationship> relationships = relationshipService.getByTableNames(allTableNames);
        if (!relationships.isEmpty()) {
            context.append("### 表关系\n");
            for (Relationship rel : relationships) {
                context.append("- ").append(rel.getFromDatabase()).append(".")
                       .append(rel.getFromTable()).append(".").append(rel.getFromColumn())
                       .append(" -> ").append(rel.getToDatabase()).append(".")
                       .append(rel.getToTable()).append(".").append(rel.getToColumn())
                       .append(" (").append(rel.getRelationshipType()).append(")\n");
            }
        }
    }

    return context.toString();
}

// 解析intent_sql JSON为SqlStep列表
private List<SqlStep> parseIntentSql(String intentSqlJson) {
    try {
        JsonNode stepsArray = objectMapper.readTree(intentSqlJson);
        List<SqlStep> steps = new ArrayList<>();

        for (JsonNode stepNode : stepsArray) {
            SqlStep step = new SqlStep();
            step.setDatasourceId(stepNode.path("datasource_id").asLong());

            // 解析tables数组
            JsonNode tablesNode = stepNode.path("tables");
            if (tablesNode.isArray()) {
                List<TableInfo> tables = new ArrayList<>();
                for (JsonNode tableNode : tablesNode) {
                    TableInfo tableInfo = new TableInfo();
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
        log.warn("解析intent_sql失败: {}", intentSqlJson, e);
        return Collections.emptyList();
    }
}

// 辅助类
public static class TableInfo {
    private String name;
    private String database;

    // getters and setters
}
```

**Prompt结构**（基于备注2和数据库动态获取）：
```
# 角色
你是一名 SQL 专家，擅长根据用户问题生成多步骤的 SQL 执行计划。

# 任务
你将获得一组预定义的 SQL 模板（JSON 数组形式），每个模板包含一个 SQL 片段、参数说明、输出字段说明等信息。
你需要根据用户的最新问题，从模板中选出合适的步骤，并为每个步骤填充具体的参数值（params 字段）。

# 模板说明
- 模板中的 sql_template 包含占位符 {{xxx}}，你需要在params中写出实际值
- paramDescs 列出了每个参数的含义，你需要从用户问题中提取对应的值
- 如果某个参数依赖前一步的输出（如 {{step1.工单状态}}），在生成当前步骤的 params 时，不需要为它生成值
- 时间范围类参数需根据用户描述进行合理转换

# 示例
[从query_log动态获取的示例问答，包含完整的数据源、数据库、表、字段、关系信息]

用户问题: [用户的具体问题]
初始模板: [匹配到的模板JSON]

预期输出: 填充了params字段的完整JSON数组
```

**模板解析**
```java
List<SqlStep> steps = parseSqlSteps(filledTemplateJson);
```
- 解析LLM返回的JSON数组为SqlStep列表
- 每个SqlStep包含填充好参数的sql_template和datasource_id
- 解析失败时降级到路径2

**使用计数更新**
```java
queryTemplateService.incrementUsageCount(matchedTemplate.getId());
```
- 记录模板使用次数，用于模板质量评估

#### 2.2.3 优势与局限

**优势**：
- 高准确性：基于已验证的模板结构，LLM只需填充参数
- 结构化输出：模板确保返回标准的多步骤JSON格式
- 示例驱动：包含历史成功案例，提高参数填充质量
- 快速响应：相比从零生成SQL，参数填充更快更稳定

**局限**：
- 依赖模板库覆盖率：需要持续积累相似问题的模板
- LLM参数理解：依赖LLM正确理解参数语义和时间转换
- 模板适配性：模板可能不完全匹配新问题的细微差异

### 2.3 路径2：Schema RAG + BFS扩表

#### 2.3.1 流程图

```
用户问题
  ↓
Schema向量检索（TopK=10）
  ↓
相似度过滤（threshold=0.5）
  ↓
提取种子表 modelId 集合
  ↓
种子表为空？ ─Yes→ 降级到语义上下文生成
  ↓ No
BFS扩表（深度=2）
  ↓
构建完整上下文（表结构+关系+Few-Shot）
  ↓
LLM生成多步SQL执行计划
  ↓
调用 executePlan 执行
  ↓
返回结果
```

#### 2.3.2 实现细节

**Schema向量检索**
```java
List<SchemaSearchResult> schemaResults =
    schemaVectorStoreService.searchSchemas(question, null, schemaSearchTopK);
Set<Long> seedModelIds = schemaResults.stream()
    .filter(r -> r.getScore() >= schemaSearchSimilarityThreshold)
    .map(r -> r.getMeta().getModelId())
    .collect(Collectors.toSet());
```
- `schemaSearchTopK = 10`：返回最相似的10个表
- `schemaSearchSimilarityThreshold = 0.5`：过滤低相似度结果
- 提取通过过滤的表作为种子表

**BFS扩表算法**
```java
private Set<Long> expandByBFS(Set<Long> seedModelIds, int maxDepth) {
    Set<Long> visited = new HashSet<>(seedModelIds);
    Queue<Long> queue = new LinkedList<>(seedModelIds);
    Map<Long, Integer> depthMap = new HashMap<>();

    while (!queue.isEmpty()) {
        Long current = queue.poll();
        int currentDepth = depthMap.get(current);
        if (currentDepth >= maxDepth) continue;

        List<Relationship> rels = relationshipService.findByModelId(current);
        for (Relationship rel : rels) {
            Long neighbor = current.equals(rel.getFromModelId())
                ? rel.getToModelId() : rel.getFromModelId();
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                depthMap.put(neighbor, currentDepth + 1);
                queue.offer(neighbor);
            }
        }
    }
    return visited;
}
```
- 从种子表开始，沿着表关系进行广度优先搜索
- `maxDepth = 2`：最多扩展2层关系
- 返回所有访问过的表 ID 集合

**构建上下文并生成执行计划**
```java
private List<SqlStep> generatePlanWithBFSContext(String question, Set<Long> modelIds) {
    // 1. 加载所有表和字段信息
    List<Model> models = new ArrayList<>();
    Map<Long, List<ColumnDefinition>> columnsMap = new HashMap<>();
    for (Long modelId : modelIds) {
        Model model = modelService.getById(modelId);
        models.add(model);
        columnsMap.put(modelId, columnDefinitionService.getByModelId(modelId));
    }

    // 2. 获取相关表关系（基于BFS扩展的表获取关系）
    List<Relationship> relationships = relationshipService.getByModelIds(new ArrayList<>(modelIds));

    // 3. 获取Few-Shot示例
    String fewShot = intentFewShotService.getFewShotExamples(null, question);

    // 4. 构建System Prompt（包含表结构、关系、示例）
    StringBuilder systemPrompt = new StringBuilder();
    systemPrompt.append("你是一个SQL生成专家。根据以下数据库结构，为用户问题生成多步骤SQL执行计划。\n\n");
    // ... 添加表结构、关系、Few-Shot示例 ...

    // 5. 调用LLM生成执行计划
    String llmResponse = aiService.generateSQL(systemPrompt.toString(), question);

    // 6. 解析JSON数组为SqlStep列表
    List<SqlStep> steps = parseSqlSteps(extractJsonBlock(llmResponse));
    return steps;
}
```

#### 2.3.3 优势与局限

**优势**：
- 自动发现相关表：通过向量检索找到语义相关的表
- 关系扩展：BFS算法自动补充关联表，避免遗漏
- 灵活性高：LLM可以处理各种问题类型

**局限**：
- 依赖相似度阈值：过高会漏掉相关表，过低会引入噪音
- BFS深度限制：只扩展2层，可能无法覆盖复杂关系链
- LLM生成质量：依赖模型能力和Prompt质量

### 2.4 路径3：BFS高召回重试

#### 2.4.1 流程图

```
用户标记不满意
  ↓
Schema向量检索（TopK=20，无相似度过滤）
  ↓
提取所有返回的表作为种子表
  ↓
BFS扩表（深度=2）
  ↓
构建完整上下文
  ↓
LLM生成多步SQL执行计划
  ↓
调用 executePlan 执行
  ↓
返回结果
```

#### 2.4.2 与路径2的差异

| 维度 | 路径2（正常召回） | 路径3（高召回） |
|------|------------------|----------------|
| TopK | 10 | 20 |
| 相似度过滤 | ≥ 0.5 | 无过滤 |
| 触发条件 | 首次查询或路径1失败 | 用户标记不满意 |
| 目标 | 平衡准确性和召回率 | 最大化召回率 |

#### 2.4.3 实现细节

```java
private List<SqlStep> generatePlanByBFSWithHighRecall(String question) {
    List<SchemaSearchResult> schemaResults =
        schemaVectorStoreService.searchSchemas(question, null, schemaSearchBfsRetryTopK);
    Set<Long> seedModelIds = schemaResults.stream()
        .filter(r -> r.getMeta() != null && r.getMeta().getModelId() != null)
        .map(r -> r.getMeta().getModelId())
        .collect(Collectors.toSet());

    Set<Long> allModelIds = expandByBFS(seedModelIds, 2);
    return generatePlanWithBFSContext(question, allModelIds);
}
```
- `schemaSearchBfsRetryTopK = 20`：返回更多候选表
- 不进行相似度过滤，接受所有返回的表
- 后续流程与路径2相同

#### 2.4.4 优势与局限

**优势**：
- 最大化召回率：不遗漏任何可能相关的表
- 用户反馈驱动：针对性解决用户不满意的问题

**局限**：
- 上下文噪音：可能引入不相关的表，影响LLM判断
- 性能开销：更多的表和关系需要处理
- Token消耗：更大的Prompt会增加LLM调用成本

## 3. 多步SQL执行机制

### 3.1 executePlan 核心流程

#### 3.1.1 流程图

```
接收 SqlStep 列表（已包含完整参数）
  ↓
初始化：stepResults = new HashMap<String, List<Map>>()
  ↓
遍历每个 SqlStep
  ↓
  ├─ 从 step.params 获取当前步骤参数
  ├─ 处理依赖参数（如 {{step1.task_status}}）
  │   └─ 从 stepResults.get("step1") 中提取对应字段值
  ├─ 替换 sql_template 中的占位符 → 生成完整SQL
  ├─ 使用 step.datasource_id 确定数据源
  ├─ 执行SQL → 获取结果
  └─ 保存结果：stepResults.put(step.id, 当前结果)
  ↓
记录查询日志
  ↓
保存助手消息到会话
  ↓
返回最终结果（最后一步的结果）
```

#### 3.1.2 关键实现

**参数填充Prompt构建**
```java
private String buildParamFillingPrompt(String template, String userQuestion, String exampleContext) {
    StringBuilder sb = new StringBuilder();
    sb.append("# 角色\n");
    sb.append("你是一名 SQL 专家，擅长根据用户问题生成多步骤的 SQL 执行计划。\n\n");

    sb.append("# 任务\n");
    sb.append("你将获得一组预定义的 SQL 模板（JSON 数组形式），每个模板包含一个 SQL 片段、参数说明、输出字段说明等信息。\n");
    sb.append("你需要根据用户的最新问题，从模板中选出合适的步骤，并为每个步骤填充具体的参数值（`params` 字段），最终输出一个 JSON 数组，结构与输入模板保持一致。\n\n");

    sb.append("# 模板说明\n");
    sb.append("- 模板中的 `sql_template` 包含占位符 `{{xxx}}`，你需要在params中写出实际值。\n");
    sb.append("- `paramDescs` 列出了每个参数的含义，你需要从用户问题中提取对应的值。\n");
    sb.append("- 如果某个参数依赖前一步的输出（如 `{{step1.工单状态}}`），在生成当前步骤的 `params` 时，不需要为它生成值。\n");
    sb.append("- `outputParamsDesc` 说明该步骤返回的字段，供后续步骤引用。\n");
    sb.append("- `tables`说明当前sql模板的表名和对应的数据库\n");
    sb.append("- `datasource_id`代表数据源id\n");
    sb.append("- 时间范围类参数需根据用户描述进行合理转换，例如"到现在"应替换为当前日期（或次日，若 SQL 使用 `< endDate`）。\n\n");

    if (exampleContext != null && !exampleContext.isEmpty()) {
        sb.append("# 示例上下文\n");
        sb.append(exampleContext).append("\n\n");
    }

    sb.append("初始模板如下:\n");
    sb.append("```json\n").append(template).append("\n```\n\n");

    sb.append("**现在的问题**:\n").append(userQuestion).append("\n\n");

    sb.append("**预期的输出**:\n");
    sb.append("```json\n```\n");
    sb.append("# 预期的输出说明:\n");
    sb.append("- 请使用```json```包裹着，并且被包裹着的的确是个json数组\n");

    return sb.toString();
}
```

**数据源推断**
```java
Long datasourceId = step.getDatasourceId();
if (datasourceId == null) {
    datasourceId = semanticContextService.inferDataSourceFromSQL(filledSql);
}
```
- 如果 SqlStep 中未指定 datasourceId，从 SQL 中的表名推断
- 通过表名匹配 Model 表，获取对应的 datasourceId

### 3.2 步骤间数据传递

#### 3.2.1 数据流转示例

假设用户问题："获取2025年7月到现在，充值工单状态排名前五的工单ID"

LLM已经在路径1/2/3中生成了完整的执行计划JSON，现在executePlan开始执行：

**Step 1: 统计工单状态**
```json
{
  "id": "step1",
  "sql_template": "SELECT CONCAT(stage, '-', status) AS task_status, COUNT(*) AS task_count FROM task WHERE create_time >= {{startDate}} AND create_time < {{endDate}} AND type = {{type}} GROUP BY stage, status ORDER BY task_count DESC LIMIT 5",
  "params": {
    "startDate": "2025-07-01",
    "endDate": "2026-03-27",
    "type": 30
  },
  "datasource_id": 1
}
```

系统替换占位符后执行：
```sql
SELECT CONCAT(stage, '-', status) AS task_status, COUNT(*) AS task_count
FROM task
WHERE create_time >= '2025-07-01' AND create_time < '2026-03-27' AND type = 30
GROUP BY stage, status
ORDER BY task_count DESC LIMIT 5
```

执行结果保存到 stepResults.put("step1", result)：
```json
[
  {"task_status": "2-1", "task_count": 150},
  {"task_status": "3-2", "task_count": 120},
  {"task_status": "1-0", "task_count": 100},
  {"task_status": "2-3", "task_count": 80},
  {"task_status": "4-1", "task_count": 60}
]
```

**Step 2: 获取工单ID**
```json
{
  "id": "step2",
  "sql_template": "SELECT task_id FROM task WHERE CONCAT(stage, '-', status) IN ({{step1.task_status}}) AND type = {{type}}",
  "params": {
    "type": 30
  },
  "datasource_id": 1
}
```

系统从 stepResults.get("step1") 提取 task_status 字段值，替换占位符：
```sql
SELECT task_id
FROM task
WHERE CONCAT(stage, '-', status) IN ('2-1', '3-2', '1-0', '2-3', '4-1')
AND type = 30
```

#### 3.2.2 关键机制

**步骤结果存储**
```java
Map<String, List<Map<String, Object>>> stepResults = new HashMap<>();
// 执行每个步骤后
stepResults.put(step.getId(), currentResult);
```
- 使用Map存储所有步骤的结果，key为步骤ID（如"step1"、"step2"）
- 后续步骤可以引用任意前面步骤的结果

**参数引用解析**
```java
// 解析依赖参数，如 {{step1.task_status}}
if (paramValue.startsWith("{{") && paramValue.endsWith("}}")) {
    String ref = paramValue.substring(2, paramValue.length() - 2);
    String[] parts = ref.split("\\.");
    String stepId = parts[0];  // "step1"
    String fieldName = parts[1];  // "task_status"

    List<Map<String, Object>> stepResult = stepResults.get(stepId);
    List<Object> values = stepResult.stream()
        .map(row -> row.get(fieldName))
        .collect(Collectors.toList());

    // 转换为SQL的IN子句格式
    String inClause = values.stream()
        .map(v -> "'" + v + "'")
        .collect(Collectors.joining(", "));
}
```
- 系统自动识别 `{{step1.task_status}}` 这样的依赖引用
- 从 stepResults 中提取对应步骤的对应字段值
- 转换为SQL的IN子句格式或其他适当格式

### 3.3 错误处理与降级

#### 3.3.1 执行失败处理

```java
try {
    result = queryExecutorService.executeQuery(datasourceId, filledSql);
    stepResults.put(step.getId(), result);
} catch (Exception e) {
    log.error("步骤 {} 执行失败: {}", step.getId(), e.getMessage());

    // 记录整个执行计划的失败日志
    QueryLog queryLog = new QueryLog();
    queryLog.setUserId(request.getUserId());
    queryLog.setConversationId(conversationId);
    queryLog.setQuestion(question);
    queryLog.setIntentJson(objectMapper.writeValueAsString(steps));  // 记录完整的执行计划JSON
    queryLog.setExecutionSuccess(false);
    queryLog.setErrorMessage("步骤 " + step.getId() + " 执行失败: " + e.getMessage());
    queryLogService.logQuery(queryLog);

    // 返回错误响应
    return QueryResponse.error("SQL执行失败: " + e.getMessage());
}
```

- 任何步骤执行失败，整个计划终止
- 记录整个执行计划的JSON到intent_json字段
- 记录失败信息和错误原因
- 返回错误响应给用户

#### 3.3.2 成功日志记录

```java
// 所有步骤执行成功后，记录整个执行计划
QueryLog queryLog = new QueryLog();
queryLog.setUserId(request.getUserId());
queryLog.setConversationId(conversationId);
queryLog.setQuestion(question);
queryLog.setTemplateId(templateId);
queryLog.setIsFromTemplate(isFromTemplate);
queryLog.setIntentJson(objectMapper.writeValueAsString(steps));  // 记录完整的执行计划JSON
queryLog.setExecutionSuccess(true);
queryLog.setResultCount(stepResults.get(steps.get(steps.size() - 1).getId()).size());  // 最后一步的结果数量
queryLog.setDatasourceId(steps.get(0).getDatasourceId());  // 第一步的数据源ID
queryLog.setExecutionTime(System.currentTimeMillis() - startTime);
queryLogId = queryLogService.logQuery(queryLog);
```

- 记录完整的执行计划JSON（包含所有步骤）到intent_json字段
- 记录整体执行结果和耗时
- 用于后续分析和优化

**步骤级别日志（可选）**

如果需要记录每个步骤的详细执行情况，建议创建新表 `query_step_log`：

```sql
CREATE TABLE query_step_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  query_log_id BIGINT NOT NULL COMMENT '关联的query_log主键',
  step_id VARCHAR(50) NOT NULL COMMENT '步骤ID（如step1、step2）',
  step_index INT NOT NULL COMMENT '步骤序号（从0开始）',
  sql_template TEXT COMMENT 'SQL模板',
  filled_sql TEXT COMMENT '填充参数后的完整SQL',
  datasource_id BIGINT COMMENT '数据源ID',
  execution_success BOOLEAN COMMENT '执行是否成功',
  result_count INT COMMENT '结果行数',
  execution_time BIGINT COMMENT '执行耗时（毫秒）',
  error_message TEXT COMMENT '错误信息',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_query_log_id (query_log_id),
  INDEX idx_step_id (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询步骤执行日志表';
```

**实现逻辑**

1. **领域实体类**
```java
@Data
public class QueryStepLog {
    private Long id;
    private Long queryLogId;
    private String stepId;
    private Integer stepIndex;
    private String sqlTemplate;
    private String filledSql;
    private Long datasourceId;
    private Boolean executionSuccess;
    private Integer resultCount;
    private Long executionTime;
    private String errorMessage;
    private Date createTime;
}
```

2. **Service层实现**
```java
@Service
@RequiredArgsConstructor
public class QueryStepLogService {
    private final QueryStepLogMapper queryStepLogMapper;

    /**
     * 记录单个步骤的执行日志
     */
    public void logStep(QueryStepLog stepLog) {
        queryStepLogMapper.insert(stepLog);
    }

    /**
     * 批量记录步骤日志
     */
    public void logSteps(List<QueryStepLog> stepLogs) {
        if (stepLogs != null && !stepLogs.isEmpty()) {
            queryStepLogMapper.batchInsert(stepLogs);
        }
    }

    /**
     * 根据query_log_id查询所有步骤日志
     */
    public List<QueryStepLog> getByQueryLogId(Long queryLogId) {
        return queryStepLogMapper.findByQueryLogId(queryLogId);
    }
}
```

3. **Mapper接口**
```java
@Mapper
public interface QueryStepLogMapper {
    void insert(QueryStepLog stepLog);
    void batchInsert(@Param("stepLogs") List<QueryStepLog> stepLogs);
    List<QueryStepLog> findByQueryLogId(@Param("queryLogId") Long queryLogId);
}
```

4. **Mapper XML**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.tecdo.mac.sql2bot.mapper.QueryStepLogMapper">

    <insert id="insert" parameterType="com.tecdo.mac.sql2bot.domain.QueryStepLog"
            useGeneratedKeys="true" keyProperty="id">
        INSERT INTO query_step_log (
            query_log_id, step_id, step_index, sql_template, filled_sql,
            datasource_id, execution_success, result_count, execution_time, error_message
        ) VALUES (
            #{queryLogId}, #{stepId}, #{stepIndex}, #{sqlTemplate}, #{filledSql},
            #{datasourceId}, #{executionSuccess}, #{resultCount}, #{executionTime}, #{errorMessage}
        )
    </insert>

    <insert id="batchInsert" parameterType="java.util.List">
        INSERT INTO query_step_log (
            query_log_id, step_id, step_index, sql_template, filled_sql,
            datasource_id, execution_success, result_count, execution_time, error_message
        ) VALUES
        <foreach collection="stepLogs" item="log" separator=",">
            (#{log.queryLogId}, #{log.stepId}, #{log.stepIndex}, #{log.sqlTemplate}, #{log.filledSql},
             #{log.datasourceId}, #{log.executionSuccess}, #{log.resultCount}, #{log.executionTime}, #{log.errorMessage})
        </foreach>
    </insert>

    <select id="findByQueryLogId" resultType="com.tecdo.mac.sql2bot.domain.QueryStepLog">
        SELECT * FROM query_step_log
        WHERE query_log_id = #{queryLogId}
        ORDER BY step_index ASC
    </select>

</mapper>
```

5. **在executePlan中集成步骤日志**
```java
private QueryResponse executePlan(List<SqlStep> steps, String question, Long conversationId, ...) {
    long startTime = System.currentTimeMillis();
    Map<String, List<Map<String, Object>>> stepResults = new HashMap<>();
    List<QueryStepLog> stepLogs = new ArrayList<>();  // 收集所有步骤日志

    // 遍历执行每个步骤
    for (int i = 0; i < steps.size(); i++) {
        SqlStep step = steps.get(i);
        long stepStartTime = System.currentTimeMillis();

        QueryStepLog stepLog = new QueryStepLog();
        stepLog.setStepId(step.getId());
        stepLog.setStepIndex(i);
        stepLog.setSqlTemplate(step.getSqlTemplate());
        stepLog.setDatasourceId(step.getDatasourceId());

        try {
            // 替换占位符生成完整SQL
            String filledSql = replacePlaceholders(step, stepResults);
            stepLog.setFilledSql(filledSql);

            // 执行SQL
            List<Map<String, Object>> result = queryExecutorService.executeQuery(
                step.getDatasourceId(), filledSql);
            stepResults.put(step.getId(), result);

            // 记录成功信息
            stepLog.setExecutionSuccess(true);
            stepLog.setResultCount(result.size());
            stepLog.setExecutionTime(System.currentTimeMillis() - stepStartTime);

        } catch (Exception e) {
            log.error("步骤 {} 执行失败: {}", step.getId(), e.getMessage());

            // 记录失败信息
            stepLog.setExecutionSuccess(false);
            stepLog.setErrorMessage(e.getMessage());
            stepLog.setExecutionTime(System.currentTimeMillis() - stepStartTime);
            stepLogs.add(stepLog);

            // 记录整体失败日志
            QueryLog queryLog = new QueryLog();
            queryLog.setIntentJson(objectMapper.writeValueAsString(steps));
            queryLog.setExecutionSuccess(false);
            queryLog.setErrorMessage("步骤 " + step.getId() + " 执行失败: " + e.getMessage());
            Long queryLogId = queryLogService.logQuery(queryLog);

            // 关联步骤日志到query_log
            stepLogs.forEach(log -> log.setQueryLogId(queryLogId));
            queryStepLogService.logSteps(stepLogs);

            return QueryResponse.error("SQL执行失败: " + e.getMessage());
        }

        stepLogs.add(stepLog);
    }

    // 所有步骤执行成功，记录整体日志
    QueryLog queryLog = new QueryLog();
    queryLog.setUserId(request.getUserId());
    queryLog.setConversationId(conversationId);
    queryLog.setQuestion(question);
    queryLog.setIntentJson(objectMapper.writeValueAsString(steps));
    queryLog.setExecutionSuccess(true);
    queryLog.setExecutionTime(System.currentTimeMillis() - startTime);
    Long queryLogId = queryLogService.logQuery(queryLog);

    // 关联所有步骤日志到query_log
    stepLogs.forEach(log -> log.setQueryLogId(queryLogId));
    queryStepLogService.logSteps(stepLogs);

    // 返回最后一步的结果
    SqlStep lastStep = steps.get(steps.size() - 1);
    return QueryResponse.success(stepResults.get(lastStep.getId()));
}
```

这样可以：
- query_log 记录整个执行计划的总体信息
- query_step_log 记录每个步骤的详细执行情况
- 通过 query_log_id 关联两张表
- 支持批量插入提高性能
- 失败时也能记录已执行步骤的情况

## 4. 配置参数说明

### 4.1 三路径查询配置总览

| 路径 | 配置参数 | 默认值 | 说明 |
|------|---------|--------|------|
| 路径1 | `MAX_TEMPLATE_CANDIDATES` | 5 | RAG模板检索返回的最大候选数 |
| 路径1 | `template.search.similarity-threshold` | 0.6 | 模板相似度过滤阈值（0-1） |
| 路径2 | `schema.search.similarity-threshold` | 0.5 | Schema检索相似度过滤阈值（0-1） |
| 路径2 | `maxDepth` | 2 | BFS扩展的最大深度 |
| 路径3 | `maxDepth` | 2 | BFS扩展的最大深度（无相似度过滤） |

### 4.2 路径1：RAG模板匹配配置

**模板检索配置**
```java
private static final int MAX_TEMPLATE_CANDIDATES = 5;
```

**相似度过滤配置（application.properties）**
```properties
template.search.similarity-threshold=0.6
```

**参数说明**：
- `MAX_TEMPLATE_CANDIDATES`：模板向量检索返回的最大候选数
  - 默认值：5
  - 控制从向量库中检索的模板候选数量
  - 较大值：提高找到匹配模板的概率，但增加LLM处理时间

- `template.search.similarity-threshold`：模板相似度过滤阈值（0-1）
  - 默认值：0.6（比Schema检索稍高，因为模板匹配要求更精确）
  - 较高值（0.7-0.9）：只保留高度相似的模板，精确但可能无匹配
  - 较低值（0.5-0.6）：保留更多候选模板，召回率高但可能不够精确

**工作流程**：
1. 向量化用户问题
2. 在模板向量库中检索TopK=5的相似模板
3. 相似度过滤（threshold=0.6），保留高相似度的模板
4. LLM填充模板参数生成执行计划
5. 解析为SqlStep列表并执行

### 4.3 路径2：Schema RAG + BFS扩展配置

**Schema检索配置（application.properties）**
```properties
schema.search.similarity-threshold=0.5
```

**参数说明**：
- `schema.search.similarity-threshold`：相似度过滤阈值（0-1）
  - 默认值：0.5
  - 较高值（0.7-0.9）：高精确度，低召回率，只保留高度相关的表
  - 较低值（0.3-0.5）：高召回率，低精确度，保留更多可能相关的表
  - 向量检索返回所有结果，然后按此阈值过滤

**BFS扩展配置**
```java
Set<Long> allModelIds = expandByBFS(seedModelIds, 2);  // maxDepth = 2
```

- `maxDepth`：BFS扩展的最大深度
  - 默认值：2
  - 深度1：只包含直接关联的表
  - 深度2：包含二度关联的表（推荐）
  - 深度3+：可能引入过多无关表

**工作流程**：
1. 向量化用户问题
2. Schema向量检索获取所有相关表
3. 相似度过滤（threshold=0.5），保留高相关度的表
4. BFS扩展2层获取关联表
5. LLM基于扩展后的表生成执行计划

### 4.4 路径3：高召回BFS重试配置

**配置说明**：
- 路径3不使用相似度过滤阈值
- 不限制检索结果数量
- 保留所有向量检索返回的表，最大化召回率

**BFS扩展配置**
```java
Set<Long> allModelIds = expandByBFS(seedModelIds, 2);  // maxDepth = 2
```

**触发条件**：
- 用户对路径1或路径2的结果标记为"不满意"
- 系统自动使用高召回模式重试

**工作流程**：
1. 向量化用户问题
2. Schema向量检索获取所有相关表
3. **不进行相似度过滤**（与路径2的关键区别）
4. BFS扩展2层获取关联表
5. LLM基于更大范围的表生成执行计划

**与路径2的区别**：
- 路径2：使用相似度阈值过滤，精确但可能遗漏
- 路径3：不过滤，全量保留，召回率最高

### 4.5 配置调优建议

**路径选择策略**：
- 路径1优先：快速、准确，适合常见查询模式
- 路径2降级：灵活、通用，适合新颖查询
- 路径3兜底：高召回、全面，适合复杂查询

**参数调优原则**：
1. **模板检索（路径1）**
   - 增加`MAX_TEMPLATE_CANDIDATES`可提高模板匹配率
   - 但会增加LLM处理时间和成本

2. **Schema检索（路径2）**
   - `similarity-threshold`过高（0.7-0.9）：精确但可能遗漏关键表
   - `similarity-threshold`过低（0.3-0.5）：召回率高但引入噪音
   - 建议根据实际数据分布和查询效果调整

3. **高召回重试（路径3）**
   - 不使用相似度过滤，保留所有检索结果
   - 会显著增加LLM上下文和Token消耗
   - 适合作为最后的兜底方案

4. **BFS扩展深度**
   - 深度2适合大多数场景
   - 复杂业务可考虑深度3，但需评估性能影响

**监控指标**：
- 各路径的使用频率和成功率
- 平均查询耗时
- LLM Token消耗
- 用户满意度反馈
| `prevResultMaxRows` | 100 | 传递给下一步的最大结果行数 |

**代码中定义**：
```java
List<Map<String, Object>> truncated = prevResult.size() > 100
    ? prevResult.subList(0, 100) : prevResult;
```

## 5. 服务依赖关系

### 5.1 依赖服务列表

```
TextToSQLService
├── AIService (LLM调用)
├── VectorSearchService (统一向量检索服务)
├── QueryExecutorService (SQL执行)
├── ConversationService (会话管理)
├── MessageService (消息管理)
├── QueryLogService (查询日志)
├── QueryStepLogService (步骤日志)
├── ModelService (表模型管理)
├── RelationshipService (表关系管理)
├── ColumnDefinitionService (字段定义管理)
└── DatabaseService (数据库管理)
```

### 5.2 核心服务职责

**AIService**
- 调用 Claude API 生成 SQL
- 提取 SQL 代码块
- 处理 LLM 响应

**VectorSearchService（统一向量检索服务）**
- 模板向量检索（路径1）
  - 检索相似模板
  - 相似度过滤（threshold=0.6）
  - 返回模板及相似度分数
- Schema向量检索（路径2/3）
  - 检索相关表
  - 路径2：相似度过滤（threshold=0.5）
  - 路径3：不过滤，全量返回
- 问答对向量索引
  - 用户满意时存储问答对
  - 用于后续RAG检索

**QueryExecutorService**
- 执行 SQL 查询
- 处理多数据源连接
- 返回查询结果

**RelationshipService**
- 查询表关系
- 支持 BFS 扩展

**QueryLogService**
- 记录整体查询日志（intent_json）
- 获取历史示例

**QueryStepLogService**
- 记录步骤级别日志
- 追踪每个步骤的执行情况
- 支持用户反馈

### 5.3 数据流转图

```
用户请求 (QueryRequest)
  ↓
[TextToSQLService]
  ↓
┌─────────────────────────────────────┐
│ 路径选择                              │
├─────────────────────────────────────┤
│ 路径1: TemplateVectorSearchService  │
│   ↓                                  │
│ QueryTemplateService                │
│   ↓                                  │
│ parseSqlSteps                       │
├─────────────────────────────────────┤
│ 路径2/3: SchemaVectorStoreService   │
│   ↓                                  │
│ RelationshipService (BFS)           │
│   ↓                                  │
│ ModelService + ColumnDefinitionService│
│   ↓                                  │
│ IntentFewShotService                │
│   ↓                                  │
│ AIService (生成执行计划)             │
└─────────────────────────────────────┘
  ↓
executePlan
  ↓
┌─────────────────────────────────────┐
│ 遍历 SqlStep                         │
├─────────────────────────────────────┤
│ QueryLogService (获取示例)           │
│   ↓                                  │
│ buildParamFillingPrompt             │
│   ↓                                  │
│ AIService (参数填充)                 │
│   ↓                                  │
│ SemanticContextService (推断数据源)  │
│   ↓                                  │
│ QueryExecutorService (执行SQL)       │
│   ↓                                  │
│ 更新 prevResult                      │
└─────────────────────────────────────┘
  ↓
QueryLogService (记录日志)
  ↓
MessageService (保存消息)
  ↓
返回 QueryResponse
```

## 6. 关键算法详解

### 6.1 BFS表扩展算法

**算法目标**：从种子表出发，沿着表关系进行广度优先搜索，发现所有相关表。

**算法步骤**：
1. 初始化：将种子表加入 visited 集合和队列
2. 从队列取出一个表，查询其所有关系
3. 对于每个关系，找到邻居表（关系的另一端）
4. 如果邻居表未访问且深度未超限，加入 visited 和队列
5. 重复步骤2-4，直到队列为空

**时间复杂度**：O(V + E)，其中 V 是表数量，E 是关系数量

**空间复杂度**：O(V)

**示例**：
```
种子表: [A]
深度0: A
深度1: A -> B, A -> C
深度2: B -> D, C -> E
结果: [A, B, C, D, E]
```

### 6.2 模板解析算法

**支持格式**：
1. JSON数组格式（多步骤）
2. 单条SQL字符串（向后兼容）

**解析逻辑**：
```java
private List<SqlStep> parseSqlSteps(String sqlTemplate) {
    try {
        JsonNode node = objectMapper.readTree(sqlTemplate);
        if (node.isArray()) {
            // 解析JSON数组
            List<SqlStep> steps = new ArrayList<>();
            for (JsonNode item : node) {
                SqlStep step = new SqlStep();
                step.setSqlTemplate(item.path("sql_template").asText(null));
                step.setDatasourceId(item.get("datasource_id").asLong());
                steps.add(step);
            }
            return steps;
        }
    } catch (Exception ignored) {}

    // 向后兼容：单条SQL字符串
    if (sqlTemplate.trim().toUpperCase().startsWith("SELECT")) {
        return List.of(new SqlStep(sqlTemplate, null));
    }
    return null;
}
```

### 6.3 参数填充算法

**输入**：
- SQL模板（包含 `{{param}}` 占位符）
- 用户问题
- 上一步查询结果（可选）
- 参考示例（可选）

**输出**：
- 填充完成的可执行SQL

**关键点**：
- LLM需要理解占位符的语义
- 从用户问题中提取参数值
- 从上一步结果中引用字段值
- 处理时间范围、枚举值等特殊类型

## 7. 性能优化策略

### 7.1 向量检索优化

**策略**：
- 使用 Redis 向量索引，支持高效的相似度搜索
- TopK 限制，避免返回过多结果
- 相似度阈值过滤，减少无关结果

**性能指标**：
- Schema检索：< 100ms（TopK=10）
- 模板检索：< 50ms（TopK=5）

### 7.2 BFS扩展优化

**策略**：
- 深度限制（maxDepth=2），避免过度扩展
- visited 集合去重，避免重复访问
- 提前终止：如果扩展后表数量过多（如 > 50），可考虑截断

**性能指标**：
- BFS扩展：< 200ms（种子表10个，关系100条）

### 7.3 LLM调用优化

**策略**：
- Prompt精简：只包含必要的表和字段信息
- 结果截断：上一步结果最多传递100行
- 批量处理：未来可考虑批量生成多个步骤的参数

**性能指标**：
- 单次LLM调用：1-3秒（取决于模型和Prompt大小）

### 7.4 SQL执行优化

**策略**：
- 连接池复用
- 查询超时设置
- 结果集大小限制

## 8. 监控与日志

### 8.1 关键日志点

**路径选择日志**：
```java
log.info("开始RAG模板检索: question={}", request.getQuestion());
log.info("RAG模板匹配成功: templateId={}, similarity={}", templateId, similarity);
log.info("进入路径2: Schema RAG + BFS扩表 + LLM");
log.info("用户不满意，触发BFS高召回重试: queryLogId={}", queryLogId);
```

**BFS扩展日志**：
```java
log.info("BFS扩展完成: 种子{}个 -> 扩展后{}个表", seedModelIds.size(), visited.size());
```

**执行日志**：
```java
log.info("步骤执行成功: sql={}, rows={}", filledSql, prevResult.size());
log.error("步骤执行失败: sql={}", filledSql, e);
```

### 8.2 性能监控指标

| 指标 | 说明 | 目标值 |
|------|------|--------|
| 总响应时间 | 从请求到返回结果 | < 5秒 |
| 路径1命中率 | 模板匹配成功的比例 | > 30% |
| 路径2成功率 | BFS+LLM生成成功的比例 | > 80% |
| SQL执行成功率 | 生成的SQL可执行的比例 | > 90% |
| 用户满意度 | 用户标记满意的比例 | > 70% |

## 9. 未来优化方向

### 9.1 智能路径选择

**当前问题**：路径选择是固定的顺序（路径1 → 路径2 → 路径3）

**优化方案**：
- 根据问题特征预测最佳路径
- 使用分类模型判断问题类型
- 动态调整路径优先级

### 9.2 自适应参数调优

**当前问题**：TopK、相似度阈值等参数是固定的

**优化方案**：
- 根据历史成功率动态调整参数
- A/B测试不同参数组合
- 用户级别的个性化参数

### 9.3 执行计划缓存

**当前问题**：相同问题每次都重新生成执行计划

**优化方案**：
- 缓存问题 → 执行计划的映射
- 使用问题向量作为缓存键
- 设置合理的过期时间

### 9.4 多步骤并行执行

**当前问题**：步骤串行执行，无法利用并行性

**优化方案**：
- 分析步骤间依赖关系
- 无依赖的步骤并行执行
- 使用异步执行框架

### 9.5 增强的错误恢复

**当前问题**：任何步骤失败都会导致整个计划终止

**优化方案**：
- 步骤级别的重试机制
- 自动修复常见SQL错误
- 降级到简化版执行计划

## 10. 总结

### 10.1 架构优势

1. **多层次策略**：从高精度到高召回，逐步降级，最大化查询成功率
2. **用户反馈驱动**：通过满意度反馈持续优化向量索引和模板库
3. **灵活的多步执行**：支持复杂查询场景，步骤间数据传递
4. **自动表发现**：BFS算法自动补充关联表，减少人工配置

### 10.2 关键技术点

- **向量检索**：Schema和模板的语义相似度搜索
- **BFS扩展**：基于表关系的图遍历算法
- **LLM集成**：参数填充和执行计划生成
- **多步执行**：步骤间数据传递和依赖处理

### 10.3 适用场景

- **路径1**：高频问题、已知问题模式
- **路径2**：常规查询、中等复杂度
- **路径3**：复杂查询、用户不满意重试

### 10.4 注意事项

1. **配置调优**：根据实际数据和查询特点调整TopK、相似度阈值等参数
2. **模板维护**：定期审查和更新模板库，删除低质量模板
3. **性能监控**：关注各路径的命中率和成功率，及时发现问题
4. **用户反馈**：鼓励用户提供满意度反馈，持续优化系统

---

**文档版本历史**：
- v1.0 (2026-03-27): 初始版本，完整描述三路径架构设计
