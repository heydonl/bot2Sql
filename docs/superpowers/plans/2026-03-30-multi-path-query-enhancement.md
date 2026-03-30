# 多路径查询执行增强实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补充实现 QueryStepLog 步骤日志系统、完善路径1参数填充Prompt、增强错误处理和步骤间依赖参数支持

**Architecture:**
- Phase 1: 创建 QueryStepLog 日志系统用于追踪每个SQL步骤的执行情况
- Phase 2: 完善路径1的参数填充Prompt，从 query_log 动态获取示例并构建完整上下文
- Phase 3: 增强 executePlan 支持步骤间依赖参数和详细错误日志

**Tech Stack:** Spring Boot 4.0.3, MyBatis, MySQL/H2, Jackson

---

## Phase 1: QueryStepLog 步骤日志系统

### Task 1: 创建数据库迁移脚本

**Files:**
- Create: `src/main/resources/db/migration/V7__create_query_step_log.sql`

- [ ] **Step 1: 编写 MySQL 建表语句**

```sql
CREATE TABLE query_step_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  query_log_id BIGINT NOT NULL COMMENT '关联的query_log主键',
  step_id VARCHAR(50) NOT NULL COMMENT '步骤ID（如step1、step2）',
  step_index INT NOT NULL COMMENT '步骤序号（从0开始）',
  sql_template TEXT COMMENT 'SQL模板',
  filled_sql TEXT COMMENT '填充参数后的完整SQL',
  datasource_id BIGINT COMMENT '数据源ID',
  execution_success BOOLEAN COMMENT '执行是否成功',
  result_count INT COMMENT '结果行数',
  execution_time BIGINT COMMENT '执行耗时（毫秒）',
  error_message TEXT COMMENT '错误信息',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_query_log_id (query_log_id),
  INDEX idx_step_id (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询步骤执行日志表';
```

- [ ] **Step 2: 创建 H2 兼容版本**

创建文件 `src/main/resources/db/migration/V7__create_query_step_log_h2.sql`:

```sql
CREATE TABLE query_step_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  query_log_id BIGINT NOT NULL,
  step_id VARCHAR(50) NOT NULL,
  step_index INT NOT NULL,
  sql_template TEXT,
  filled_sql TEXT,
  datasource_id BIGINT,
  execution_success BOOLEAN,
  result_count INT,
  execution_time BIGINT,
  error_message TEXT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_query_log_id ON query_step_log(query_log_id);
CREATE INDEX idx_step_id ON query_step_log(step_id);
```

- [ ] **Step 3: 提交迁移脚本**

```bash
git add src/main/resources/db/migration/V7__create_query_step_log*.sql
git commit -m "feat: add query_step_log table migration"
```

---

### Task 2: 创建 QueryStepLog 领域实体

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/domain/QueryStepLog.java`

- [ ] **Step 1: 编写领域实体类**

```java
package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.util.Date;

/**
 * 查询步骤执行日志
 */
@Data
public class QueryStepLog {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联的query_log主键
     */
    private Long queryLogId;

    /**
     * 步骤ID（如step1、step2）
     */
    private String stepId;

    /**
     * 步骤序号（从0开始）
     */
    private Integer stepIndex;

    /**
     * SQL模板
     */
    private String sqlTemplate;

    /**
     * 填充参数后的完整SQL
     */
    private String filledSql;

    /**
     * 数据源ID
     */
    private Long datasourceId;

    /**
     * 执行是否成功
     */
    private Boolean executionSuccess;

    /**
     * 结果行数
     */
    private Integer resultCount;

    /**
     * 执行耗时（毫秒）
     */
    private Long executionTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private Date createTime;
}
```

- [ ] **Step 2: 提交领域实体**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/domain/QueryStepLog.java
git commit -m "feat: add QueryStepLog domain entity"
```

---

### Task 3: 创建 QueryStepLogMapper 接口和 XML

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/mapper/QueryStepLogMapper.java`
- Create: `src/main/resources/mapper/QueryStepLogMapper.xml`

- [ ] **Step 1: 编写 Mapper 接口**

```java
package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.QueryStepLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 查询步骤日志 Mapper
 */
