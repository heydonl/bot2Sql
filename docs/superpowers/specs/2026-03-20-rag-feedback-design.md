# RAG 用户反馈和 BFS 动态模板生成设计方案

## 概述

基于需求文档实现两阶段 RAG 系统：
1. **第一阶段**：用户提问 → embedding → RAG 找相似 SQL 模板 → LLM 生成参数值 → 填充模板 → 执行 SQL
2. **第二阶段**：用户反馈机制 → 不满意时 BFS 表发现 → 动态生成新模板

## 数据库表结构调整

### 1. query_template 表增强

在现有基础上添加以下字段：

```sql
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS name VARCHAR(200) COMMENT '模板名称';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS description TEXT COMMENT '模板描述';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS template_source VARCHAR(50) DEFAULT 'manual' COMMENT '模板来源: manual(手动), bfs_generated(BFS生成), llm_generated(LLM生成)';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS parent_template_id BIGINT COMMENT '父模板ID（用于BFS生成的模板）';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS bfs_context JSON COMMENT 'BFS发现的表上下文信息';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS similarity_threshold DECIMAL(3,2) DEFAULT 0.70 COMMENT '相似度阈值';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用';
```

### 2. 新增用户反馈表

```sql
CREATE TABLE IF NOT EXISTS query_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    conversation_id BIGINT COMMENT '会话ID',
    query_log_id BIGINT NOT NULL COMMENT '查询日志ID',
    template_id BIGINT COMMENT '使用的模板ID',
    question VARCHAR(500) NOT NULL COMMENT '用户问题',
    generated_sql TEXT COMMENT '生成的SQL',
    rating INT NOT NULL COMMENT '用户评分: 1=满意, 0=不满意',
    feedback_reason VARCHAR(500) COMMENT '反馈原因',
    template_similarity DECIMAL(5,4) COMMENT '模板相似度',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_query_log_id (query_log_id),
    INDEX idx_template_id (template_id),
    INDEX idx_rating (rating),
    FOREIGN KEY (query_log_id) REFERENCES query_log(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询反馈表';
```

### 3. BFS 表发现记录表

```sql
CREATE TABLE IF NOT EXISTS bfs_discovery_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    feedback_id BIGINT NOT NULL COMMENT '反馈ID',
    question VARCHAR(500) NOT NULL COMMENT '用户问题',
    discovered_tables JSON NOT NULL COMMENT '发现的表列表',
    table_relationships JSON COMMENT '表关系信息',
    few_shot_examples JSON COMMENT '使用的few-shot示例',
    generated_template_id BIGINT COMMENT '生成的模板ID',
    success BOOLEAN DEFAULT FALSE COMMENT '是否成功生成模板',
    error_message TEXT COMMENT '错误信息',
    execution_time BIGINT COMMENT '执行时间（毫秒）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_feedback_id (feedback_id),
    INDEX idx_success (success),
    FOREIGN KEY (feedback_id) REFERENCES query_feedback(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BFS表发现日志';
```

## 核心服务设计

### 1. QueryFeedbackService

负责处理用户反馈和触发第二阶段流程：

```java
@Service
public class QueryFeedbackService {

    // 保存用户反馈
    public void saveFeedback(Long userId, Long conversationId, Long queryLogId,
                           Long templateId, String question, String generatedSql,
                           Integer rating, String feedbackReason);

    // 获取模板反馈统计
    public TemplateFeedbackStats getTemplateFeedbackStats(Long templateId);

    // 更新模板评分
    private void updateTemplateRating(Long templateId, Integer rating);
}
```

### 2. BFSTableDiscoveryService

实现 BFS 表发现和动态模板生成：

```java
@Service
public class BFSTableDiscoveryService {

    // 主要方法：发现表并生成模板
    public BFSDiscoveryResult discoverTablesAndGenerateTemplate(String question, Long datasourceId);

    // BFS搜索相关表（高召回率）
    private List<Long> searchRelevantTables(String question, Long datasourceId);

    // BFS遍历表关系（2层深度）
    private Set<Long> bfsDiscoverRelatedTables(List<Long> seedTables, int maxDepth);

    // 获取few-shot示例
    private List<FewShotExample> getFewShotExamples(Long datasourceId);

    // 生成动态模板
    private QueryTemplate generateDynamicTemplate(String question, Set<Long> discoveredTables,
                                                 List<FewShotExample> fewShots);
}
```

### 3. TextToSQLService 增强

在现有 processQuery 方法中集成第二阶段流程：

```java
public class TextToSQLService {

    public QueryResponse processQuery(QueryRequest request) {
        // ... 现有第一阶段逻辑 ...

        // 在返回结果中添加反馈所需信息
        response.setQueryLogId(queryLogId);
        response.setTemplateId(templateId);
        response.setFromTemplate(isFromTemplate);
        response.setTemplateSimilarity(templateSimilarity);

        return response;
    }
}
```

## 实现流程

### 第一阶段（已实现，需微调）
1. 用户自然语言提问
2. 生成 embedding
3. RAG 向量搜索相似模板（相似度阈值 0.7）
4. LLM 生成模板参数值
5. 填充 SQL 模板并执行
6. 返回结果（包含反馈所需信息）

### 第二阶段（新增）
1. 用户点击满意/不满意按钮
2. 保存反馈到 query_feedback 表
3. 如果不满意，触发 BFS 流程：
   - 高召回率搜索相关表
   - BFS 遍历表关系（2层深度）
   - 收集 few-shot 示例
   - 构建增强 prompt
   - LLM 生成新模板和参数
   - 执行 SQL 并返回结果
4. 用户可继续评分直到满意

## 关键技术点

### 1. 向量相似度搜索
- 使用 Redis 存储模板向量
- 余弦相似度计算
- 可配置相似度阈值

### 2. BFS 表关系遍历
- 基于 relationship 表的图遍历
- 限制深度为 2 层避免过度扩展
- 支持双向关系遍历

### 3. 动态 Prompt 构建
- 整合表结构、关系、few-shot 示例
- 结构化 JSON 输出格式
- 参数映射定义

### 4. 模板质量评估
- 基于用户反馈的评分系统
- 使用次数和成功率统计
- 自动禁用低质量模板

## 配置参数

```properties
# RAG 配置
rag.template.similarity.threshold=0.7
rag.template.max.candidates=5

# BFS 配置
bfs.max.depth=2
bfs.max.tables=20
bfs.few.shot.limit=5

# 模板管理
template.auto.disable.threshold=0.3
template.min.usage.count=10
```

## 错误处理

1. **RAG 检索失败**：降级到传统骨架匹配
2. **BFS 发现失败**：记录错误，返回友好提示
3. **LLM 生成失败**：重试机制，最终降级
4. **SQL 执行失败**：错误分析和用户提示

## 性能优化

1. **向量索引优化**：使用 Redis 集群
2. **BFS 缓存**：缓存表关系图
3. **异步处理**：反馈处理异步化
4. **批量操作**：模板向量批量更新

## 监控指标

1. **第一阶段成功率**：模板匹配率、SQL 执行成功率
2. **第二阶段触发率**：用户不满意比例
3. **BFS 成功率**：动态模板生成成功率
4. **整体满意度**：用户最终满意度统计