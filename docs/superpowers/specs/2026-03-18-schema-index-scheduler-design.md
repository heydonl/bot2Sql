# Schema 索引定时任务设计文档

## 1. 背景与目标

**背景：** 当前 TextToSQLService 在生成 SQL 时，使用关键词 RAG 检索相关表结构。这种方式在意图分析完成后，无法根据骨架字符串精确检索相关表，导致 LLM 生成 SQL 时缺乏准确的表结构上下文。

**目标：** 创建一个定时任务模块，将 model 表和 column_definition 表的数据同步到 RedisStack 向量数据库中。查询时根据意图骨架字符串进行语义检索，将相关表结构信息传给 LLM，由 LLM 生成带 `{{paramName}}` 占位符的 SQL 骨架，再由 TemplateParameterService 填充参数执行。

---

## 2. 整体架构

### 2.1 新增文件

```
src/main/java/com/tecdo/mac/sql2bot/
├── scheduler/
│   ├── SchemaIndexScheduler.java       # 定时任务入口（@Scheduled，每小时）
│   └── SchemaIndexService.java         # 增量索引逻辑
├── service/
│   └── SchemaVectorStoreService.java   # RedisStack 向量存储服务
└── controller/
    └── SchedulerController.java        # 管理接口（启停、手动触发、状态查询）
```

### 2.2 修改文件

```
src/main/java/com/tecdo/mac/sql2bot/
├── service/
│   └── TextToSQLService.java           # 第128行附近集成骨架检索 + LLM 生成 SQL 骨架
└── mapper/
    ├── ModelMapper.java                # 新增 selectUpdatedAfter 方法
    └── ColumnDefinitionMapper.java     # 新增 selectUpdatedAfter 方法
```

### 2.3 数据流总览

```
【定时任务】每小时
model + column_definition → 合并文本 → Embedding → RedisStack HSET

【查询流程】
用户问题 → 意图分析 → 骨架字符串
                              ↓
                    Embedding → RedisStack KNN 搜索
                              ↓
                    top-K 相关表的 meta JSON
                              ↓
                    LLM（意图JSON + 表结构）→ SQL 骨架（{{param}}）
                              ↓
                    TemplateParameterService 填充参数
                              ↓
                    执行 SQL → 返回结果
```

---

## 3. 组件设计

### 3.1 SchemaIndexScheduler（定时任务入口）

**职责：** 每小时触发一次增量索引，支持动态启停，失败时发送告警。

**关键实现：**
```java
@Component
@ConditionalOnProperty(name = "scheduler.schema-index.enabled", havingValue = "true", matchIfMissing = true)
public class SchemaIndexScheduler {

    private volatile boolean running = true;

    @Scheduled(cron = "${scheduler.schema-index.cron:0 0 * * * ?}")
    public void indexSchema() {
        if (!running) return;
        try {
            schemaIndexService.incrementalIndex();
        } catch (Exception e) {
            log.error("Schema 增量索引失败", e);
            alertService.sendAlert("Schema 索引任务失败", e.getMessage());
        }
    }

    public void start() { running = true; }
    public void stop()  { running = false; }
    public boolean isRunning() { return running; }
}
```

### 3.2 SchemaIndexService（增量索引逻辑）

**职责：** 查询上次执行时间之后更新的 model 和 column_definition，合并成完整文本描述，生成 embedding 并存储到 RedisStack。

**增量更新逻辑：**
- 上次执行时间存储在 Redis 中，key：`schema:last_index_time`
- 查询 `updated_at > lastIndexTime` 的 model 记录
- 每个 model 查出其所有 column_definition，合并成一条索引
- 执行完成后更新 `schema:last_index_time`

**合并文本格式：**
```
表名: work_order_task, 显示名: 工单任务表, 描述: 存储广告账户相关的工单信息,
字段: id(主键ID), advertiser_id(广告主ID), task_type(工单类型), amount(金额), created_at(创建时间)
```

**全量索引：** 提供 `fullIndex()` 方法，清空现有索引后重新索引所有表，供手动触发使用。

### 3.3 SchemaVectorStoreService（RedisStack 向量存储）

**职责：** 管理 RedisStack 向量索引的创建、存储和搜索。

**RedisStack 索引结构：**
```bash
FT.CREATE idx:schema ON HASH PREFIX 1 "schema:"
SCHEMA
  table_name TEXT
  datasource_id NUMERIC
  vector VECTOR HNSW 6 TYPE FLOAT32 DIM 1536 DISTANCE_METRIC COSINE
```

