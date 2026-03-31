# 用户反馈系统设计文档

> **创建日期：** 2026-03-31
> **目标：** 实现用户满意度反馈机制，将优质问答对沉淀为用户模板，提升查询准确性

## 一、概述

### 1.1 背景

当前系统支持三路径查询：
- 路径1：RAG 模板检索（query_template）
- 路径2：Schema RAG + BFS 扩表
- 路径3：高召回 BFS 重试

需要增加用户反馈机制，将用户满意的问答对沉淀为可复用的模板，并支持不满意时的重试。

### 1.2 核心目标

1. 用户可对查询结果进行满意/不满意评价
2. 满意的问答对自动沉淀为 user_query_template
3. 不满意时触发高召回 BFS 重新生成答案
4. user_query_template 与 query_template 统一纳入 RAG 检索

### 1.3 设计原则

- **最小化改动**：在现有架构基础上扩展
- **数据一致性**：事务保证评分更新的原子性
- **性能优先**：向量索引异步更新，不阻塞主流程
- **可追溯性**：保留完整的重试历史链

## 二、数据库设计

### 2.1 新建 user_query_template 表

```sql
CREATE TABLE IF NOT EXISTS user_query_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    question VARCHAR(500) NOT NULL COMMENT '用户问题',
    generated_sql TEXT NOT NULL COMMENT '生成的SQL',
    datasource_id BIGINT COMMENT '数据源ID',
    total_score INT DEFAULT 0 COMMENT '总评分（满意次数）',
    rating_count INT DEFAULT 0 COMMENT '评分次数（满意+不满意）',
    avg_score DECIMAL(3,2) DEFAULT 0.00 COMMENT '平均分',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_question_sql (question(255), generated_sql(255)),
    INDEX idx_avg_score (avg_score DESC),
    INDEX idx_datasource (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户查询模板';
```

**字段说明：**
- `total_score`：累计满意次数
- `rating_count`：总评价次数（满意+不满意）
- `avg_score`：平均分 = total_score / rating_count
- 唯一索引确保同一问答对不重复

### 2.2 扩展 query_log 表

```sql
ALTER TABLE query_log ADD COLUMN satisfied BOOLEAN COMMENT '用户是否满意（NULL=未评价，TRUE=满意，FALSE=不满意）';
ALTER TABLE query_log ADD COLUMN retry_from_id BIGINT COMMENT '重试来源的query_log ID';
ALTER TABLE query_log ADD COLUMN source_type VARCHAR(20) COMMENT '来源类型（user_template, system_template, bfs）';
ALTER TABLE query_log ADD COLUMN source_template_id BIGINT COMMENT '来源模板ID';
ALTER TABLE query_log ADD INDEX idx_retry_from (retry_from_id);
ALTER TABLE query_log ADD INDEX idx_source (source_type, source_template_id);
```

**字段说明：**
- `satisfied`：三态字段，NULL 表示未评价
- `retry_from_id`：形成重试链，可追溯原始查询
- `source_type`：标识答案来源，用于判断是否可沉淀为用户模板
- `source_template_id`：关联到具体的模板记录

### 2.3 Redis 向量存储元数据扩展

在现有的向量索引元数据中添加 type 字段：

```json
{
  "type": 1,
  "templateId": 123,
  "question": "查询广告主123的数据",
  "sql": "SELECT * FROM advertiser WHERE id = 123",
  "score": 0.95
}
```

**type 定义：**
- `1`：来自 user_query_template
- `2`：来自 query_template（系统模板）

## 三、业务流程设计

### 3.1 用户提交满意

```
输入：request.satisfied = TRUE, request.retryQueryLogId = <query_log.id>

1. 查询 query_log 记录
2. 检查 query_log.source_type：

   情况A：source_type = 'system_template'
   - 更新 query_log.satisfied = TRUE
   - 流程结束（系统模板不沉淀为用户模板）

   情况B：source_type != 'system_template'
   - 用 (question, generated_sql) 查询 user_query_template
   - 如果不存在（全新问答对）：
     * 创建 user_query_template 记录
       - total_score = 1
       - rating_count = 1
       - avg_score = 1.00
     * 更新 query_log.satisfied = TRUE
     * 异步调用 indexUserQueryTemplate() 存入 Redis 向量库
   - 如果已存在：
     * 更新 user_query_template：
       - total_score = total_score + 1
       - rating_count = rating_count + 1
       - avg_score = total_score / rating_count
     * 更新 query_log.satisfied = TRUE
     * 异步更新 Redis 向量库中的评分元数据

3. 返回成功响应
```

