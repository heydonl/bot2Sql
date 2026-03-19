# LLM 批量生成表描述 Design

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 LLM 批量为 model 表中所有表自动生成中文业务描述，写回数据库，并触发全量重新索引，提升 KNN 向量搜索命中率。

**Architecture:** 新增 `DescriptionGeneratorService`，通过固定线程池（5 并发，Service 级别单例）异步处理所有 model，每张表调用 AIService 生成描述后写回 DB，全部完成后在同一异步线程中调用 `SchemaIndexService.fullIndex()`。进度存 Redis，通过新增 API 端点查询。

**Tech Stack:** Spring Boot 4.0.3, Java 17, MyBatis, Redis, AIService (Claude API), ExecutorService

---

## 设计决策

### 触发方式
手动触发：`POST /api/scheduler/schema-index/generate-descriptions`，立即返回，后台异步执行。

**互斥控制**：用 `JedisPooled.set("schema:desc_gen:running", "true", SetParams.setParams().nx().ex(7200))` 原子操作作为互斥锁。若返回 null（key 已存在），说明已在运行，返回 400。

**触发流程**：
1. `SET schema:desc_gen:running "true" NX EX 7200`（原子操作，失败则返回 400）
2. 查 `modelMapper.selectAll()`，获取 total
3. 重置 Redis（使用 `JedisPooled.set(key, "0", SetParams.setParams().ex(86400))`）：
   - `set("schema:desc_gen:total", String.valueOf(total), SetParams.setParams().ex(86400))`
   - `set("schema:desc_gen:done", "0", SetParams.setParams().ex(86400))`
   - `set("schema:desc_gen:failed", "0", SetParams.setParams().ex(86400))`
4. 返回 `{ "status": "started", "total": total }`
5. 后台异步启动任务

若 `total == 0`，执行步骤 3 后直接 `DEL schema:desc_gen:running`，返回 `{ "status": "started", "total": 0 }`，不启动任务。

**任务完成/失败时**：finally 块执行 `JedisPooled.del("schema:desc_gen:running")`，确保下次可以重新触发。

**已知限制**：`selectAll()` 在触发时获取 total，任务执行期间新插入的表不会被处理，total 不会更新。应用在任务执行中途强制关闭时，`running` key 依赖 TTL=2h 自动过期兜底。

### 处理范围
全部 model 表（不管是否已有描述，全部覆盖重新生成）。

### 并发控制
线程池为 Service 级别单例（`@PostConstruct` 初始化为 `Executors.newFixedThreadPool(5)`，`@PreDestroy` 调用 `executor.shutdown()`——停止接受新任务，已提交任务继续执行直到完成，finally 块仍会执行）。每个线程独立调用 `modelMapper.update()`，Spring 管理的 Mapper bean 线程安全，每次 update 独立事务，无需加锁。

### 进度追踪
Redis keys（均使用 `JedisPooled` 操作）：
- `schema:desc_gen:running` — 互斥锁（TTL=2h，任务完成后 DEL）
- `schema:desc_gen:total` — 总数（TTL=24h，任务开始时重置）
- `schema:desc_gen:done` — 已处理数（TTL=24h，任务开始时重置为 0，用 `JedisPooled.incr()` 原子递增）
- `schema:desc_gen:failed` — 失败数（TTL=24h，任务开始时重置为 0，用 `JedisPooled.incr()` 原子递增）

**`done` 语义**：`done` = 已处理总数，包含以下所有情况：
- 成功生成并写回 DB：`done` +1
- 生成失败（Claude API 异常、返回空）：`done` +1，`failed` +1
- `selectById` 返回 null（表已被删除）：`done` +1（不计 failed）

任务完成条件：`done == total`，此时 `percent == 100`。

**已知限制**：`INCR` 不刷新 TTL，若任务运行超过 24h（490 张表约 16 分钟，不会触发），key 会过期。`percent` 计算 cap 到 100：`total == 0 ? 0 : Math.min(100, (int)(done * 100 / total))`。

**状态查询在任务从未运行时**：所有 key 不存在，返回默认值 `{ running: false, total: 0, done: 0, failed: 0, percent: 0 }`。

### fullIndex 调用时机
所有 model 处理完毕后（`done == total`），在同一个异步任务线程中直接调用 `schemaIndexService.fullIndex()`。**无论成功失败（包括全部失败的情况）均触发 `fullIndex()`**。`fullIndex()` 异常时记录 error 日志，不影响 finally 块将 `running` DEL。

### model.description 写回方式
先调用 `modelMapper.selectById(model.getId())` 获取完整对象：
- 若返回 null（表已被删除）：记录 warn 日志，`done` +1，跳过（不计 failed）
- 若正常：`setDescription(generatedDesc)`，再调用 `modelMapper.update(model)`

### AIService 返回值处理
调用 `aiService.generateSQL(systemPrompt, userPrompt)` 后：
- 若返回 null 或空字符串（trim 后为空）：记录 error 日志，`done` +1，`failed` +1，跳过写回
- 若正常：写回 DB

### ColumnDefinition 为空时的处理
若 `columnDefinitionMapper.selectByModelId()` 返回空列表，`userPrompt` 中字段部分写为 `"（无字段信息）"`，继续调用 LLM 生成描述，不跳过。

### Prompt 设计
调用方式：`aiService.generateSQL(systemPrompt, userPrompt)`

字段列表最多取前 50 个（超过 50 个时在 prompt 中注明"（仅展示前 50 个字段）"），防止 token 超限。

```
systemPrompt:
你是一个数据库业务分析专家。根据以下表结构信息，生成一段简洁的中文业务描述（50-100字），
描述该表的业务用途、存储的核心数据和主要使用场景。只输出描述文字，不要加任何前缀或解释。

userPrompt:
表名: {model.tableName}
字段: {col.columnName}({col.columnType}), ...   // 若无字段则写"（无字段信息）"，超过50个字段时追加"（仅展示前50个字段）"
```

字段来源：`ColumnDefinition.columnName` 和 `ColumnDefinition.columnType`。

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `service/DescriptionGeneratorService.java` | 新建 | 异步批量生成描述，进度管理 |
| `controller/SchedulerController.java` | 修改（已存在） | 新增两个端点 |

不新增 Mapper，复用 `ModelMapper.selectAll()`、`ModelMapper.selectById()`、`ModelMapper.update()` 和 `ColumnDefinitionMapper.selectByModelId()`。

---

## API

### 触发生成
```
POST /api/scheduler/schema-index/generate-descriptions
Response: { "code": 200, "data": { "status": "started", "total": 490 }, "message": "success" }
```
若已在运行中，返回：
```json
{ "code": 400, "message": "描述生成任务正在运行中，请稍后再试" }
```

### 查询进度
```
GET /api/scheduler/schema-index/generate-descriptions/status
Response:
{
  "code": 200,
  "data": {
    "running": false,
    "total": 490,
    "done": 490,
    "failed": 2,
    "percent": 100
  }
}
```
`percent` 计算：`total == 0 ? 0 : Math.min(100, (int)(done * 100 / total))`

---

## 错误处理汇总

| 情况 | done | failed | 行为 |
|------|------|--------|------|
| 成功生成并写回 | +1 | 不变 | 正常 |
| Claude API 异常 | +1 | +1 | error 日志，跳过写回 |
| 返回 null 或空字符串 | +1 | +1 | error 日志，跳过写回 |
| `selectById` 返回 null | +1 | 不变 | warn 日志，跳过 |
| `modelMapper.update()` 异常 | +1 | +1 | error 日志，跳过 |
| `fullIndex()` 异常 | — | — | error 日志，不影响流程 |