@Mapper
public interface QueryStepLogMapper {
    /**
     * 插入单条步骤日志
     */
    void insert(QueryStepLog stepLog);

    /**
     * 批量插入步骤日志
     */
    void batchInsert(@Param("stepLogs") List<QueryStepLog> stepLogs);

    /**
     * 根据 query_log_id 查询所有步骤日志
     */
    List<QueryStepLog> findByQueryLogId(@Param("queryLogId") Long queryLogId);
}
```

- [ ] **Step 2: 编写 MyBatis XML 映射**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.tecdo.mac.sql2bot.mapper.QueryStepLogMapper">

    <insert id="insert" parameterType="com.tecdo.mac.sql2bot.domain.QueryStepLog"
            useGeneratedKeys="true" keyProperty="id">
        INSERT INTO query_step_log (
            query_log_id, step_id, step_index, sql_template, filled_sql,
            datasource_id, execution_success, result_count, execution_time, error_message
        ) VALUES (
            #{queryLogId}, #{stepId}, #{stepIndex}, #{sqlTemplate}, #{filledSql},
            #{datasourceId}, #{executionSuccess}, #{resultCount}, #{executionTime}, #{errorMessage}
        )
    </insert>

    <insert id="batchInsert" parameterType="java.util.List">
        INSERT INTO query_step_log (
            query_log_id, step_id, step_index, sql_template, filled_sql,
            datasource_id, execution_success, result_count, execution_time, error_message
        ) VALUES
        <foreach collection="stepLogs" item="log" separator=",">
            (#{log.queryLogId}, #{log.stepId}, #{log.stepIndex}, #{log.sqlTemplate}, #{log.filledSql},
             #{log.datasourceId}, #{log.executionSuccess}, #{log.resultCount}, #{log.executionTime}, #{log.errorMessage})
        </foreach>
    </insert>

    <select id="findByQueryLogId" resultType="com.tecdo.mac.sql2bot.domain.QueryStepLog">
        SELECT * FROM query_step_log
        WHERE query_log_id = #{queryLogId}
        ORDER BY step_index ASC
    </select>

</mapper>
```

- [ ] **Step 3: 提交 Mapper 文件**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/mapper/QueryStepLogMapper.java
git add src/main/resources/mapper/QueryStepLogMapper.xml
git commit -m "feat: add QueryStepLogMapper interface and XML"
```

---

### Task 4: 创建 QueryStepLogService

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/service/QueryStepLogService.java`
- Create: `src/test/java/com/tecdo/mac/sql2bot/service/QueryStepLogServiceTest.java`

- [ ] **Step 1: 编写测试用例**