### 3.2 用户提交不满意

```
输入：request.satisfied = FALSE, request.retryQueryLogId = <query_log.id>

1. 查询 query_log 记录
2. 更新 query_log.satisfied = FALSE
3. 检查 query_log.source_type：

   情况A：source_type = 'user_template'
   - 根据 source_template_id 查询 user_query_template
   - 更新评分：
     * rating_count = rating_count + 1
     * avg_score = total_score / rating_count（total_score 不变）
   - 异步更新 Redis 向量库中的评分元数据

   情况B：source_type = 'system_template' 或 'bfs'
   - 仅更新 query_log.satisfied = FALSE

4. 调用 generatePlanByBFSWithHighRecall() 生成新答案
5. 创建新的 query_log 记录：
   - retry_from_id = 原 query_log.id
   - source_type = 'bfs'
   - satisfied = NULL
6. 返回新答案给用户
```

### 3.3 路径1 RAG 检索增强

```
原流程：
  templateVectorSearchService.searchSimilarTemplates()
  -> 返回 query_template 匹配结果

新流程：
  templateVectorSearchService.searchSimilarTemplates()
  -> 返回结果包含 type 字段
  -> if (type == 1) {
       // 来自 user_query_template
       userTemplate = userQueryTemplateService.findById(templateId);
       return executeUserTemplate(userTemplate, ...);
     } else {
       // 来自 query_template（现有逻辑）
       systemTemplate = queryTemplateService.getById(templateId);
       // 参数填充逻辑
     }
```

## 四、服务层设计

### 4.1 新增 UserQueryTemplateService

```java
@Service
@RequiredArgsConstructor
public class UserQueryTemplateService {
    private final UserQueryTemplateMapper mapper;

    /**
     * 根据问题和SQL查找
     */
    UserQueryTemplate findByQuestionAndSql(String question, String sql);

    /**
     * 创建新记录
     */
    UserQueryTemplate create(String question, String sql, Long datasourceId);

    /**
     * 更新评分（满意）
     */
    void updateScoreOnSatisfied(Long id);

    /**
     * 更新评分（不满意）
     */
    void updateScoreOnUnsatisfied(Long id);

    /**
     * 根据ID查询
     */
    UserQueryTemplate findById(Long id);
}
```

### 4.2 扩展 SchemaVectorStoreService

```java
/**
 * 为 user_query_template 建立向量索引
 */
public void indexUserQueryTemplate(Long userTemplateId, String question, String sql) {
    String embedding = embeddingService.generateEmbedding(question);

    Map<String, Object> meta = new HashMap<>();
    meta.put("type", 1);  // user_template
    meta.put("templateId", userTemplateId);
    meta.put("question", question);
    meta.put("sql", sql);

    // 存入 Redis 向量库
    redisVectorStore.add(embedding, meta);
}

/**
 * 更新向量索引的评分元数据
 */
public void updateTemplateScore(Long templateId, int type, BigDecimal score) {
    // 更新 Redis 中对应向量的元数据
}
```

### 4.3 修改 TemplateVectorSearchService

```java
public static class TemplateSearchResult {
    private double similarity;
    private TemplateMeta meta;
}

public static class TemplateMeta {
    private Integer type;  // 1=user_template, 2=system_template
    private Long templateId;
    private String question;
    private String sql;
    private BigDecimal score;
}
```

### 4.4 修改 TextToSQLService.processQuery()

**关键修改点：**

1. **处理用户反馈（在方法开始处）**

```java
// 处理用户反馈
if (request.getSatisfied() != null && request.getRetryQueryLogId() != null) {
    handleUserFeedback(request);

    if (Boolean.TRUE.equals(request.getSatisfied())) {
        // 满意，直接返回
        return QueryResponse.success("感谢您的反馈");
    }
    // 不满意，继续执行后续流程生成新答案
}
```

2. **路径1增强：支持 user_template**

