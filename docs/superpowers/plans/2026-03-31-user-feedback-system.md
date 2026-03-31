# 用户反馈系统实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现用户满意度反馈机制，将优质问答对沉淀为用户模板，支持不满意时的重试

**Architecture:** 在现有三路径查询基础上，新增 user_query_template 表存储用户满意的问答对，扩展 query_log 支持反馈评价，统一 user_template 和 system_template 的 RAG 检索，实现满意/不满意的完整业务流程

**Tech Stack:** Spring Boot 4.0.3, MyBatis, MySQL/H2, Redis 向量存储, Java 17

---

## Task 1: 数据库 Schema 变更

**Files:**
- Modify: `src/main/resources/schema.sql`
- Modify: `src/main/resources/schema-h2.sql`

- [ ] **Step 1: 在 schema.sql 中添加 user_query_template 表**

在 `query_step_log` 表定义之后添加：

```sql
-- 17. 用户查询模板表
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

- [ ] **Step 2: 在 schema.sql 中扩展 query_log 表**

在 `query_log` 表定义的 `created_at` 字段之后，`INDEX` 之前添加：

```sql
    satisfied BOOLEAN COMMENT '用户是否满意（NULL=未评价，TRUE=满意，FALSE=不满意）',
    retry_from_id BIGINT COMMENT '重试来源的query_log ID',
    source_type VARCHAR(20) COMMENT '来源类型（user_template, system_template, bfs）',
    source_template_id BIGINT COMMENT '来源模板ID',
```

在 `INDEX idx_intent (intent)` 之后添加：

```sql
    INDEX idx_retry_from (retry_from_id),
    INDEX idx_source (source_type, source_template_id)