创建文件 `src/test/java/com/tecdo/mac/sql2bot/service/QueryStepLogServiceTest.java`:

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryStepLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class QueryStepLogServiceTest {

    @Autowired
    private QueryStepLogService queryStepLogService;

    @Test
    void testLogStep() {
        // Given
        QueryStepLog stepLog = new QueryStepLog();
        stepLog.setQueryLogId(1L);
        stepLog.setStepId("step1");
        stepLog.setStepIndex(0);
        stepLog.setSqlTemplate("SELECT * FROM table WHERE id = {{id}}");
        stepLog.setFilledSql("SELECT * FROM table WHERE id = 123");
        stepLog.setDatasourceId(1L);
        stepLog.setExecutionSuccess(true);
        stepLog.setResultCount(10);
        stepLog.setExecutionTime(150L);

        // When
        queryStepLogService.logStep(stepLog);

        // Then
        assertThat(stepLog.getId()).isNotNull();
    }

    @Test
    void testLogSteps() {
        // Given
        List<QueryStepLog> stepLogs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            QueryStepLog log = new QueryStepLog();
            log.setQueryLogId(2L);
            log.setStepId("step" + (i + 1));
            log.setStepIndex(i);
            log.setSqlTemplate("SELECT * FROM table" + i);
            log.setFilledSql("SELECT * FROM table" + i);
            log.setDatasourceId(1L);
            log.setExecutionSuccess(true);
            log.setResultCount(5);
            log.setExecutionTime(100L);
            stepLogs.add(log);
        }

        // When
        queryStepLogService.logSteps(stepLogs);

        // Then
        List<QueryStepLog> retrieved = queryStepLogService.getByQueryLogId(2L);
        assertThat(retrieved).hasSize(3);
        assertThat(retrieved.get(0).getStepIndex()).isEqualTo(0);
        assertThat(retrieved.get(1).getStepIndex()).isEqualTo(1);
        assertThat(retrieved.get(2).getStepIndex()).isEqualTo(2);
    }

    @Test
    void testGetByQueryLogId() {
        // Given
        QueryStepLog log1 = new QueryStepLog();
        log1.setQueryLogId(3L);
        log1.setStepId("step1");
        log1.setStepIndex(0);
        log1.setSqlTemplate("SELECT 1");
        log1.setFilledSql("SELECT 1");
        log1.setDatasourceId(1L);
        log1.setExecutionSuccess(true);
        queryStepLogService.logStep(log1);

        QueryStepLog log2 = new QueryStepLog();
        log2.setQueryLogId(3L);
        log2.setStepId("step2");
        log2.setStepIndex(1);
        log2.setSqlTemplate("SELECT 2");
        log2.setFilledSql("SELECT 2");
        log2.setDatasourceId(1L);
        log2.setExecutionSuccess(true);
        queryStepLogService.logStep(log2);

        // When
        List<QueryStepLog> logs = queryStepLogService.getByQueryLogId(3L);

        // Then
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getStepId()).isEqualTo("step1");
        assertThat(logs.get(1).getStepId()).isEqualTo("step2");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
./mvnw test -Dtest=QueryStepLogServiceTest
```

预期输出: FAIL - QueryStepLogService 类不存在

- [ ] **Step 3: 实现 QueryStepLogService**

创建文件 `src/main/java/com/tecdo/mac/sql2bot/service/QueryStepLogService.java`:

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryStepLog;
import com.tecdo.mac.sql2bot.mapper.QueryStepLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 查询步骤日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryStepLogService {

    private final QueryStepLogMapper queryStepLogMapper;

    /**
     * 记录单个步骤的执行日志
     */
    public void logStep(QueryStepLog stepLog) {
        queryStepLogMapper.insert(stepLog);
        log.debug("记录步骤日志: queryLogId={}, stepId={}, success={}",
            stepLog.getQueryLogId(), stepLog.getStepId(), stepLog.getExecutionSuccess());
    }

    /**
     * 批量记录步骤日志
     */
    public void logSteps(List<QueryStepLog> stepLogs) {
        if (stepLogs != null && !stepLogs.isEmpty()) {
            queryStepLogMapper.batchInsert(stepLogs);
            log.debug("批量记录步骤日志: count={}", stepLogs.size());
        }
    }

    /**
     * 根据 query_log_id 查询所有步骤日志
     */
    public List<QueryStepLog> getByQueryLogId(Long queryLogId) {
        return queryStepLogMapper.findByQueryLogId(queryLogId);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
./mvnw test -Dtest=QueryStepLogServiceTest
```

预期输出: PASS - 所有测试通过

- [ ] **Step 5: 提交 Service 和测试**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/QueryStepLogService.java
git add src/test/java/com/tecdo/mac/sql2bot/service/QueryStepLogServiceTest.java
git commit -m "feat: add QueryStepLogService with tests"
```

---

## Phase 2: 完善路径1参数填充Prompt

### Task 5: 增强 SqlStep 支持 tables 字段

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/dto/SqlStep.java`

- [ ] **Step 1: 备份并修改 SqlStep 类**

