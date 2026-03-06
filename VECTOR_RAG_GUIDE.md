# 向量 RAG 实现指南

## 概述

已将 SQL2Bot 的 RAG 功能从关键词匹配升级为基于 **Embedding 向量** 和 **Redis 向量数据库** 的语义检索系统。

## 核心改进

### 1. 从关键词匹配到向量检索

**旧方案（关键词匹配）**:
- 简单的字符串匹配
- 无法理解语义
- 对中文支持有限
- 无法识别同义词

**新方案（向量检索）**:
- 使用 Embedding 模型生成语义向量
- 基于余弦相似度的语义检索
- 支持模糊匹配和同义词
- 更准确的相关性计算

### 2. 使用 Redis 作为向量数据库

**优势**:
- 高性能的向量存储和检索
- 支持持久化
- 易于集成
- 支持分布式部署

## 架构设计

```
用户问题
    ↓
EmbeddingService (生成查询向量)
    ↓
VectorStoreService (Redis 向量检索)
    ↓
VectorRAGService (组装检索结果)
    ↓
SemanticContextService (构建 LLM 上下文)
    ↓
TextToSQLService (生成 SQL)
```

## 核心组件

### 1. EmbeddingService
**文件**: `EmbeddingService.java`

**功能**:
- 调用 Embedding API 生成文本向量
- 计算向量余弦相似度
- 支持批量生成

**配置**:
```properties
embedding.api.base-url=http://localhost:5580
embedding.api.key=qwe23hy
embedding.dimension=1024
```

**API**: 使用 OpenAI 兼容的 Embedding API
- 模型: `text-embedding-ada-002`
- 维度: 1536 (OpenAI) 或自定义

### 2. VectorStoreService
**文件**: `VectorStoreService.java`

**功能**:
- 存储表和字段的向量到 Redis
- 基于向量相似度检索
- 管理向量索引

**Redis 数据结构**:
```
vector:model:{modelId}     -> 表的向量 (序列化为字符串)
meta:model:{modelId}       -> 表的元数据 (JSON)
vector:column:{columnId}   -> 字段的向量
meta:column:{columnId}     -> 字段的元数据 (JSON)
```

**核心方法**:
- `indexModel(Model)`: 为表生成并存储向量
- `indexColumn(ColumnDefinition)`: 为字段生成并存储向量
- `searchModels(query, datasourceId, topK)`: 搜索相关的表
- `searchColumns(query, modelIds, topK)`: 搜索相关的字段

### 3. VectorRAGService
**文件**: `VectorRAGService.java`

**功能**:
- 协调向量检索流程
- 组装检索结果
- 管理索引生命周期

**核心方法**:
- `retrieveRelevantSchema(question, datasourceId, topK)`: 检索相关的表和字段
- `indexAllModelsAndColumns()`: 为所有表和字段建立索引
- `indexModel(modelId)`: 为单个表建立索引
- `deleteModelIndex(modelId)`: 删除表的索引

### 4. VectorIndexController
**文件**: `VectorIndexController.java`

**功能**: 提供索引管理 API

**API 端点**:
- `POST /api/vector-index/rebuild-all`: 重建所有索引
- `POST /api/vector-index/index-model/{modelId}`: 为单个表建立索引
- `DELETE /api/vector-index/model/{modelId}`: 删除表的索引

## 使用流程

### 1. 启动 Redis

```bash
# 使用 Docker 启动 Redis
docker run -d --name redis -p 6379:6379 redis:latest

# 或使用本地 Redis
redis-server
```

### 2. 配置 application.properties

```properties
# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=
spring.data.redis.database=0

# Embedding Configuration
embedding.api.base-url=http://localhost:5580
embedding.api.key=qwe23hy
embedding.dimension=1024
```

### 3. 启动应用

```bash
./mvnw spring-boot:run
```

### 4. 建立向量索引

**方式 1: 通过 API 建立索引**
```bash
# 为所有表和字段建立索引
curl -X POST http://localhost:8082/api/vector-index/rebuild-all

# 为单个表建立索引
curl -X POST http://localhost:8082/api/vector-index/index-model/1
```

**方式 2: 在代码中自动建立索引**
```java
@Component
@RequiredArgsConstructor
public class IndexInitializer implements ApplicationRunner {

    private final VectorRAGService vectorRAGService;

    @Override
    public void run(ApplicationArguments args) {
        // 应用启动时自动建立索引
        vectorRAGService.indexAllModelsAndColumns();
    }
}
```

### 5. 使用向量 RAG 查询

查询 API 保持不变，系统会自动使用向量 RAG：

```bash
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "question": "查询所有数据源的名称和类型",
    "createNewConversation": true
  }'
```

## 工作原理