```java
// 路径1：RAG 模板检索
List<TemplateSearchResult> results = templateVectorSearchService.searchSimilarTemplates(...);
if (!results.isEmpty()) {
    TemplateSearchResult bestMatch = results.get(0);

    if (bestMatch.getMeta().getType() == 1) {
        // 来自 user_query_template
        UserQueryTemplate userTemplate =
            userQueryTemplateService.findById(bestMatch.getMeta().getTemplateId());

        List<SqlStep> steps = parseUserTemplateSql(userTemplate.getGeneratedSql());
        return executePlan(request.getQuestion(), steps, conversationId, request,
            userTemplate.getId(), "user_template", bestMatch.getSimilarity(), startTime);
    } else {
        // 来自 query_template（现有逻辑）
        QueryTemplate systemTemplate =
            queryTemplateService.getById(bestMatch.getMeta().getTemplateId());
        // ... 参数填充逻辑
        return executePlan(..., systemTemplate.getId(), "system_template", ...);
    }
}
```

3. **executePlan 方法签名调整**

```java
private QueryResponse executePlan(
    String question,
    List<SqlStep> steps,
    Long conversationId,
    QueryRequest request,
    Long sourceTemplateId,
    String sourceType,  // 新增
    double similarity,
    long startTime
) {
    // 保存 query_log 时设置 source_type 和 source_template_id
    queryLog.setSourceType(sourceType);
    queryLog.setSourceTemplateId(sourceTemplateId);
    // ...
}
```

4. **新增 handleUserFeedback 方法**

```java
@Transactional
private void handleUserFeedback(QueryRequest request) {
    Long queryLogId = request.getRetryQueryLogId();
    Boolean satisfied = request.getSatisfied();

    // 1. 查询 query_log
    QueryLog queryLog = queryLogService.findById(queryLogId);
    if (queryLog == null) {
        throw new IllegalArgumentException("查询记录不存在");
    }

    // 2. 检查是否已评价
    if (queryLog.getSatisfied() != null) {
        throw new IllegalStateException("该查询已评价过");
    }

    // 3. 更新 query_log.satisfied
    queryLogService.updateSatisfied(queryLogId, satisfied);

    // 4. 处理评分逻辑
    if ("system_template".equals(queryLog.getSourceType())) {
        // 来自系统模板，不处理 user_query_template
        return;
    }

    String question = queryLog.getQuestion();
    String sql = queryLog.getGeneratedSql();

    if (Boolean.TRUE.equals(satisfied)) {
        // 满意
        UserQueryTemplate existing =
            userQueryTemplateService.findByQuestionAndSql(question, sql);

        if (existing == null) {
            // 全新问答对
            UserQueryTemplate newTemplate =
                userQueryTemplateService.create(question, sql, queryLog.getDatasourceId());

            // 异步建立向量索引
            CompletableFuture.runAsync(() -> {
                try {
                    schemaVectorStoreService.indexUserQueryTemplate(
                        newTemplate.getId(), question, sql);
                } catch (Exception e) {
                    log.error("索引用户模板失败", e);
                }
            });
        } else {
            // 已存在，更新评分
            userQueryTemplateService.updateScoreOnSatisfied(existing.getId());

            // 异步更新向量索引评分
            CompletableFuture.runAsync(() -> {
                try {
                    schemaVectorStoreService.updateTemplateScore(
                        existing.getId(), 1, existing.getAvgScore());
                } catch (Exception e) {
                    log.error("更新向量索引评分失败", e);
                }
            });
        }
    } else {
        // 不满意
        if ("user_template".equals(queryLog.getSourceType())) {
            Long templateId = queryLog.getSourceTemplateId();
            userQueryTemplateService.updateScoreOnUnsatisfied(templateId);

            // 异步更新向量索引评分
            UserQueryTemplate template = userQueryTemplateService.findById(templateId);
            CompletableFuture.runAsync(() -> {
                try {
                    schemaVectorStoreService.updateTemplateScore(
                        templateId, 1, template.getAvgScore());
                } catch (Exception e) {
                    log.error("更新向量索引评分失败", e);
                }
            });
        }
    }
}
```

### 4.5 扩展 QueryRequest DTO

```java
@Data
public class QueryRequest {
    // 现有字段...

    // 新增字段
    private Boolean satisfied;  // 用户满意度（null=未评价，true=满意，false=不满意）
    private Long retryQueryLogId;  // 要评价的 query_log ID
}
```

