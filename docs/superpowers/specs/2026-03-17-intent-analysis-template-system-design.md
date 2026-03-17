# 意图分析与模板系统设计文档

**日期：** 2026-03-17
**状态：** 已批准

---

## 1. 背景与目标

在现有的 `TextToSQLService.processQuery()` 流程中，每次查询都需要调用 LLM 生成 SQL，成本高且响应慢。

本设计在现有流程中引入**意图分析 + 模板缓存**机制：
- 先分析用户意图，生成结构化的骨架
- 用骨架从 RAG 中检索已有模板
- 模板命中则直接填参执行，未命中则调用 LLM 生成并保存模板
- 用户对结果评分，评分越高的模板越容易被检索到

---

## 2. 整体架构

### 核心流程

```
TextToSQLService.processQuery()
    ↓
1. 意图分析（IntentAnalysisService）
   - 动态加载 few-shot 示例
   - 调用 LLM 分析用户问题
   - 生成意图 JSON 和骨架字符串
    ↓
2. 两层模板检索（QueryTemplateService）
   - 第一层：精确匹配（Redis String，骨架 key）→ 按评分排序
   - 第二层：向量相似度搜索（RedisStack）→ 按相似度×评分排序
    ↓
3. 模板验证（QueryTemplateService）
   - 字段级匹配：检查 dimensions、metrics、filters 是否在模板支持列表中
   - 验证失败则尝试下一个候选模板
    ↓
4. 分支处理
   ├─ 找到有效模板
   │   ↓
   │  参数提取和填充（TemplateParameterService）
   │   - 从意图 JSON 提取参数
   │   - 智能转换（日期格式、数组转 IN 子句等）
   │   - 填充到 SQL 模板
   │   ↓
   │  执行 SQL（失败则尝试下一个候选模板）
   │
   └─ 未找到或全部验证失败
       ↓
      根据意图 JSON 的 entity 和 dimensions 从 RAG 获取相关表
       ↓
      SemanticContextService 生成这些表的完整上下文
       ↓
      LLM 生成 SQL
       ↓
      保存为新模板（直接可用，无需审核，初始评分=0）
       ↓
      执行 SQL
    ↓
5. 记录查询日志（QueryLogService）
    ↓
6. 返回结果（包含 queryLogId，用于后续评分）
    ↓
7. 用户评分 → 更新模板评分 + 查询日志
```

---

## 3. 数据模型

### 3.1 MySQL 表

#### query_template（SQL 查询模板）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| skeleton | VARCHAR(500) | 骨架字符串（精确匹配 key，唯一索引） |
| sql_template | TEXT | SQL 模板（带占位符） |
| entity | VARCHAR(100) | 实体类型（如 work_order） |
| intent | VARCHAR(50) | 意图类型 |
| supported_dimensions | JSON | 支持的维度字段列表 |
| supported_metrics | JSON | 支持的指标列表 |
| parameters | JSON | 参数定义列表 |
| example_question | VARCHAR(500) | 示例问题 |
| example_intent_json | TEXT | 示例意图 JSON |
| score | DECIMAL(3,2) | 平均评分（0-5） |
| rating_count | INT | 评分次数 |
| usage_count | INT | 使用次数 |
| datasource_id | BIGINT | 关联数据源 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

> 注意：模板无需审核，生成后直接可用。

#### template_rating（模板评分记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| template_id | BIGINT | 模板 ID |
| user_id | BIGINT | 用户 ID |
| score | INT | 评分（1-5） |
| conversation_id | BIGINT | 关联会话 ID |
| created_at | DATETIME | 创建时间 |

> 唯一约束：`(template_id, user_id)`，同一用户对同一模板重复评分时更新评分。
> 评分更新使用数据库原子操作：`UPDATE SET score = (score * rating_count + new_score) / (rating_count + 1), rating_count = rating_count + 1`

#### intent_few_shot（意图分类 Few-shot 示例）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| intent | VARCHAR(50) | 目标意图类型 |
| question | VARCHAR(500) | 示例问题 |
| intent_json | TEXT | 正确的意图 JSON |
| skeleton | VARCHAR(1000) | 骨架字符串 |
| is_active | BOOLEAN | 是否启用 |
| datasource_id | BIGINT | 关联数据源（按数据源隔离 few-shot） |
| created_by | BIGINT | 创建人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

> 用于优化意图分类，所有意图类型都可以添加 few-shot，不限于 OTHER。