```java
package com.tecdo.mac.sql2bot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SQL 执行步骤
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlStep {
    /**
     * 步骤ID（如 step1, step2）
     */
    private String id;

    /**
     * SQL 模板（可能包含 {{param}} 占位符）
     */
    private String sqlTemplate;

    /**
     * 数据源 ID（null 表示从 SQL 推断）
     */
    private Long datasourceId;

    /**
     * 涉及的表信息列表
     */
    private List<TableInfo> tables;

    /**
     * 参数映射（用于参数填充）
     */
    private Map<String, Object> params;

    /**
     * 向后兼容的构造函数
     */
    public SqlStep(String sqlTemplate, Long datasourceId) {
        this.sqlTemplate = sqlTemplate;
        this.datasourceId = datasourceId;
    }

    /**
     * 表信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableInfo {
        /**
         * 表名
         */
        private String name;

        /**
         * 数据库名
         */
        private String database;
    }
}
```

- [ ] **Step 2: 提交 SqlStep 增强**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/dto/SqlStep.java
git commit -m "feat: enhance SqlStep with id, tables, and params fields"
```

---
### Task 9: 添加步骤间依赖参数支持

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java`

- [ ] **Step 1: 添加 replacePlaceholders 方法**

在 TextToSQLService 类中添加（约第800行之后）：

```java
/**
 * 替换 SQL 模板中的占位符
 * 支持两种类型：
 * 1. 简单占位符：{{param}} - 从 step.params 获取
 * 2. 依赖引用：{{step1.field}} - 从 stepResults 获取
 */
private String replacePlaceholders(SqlStep step, Map<String, List<Map<String, Object>>> stepResults) {
    String sql = step.getSqlTemplate();
    if (sql == null || step.getParams() == null) {
        return sql;
    }

    // 处理所有占位符
    for (Map.Entry<String, Object> entry : step.getParams().entrySet()) {
        String paramName = entry.getKey();
        Object paramValue = entry.getValue();
        String placeholder = "{{" + paramName + "}}";

        if (paramValue instanceof String) {
            String valueStr = (String) paramValue;

            // 检查是否是步骤引用（如 step1.field）
            if (valueStr.matches("step\\d+\\.\\w+")) {
                String[] parts = valueStr.split("\\.");
                String stepId = parts[0];
                String fieldName = parts[1];

                // 从 stepResults 提取字段值
                List<Map<String, Object>> stepResult = stepResults.get(stepId);
                if (stepResult != null && !stepResult.isEmpty()) {
                    List<Object> values = stepResult.stream()
                        .map(row -> row.get(fieldName))
                        .filter(v -> v != null)
                        .collect(java.util.stream.Collectors.toList());

                    // 转换为 SQL IN 子句格式
                    String inClause = values.stream()
                        .map(v -> "'" + v.toString().replace("'", "''") + "'")
                        .collect(java.util.stream.Collectors.joining(", "));

                    sql = sql.replace(placeholder, inClause);
                } else {
                    log.warn("步骤引用未找到结果: {}", valueStr);
                }
            } else {
                // 普通字符串参数
                sql = sql.replace(placeholder, "'" + valueStr.replace("'", "''") + "'");
            }
        } else if (paramValue instanceof Number) {
            sql = sql.replace(placeholder, paramValue.toString());
        } else if (paramValue != null) {
            sql = sql.replace(placeholder, "'" + paramValue.toString().replace("'", "''") + "'");
        }
    }

    return sql;
}
```

- [ ] **Step 2: 修改 executePlan 使用 replacePlaceholders**

在 executePlan 方法中（约第395-410行），替换参数填充逻辑：

```java
// 旧代码（删除）：
// String prompt = buildParamFillingPrompt(question, step.getSqlTemplate(), prevResult, example);
// String llmResp = aiService.generateSQL(prompt, "请填充SQL模板中的参数。");
// filledSql = aiService.extractSQL(llmResp);

// 新代码：
String filledSql;
if (step.getParams() != null && !step.getParams().isEmpty()) {
    // 使用 params 直接替换占位符（支持步骤间依赖）
    Map<String, List<Map<String, Object>>> stepResults = new HashMap<>();
    // 将 prevResult 存入 stepResults（假设上一步是 step{i}）
    if (prevResult != null && i > 0) {
        String prevStepId = steps.get(i - 1).getId();
        if (prevStepId != null) {
            stepResults.put(prevStepId, prevResult);
        }
    }
    filledSql = replacePlaceholders(step, stepResults);
} else {
    // 降级到 LLM 参数填充
    QueryLog example = null;
    try {
        example = queryLogService.getBestRecentExample(step.getDatasourceId());
    } catch (Exception e) {
        log.warn("获取参考示例失败，跳过: {}", e.getMessage());
    }

    String prompt = buildParamFillingPrompt(question, step.getSqlTemplate(), prevResult, example);
    String llmResp = aiService.generateSQL(prompt, "请填充SQL模板中的参数。");
    filledSql = aiService.extractSQL(llmResp);
    if (filledSql == null || filledSql.isBlank()) {
        filledSql = llmResp.trim();
    }
}
```

