# LLM 批量生成表描述 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 LLM 批量为 model 表中所有表自动生成中文业务描述，写回数据库，并触发全量重新索引，提升 KNN 向量搜索命中率。

**Architecture:** 新增 `DescriptionGeneratorService`，通过固定线程池（5 并发）异步处理所有 model，每张表调用 AIService 生成描述后写回 DB，全部完成后自动触发 `SchemaIndexService.fullIndex()`。进度存 Redis，通过新增 API 端点查询。

**Tech Stack:** Spring Boot 4.0.3, Java 17, MyBatis, Redis, AIService (Claude API), ExecutorService

---

## 设计决策

### 触发方式
手动触发：`POST /api/scheduler/schema-index/generate-descriptions`，立即返回，后台异步执行。

### 处理范围
全部 model 表（不管是否已有描述，全部覆盖重新生成）。

### 并发控制
固定线程池 5 个线程，防止 Claude API 过载。

### 进度追踪
Redis keys：
- `schema:desc_gen:total` — 总数
- `schema:desc_gen:done` — 已完成数
- `schema:desc_gen:failed` — 失败数
- `schema:desc_gen:running` — 是否运行中（"true"/"false"）

### Prompt 设计
```
你是一个数据库业务分析专家。根据以下表结构信息，生成一段简洁的中文业务描述（50-100字），
描述该表的业务用途、存储的核心数据和主要使用场景。只输出描述文字，不要加任何前缀或解释。

表名: {tableName}
字段: {col1}({type1}), {col2}({type2}), ...
```

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `service/DescriptionGeneratorService.java` | 新建 | 异步批量生成描述，进度管理 |
| `controller/SchedulerController.java` | 修改 | 新增两个端点 |

不新增 Mapper，复用 `ModelMapper.update()` 和 `ColumnDefinitionMapper.selectByModelId()`。

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
    "running": true,
    "total": 490,
    "done": 123,
    "failed": 2,
    "percent": 25
  }
}
```