#### query_log（查询日志）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID |
| conversation_id | BIGINT | 会话 ID |
| question | VARCHAR(500) | 用户问题 |
| intent | VARCHAR(50) | 识别的意图 |
| intent_json | TEXT | 意图 JSON |
| skeleton | VARCHAR(1000) | 骨架字符串 |
| template_id | BIGINT | 使用的模板 ID |
| is_from_template | BOOLEAN | 是否使用模板 |
| generated_sql | TEXT | 生成的 SQL |
| execution_success | BOOLEAN | 执行是否成功 |
| execution_time | BIGINT | 执行时间（毫秒） |
| result_count | INT | 结果行数 |
| rating | INT | 用户评分（1-5） |
| is_labeled | BOOLEAN | 是否已标注为 few-shot |
| datasource_id | BIGINT | 数据源 ID |
| created_at | DATETIME | 创建时间 |

索引：`idx_user_id(user_id)`、`idx_template_id(template_id)`、`idx_created_at(created_at)`、`idx_intent(intent)`

### 3.2 RedisStack 向量存储

**索引：** `query_template_idx`

```
Schema:
- template_id (NUMERIC, SORTABLE)
- skeleton (TEXT)
- skeleton_vector (VECTOR, HNSW, DIM=1024, DISTANCE_METRIC=COSINE)
  注：维度以 application.properties 中 embedding.dimension 配置为准，当前为 1024
- entity (TAG)
- score (NUMERIC, SORTABLE)
```

**存储格式：**
```
Key: template:vector:{templateId}
Value (Hash):
{
  "template_id": "1",
  "skeleton": "INTENT=SINGLE_METRIC_QUERY | ...",
  "skeleton_vector": <binary vector>,
  "entity": "work_order",
  "score": "4.5"
}
```

**精确匹配缓存：**
```
Key: template:exact:{skeleton}
Value: templateId
TTL: 1 hour
```

---

## 4. 服务层设计

### 4.1 IntentAnalysisService（意图分析）

**职责：**
- 动态加载 few-shot 示例构建 prompt
- 调用 LLM 分析用户问题
- 解析 JSON，生成骨架字符串
- 检测到 OTHER 意图时记录日志
- JSON 解析失败时降级：构造 `intent=OTHER` 的响应返回（不抛出异常），继续走 LLM 生成流程

**关键方法：**
- `analyzeIntent(IntentAnalysisRequest)` → `IntentAnalysisResponse`
- `convertToSkeleton(IntentAnalysisResponse)` → `String`
- `buildIntentAnalysisPrompt(IntentAnalysisRequest request, List<IntentFewShot> fewShots)` → `String`

**Few-shot 注入格式（追加在 `### 现在，请分析以下用户问题` 之前）：**
```
#### 示例：{intent}
用户：{question}
输出：
{intent_json}
```

### 4.2 QueryTemplateService（模板管理）

**职责：**
- 两层模板检索（精确匹配 + 向量相似度）
- 字段级模板验证
- 模板保存（直接可用，无需审核）
- 评分更新（同步 MySQL + RedisStack）

**关键方法：**
- `findTemplate(skeleton, skeletonVector)` → `QueryTemplate`
- `validateTemplate(template, intent)` → `boolean`
- `saveGeneratedTemplate(...)` → `QueryTemplate`
- `updateRating(templateId, score)` → `void`

**两层检索策略：**
1. 精确匹配：Redis String → MySQL（按评分排序）
2. 向量搜索：RedisStack KNN → 按 `相似度 × 评分` 排序
3. 多个候选时：返回评分最高的，执行失败则自动尝试下一个

### 4.3 TemplateParameterService（参数填充）

**职责：**
- 从意图 JSON 提取参数值
- 智能类型转换和格式化
- 填充 SQL 模板占位符

**支持的参数类型：**
- `DATE`：日期格式转换
- `ARRAY_TO_IN`：数组转 IN 子句
- `DIMENSION`：维度字段名
- `METRIC`：指标字段名
- `STRING`：字符串（加引号）
- `NUMBER`：数字

### 4.4 QueryLogService（查询日志）

**职责：**
- 记录所有查询（成功和失败）
- 更新评分
- 标注 few-shot
- 统计分析

### 4.5 IntentFewShotService（Few-shot 管理）

**职责：**
- 管理 few-shot 示例（增删改查）
- 从查询日志创建 few-shot（支持修正意图）
- 批量标注

---

## 5. API 接口

### 5.1 QueryController（`/api/query`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/query` | 自然语言查询（集成意图分析和模板系统） |
| POST | `/api/query/rate` | 对查询结果评分 |
| POST | `/api/query/analyze-intent` | 意图分析（调试用） |