### 1. 索引阶段

```java
// 为表生成向量
String text = "表名: datasource, 显示名: 数据源, 描述: 存储数据源配置信息";
float[] embedding = embeddingService.generateEmbedding(text);
vectorStoreService.indexModel(model);
```

### 2. 检索阶段

```java
// 生成查询向量
float[] queryVector = embeddingService.generateEmbedding("查询所有数据源");

// 计算相似度
for (Model model : allModels) {
    float[] modelVector = getVectorFromRedis(model.getId());
    double similarity = cosineSimilarity(queryVector, modelVector);
}

// 返回 Top-K 最相关的表
```

### 3. 相似度计算

使用余弦相似度：

```
similarity = (A · B) / (||A|| * ||B||)
```

其中：
- A 是查询向量
- B 是表/字段向量
- · 表示点积
- ||·|| 表示向量的模

## 性能优化

### 1. 批量索引

```java
// 批量生成 embedding
List<String> texts = models.stream()
    .map(m -> buildModelText(m))
    .collect(Collectors.toList());

List<float[]> embeddings = embeddingService.generateEmbeddings(texts);
```

### 2. 缓存查询向量

```java
@Cacheable("query-vectors")
public float[] getQueryVector(String question) {
    return embeddingService.generateEmbedding(question);
}
```

### 3. 异步索引

```java
@Async
public CompletableFuture<Void> indexModelAsync(Model model) {
    vectorStoreService.indexModel(model);
    return CompletableFuture.completedFuture(null);
}
```

## 测试示例

### 1. 语义理解测试

```bash
# 测试同义词识别
curl -X POST http://localhost:8082/api/query \
  -d '{"userId": 1, "question": "有多少个数据库连接？"}'
# 应该能识别 "数据库连接" = "数据源"

# 测试模糊匹配
curl -X POST http://localhost:8082/api/query \
  -d '{"userId": 1, "question": "表的定义信息"}'
# 应该能识别 "表的定义" = "model 表"
```

### 2. 多语言测试

```bash
# 中文查询
curl -X POST http://localhost:8082/api/query \
  -d '{"userId": 1, "question": "查询所有数据源"}'

# 英文查询
curl -X POST http://localhost:8082/api/query \
  -d '{"userId": 1, "question": "Show all datasources"}'
```

## 监控和调试

### 1. 查看索引状态

```bash
# 连接 Redis
redis-cli

# 查看所有向量键
KEYS vector:*

# 查看表向量
GET vector:model:1

# 查看元数据
GET meta:model:1
```

### 2. 日志监控

```
INFO  - Vector RAG retrieval for question: 查询所有数据源, datasourceId: null, topK: 10
INFO  - Found 2 relevant models: [1, 2]
INFO  - Found 8 relevant columns
INFO  - Vector RAG retrieved 2 models, 8 columns, 1 relationships
```

## 与旧版本的对比

| 特性 | 关键词匹配 | 向量检索 |
|------|-----------|---------|
| 语义理解 | ❌ | ✅ |
| 同义词识别 | ❌ | ✅ |
| 模糊匹配 | 有限 | ✅ |
| 中文支持 | 有限 | ✅ |
| 准确性 | 中等 | 高 |
| 性能 | 快 | 中等 |
| 存储需求 | 低 | 中等 |

## 未来改进方向

### 1. 使用专业向量数据库
- Milvus
- Pinecone
- Weaviate
- Qdrant

### 2. 优化 Embedding 模型
- 使用中文优化的模型
- 使用领域特定的模型
- Fine-tune 模型

### 3. 混合检索
- 结合关键词匹配和向量检索
- 使用 BM25 + 向量检索

### 4. 增量更新
- 只更新变化的表/字段
- 使用消息队列异步更新

## 常见问题

### Q1: Embedding API 调用失败怎么办？
A: 检查 API 配置，确保 base-url 和 api-key 正确。可以使用本地 Embedding 模型作为备选。

### Q2: Redis 内存占用过大怎么办？
A: 可以：
- 减少向量维度
- 只索引重要的表/字段
- 使用 Redis 持久化策略

### Q3: 检索结果不准确怎么办？
A: 可以：
- 调整 topK 参数
- 优化表/字段的描述信息
- 使用更好的 Embedding 模型

### Q4: 如何处理大规模数据库？
A: 可以：
- 使用分片策略
- 按数据源分别索引
- 使用专业向量数据库

## 总结

向量 RAG 方案相比关键词匹配有显著优势：
- ✅ 更准确的语义理解
- ✅ 更好的中文支持
- ✅ 支持同义词和模糊匹配
- ✅ 可扩展性强

建议在生产环境中使用向量 RAG 方案，以获得更好的 Text-to-SQL 准确性。
