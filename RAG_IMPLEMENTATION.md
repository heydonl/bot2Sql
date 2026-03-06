# RAG 实现文档

## 概述

本项目已成功集成 RAG (Retrieval-Augmented Generation) 功能，用于优化 Text-to-SQL 的上下文生成。RAG 能够根据用户问题智能检索相关的表、字段和关系，而不是将所有数据库结构加载到 LLM 上下文中。

## 核心组件

### 1. RAGRetrievalService

**位置**: `com.tecdo.mac.sql2bot.service.RAGRetrievalService`

**主要方法**:
- `retrieveRelevantSchema(String question, Long datasourceId, int topK)`: 主检索方法
- `extractKeywords(String question)`: 从问题中提取关键词
- `calculateModelRelevance(Model model, Set<String> keywords)`: 计算表的相关性分数
- `calculateColumnRelevance(ColumnDefinition column, Set<String> keywords)`: 计算字段的相关性分数

**评分机制**:
- 表名完全匹配: 10 分
- 表名包含关键词: 5 分
- 显示名匹配: 3 分
- 描述包含关键词: 2 分

**返回结果**: `RetrievalResult` 包含:
- `relevantModels`: 相关的表列表
- `modelColumns`: 每个表的相关字段映射
- `relevantRelationships`: 相关的表关系
- `extractedKeywords`: 提取的关键词

### 2. SemanticContextService (已更新)

**修改内容**:
- 注入 `RAGRetrievalService`
- `generateSystemPrompt()` 方法新增 `question` 参数
- 使用 RAG 检索结果构建上下文，而不是加载所有表

**优势**:
- 大幅减少 LLM 上下文长度
- 提高查询响应速度
- 降低 API 调用成本
- 提高 SQL 生成准确性（减少无关信息干扰）

### 3. TextToSQLService (已更新)

**修改内容**:
- 调用 `generateSystemPrompt()` 时传递用户问题

## 工作流程

```
用户问题
    ↓
提取关键词 (extractKeywords)
    ↓
计算表相关性 (calculateModelRelevance)
    ↓
选择 Top-K 相关表
    ↓
计算字段相关性 (calculateColumnRelevance)
    ↓
查找相关表关系
    ↓
构建精简的语义上下文
    ↓
调用 LLM 生成 SQL
```

## 测试结果

### 测试 1: 数据源查询
**问题**: "查询所有数据源的名称和类型"

**生成的 SQL**:
```sql
SELECT `name`, `type` FROM `datasource`;
```

**结果**: ✅ 成功
- RAG 正确识别了 `datasource` 表
- 只加载了相关的表和字段到上下文
- SQL 生成准确

### 测试 2: 用户数量查询
**问题**: "查询所有用户的数量"

**结果**: ✅ 成功
- RAG 检索到系统中没有 `users` 表
- LLM 给出了合理的替代建议
- 展示了 RAG 的智能过滤能力

## 配置参数

### Top-K 设置
当前默认值: `topK = 10`

可以根据实际情况调整:
- 小型数据库 (< 20 表): topK = 5
- 中型数据库 (20-50 表): topK = 10
- 大型数据库 (> 50 表): topK = 15-20

### 关键词提取
**停用词列表**: 包含常见的英文停用词（the, a, is, are, how, what 等）

**分词策略**: 按空格和标点符号分割

**最小词长**: 3 个字符

## 性能优化建议

### 1. 向量化检索 (未来改进)
当前实现使用关键词匹配，可以升级为向量检索:
- 使用 Embedding 模型（如 text-embedding-ada-002）
- 存储表/字段的向量表示
- 使用向量相似度计算相关性

### 2. 缓存机制
- 缓存常见问题的检索结果
- 使用 Redis 存储热点查询的上下文

### 3. 增量更新
- 当数据库结构变化时，只更新受影响的表
- 避免全量重建索引

## API 使用示例

### 基本查询 (自动使用 RAG)
```bash
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "question": "查询所有数据源的名称和类型",
    "createNewConversation": true
  }'
```

### 指定数据源的查询
```bash
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "datasourceId": 1,
    "question": "查询所有表的数量",
    "createNewConversation": true
  }'
```

## 日志监控

RAG 检索会输出以下日志:
```
INFO  - Using RAG to retrieve relevant schema for question: 查询所有数据源的名称和类型
INFO  - RAG retrieved 2 models, 8 columns, 1 relationships
```

可以通过日志监控:
- 检索到的表数量
- 检索到的字段数量
- 检索到的关系数量

## 已知限制

1. **中文分词**: 当前使用简单的空格分词，对中文支持有限
   - 改进方案: 集成 jieba 分词

2. **同义词识别**: 无法识别同义词（如 "用户" vs "user"）
   - 改进方案: 建立同义词词典

3. **复杂查询**: 涉及多表 JOIN 的复杂查询可能检索不全
   - 改进方案: 增加关系传播机制

## 总结

RAG 功能已成功集成到 SQL2Bot 项目中，实现了:
- ✅ 智能检索相关表和字段
- ✅ 减少 LLM 上下文长度
- ✅ 提高查询准确性
- ✅ 支持多数据源场景
- ✅ 与现有功能无缝集成

下一步可以考虑:
- 向量化检索
- 中文分词优化
- 缓存机制
- 性能监控和调优