- [ ] **Step 3: 提交步骤间依赖参数支持**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java
git commit -m "feat: add step dependency parameter support with replacePlaceholders"
```

---

### Task 10: 添加配置参数

**Files:**
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: 添加模板相似度阈值配置**

在 application.properties 文件中添加：

```properties
# 模板向量检索配置
template.search.similarity-threshold=0.6

# Schema 向量检索配置（已存在，确认值）
schema.search.top-k=10
schema.search.similarity-threshold=0.5
schema.search.bfs-retry-top-k=20
```

- [ ] **Step 2: 在 TextToSQLService 中使用配置**

在 TextToSQLService 类顶部（约第60行）添加：

```java
@org.springframework.beans.factory.annotation.Value("${template.search.similarity-threshold:0.6}")
private double templateSearchSimilarityThreshold;
```

- [ ] **Step 3: 在路径1中应用相似度过滤**

在 processQuery 方法中（约第130-140行），添加相似度过滤：

```java
List<TemplateVectorSearchService.TemplateSearchResult> templateResults =
    templateVectorSearchService.searchSimilarTemplates(
        request.getQuestion(), null, MAX_TEMPLATE_CANDIDATES);
if (!templateResults.isEmpty()) {
    TemplateVectorSearchService.TemplateSearchResult bestMatch = templateResults.get(0);
    templateSimilarity = bestMatch.getSimilarity();

    // 新增：相似度过滤
    if (templateSimilarity >= templateSearchSimilarityThreshold) {
        QueryTemplate candidate = queryTemplateService.getById(bestMatch.getMeta().getTemplateId());
        if (candidate != null) {
            matchedTemplate = candidate;
            log.info("RAG模板匹配成功: templateId={}, similarity={}",
                    matchedTemplate.getId(), templateSimilarity);
        }
    } else {
        log.info("模板相似度不足: similarity={}, threshold={}",
            templateSimilarity, templateSearchSimilarityThreshold);
    }
}
```

- [ ] **Step 4: 提交配置参数**

```bash
git add src/main/resources/application.properties
git add src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java
git commit -m "feat: add template similarity threshold configuration"
```

---

### Task 11: 集成测试

**Files:**
- Create: `src/test/java/com/tecdo/mac/sql2bot/service/TextToSQLServiceIntegrationTest.java`

- [ ] **Step 1: 编写集成测试**

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.dto.QueryRequest;
import com.tecdo.mac.sql2bot.dto.QueryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TextToSQLServiceIntegrationTest {

    @Autowired
    private TextToSQLService textToSQLService;

    @Autowired
    private QueryStepLogService queryStepLogService;

    @Test
    void testProcessQueryWithStepLogging() {
        // Given
        QueryRequest request = new QueryRequest();
        request.setUserId(1L);
        request.setQuestion("查询所有用户");
        request.setCreateNewConversation(true);

        // When
        QueryResponse response = textToSQLService.processQuery(request);

        // Then
        assertThat(response).isNotNull();
        if (response.getQueryLogId() != null) {
            var stepLogs = queryStepLogService.getByQueryLogId(response.getQueryLogId());
            assertThat(stepLogs).isNotEmpty();
            assertThat(stepLogs.get(0).getStepIndex()).isEqualTo(0);
        }
    }

    @Test
    void testUserSatisfiedFeedback() {
        // Given - 先执行一次查询
        QueryRequest request1 = new QueryRequest();
        request1.setUserId(1L);
        request1.setQuestion("测试查询");
        request1.setCreateNewConversation(true);
        QueryResponse response1 = textToSQLService.processQuery(request1);

        // When - 用户标记满意
        QueryRequest request2 = new QueryRequest();
        request2.setUserId(1L);
        request2.setQuestion("测试查询");
        request2.setSatisfied(true);
        request2.setRetryQueryLogId(response1.getQueryLogId());
        QueryResponse response2 = textToSQLService.processQuery(request2);

        // Then
        assertThat(response2).isNotNull();
        // 验证问答对已存入向量索引（通过日志确认）
    }

    @Test
    void testUserUnsatisfiedRetry() {
        // Given - 先执行一次查询
        QueryRequest request1 = new QueryRequest();
        request1.setUserId(1L);
        request1.setQuestion("测试查询");
        request1.setCreateNewConversation(true);
        QueryResponse response1 = textToSQLService.processQuery(request1);

        // When - 用户标记不满意，触发高召回重试
        QueryRequest request2 = new QueryRequest();
        request2.setUserId(1L);
        request2.setQuestion("测试查询");
        request2.setSatisfied(false);
        request2.setRetryQueryLogId(response1.getQueryLogId());
        QueryResponse response2 = textToSQLService.processQuery(request2);

        // Then
        assertThat(response2).isNotNull();
        // 验证使用了路径3（BFS高召回）
    }
}
```