> 注：现有的 `/api/query/intent-to-skeleton` 端点由 `/api/query/analyze-intent` 覆盖，实现时删除。

### 5.2 TemplateController（`/api/templates`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/templates` | 查询模板列表（支持按 intent、entity 筛选） |
| GET | `/api/templates/{id}` | 获取模板详情 |
| DELETE | `/api/templates/{id}` | 删除模板 |
| GET | `/api/templates/stats` | 模板统计信息 |

### 5.3 QueryLogController（`/api/query-logs`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/query-logs` | 查询日志列表（支持多种筛选条件） |
| GET | `/api/query-logs/{id}` | 获取日志详情 |
| POST | `/api/query-logs/{id}/label-as-few-shot` | 标注为 few-shot |
| POST | `/api/query-logs/batch-label` | 批量标注 |
| GET | `/api/query-logs/stats` | 查询统计 |
| GET | `/api/query-logs/intent-distribution` | 意图分布统计 |

### 5.4 FewShotController（`/api/few-shots`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/few-shots` | 获取 few-shot 列表 |
| POST | `/api/few-shots` | 添加 few-shot 示例 |
| PUT | `/api/few-shots/{id}` | 更新 few-shot |
| DELETE | `/api/few-shots/{id}` | 删除 few-shot |
| POST | `/api/few-shots/{id}/toggle` | 启用/禁用 few-shot |

---

## 6. 错误处理策略

| 场景 | 处理方式 |
|------|------|
| 意图分析 JSON 解析失败 | 降级：返回 OTHER 意图，走 LLM 生成流程 |
| 所有候选模板验证失败 | 降级：走 LLM 生成流程 |
| 参数填充失败 | 降级：走 LLM 生成流程，标记模板有问题 |
| 模板 SQL 执行失败 | 自动尝试下一个候选模板，全部失败则报错 |
| RedisStack 不可用 | 降级：只使用精确匹配（MySQL） |
| 复杂 filter 条件 | 降级：走 LLM 生成流程 |
| 重复评分 | 更新评分（不拒绝） |
| 评分超出范围（1-5） | 抛出参数异常 |

---

## 7. 新增文件清单

### 实体类
- `domain/QueryTemplate.java`
- `domain/TemplateRating.java`
- `domain/IntentFewShot.java`
- `domain/QueryLog.java`

### DTO 类
- `dto/intent/QueryIntent.java`（已存在）
- `dto/intent/IntentAnalysisRequest.java`（已存在）
- `dto/intent/IntentAnalysisResponse.java`（已存在，需调整）
- `dto/RateQueryRequest.java`
- `dto/LabelFewShotRequest.java`
- `dto/BatchLabelRequest.java`
- `dto/QueryLogFilter.java`
- `dto/QueryLogStats.java`
- `dto/TemplateStats.java`
- `dto/TemplateParameter.java`

### Service 类
- `service/IntentAnalysisService.java`（已存在，需调整）
- `service/QueryTemplateService.java`（新增）
- `service/TemplateParameterService.java`（新增）
- `service/QueryLogService.java`（新增）
- `service/IntentFewShotService.java`（新增）

### Controller 类
- `controller/QueryController.java`（已存在，需调整）
- `controller/TemplateController.java`（新增）
- `controller/QueryLogController.java`（新增）
- `controller/FewShotController.java`（新增）

### Mapper 类
- `mapper/QueryTemplateMapper.java`（新增）
- `mapper/TemplateRatingMapper.java`（新增）
- `mapper/IntentFewShotMapper.java`（新增）
- `mapper/QueryLogMapper.java`（新增）

### MyBatis XML
- `resources/mapper/QueryTemplateMapper.xml`（新增）
- `resources/mapper/TemplateRatingMapper.xml`（新增）
- `resources/mapper/IntentFewShotMapper.xml`（新增）
- `resources/mapper/QueryLogMapper.xml`（新增）

### SQL Schema
- `resources/schema.sql`（新增表）
- `resources/schema-h2.sql`（新增表）

---

## 8. 修改文件清单

- `service/TextToSQLService.java` - 集成意图分析和模板系统
- `service/SemanticContextService.java` - 新增 `generateSystemPromptForTables()` 方法
- `dto/QueryResponse.java` - 新增 `queryLogId`、`templateId`、`isFromTemplate`、`skeleton` 字段；`QueryResponse.success()` 工厂方法保持兼容，新字段通过 setter 在 `TextToSQLService` 的模板命中/未命中分支中分别填充
- `controller/QueryController.java` - 新增评分和意图分析端点，删除 `/intent-to-skeleton` 端点