## 五、错误处理和边界情况

### 5.1 边界情况

**1. 重复评价**
- 检查 `query_log.satisfied` 是否已有值
- 如果已评价，抛出异常或返回提示

**2. 向量索引失败**
- 不影响主流程，记录错误日志
- 可通过定时任务补偿索引

**3. 并发评分**
- 使用 `@Transactional` 保证事务隔离
- 数据库唯一索引防止重复创建

**4. 重试链过长**
- 限制 `retry_from_id` 追溯深度（建议最多3次）
- 前端提示用户重新提问

### 5.2 数据一致性

**事务边界：**
- `handleUserFeedback` 方法使用 `@Transactional`
- 向量索引更新异步执行，不在事务内

**评分计算公式：**
```
满意：total_score++, rating_count++
不满意：rating_count++（total_score 不变）
avg_score = total_score / rating_count
```

### 5.3 性能优化

**1. 索引优化**
- `user_query_template` 表：(question, generated_sql) 唯一索引
- `query_log` 表：retry_from_id 索引、(source_type, source_template_id) 联合索引

**2. 缓存策略**
- 热门 user_query_template 结果缓存（Redis，TTL 5分钟）
- 向量检索结果缓存（短期）

**3. 异步处理**
- 向量索引建立/更新使用 `CompletableFuture.runAsync()`
- 不阻塞主流程响应

## 六、监控指标

### 6.1 业务指标

- **用户满意率**：满意次数 / 总评价次数
- **user_query_template 使用率**：来自 user_template 的查询 / 总查询
- **重试率**：不满意次数 / 总评价次数
- **各路径命中率**：user_template / system_template / bfs

### 6.2 技术指标

- 向量索引成功率
- 评分更新耗时
- 重试链深度分布
- 并发评分冲突次数

## 七、测试策略

### 7.1 单元测试

- `UserQueryTemplateService` 各方法测试
- `handleUserFeedback` 各分支覆盖
- 评分计算逻辑测试

### 7.2 集成测试

- 满意流程：全新问答对 -> 创建 user_query_template -> 向量索引
- 满意流程：已存在问答对 -> 更新评分
- 不满意流程：触发 BFS 重试 -> 创建新 query_log
- 路径1增强：user_template 优先匹配

### 7.3 边界测试

- 重复评价拦截
- 并发评分冲突
- 向量索引失败降级
- 重试链深度限制

## 八、实施计划

### 阶段1：数据库和基础服务（2-3天）
1. 创建 user_query_template 表
2. 扩展 query_log 表字段
3. 实现 UserQueryTemplateService
4. 实现 UserQueryTemplateMapper

### 阶段2：向量存储扩展（1-2天）
1. 扩展 SchemaVectorStoreService.indexUserQueryTemplate()
2. 修改 TemplateVectorSearchService 返回结构
3. 测试向量索引和检索

### 阶段3：核心流程实现（2-3天）
1. 实现 handleUserFeedback() 方法
2. 修改 processQuery() 路径1逻辑
3. 调整 executePlan() 方法签名
4. 扩展 QueryRequest DTO

### 阶段4：测试和优化（1-2天）
1. 单元测试和集成测试
2. 性能测试和优化
3. 监控指标接入

**总计：6-10天**

## 九、风险和缓解

### 9.1 风险

1. **向量索引失败率高**
   - 缓解：异步重试机制 + 定时补偿任务

2. **评分数据不一致**
   - 缓解：事务保证 + 数据库约束

3. **性能影响**
   - 缓解：异步处理 + 缓存优化

4. **重试链过长**
   - 缓解：深度限制 + 前端提示

### 9.2 回滚方案

- 数据库表可保留，不影响现有功能
- 代码通过特性开关控制启用/禁用
- 向量索引可独立清理

## 十、总结

本设计方案在现有架构基础上，通过最小化改动实现了用户反馈系统：

1. **数据层**：新建 user_query_template 表，扩展 query_log 表
2. **向量层**：统一 user_template 和 system_template 的 RAG 检索
3. **业务层**：实现满意/不满意的完整流程
4. **性能**：异步处理向量索引，不影响主流程

该方案符合"最小化改动、数据一致性、性能优先、可追溯性"的设计原则，可快速实施并验证效果。