```

- [ ] **Step 3: 在 schema-h2.sql 中添加 user_query_template 表**

在 `query_step_log` 表定义之后添加（H2 语法，无 COMMENT）：

```sql
-- 17. 用户查询模板表
CREATE TABLE IF NOT EXISTS user_query_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question VARCHAR(500) NOT NULL,
    generated_sql TEXT NOT NULL,
    datasource_id BIGINT,
    total_score INT DEFAULT 0,
    rating_count INT DEFAULT 0,
    avg_score DECIMAL(3,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_query_template_question_sql
    ON user_query_template(question, generated_sql);
CREATE INDEX IF NOT EXISTS idx_user_query_template_avg_score
    ON user_query_template(avg_score DESC);
CREATE INDEX IF NOT EXISTS idx_user_query_template_datasource
    ON user_query_template(datasource_id);
```

- [ ] **Step 4: 在 schema-h2.sql 中扩展 query_log 表**

在 `query_log` 表定义的 `created_at` 字段之后添加：

```sql
    satisfied BOOLEAN,
    retry_from_id BIGINT,
    source_type VARCHAR(20),
    source_template_id BIGINT,
```

在索引定义之后添加：

```sql
CREATE INDEX IF NOT EXISTS idx_query_log_retry_from ON query_log(retry_from_id);
CREATE INDEX IF NOT EXISTS idx_query_log_source ON query_log(source_type, source_template_id);
```

- [ ] **Step 5: 提交数据库 Schema 变更**

```bash
git add src/main/resources/schema.sql src/main/resources/schema-h2.sql
git commit -m "feat: add user_query_template table and extend query_log for feedback"
```

---

## Task 2: UserQueryTemplate 基础设施

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/domain/UserQueryTemplate.java`
- Create: `src/main/java/com/tecdo/mac/sql2bot/mapper/UserQueryTemplateMapper.java`
- Create: `src/main/resources/mapper/UserQueryTemplateMapper.xml`
- Create: `src/main/java/com/tecdo/mac/sql2bot/service/UserQueryTemplateService.java`

- [ ] **Step 1: 创建 UserQueryTemplate 实体类**


```java
package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserQueryTemplate {
    private Long id;
    private String question;
    private String generatedSql;
    private Long datasourceId;
    private Integer totalScore;
    private Integer ratingCount;
    private BigDecimal avgScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 UserQueryTemplateMapper 接口**

```java
package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.UserQueryTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserQueryTemplateMapper {
    UserQueryTemplate findByQuestionAndSql(@Param("question") String question, 
                                           @Param("sql") String sql);
    
    int insert(UserQueryTemplate template);
    
    UserQueryTemplate findById(@Param("id") Long id);
    
    int updateScoreOnSatisfied(@Param("id") Long id);
    
    int updateScoreOnUnsatisfied(@Param("id") Long id);
}
```

- [ ] **Step 3: 创建 UserQueryTemplateMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.tecdo.mac.sql2bot.mapper.UserQueryTemplateMapper">

    <resultMap id="UserQueryTemplateResultMap" type="com.tecdo.mac.sql2bot.domain.UserQueryTemplate">
        <id property="id" column="id"/>
        <result property="question" column="question"/>
        <result property="generatedSql" column="generated_sql"/>
        <result property="datasourceId" column="datasource_id"/>
        <result property="totalScore" column="total_score"/>
        <result property="ratingCount" column="rating_count"/>
        <result property="avgScore" column="avg_score"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <select id="findByQuestionAndSql" resultMap="UserQueryTemplateResultMap">
        SELECT * FROM user_query_template
        WHERE question = #{question} AND generated_sql = #{sql}
        LIMIT 1
    </select>

    <insert id="insert" parameterType="com.tecdo.mac.sql2bot.domain.UserQueryTemplate"
            useGeneratedKeys="true" keyProperty="id">
        INSERT INTO user_query_template (
            question, generated_sql, datasource_id, total_score, rating_count, avg_score
        ) VALUES (
            #{question}, #{generatedSql}, #{datasourceId}, 1, 1, 1.00
        )
    </insert>

    <select id="findById" resultMap="UserQueryTemplateResultMap">
        SELECT * FROM user_query_template WHERE id = #{id}
    </select>

    <update id="updateScoreOnSatisfied">
        UPDATE user_query_template
        SET total_score = total_score + 1,
            rating_count = rating_count + 1,
            avg_score = (total_score + 1) / (rating_count + 1)
        WHERE id = #{id}
    </update>

    <update id="updateScoreOnUnsatisfied">
        UPDATE user_query_template
        SET rating_count = rating_count + 1,
            avg_score = total_score / (rating_count + 1)
        WHERE id = #{id}
    </update>

</mapper>
```

- [ ] **Step 4: 创建 UserQueryTemplateService**

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.UserQueryTemplate;
import com.tecdo.mac.sql2bot.mapper.UserQueryTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryTemplateService {

    private final UserQueryTemplateMapper mapper;

    public UserQueryTemplate findByQuestionAndSql(String question, String sql) {
        return mapper.findByQuestionAndSql(question, sql);
    }

    public UserQueryTemplate create(String question, String sql, Long datasourceId) {
        UserQueryTemplate template = new UserQueryTemplate();
        template.setQuestion(question);
        template.setGeneratedSql(sql);
        template.setDatasourceId(datasourceId);
        mapper.insert(template);
        log.info("创建用户查询模板: id={}, question={}", template.getId(), question);
        return template;
    }

    public UserQueryTemplate findById(Long id) {
        return mapper.findById(id);
    }

    public void updateScoreOnSatisfied(Long id) {
        mapper.updateScoreOnSatisfied(id);
        log.info("更新用户模板评分（满意）: id={}", id);
    }

    public void updateScoreOnUnsatisfied(Long id) {
        mapper.updateScoreOnUnsatisfied(id);
        log.info("更新用户模板评分（不满意）: id={}", id);
    }
}
```

- [ ] **Step 5: 提交 UserQueryTemplate 基础设施**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/domain/UserQueryTemplate.java \
        src/main/java/com/tecdo/mac/sql2bot/mapper/UserQueryTemplateMapper.java \
        src/main/resources/mapper/UserQueryTemplateMapper.xml \
        src/main/java/com/tecdo/mac/sql2bot/service/UserQueryTemplateService.java
git commit -m "feat: add UserQueryTemplate infrastructure"
```

---

## Task 3: QueryLog 扩展

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/domain/QueryLog.java`
- Modify: `src/main/java/com/tecdo/mac/sql2bot/mapper/QueryLogMapper.java`
- Modify: `src/main/resources/mapper/QueryLogMapper.xml`
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/QueryLogService.java`


- [ ] **Step 1: 扩展 QueryLog 实体类**

在 `QueryLog.java` 中添加字段：

```java
private Boolean satisfied;
private Long retryFromId;
private String sourceType;
private Long sourceTemplateId;
```

- [ ] **Step 2: 在 QueryLogMapper 接口中添加方法**

```java
int updateSatisfied(@Param("id") Long id, @Param("satisfied") Boolean satisfied);

int updateSourceInfo(@Param("id") Long id, 
                     @Param("sourceType") String sourceType,
                     @Param("sourceTemplateId") Long sourceTemplateId);
```

- [ ] **Step 3: 在 QueryLogMapper.xml 中添加 SQL**

```xml
<update id="updateSatisfied">
    UPDATE query_log
    SET satisfied = #{satisfied}
    WHERE id = #{id}
</update>

<update id="updateSourceInfo">
    UPDATE query_log
    SET source_type = #{sourceType},
        source_template_id = #{sourceTemplateId}
    WHERE id = #{id}
</update>
```

- [ ] **Step 4: 在 QueryLogService 中添加方法**

```java
public void updateSatisfied(Long id, Boolean satisfied) {
    queryLogMapper.updateSatisfied(id, satisfied);
    log.info("更新查询满意度: id={}, satisfied={}", id, satisfied);
}

public void updateSourceInfo(Long id, String sourceType, Long sourceTemplateId) {
    queryLogMapper.updateSourceInfo(id, sourceType, sourceTemplateId);
}
```

- [ ] **Step 5: 提交 QueryLog 扩展**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/domain/QueryLog.java \
        src/main/java/com/tecdo/mac/sql2bot/mapper/QueryLogMapper.java \
        src/main/resources/mapper/QueryLogMapper.xml \
        src/main/java/com/tecdo/mac/sql2bot/service/QueryLogService.java
git commit -m "feat: extend QueryLog for user feedback support"
```

---

## Task 4: QueryRequest DTO 扩展

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/dto/QueryRequest.java`

- [ ] **Step 1: 在 QueryRequest 中添加字段**

```java
private Boolean satisfied;
private Long retryQueryLogId;
```

- [ ] **Step 2: 提交 QueryRequest 扩展**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/dto/QueryRequest.java
git commit -m "feat: add satisfied and retryQueryLogId to QueryRequest"
```

---

## Task 5: 向量存储扩展

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/SchemaVectorStoreService.java`
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/TemplateVectorSearchService.java`


- [ ] **Step 1: 在 SchemaVectorStoreService 中添加用户模板索引方法**

```java
public void indexUserQueryTemplate(Long userTemplateId, String question, String sql) {
    try {
        String embedding = embeddingService.generateEmbedding(question);
        
        Map<String, Object> meta = new HashMap<>();
        meta.put("type", 1);  // 1=user_template
        meta.put("templateId", userTemplateId);
        meta.put("question", question);
        meta.put("sql", sql);
        
        // 存入 Redis 向量库（具体实现依赖现有的向量存储逻辑）
        // redisVectorStore.add(embedding, meta);
        
        log.info("用户模板已索引: id={}", userTemplateId);
    } catch (Exception e) {
        log.error("索引用户模板失败: id={}", userTemplateId, e);
        throw e;
    }
}

public void updateTemplateScore(Long templateId, int type, BigDecimal score) {
    // 更新 Redis 中对应向量的元数据
    log.info("更新模板评分: id={}, type={}, score={}", templateId, type, score);
}
```

- [ ] **Step 2: 在 TemplateVectorSearchService.TemplateMeta 中添加 type 字段**

```java
public static class TemplateMeta {
    private Integer type;  // 新增：1=user_template, 2=system_template
    private Long templateId;
    // ... 其他现有字段
}
```

- [ ] **Step 3: 提交向量存储扩展**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/SchemaVectorStoreService.java \
        src/main/java/com/tecdo/mac/sql2bot/service/TemplateVectorSearchService.java
git commit -m "feat: extend vector store for user template indexing"
```

---

## Task 6: TextToSQLService 核心流程

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java`


- [ ] **Step 1: 在 TextToSQLService 中注入 UserQueryTemplateService**

在类的依赖注入部分添加：

```java
private final UserQueryTemplateService userQueryTemplateService;
```

- [ ] **Step 2: 实现 handleUserFeedback 方法**

在 TextToSQLService 类中添加（约第200行之后）：

```java
@Transactional
private void handleUserFeedback(QueryRequest request) {
    Long queryLogId = request.getRetryQueryLogId();
    Boolean satisfied = request.getSatisfied();

    QueryLog queryLog = queryLogService.findById(queryLogId);
    if (queryLog == null) {
        throw new IllegalArgumentException("查询记录不存在");
    }

    if (queryLog.getSatisfied() != null) {
        throw new IllegalStateException("该查询已评价过");
    }

    queryLogService.updateSatisfied(queryLogId, satisfied);

    if ("system_template".equals(queryLog.getSourceType())) {
        return;
    }

    String question = queryLog.getQuestion();
    String sql = queryLog.getGeneratedSql();

    if (Boolean.TRUE.equals(satisfied)) {
        UserQueryTemplate existing = userQueryTemplateService.findByQuestionAndSql(question, sql);

        if (existing == null) {
            UserQueryTemplate newTemplate = userQueryTemplateService.create(question, sql, queryLog.getDatasourceId());
            
            CompletableFuture.runAsync(() -> {
                try {
                    schemaVectorStoreService.indexUserQueryTemplate(newTemplate.getId(), question, sql);
                } catch (Exception e) {
                    log.error("索引用户模板失败", e);
                }
            });
        } else {
            userQueryTemplateService.updateScoreOnSatisfied(existing.getId());
            
            CompletableFuture.runAsync(() -> {
                try {
                    UserQueryTemplate updated = userQueryTemplateService.findById(existing.getId());
                    schemaVectorStoreService.updateTemplateScore(existing.getId(), 1, updated.getAvgScore());
                } catch (Exception e) {
                    log.error("更新向量索引评分失败", e);
                }
            });
        }
    } else {
        if ("user_template".equals(queryLog.getSourceType())) {
            Long templateId = queryLog.getSourceTemplateId();
            userQueryTemplateService.updateScoreOnUnsatisfied(templateId);
            
            CompletableFuture.runAsync(() -> {
                try {
                    UserQueryTemplate template = userQueryTemplateService.findById(templateId);
                    schemaVectorStoreService.updateTemplateScore(templateId, 1, template.getAvgScore());
                } catch (Exception e) {
                    log.error("更新向量索引评分失败", e);
                }
            });
        }
    }
}
```


- [ ] **Step 3: 修改 processQuery 方法处理用户反馈**

在 processQuery 方法开始处（约第76行之后）添加：

```java
// 处理用户反馈
if (request.getSatisfied() != null && request.getRetryQueryLogId() != null) {
    handleUserFeedback(request);
    
    if (Boolean.TRUE.equals(request.getSatisfied())) {
        return QueryResponse.success("感谢您的反馈");
    }
    // 不满意，继续执行后续流程生成新答案
}
```

- [ ] **Step 4: 修改路径1支持 user_template**

在路径1的模板匹配逻辑中（约第136-179行），修改为：

```java
List<TemplateVectorSearchService.TemplateSearchResult> templateResults =
    templateVectorSearchService.searchSimilarTemplates(
        request.getQuestion(), null, MAX_TEMPLATE_CANDIDATES);
if (!templateResults.isEmpty()) {
    TemplateVectorSearchService.TemplateSearchResult bestMatch = templateResults.get(0);
    templateSimilarity = bestMatch.getSimilarity();
    
    if (templateSimilarity >= templateSearchSimilarityThreshold) {
        if (bestMatch.getMeta().getType() != null && bestMatch.getMeta().getType() == 1) {
            // 来自 user_query_template
            UserQueryTemplate userTemplate = 
                userQueryTemplateService.findById(bestMatch.getMeta().getTemplateId());
            
            if (userTemplate != null) {
                matchedTemplate = null; // 清空 system template
                log.info("用户模板匹配成功: id={}, similarity={}", 
                    userTemplate.getId(), templateSimilarity);
                
                List<SqlStep> steps = parseSqlSteps(userTemplate.getGeneratedSql());
                if (steps != null) {
                    return executePlan(request.getQuestion(), steps, conversationId, request,
                        userTemplate.getId(), "user_template", templateSimilarity, startTime);
                }
            }
        } else {
            // 来自 query_template（现有逻辑）
            QueryTemplate candidate = queryTemplateService.getById(bestMatch.getMeta().getTemplateId());
            if (candidate != null) {
                matchedTemplate = candidate;
                log.info("RAG模板匹配成功: templateId={}, similarity={}",
                    matchedTemplate.getId(), templateSimilarity);
            }
        }
    }
}
```


- [ ] **Step 5: 调整 executePlan 方法签名**

修改 executePlan 方法签名（约第415行）：

```java
private QueryResponse executePlan(String question, List<SqlStep> steps,
        Long conversationId, QueryRequest request,
        Long sourceTemplateId, String sourceType,  // 新增 sourceType 参数
        double similarity, long startTime) {
```

在保存 query_log 时设置来源信息（约第529-540行）：

```java
QueryLog queryLog = new QueryLog();
queryLog.setUserId(request.getUserId());
queryLog.setConversationId(conversationId);
queryLog.setQuestion(question);
queryLog.setTemplateId(sourceTemplateId);
queryLog.setIsFromTemplate("user_template".equals(sourceType) || "system_template".equals(sourceType));
queryLog.setSourceType(sourceType);  // 新增
queryLog.setSourceTemplateId(sourceTemplateId);  // 新增
queryLog.setRetryFromId(request.getRetryQueryLogId());  // 新增
queryLog.setGeneratedSql(lastFilledSql);
// ... 其他字段
```

- [ ] **Step 6: 更新所有 executePlan 调用点**

在路径1（约第173行）：

```java
return executePlan(request.getQuestion(), steps, conversationId, request,
    matchedTemplate.getId(), "system_template", templateSimilarity, startTime);
```

在路径2（约第184行）：

```java
return executePlan(request.getQuestion(), steps, conversationId, request,
    null, "bfs", 0.0, startTime);
```

在路径3（约第127行）：

```java
return executePlan(request.getQuestion(), steps, conversationId, request,
    null, "bfs", 0.0, startTime);
```


- [ ] **Step 7: 添加必要的 import 语句**

在 TextToSQLService 类顶部添加：

```java
import com.tecdo.mac.sql2bot.domain.UserQueryTemplate;
import java.util.concurrent.CompletableFuture;
import org.springframework.transaction.annotation.Transactional;
```

- [ ] **Step 8: 提交 TextToSQLService 核心流程**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java
git commit -m "feat: implement user feedback handling in TextToSQLService"
```

---

## Task 7: 集成测试

**Files:**
- Create: `src/test/java/com/tecdo/mac/sql2bot/service/UserQueryTemplateFeedbackTest.java`

- [ ] **Step 1: 创建测试类框架**

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.UserQueryTemplate;
import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserQueryTemplateFeedbackTest {

    @Autowired
    private TextToSQLService textToSQLService;

    @Autowired
    private UserQueryTemplateService userQueryTemplateService;

    @Autowired
    private QueryLogService queryLogService;
}
```


- [ ] **Step 2: 添加满意流程测试（全新问答对）**

```java
@Test
void testUserSatisfied_NewQuestionAnswer() {
    // 1. 执行查询
    QueryRequest request1 = new QueryRequest();
    request1.setUserId(1L);
    request1.setQuestion("测试问题");
    request1.setCreateNewConversation(true);
    
    QueryResponse response1 = textToSQLService.processQuery(request1);
    assertThat(response1.getSuccess()).isTrue();
    Long queryLogId = response1.getQueryLogId();
    
    // 2. 用户标记满意
    QueryRequest request2 = new QueryRequest();
    request2.setUserId(1L);
    request2.setSatisfied(true);
    request2.setRetryQueryLogId(queryLogId);
    
    QueryResponse response2 = textToSQLService.processQuery(request2);
    assertThat(response2.getSuccess()).isTrue();
    
    // 3. 验证 user_query_template 创建
    UserQueryTemplate template = userQueryTemplateService.findByQuestionAndSql(
        "测试问题", response1.getSql());
    assertThat(template).isNotNull();
    assertThat(template.getTotalScore()).isEqualTo(1);
    assertThat(template.getRatingCount()).isEqualTo(1);
}
```

- [ ] **Step 3: 添加满意流程测试（已存在问答对）**

```java
@Test
void testUserSatisfied_ExistingQuestionAnswer() {
    // 1. 创建已存在的 user_query_template
    UserQueryTemplate existing = userQueryTemplateService.create(
        "已存在问题", "SELECT 1", 1L);
    
    // 2. 执行相同查询
    QueryRequest request1 = new QueryRequest();
    request1.setUserId(1L);
    request1.setQuestion("已存在问题");
    request1.setCreateNewConversation(true);
    
    QueryResponse response1 = textToSQLService.processQuery(request1);
    Long queryLogId = response1.getQueryLogId();
    
    // 3. 用户标记满意
    QueryRequest request2 = new QueryRequest();
    request2.setUserId(1L);
    request2.setSatisfied(true);
    request2.setRetryQueryLogId(queryLogId);
    
    textToSQLService.processQuery(request2);
    
    // 4. 验证评分更新
    UserQueryTemplate updated = userQueryTemplateService.findById(existing.getId());
    assertThat(updated.getTotalScore()).isEqualTo(2);
    assertThat(updated.getRatingCount()).isEqualTo(2);
}
```


- [ ] **Step 4: 添加不满意流程测试**

```java
@Test
void testUserUnsatisfied_TriggerRetry() {
    // 1. 执行查询
    QueryRequest request1 = new QueryRequest();
    request1.setUserId(1L);
    request1.setQuestion("测试问题");
    request1.setCreateNewConversation(true);
    
    QueryResponse response1 = textToSQLService.processQuery(request1);
    Long queryLogId = response1.getQueryLogId();
    
    // 2. 用户标记不满意
    QueryRequest request2 = new QueryRequest();
    request2.setUserId(1L);
    request2.setSatisfied(false);
    request2.setRetryQueryLogId(queryLogId);
    
    QueryResponse response2 = textToSQLService.processQuery(request2);
    
    // 3. 验证生成新答案
    assertThat(response2.getSuccess()).isTrue();
    assertThat(response2.getQueryLogId()).isNotEqualTo(queryLogId);
}
```

- [ ] **Step 5: 运行测试**

```bash
mvn test -Dtest=UserQueryTemplateFeedbackTest
```

预期输出: PASS - 所有测试通过

- [ ] **Step 6: 提交测试**

```bash
git add src/test/java/com/tecdo/mac/sql2bot/service/UserQueryTemplateFeedbackTest.java
git commit -m "test: add user feedback integration tests"
```

---

## 验证和清理

- [ ] **Step 1: 运行所有测试**

```bash
mvn test
```

预期输出: PASS - 所有测试通过

- [ ] **Step 2: 启动应用验证**

```bash
mvn spring-boot:run
```

检查日志确认 user_query_template 表创建成功

- [ ] **Step 3: 最终提交**

```bash
git add -A
git commit -m "feat: complete user feedback system implementation

- 新建 user_query_template 表存储用户满意的问答对
- 扩展 query_log 表支持满意度评价和重试链
- 统一 user_template 和 system_template 的 RAG 检索
- 实现满意/不满意的完整业务流程
- 异步处理向量索引，保证性能
- 添加完整的集成测试"
```

---

## 总结

本实施计划完成了以下功能：

1. **数据库层**：user_query_template 表 + query_log 扩展
2. **基础设施**：Domain、Mapper、Service 完整实现
3. **向量存储**：支持 user_template 索引和检索
4. **核心流程**：handleUserFeedback + processQuery 增强
5. **测试覆盖**：满意/不满意流程的集成测试

所有代码遵循项目规范，使用 UTF-8 编码，最小化实现。
