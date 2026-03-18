# Schema 索引定时任务实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建定时任务模块，将 model 和 column_definition 表数据同步到 RedisStack 向量数据库，支持基于意图骨架的语义检索

**Architecture:** 使用 RedisStack 原生 KNN 向量搜索替换现有的 VectorStoreService。定时任务每小时增量同步表结构变更，查询时根据意图骨架字符串进行语义检索，将相关表结构传给 LLM 生成 SQL 骨架和参数定义。

**Tech Stack:** Spring Boot 4.0.3, MyBatis, RedisStack (HNSW 向量索引), Jedis, Embedding API

---

## 文件结构

### 新增文件
- `src/main/java/com/tecdo/mac/sql2bot/scheduler/SchemaIndexScheduler.java` - 定时任务入口
- `src/main/java/com/tecdo/mac/sql2bot/scheduler/SchemaIndexService.java` - 增量索引逻辑
- `src/main/java/com/tecdo/mac/sql2bot/service/SchemaVectorStoreService.java` - RedisStack 向量存储服务
- `src/main/java/com/tecdo/mac/sql2bot/controller/SchedulerController.java` - 管理接口
- `src/test/java/com/tecdo/mac/sql2bot/scheduler/SchemaIndexServiceTest.java` - 索引服务测试
- `src/test/java/com/tecdo/mac/sql2bot/service/SchemaVectorStoreServiceTest.java` - 向量存储测试

### 修改文件
- `src/main/java/com/tecdo/mac/sql2bot/mapper/ModelMapper.java` - 新增 selectUpdatedAfter 方法
- `src/main/resources/mapper/ModelMapper.xml` - 新增 SQL 映射
- `src/main/java/com/tecdo/mac/sql2bot/mapper/ColumnDefinitionMapper.java` - 新增 selectUpdatedAfter 方法
- `src/main/resources/mapper/ColumnDefinitionMapper.xml` - 新增 SQL 映射
- `src/main/java/com/tecdo/mac/sql2bot/service/VectorRAGService.java` - 改为依赖 SchemaVectorStoreService
- `src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java` - 集成骨架检索逻辑
- `src/main/resources/application.properties` - 新增配置项

### 废弃文件
- `src/main/java/com/tecdo/mac/sql2bot/service/VectorStoreService.java` - 标记为 @Deprecated

---

## Task 1: Mapper 新增 selectUpdatedAfter 方法

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/mapper/ModelMapper.java`
- Modify: `src/main/resources/mapper/ModelMapper.xml`
- Modify: `src/main/java/com/tecdo/mac/sql2bot/mapper/ColumnDefinitionMapper.java`
- Modify: `src/main/resources/mapper/ColumnDefinitionMapper.xml`

- [ ] **Step 1: 在 ModelMapper.java 新增方法**

在 `selectVisible()` 方法后添加：
```java
import java.time.LocalDateTime;

/**
 * 查询 updated_at 大于指定时间的 model
 */
List<Model> selectUpdatedAfter(@Param("updatedAfter") LocalDateTime updatedAfter);

/**
 * 查询所有 model（含 datasource 信息）
 */
List<Model> selectAllWithDatasource();
```

- [ ] **Step 2: 在 ModelMapper.xml 新增 SQL**

在 `selectVisible` 查询后添加：
```xml
<select id="selectUpdatedAfter" resultMap="BaseResultMap">
    SELECT * FROM model WHERE updated_at > #{updatedAfter} ORDER BY updated_at ASC
</select>

<select id="selectAllWithDatasource" resultMap="BaseResultMap">
    SELECT * FROM model ORDER BY created_at DESC
</select>
```

- [ ] **Step 3: 在 ColumnDefinitionMapper.java 新增方法**

```java
import java.time.LocalDateTime;

/**
 * 查询 updated_at 大于指定时间的 column_definition
 */
List<ColumnDefinition> selectUpdatedAfter(@Param("updatedAfter") LocalDateTime updatedAfter);
```

- [ ] **Step 4: 在 ColumnDefinitionMapper.xml 新增 SQL**

```xml
<select id="selectUpdatedAfter" resultMap="BaseResultMap">
    SELECT * FROM column_definition WHERE updated_at > #{updatedAfter} ORDER BY updated_at ASC