**Hash 存储结构（key: `schema:{modelId}`）：**
```
table_name    → "work_order_task"
datasource_id → 11
vector        → <float[1536] 二进制>
meta          → { 完整表结构 JSON }
```

**meta JSON 结构：**
```json
{
  "modelId": 123,
  "tableName": "work_order_task",
  "displayName": "工单任务表",
  "description": "存储广告账户相关的工单信息",
  "datasourceId": 11,
  "datasourceName": "uni-agency-db",
  "columns": [
    { "columnName": "advertiser_id", "displayName": "广告主ID", "columnType": "BIGINT" },
    { "columnName": "task_type",     "displayName": "工单类型", "columnType": "INT" },
    { "columnName": "amount",        "displayName": "金额",     "columnType": "DECIMAL" }
  ]
}
```

**KNN 搜索：**
```bash
FT.SEARCH idx:schema
  "@datasource_id:[11 11]=>[KNN 10 @vector $BLOB AS score]"
  PARAMS 2 BLOB <queryVector>
  SORTBY score
  LIMIT 0 10
  DIALECT 2
```

### 3.4 TextToSQLService 集成（第128行附近）

**在意图分析生成骨架字符串之后，插入以下逻辑：**

```java
// 1. 将骨架字符串转成 embedding
// 2. 在 RedisStack 中 KNN 搜索相关表（top 10，按 datasourceId 过滤）
// 3. 获取 top-K 表的 meta JSON（表名、字段、数据源）
// 4. 构建 LLM Prompt（意图 JSON + 表结构）
// 5. 调用 LLM 生成带 {{paramName}} 占位符的 SQL 骨架
// 6. 调用 TemplateParameterService.fillTemplate() 填充参数
// 7. 执行 SQL
```

**LLM Prompt 格式：**
```
你是一个 SQL 生成专家。根据以下信息生成 SQL 骨架：

## 用户意图
{intentJson}

## 相关表结构
表名: work_order_task（工单任务表）
数据源: uni-agency-db
字段:
- advertiser_id (BIGINT) - 广告主ID
- task_type (INT) - 工单类型
- amount (DECIMAL) - 金额
- created_at (DATETIME) - 创建时间

## 要求
1. 生成标准 MySQL SQL
2. 所有参数使用 {{paramName}} 占位符
3. 只输出 SQL，不要解释
4. 使用 ```sql ``` 代码块包裹
```

### 3.5 SchedulerController（管理接口）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/scheduler/schema-index/full` | 手动触发全量索引 |
| POST | `/api/scheduler/schema-index/incremental` | 手动触发增量索引 |
| POST | `/api/scheduler/schema-index/start` | 启动定时任务 |
| POST | `/api/scheduler/schema-index/stop` | 停止定时任务 |
| GET  | `/api/scheduler/schema-index/status` | 查看状态（上次执行时间、索引数量、是否运行中） |

---

## 4. 错误处理

| 场景 | 处理方式 |
|------|------|
| 数据库连接失败 | 记录错误日志 + 发送告警，跳过本次执行 |
| Redis 不可用 | 记录错误日志 + 发送告警，跳过本次执行 |
| Embedding 生成失败 | 记录错误日志，跳过该条记录，继续处理其他记录 |
| LLM 生成 SQL 失败 | 降级到原有的 RAG 检索流程 |
| 参数填充失败 | 降级到原有的 LLM 直接生成流程 |

### AlertService（告警通知）

```java
@Component
public class AlertService {
    // 支持钉钉 / 企业微信 / 邮件，通过配置选择
    public void sendAlert(String title, String message);
}
```

---

## 5. 配置项

```properties
# 定时任务开关（默认开启）
scheduler.schema-index.enabled=true

# 执行频率（默认每小时）
scheduler.schema-index.cron=0 0 * * * ?

# 告警通知方式（dingtalk / wechat / email）
scheduler.alert.type=dingtalk
scheduler.alert.webhook=https://oapi.dingtalk.com/robot/send?access_token=xxx
```

---

## 6. Mapper 新增方法

### ModelMapper
```java
// 查询 updated_at 大于指定时间的 model
List<Model> selectUpdatedAfter(@Param("updatedAfter") LocalDateTime updatedAfter);
```

### ColumnDefinitionMapper
```java
// 查询 updated_at 大于指定时间的 column_definition
List<ColumnDefinition> selectUpdatedAfter(@Param("updatedAfter") LocalDateTime updatedAfter);
```