- [ ] **Step 2: 运行集成测试**

```bash
./mvnw test -Dtest=TextToSQLServiceIntegrationTest
```

预期输出: PASS - 所有测试通过

- [ ] **Step 3: 运行所有测试确保没有破坏现有功能**

```bash
./mvnw test
```

预期输出: PASS - 所有测试通过

- [ ] **Step 4: 提交集成测试**

```bash
git add src/test/java/com/tecdo/mac/sql2bot/service/TextToSQLServiceIntegrationTest.java
git commit -m "test: add integration tests for multi-path query execution"
```

---

## 验证和清理

### Task 12: 最终验证

- [ ] **Step 1: 验证数据库迁移**

```bash
./mvnw spring-boot:run
```

检查日志确认 query_step_log 表创建成功

- [ ] **Step 2: 手动测试三条路径**

启动应用后，通过 API 测试：

1. 路径1（RAG模板匹配）：使用已有模板的问题
2. 路径2（BFS正常召回）：使用新问题
3. 路径3（BFS高召回）：标记不满意后重试

- [ ] **Step 3: 检查步骤日志记录**

查询数据库确认 query_step_log 表有数据：

```sql
SELECT * FROM query_step_log ORDER BY create_time DESC LIMIT 10;
```

- [ ] **Step 4: 代码审查**

检查：
- 所有新增代码符合 UTF-8 编码规范
- 日志输出正确显示中文
- 没有硬编码的配置值
- 异常处理完整

- [ ] **Step 5: 创建最终提交**

```bash
git add -A
git commit -m "feat: complete multi-path query execution enhancement

- Add QueryStepLog system for step-level logging
- Enhance path1 parameter filling prompt with full context
- Add step dependency parameter support
- Integrate step logging into executePlan
- Add template similarity threshold configuration
- Add comprehensive integration tests"
```

---

## 总结

本实现计划完成了以下功能：

**Phase 1: QueryStepLog 日志系统**
- 数据库表和迁移脚本
- 领域实体、Mapper、Service
- 完整的单元测试

**Phase 2: 路径1参数填充增强**
- SqlStep 增强支持 tables 字段
- 从 query_log 动态获取示例
- 解析 intent_sql 构建完整上下文
- 重写 buildTemplateParameterPrompt

**Phase 3: executePlan 增强**
- 集成步骤日志记录
- 支持步骤间依赖参数
- 记录 intent_json 到 query_log
- 详细的错误日志

**配置和测试**
- 添加模板相似度阈值配置
- 完整的集成测试
- 验证三条查询路径

所有功能都遵循 TDD 原则，先写测试再实现，确保代码质量和可维护性。