</select>
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/mapper/ src/main/resources/mapper/
git commit -m "feat(mapper): 新增 selectUpdatedAfter 方法支持增量索引"
```

---


## Task 2: SchemaVectorStoreService

**Files:**
- Create: src/main/java/com/tecdo/mac/sql2bot/service/SchemaVectorStoreService.java
- Modify: src/main/resources/application.properties

- [ ] **Step 1: 在 application.properties 末尾追加配置**

scheduler.schema-index.enabled=true
scheduler.schema-index.cron=0 0 * * * ?

- [ ] **Step 2: 检查 pom.xml jedis 依赖，如无则添加**

redis.clients:jedis 依赖

- [ ] **Step 3: 检查是否已有 JedisPool Bean，如无则创建 RedisConfig.java**

路径: src/main/java/com/tecdo/mac/sql2bot/config/RedisConfig.java
注入 host/port 配置，创建 JedisPool Bean，maxTotal=8, maxIdle=8, minIdle=0

- [ ] **Step 4: 创建 SchemaVectorStoreService.java**

实现方法：
- @PostConstruct initIndex() - 检查并创建 RedisStack HNSW 索引（idx:schema，PREFIX schema:，DIM 1024，COSINE）
- indexModel(Model, List<ColumnDefinition>, String datasourceName) - 合并文本生成 embedding，HSET 到 RedisStack
- searchSchemas(String query, Long datasourceId, int topK) - KNN 搜索返回 List<SchemaSearchResult>
- deleteModel(Long modelId) - 删除单个表索引
- clearAll() - 清空所有 schema: 前缀的 key
- getIndexCount() - 返回索引数量

内部类：
- SchemaMeta（modelId, tableName, displayName, description, datasourceId, datasourceName, List<ColumnInfo>）
- SchemaMeta.ColumnInfo（columnName, displayName, columnType）
- SchemaSearchResult（SchemaMeta meta, double score）

向量存储：key=schema:{modelId}，HASH 字段：table_name, datasource_id, meta(JSON), vector(FLOAT32 binary little-endian)
KNN 查询语法（有 datasourceId 时过滤）：@datasource_id:[{id} {id}]=>[KNN {topK} @vector $BLOB AS score]

- [ ] **Step 5: 编译验证**

mvn compile -q
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

git add src/main/java/com/tecdo/mac/sql2bot/service/SchemaVectorStoreService.java
git add src/main/java/com/tecdo/mac/sql2bot/config/
git add src/main/resources/application.properties
git commit -m "feat(service): 新增 SchemaVectorStoreService 使用 RedisStack KNN 向量搜索"

---

## Task 3: SchemaIndexService

**Files:**
- Create: src/main/java/com/tecdo/mac/sql2bot/scheduler/SchemaIndexService.java

- [ ] **Step 1: 创建 scheduler 包目录**

确保目录 src/main/java/com/tecdo/mac/sql2bot/scheduler/ 存在

- [ ] **Step 2: 创建 SchemaIndexService.java**

实现方法：
- incrementalIndex() - 增量索引：从 Redis 读取 schema:last_index_time，查询 updated_at > lastIndexTime 的 model，每个 model 查出所有 column_definition，调用 SchemaVectorStoreService.indexModel()，完成后更新 schema:last_index_time
- fullIndex() - 全量索引：调用 SchemaVectorStoreService.clearAll()，然后索引所有 model
- getLastIndexTime() - 从 Redis 读取上次执行时间，返回 LocalDateTime（null 表示从未执行）

依赖：SchemaVectorStoreService, ModelMapper, ColumnDefinitionMapper, DataSourceMapper, RedisTemplate<String, String>
Redis key：schema:last_index_time，值格式：ISO-8601 字符串

增量索引逻辑：
1. lastIndexTime = getLastIndexTime()
2. if lastIndexTime == null: models = modelMapper.selectAll()
   else: models = modelMapper.selectUpdatedAfter(lastIndexTime) + 查找 column_definition 有更新的 model（去重）
3. for each model: 查出 columns，获取 datasourceName，调用 schemaVectorStoreService.indexModel()
4. updateLastIndexTime(now)

- [ ] **Step 3: 编译验证**

mvn compile -q
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

git add src/main/java/com/tecdo/mac/sql2bot/scheduler/SchemaIndexService.java
git commit -m "feat(scheduler): 新增 SchemaIndexService 实现增量索引逻辑"

---

## Task 4: SchemaIndexScheduler

**Files:**
- Create: src/main/java/com/tecdo/mac/sql2bot/scheduler/SchemaIndexScheduler.java

- [ ] **Step 1: 确认主类有 @EnableScheduling 注解**

检查 src/main/java/com/tecdo/mac/sql2bot/Sql2botApplication.java，确认有 @EnableScheduling，如无则添加。

- [ ] **Step 2: 创建 SchemaIndexScheduler.java**

@Component, @ConditionalOnProperty(name = "scheduler.schema-index.enabled", havingValue = "true", matchIfMissing = true)
依赖：SchemaIndexService
字段：private volatile boolean running = true
方法：
- @Scheduled(cron = "${scheduler.schema-index.cron:0 0 * * * ?}") indexSchema() - 检查 running，调用 schemaIndexService.incrementalIndex()，异常时 log.error
- start() - running = true
- stop() - running = false
- isRunning() - return running

- [ ] **Step 3: 编译验证**

mvn compile -q
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

git add src/main/java/com/tecdo/mac/sql2bot/scheduler/SchemaIndexScheduler.java
git add src/main/java/com/tecdo/mac/sql2bot/Sql2botApplication.java
git commit -m "feat(scheduler): 新增 SchemaIndexScheduler 定时任务入口"

---

## Task 5: SchedulerController

**Files:**
- Create: src/main/java/com/tecdo/mac/sql2bot/controller/SchedulerController.java

- [ ] **Step 1: 创建 SchedulerController.java**

@RestController, @RequestMapping("/api/scheduler/schema-index")
依赖：SchemaIndexScheduler, SchemaIndexService, SchemaVectorStoreService

端点：
- POST /full - 调用 schemaIndexService.fullIndex()，返回 Result.success("全量索引完成")
- POST /incremental - 调用 schemaIndexService.incrementalIndex()，返回 Result.success("增量索引完成")
- POST /start - 调用 schemaIndexScheduler.start()，返回 Result.success("定时任务已启动")
- POST /stop - 调用 schemaIndexScheduler.stop()，返回 Result.success("定时任务已停止")
- GET /status - 返回 Map{running, indexCount, lastIndexTime}

注意：检查项目中 Result 类的包路径，使用正确的导入。

- [ ] **Step 2: 编译验证**

mvn compile -q
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

git add src/main/java/com/tecdo/mac/sql2bot/controller/SchedulerController.java
git commit -m "feat(controller): 新增 SchedulerController 提供索引管理接口"

---

## Task 6: 更新 VectorRAGService

**Files:**
- Modify: src/main/java/com/tecdo/mac/sql2bot/service/VectorRAGService.java
- Modify: src/main/java/com/tecdo/mac/sql2bot/service/VectorStoreService.java

- [ ] **Step 1: 在 VectorStoreService.java 类上添加 @Deprecated**

在类声明前添加：
/**
 * @deprecated 已废弃，请使用 SchemaVectorStoreService
 */
@Deprecated

- [ ] **Step 2: 修改 VectorRAGService.java 依赖**

将 private final VectorStoreService vectorStoreService; 替换为 private final SchemaVectorStoreService schemaVectorStoreService;

- [ ] **Step 3: 修改 retrieveRelevantSchema 方法**

调用 schemaVectorStoreService.searchSchemas(question, datasourceId, topK) 替换原来的 vectorStoreService.searchModels()
从 SchemaSearchResult 中提取 model 信息，构建 RetrievalResult：
- 从 SchemaMeta 中获取 modelId，通过 modelService.getById(modelId) 加载完整 Model
- 从 SchemaMeta.columns 中构建 ColumnDefinition 列表（不需要再调用 vectorStoreService.searchColumns）

- [ ] **Step 4: 修改 indexAllModelsAndColumns 方法**

调用 schemaIndexService.fullIndex() 替换原来的逻辑

- [ ] **Step 5: 修改 indexModel 方法**

调用 schemaIndexService 的相关方法

- [ ] **Step 6: 编译验证**

mvn compile -q
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

git add src/main/java/com/tecdo/mac/sql2bot/service/VectorRAGService.java
git add src/main/java/com/tecdo/mac/sql2bot/service/VectorStoreService.java
git commit -m "refactor(service): VectorRAGService 改为依赖 SchemaVectorStoreService"

---

## Task 7: TextToSQLService 集成骨架检索

**Files:**
- Modify: src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java

目标：在第 121 行 if (sql == null) 块内，第 129 行 semanticContextService.generateSystemPrompt() 调用之前，插入骨架检索逻辑。

- [ ] **Step 1: 在 TextToSQLService 中注入 SchemaVectorStoreService**

在类的依赖注入字段中添加：
private final SchemaVectorStoreService schemaVectorStoreService;

- [ ] **Step 2: 在 if (sql == null) 块内插入骨架检索逻辑**

在第 128 行（String systemPrompt = semanticContextService.generateSystemPrompt(...) 之前）插入：

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

- [ ] **Step 3: 修改 generateSystemPrompt 调用**

如果 schemaContext 不为 null，使用新的 prompt 构建方式（意图 JSON + 表结构），否则降级到原有的 semanticContextService.generateSystemPrompt()：

String systemPrompt;
if (schemaContext != null && intentResponse != null) {
    systemPrompt = buildSchemaBasedPrompt(intentResponse, schemaContext);
} else {
    systemPrompt = semanticContextService.generateSystemPrompt(datasourceId, request.getQuestion());
}

- [ ] **Step 4: 添加辅助方法 buildSchemaContext**

private String buildSchemaContext(List<SchemaVectorStoreService.SchemaSearchResult> results) {
    StringBuilder sb = new StringBuilder();
    for (SchemaVectorStoreService.SchemaSearchResult result : results) {
        SchemaVectorStoreService.SchemaMeta meta = result.getMeta();
        sb.append("表名: ").append(meta.getTableName());
        if (meta.getDisplayName() != null) sb.append("（").append(meta.getDisplayName()).append("）");
        sb.append("
");
        if (meta.getDatasourceName() != null) sb.append("数据源: ").append(meta.getDatasourceName()).append("
");
        sb.append("字段:
");
        if (meta.getColumns() != null) {
            for (SchemaVectorStoreService.SchemaMeta.ColumnInfo col : meta.getColumns()) {
                sb.append("- ").append(col.getColumnName());
                if (col.getColumnType() != null) sb.append(" (").append(col.getColumnType()).append(")");
                if (col.getDisplayName() != null) sb.append(" - ").append(col.getDisplayName());
                sb.append("
");
            }
        }
        sb.append("
");
    }
    return sb.toString();
}

- [ ] **Step 5: 添加辅助方法 buildSchemaBasedPrompt**

private String buildSchemaBasedPrompt(IntentAnalysisResponse intentResponse, String schemaContext) {
    String intentJson;
    try {
        intentJson = objectMapper.writeValueAsString(intentResponse);
    } catch (Exception e) {
        intentJson = intentResponse.toString();
    }
    return "你是一个 SQL 生成专家。根据以下信息生成 SQL 骨架和参数定义：

" +
           "## 用户意图
" + intentJson + "

" +
           "## 相关表结构
" + schemaContext +
           "## 要求
" +
           "1. 生成标准 MySQL SQL，所有参数使用 {{paramName}} 占位符
" +
           "2. 同时输出参数定义列表，说明每个参数对应意图 JSON 的哪个字段和类型
" +
           "3. 使用以下 JSON 格式输出，用  代码块包裹：

" +
           "{
" +
           "  "sql_template": "SELECT ... WHERE advertiser_id = {{advertiserId}}",
" +
           "  "parameters": [
" +
           "    { "name": "advertiserId", "source": "entity.dimensionFilter.conditions[0].value", "type": "NUMBER" }
" +
           "  ]
" +
           "}";
}

- [ ] **Step 6: 修改 LLM 响应解析**

在 sql = aiService.extractSQL(aiResponse) 之后，尝试解析 JSON 格式的响应：

// 尝试解析 schema-based LLM 响应（含 sql_template + parameters）
if (schemaContext != null && sql != null) {
    try {
        String jsonBlock = extractJsonBlock(aiResponse);
        if (jsonBlock != null) {
            JsonNode root = objectMapper.readTree(jsonBlock);
            if (root.has("sql_template") && root.has("parameters")) {
                QueryTemplate dynamicTemplate = new QueryTemplate();
                dynamicTemplate.setSqlTemplate(root.get("sql_template").asText());
                dynamicTemplate.setParameters(root.get("parameters").toString());
                if (intentResponse != null) {
                    sql = templateParameterService.fillTemplate(dynamicTemplate, intentResponse);
                    log.info("Schema-based 模板填充成功: {}", sql);
                }
            }
        }
    } catch (Exception e) {
        log.warn("Schema-based 响应解析失败，使用原始 SQL", e);
    }
}

- [ ] **Step 7: 添加辅助方法 extractJsonBlock**

private String extractJsonBlock(String text) {
    int start = text.indexOf("", start);
    if (end == -1) return null;
    return text.substring(start, end).trim();
}

- [ ] **Step 8: 编译验证**

mvn compile -q
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

git add src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java
git commit -m "feat(service): TextToSQLService 集成骨架检索和 Schema-based SQL 生成"

---

## Task 8: 端到端验证

- [ ] **Step 1: 启动应用**

mvn spring-boot:run
Expected: 应用启动成功，日志显示 "RedisStack 索引 idx:schema 已存在" 或 "创建 RedisStack 索引 idx:schema 成功"

- [ ] **Step 2: 手动触发全量索引**

curl -X POST http://localhost:8082/api/scheduler/schema-index/full
Expected: {"code":200,"message":"全量索引完成"}

- [ ] **Step 3: 查看索引状态**

curl http://localhost:8082/api/scheduler/schema-index/status
Expected: indexCount > 0

- [ ] **Step 4: 测试查询**

curl -X POST http://localhost:8082/api/query -H "Content-Type: application/json" -d '{"question":"帮我看一下广告主5316的总充值金额","userId":1}'
Expected: 返回正确的 SQL 查询结果，不再出现 "未找到与问题相关的表" 错误

- [ ] **Step 5: Commit（如有配置调整）**

git add -A
git commit -m "fix: 端到端验证后的配置调整"

---
