# 意图分析与模板系统实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 TextToSQLService 中集成意图分析和模板缓存系统，优化查询性能

**Architecture:** 两层模板检索（精确匹配 + 向量相似度），模板命中直接填参执行，未命中调用 LLM 生成并保存模板，用户评分驱动模板优化

**Tech Stack:** Spring Boot 4.0.3, MyBatis, MySQL, RedisStack (RediSearch + Vector), Claude API

---

## Chunk 1: 数据库 Schema + Domain 实体

### Task 1: 创建数据库表（MySQL）

**Files:**
- Modify: `src/main/resources/schema.sql` (追加到文件末尾)

- [ ] **Step 1: 追加 query_template 表定义**

在 `schema.sql` 文件末尾追加：

```sql
-- 意图分析与模板系统表

-- 1. SQL 查询模板表
CREATE TABLE IF NOT EXISTS query_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    skeleton VARCHAR(1000) NOT NULL COMMENT '骨架字符串（精确匹配key）',
    sql_template TEXT NOT NULL COMMENT 'SQL模板（带占位符）',
    entity VARCHAR(100) COMMENT '实体类型',
    intent VARCHAR(50) NOT NULL COMMENT '意图类型',
    supported_dimensions JSON COMMENT '支持的维度字段列表',
    supported_metrics JSON COMMENT '支持的指标列表',
    parameters JSON COMMENT '参数定义列表',
    example_question VARCHAR(500) COMMENT '示例问题',
    example_intent_json TEXT COMMENT '示例意图JSON',
    score DECIMAL(3,2) DEFAULT 0.00 COMMENT '平均评分（0-5）',
    rating_count INT DEFAULT 0 COMMENT '评分次数',
    usage_count INT DEFAULT 0 COMMENT '使用次数',
    datasource_id BIGINT COMMENT '关联数据源',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_skeleton (skeleton),
    INDEX idx_intent (intent),
    INDEX idx_entity (entity),
    INDEX idx_score (score DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL查询模板';
```

- [ ] **Step 2: 追加 template_rating 表定义**

```sql
-- 2. 模板评分记录表
CREATE TABLE IF NOT EXISTS template_rating (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    score INT NOT NULL COMMENT '评分（1-5）',
    conversation_id BIGINT COMMENT '关联会话ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_template_user (template_id, user_id),
    INDEX idx_template_id (template_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板评分记录';
```

- [ ] **Step 3: 追加 intent_few_shot 表定义**

```sql
-- 3. 意图分类 Few-shot 示例表
CREATE TABLE IF NOT EXISTS intent_few_shot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    intent VARCHAR(50) NOT NULL COMMENT '目标意图类型',
    question VARCHAR(500) NOT NULL COMMENT '示例问题',
    intent_json TEXT NOT NULL COMMENT '正确的意图JSON',
    skeleton VARCHAR(1000) COMMENT '骨架字符串',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    datasource_id BIGINT COMMENT '关联数据源',
    created_by BIGINT COMMENT '创建人',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_intent (intent),
    INDEX idx_active (is_active),
    INDEX idx_datasource (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图分类Few-shot示例';
```

- [ ] **Step 4: 追加 query_log 表定义**

```sql
-- 4. 查询日志表
CREATE TABLE IF NOT EXISTS query_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    conversation_id BIGINT COMMENT '会话ID',
    question VARCHAR(500) NOT NULL COMMENT '用户问题',
    intent VARCHAR(50) COMMENT '识别的意图',
    intent_json TEXT COMMENT '意图JSON',
    skeleton VARCHAR(1000) COMMENT '骨架字符串',
    template_id BIGINT COMMENT '使用的模板ID',
    is_from_template BOOLEAN DEFAULT FALSE COMMENT '是否使用模板',
    generated_sql TEXT COMMENT '生成的SQL',
    execution_success BOOLEAN COMMENT '执行是否成功',
    execution_time BIGINT COMMENT '执行时间（毫秒）',
    result_count INT COMMENT '结果行数',
    rating INT COMMENT '用户评分（1-5）',
    is_labeled BOOLEAN DEFAULT FALSE COMMENT '是否已标注为few-shot',
    datasource_id BIGINT COMMENT '数据源ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_template_id (template_id),
    INDEX idx_created_at (created_at),
    INDEX idx_intent (intent)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询日志';
```

- [ ] **Step 5: 验证 SQL 语法**

Run: `mysql -u root -p123456 < src/main/resources/schema.sql`
Expected: 无语法错误，表创建成功

- [ ] **Step 6: Commit schema changes**

```bash
git add src/main/resources/schema.sql
git commit -m "feat(schema): 添加意图分析和模板系统表"
```

---

### Task 2: 创建 QueryTemplate 实体类

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/domain/QueryTemplate.java`

- [ ] **Step 1: 创建 QueryTemplate 实体**

```java
package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SQL 查询模板实体
 */
@Data
public class QueryTemplate {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 骨架字符串（精确匹配key）
     */
    private String skeleton;

    /**
     * SQL模板（带占位符）
     */
    private String sqlTemplate;

    /**
     * 实体类型
     */
    private String entity;

    /**
     * 意图类型
     */
    private String intent;

    /**
     * 支持的维度字段列表（JSON）
     */
    private String supportedDimensions;

    /**
     * 支持的指标列表（JSON）
     */
    private String supportedMetrics;

    /**
     * 参数定义列表（JSON）
     */
    private String parameters;

    /**
     * 示例问题
     */
    private String exampleQuestion;

    /**
     * 示例意图JSON
     */
    private String exampleIntentJson;

    /**
     * 平均评分（0-5）
     */
    private BigDecimal score;

    /**
     * 评分次数
     */
    private Integer ratingCount;

    /**
     * 使用次数
     */
    private Integer usageCount;

    /**
     * 关联数据源
     */
    private Long datasourceId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/domain/QueryTemplate.java
git commit -m "feat(domain): 添加 QueryTemplate 实体类"
```

---

### Task 3: 创建 TemplateRating 实体类

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/domain/TemplateRating.java`

- [ ] **Step 1: 创建 TemplateRating 实体**

```java
package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 模板评分记录实体
 */
@Data
public class TemplateRating {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 模板ID
     */
    private Long templateId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 评分（1-5）
     */
    private Integer score;

    /**
     * 关联会话ID
     */
    private Long conversationId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/domain/TemplateRating.java
git commit -m "feat(domain): 添加 TemplateRating 实体类"
```

---

### Task 4: 创建 IntentFewShot 实体类

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/domain/IntentFewShot.java`

- [ ] **Step 1: 创建 IntentFewShot 实体**

```java
package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 意图分类 Few-shot 示例实体
 */
@Data
public class IntentFewShot {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 目标意图类型
     */
    private String intent;

    /**
     * 示例问题
     */
    private String question;

    /**
     * 正确的意图JSON
     */
    private String intentJson;

    /**
     * 骨架字符串
     */
    private String skeleton;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 关联数据源
     */
    private Long datasourceId;

    /**
     * 创建人
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/domain/IntentFewShot.java
git commit -m "feat(domain): 添加 IntentFewShot 实体类"
```

---

### Task 5: 创建 QueryLog 实体类

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/domain/QueryLog.java`

- [ ] **Step 1: 创建 QueryLog 实体**

```java
package com.tecdo.mac.sql2bot.domain;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 查询日志实体
 */
@Data
public class QueryLog {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 用户问题
     */
    private String question;

    /**
     * 识别的意图
     */
    private String intent;

    /**
     * 意图JSON
     */
    private String intentJson;

    /**
     * 骨架字符串
     */
    private String skeleton;

    /**
     * 使用的模板ID
     */
    private Long templateId;

    /**
     * 是否使用模板
     */
    private Boolean isFromTemplate;

    /**
     * 生成的SQL
     */
    private String generatedSql;

    /**
     * 执行是否成功
     */
    private Boolean executionSuccess;

    /**
     * 执行时间（毫秒）
     */
    private Long executionTime;

    /**
     * 结果行数
     */
    private Integer resultCount;

    /**
     * 用户评分（1-5）
     */
    private Integer rating;

    /**
     * 是否已标注为few-shot
     */
    private Boolean isLabeled;

    /**
     * 数据源ID
     */
    private Long datasourceId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/domain/QueryLog.java
git commit -m "feat(domain): 添加 QueryLog 实体类"
```

---

## Chunk 2: MyBatis Mappers

### Task 6: 创建 QueryTemplateMapper

### Task 6: 创建 QueryTemplateMapper

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/mapper/QueryTemplateMapper.java`
- Create: `src/main/resources/mapper/QueryTemplateMapper.xml`

- [ ] **Step 1: 创建 Mapper 接口**

```java
package com.tecdo.mac.sql2bot.mapper;

import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * QueryTemplate Mapper
 */
@Mapper
public interface QueryTemplateMapper {

    /**
     * 插入模板
     */
    int insert(QueryTemplate template);

    /**
     * 根据ID查询
     */
    QueryTemplate selectById(@Param("id") Long id);

    /**
     * 根据骨架精确查询
     */
    QueryTemplate selectBySkeleton(@Param("skeleton") String skeleton);

    /**
     * 查询列表（支持筛选）
     */
    List<QueryTemplate> selectList(
        @Param("intent") String intent,
        @Param("entity") String entity,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    /**
     * 统计总数
     */
    int count(
        @Param("intent") String intent,
        @Param("entity") String entity
    );

    /**
     * 更新评分（原子操作）
     */
    int updateRating(
        @Param("id") Long id,
        @Param("newScore") int newScore
    );

    /**
     * 增加使用次数
     */
    int incrementUsageCount(@Param("id") Long id);

    /**
     * 删除模板
     */
    int deleteById(@Param("id") Long id);

    /**
     * 获取统计信息
     */
    QueryTemplateStats getStats();

    /**
     * 统计信息内部类
     */
    class QueryTemplateStats {
        public Long totalTemplates;
        public Double averageScore;
        public Long totalUsageCount;
        public Long totalRatingCount;
    }
}
```

- [ ] **Step 2: 创建 MyBatis XML 映射**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.tecdo.mac.sql2bot.mapper.QueryTemplateMapper">

    <resultMap id="BaseResultMap" type="com.tecdo.mac.sql2bot.domain.QueryTemplate">
        <id column="id" property="id"/>
        <result column="skeleton" property="skeleton"/>
        <result column="sql_template" property="sqlTemplate"/>
        <result column="entity" property="entity"/>
        <result column="intent" property="intent"/>
        <result column="supported_dimensions" property="supportedDimensions"/>
        <result column="supported_metrics" property="supportedMetrics"/>
        <result column="parameters" property="parameters"/>
        <result column="example_question" property="exampleQuestion"/>
        <result column="example_intent_json" property="exampleIntentJson"/>
        <result column="score" property="score"/>
        <result column="rating_count" property="ratingCount"/>
        <result column="usage_count" property="usageCount"/>
        <result column="datasource_id" property="datasourceId"/>
        <result column="created_at" property="createdAt"/>
        <result column="updated_at" property="updatedAt"/>
    </resultMap>

    <insert id="insert" parameterType="com.tecdo.mac.sql2bot.domain.QueryTemplate"
            useGeneratedKeys="true" keyProperty="id">
        INSERT INTO query_template (
            skeleton, sql_template, entity, intent,
            supported_dimensions, supported_metrics, parameters,
            example_question, example_intent_json,
            score, rating_count, usage_count, datasource_id
        ) VALUES (
            #{skeleton}, #{sqlTemplate}, #{entity}, #{intent},
            #{supportedDimensions}, #{supportedMetrics}, #{parameters},
            #{exampleQuestion}, #{exampleIntentJson},
            #{score}, #{ratingCount}, #{usageCount}, #{datasourceId}
        )
    </insert>

    <select id="selectById" resultMap="BaseResultMap">
        SELECT * FROM query_template WHERE id = #{id}
    </select>

    <select id="selectBySkeleton" resultMap="BaseResultMap">
        SELECT * FROM query_template WHERE skeleton = #{skeleton}
    </select>

    <select id="selectList" resultMap="BaseResultMap">
        SELECT * FROM query_template
        <where>
            <if test="intent != null and intent != ''">
                AND intent = #{intent}
            </if>
            <if test="entity != null and entity != ''">
                AND entity = #{entity}
            </if>
        </where>
        ORDER BY score DESC, usage_count DESC
        LIMIT #{offset}, #{limit}
    </select>

    <select id="count" resultType="int">
        SELECT COUNT(*) FROM query_template
        <where>
            <if test="intent != null and intent != ''">
                AND intent = #{intent}
            </if>
            <if test="entity != null and entity != ''">
                AND entity = #{entity}
            </if>
        </where>
    </select>

    <update id="updateRating">
        UPDATE query_template
        SET score = (score * rating_count + #{newScore}) / (rating_count + 1),
            rating_count = rating_count + 1
        WHERE id = #{id}
    </update>

    <update id="incrementUsageCount">
        UPDATE query_template
        SET usage_count = usage_count + 1
        WHERE id = #{id}
    </update>

    <delete id="deleteById">
        DELETE FROM query_template WHERE id = #{id}
    </delete>

    <select id="getStats" resultType="com.tecdo.mac.sql2bot.mapper.QueryTemplateMapper$QueryTemplateStats">
        SELECT
            COUNT(*) as totalTemplates,
            AVG(score) as averageScore,
            SUM(usage_count) as totalUsageCount,
            SUM(rating_count) as totalRatingCount
        FROM query_template
    </select>

</mapper>
```

- [ ] **Step 3: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/mapper/QueryTemplateMapper.java
git add src/main/resources/mapper/QueryTemplateMapper.xml
git commit -m "feat(mapper): 添加 QueryTemplateMapper"
```

---

## Chunk 3: Service 层 - QueryTemplateService

### Task 12: 创建 QueryTemplateService

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/service/QueryTemplateService.java`

- [ ] **Step 1: 创建 QueryTemplateService 类框架**

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.dto.intent.IntentAnalysisResponse;
import com.tecdo.mac.sql2bot.mapper.QueryTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 查询模板服务
 * 负责模板的检索、验证、保存和评分更新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryTemplateService {

    private final QueryTemplateMapper templateMapper;

    /**
     * 两层模板检索：精确匹配 + 向量相似度
     *
     * @param skeleton 骨架字符串
     * @return 匹配的模板（按评分排序），未找到返回 null
     */
    public QueryTemplate findTemplate(String skeleton) {
        // TODO: 实现两层检索逻辑
        return null;
    }

    /**
     * 验证模板是否适用于当前意图
     *
     * @param template 候选模板
     * @param intent 当前意图
     * @return true 如果模板适用
     */
    public boolean validateTemplate(QueryTemplate template, IntentAnalysisResponse intent) {
        // TODO: 实现字段级验证
        return false;
    }

    /**
     * 保存 LLM 生成的模板
     *
     * @param skeleton 骨架字符串
     * @param sqlTemplate SQL 模板
     * @param intent 意图信息
     * @param datasourceId 数据源 ID
     * @return 保存的模板
     */
    @Transactional
    public QueryTemplate saveGeneratedTemplate(String skeleton, String sqlTemplate,
                                               IntentAnalysisResponse intent, Long datasourceId) {
        // TODO: 实现模板保存逻辑
        return null;
    }

    /**
     * 更新模板评分
     *
     * @param templateId 模板 ID
     * @param score 新评分（1-5）
     */
    @Transactional
    public void updateRating(Long templateId, int score) {
        // TODO: 实现评分更新逻辑
    }

    /**
     * 增加模板使用次数
     *
     * @param templateId 模板 ID
     */
    @Transactional
    public void incrementUsageCount(Long templateId) {
        templateMapper.incrementUsageCount(templateId);
    }
}
```

- [ ] **Step 2: 实现 findTemplate 方法（精确匹配）**

```java
/**
 * 两层模板检索：精确匹配 + 向量相似度
 *
 * @param skeleton 骨架字符串
 * @return 匹配的模板（按评分排序），未找到返回 null
 */
public QueryTemplate findTemplate(String skeleton) {
    log.debug("查找模板: skeleton={}", skeleton);

    // 第一层：精确匹配（按评分排序）
    List<QueryTemplate> exactMatches = templateMapper.findBySkeleton(skeleton);
    if (!exactMatches.isEmpty()) {
        QueryTemplate best = exactMatches.get(0);
        log.info("精确匹配到模板: id={}, score={}", best.getId(), best.getScore());
        return best;
    }

    // TODO: 第二层：向量相似度搜索（RedisStack）
    log.debug("精确匹配未找到，暂不支持向量搜索");
    return null;
}
```

- [ ] **Step 3: 实现 validateTemplate 方法（字段级验证）**

```java
/**
 * 验证模板是否适用于当前意图
 *
 * @param template 候选模板
 * @param intent 当前意图
 * @return true 如果模板适用
 */
public boolean validateTemplate(QueryTemplate template, IntentAnalysisResponse intent) {
    log.debug("验证模板: templateId={}, intent={}", template.getId(), intent.getIntent());

    // 1. 意图类型必须匹配
    if (!template.getIntent().equals(intent.getIntent())) {
        log.warn("意图类型不匹配: template={}, intent={}", template.getIntent(), intent.getIntent());
        return false;
    }

    // 2. 实体类型必须匹配（如果模板指定了实体）
    if (template.getEntity() != null && !template.getEntity().equals(intent.getEntity())) {
        log.warn("实体类型不匹配: template={}, intent={}", template.getEntity(), intent.getEntity());
        return false;
    }

    // 3. 维度字段验证（intent 的维度必须是模板支持的子集）
    if (template.getSupportedDimensions() != null && !intent.getDimensions().isEmpty()) {
        try {
            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            List<String> supportedDims = mapper.readValue(
                template.getSupportedDimensions(),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );

            for (String dim : intent.getDimensions()) {
                if (!supportedDims.contains(dim)) {
                    log.warn("维度字段不支持: dim={}, supported={}", dim, supportedDims);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("解析 supportedDimensions 失败", e);
            return false;
        }
    }

    // 4. 指标字段验证（intent 的指标必须是模板支持的子集）
    if (template.getSupportedMetrics() != null && !intent.getMetrics().isEmpty()) {
        try {
            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            List<String> supportedMetrics = mapper.readValue(
                template.getSupportedMetrics(),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );

            for (String metric : intent.getMetrics()) {
                if (!supportedMetrics.contains(metric)) {
                    log.warn("指标字段不支持: metric={}, supported={}", metric, supportedMetrics);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("解析 supportedMetrics 失败", e);
            return false;
        }
    }

    log.info("模板验证通过: templateId={}", template.getId());
    return true;
}
```

- [ ] **Step 4: 实现 saveGeneratedTemplate 方法**

```java
/**
 * 保存 LLM 生成的模板
 *
 * @param skeleton 骨架字符串
 * @param sqlTemplate SQL 模板
 * @param intent 意图信息
 * @param datasourceId 数据源 ID
 * @return 保存的模板
 */
@Transactional
public QueryTemplate saveGeneratedTemplate(String skeleton, String sqlTemplate,
                                           IntentAnalysisResponse intent, Long datasourceId) {
    log.info("保存生成的模板: skeleton={}, intent={}", skeleton, intent.getIntent());

    QueryTemplate template = new QueryTemplate();
    template.setSkeleton(skeleton);
    template.setSqlTemplate(sqlTemplate);
    template.setIntent(intent.getIntent());
    template.setEntity(intent.getEntity());
    template.setDatasourceId(datasourceId);

    // 序列化维度和指标列表
    try {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        if (!intent.getDimensions().isEmpty()) {
            template.setSupportedDimensions(mapper.writeValueAsString(intent.getDimensions()));
        }
        if (!intent.getMetrics().isEmpty()) {
            template.setSupportedMetrics(mapper.writeValueAsString(intent.getMetrics()));
        }
    } catch (Exception e) {
        log.error("序列化维度/指标失败", e);
    }

    // 保存到数据库
    templateMapper.insert(template);
    log.info("模板保存成功: id={}", template.getId());

    // TODO: 同步到 RedisStack（向量索引）

    return template;
}
```

- [ ] **Step 5: 实现 updateRating 方法**

```java
/**
 * 更新模板评分
 *
 * @param templateId 模板 ID
 * @param score 新评分（1-5）
 */
@Transactional
public void updateRating(Long templateId, int score) {
    if (score < 1 || score > 5) {
        throw new IllegalArgumentException("评分必须在 1-5 之间");
    }

    log.info("更新模板评分: templateId={}, score={}", templateId, score);
    templateMapper.updateRating(templateId, score);

    // TODO: 同步更新 RedisStack 中的评分权重
}
```

- [ ] **Step 6: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/QueryTemplateService.java
git commit -m "feat(service): 添加 QueryTemplateService 实现模板检索和验证"
```

---

## Chunk 4: Service 层 - TemplateParameterService

### Task 13: 创建 TemplateParameterService

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/service/TemplateParameterService.java`

- [ ] **Step 1: 创建 TemplateParameterService 类框架**

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.dto.TemplateParameter;
import com.tecdo.mac.sql2bot.dto.intent.IntentAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板参数服务
 * 负责从意图 JSON 提取参数并填充 SQL 模板
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateParameterService {

    private final ObjectMapper objectMapper;

    /**
     * 从意图中提取参数值并填充模板
     *
     * @param template SQL 模板
     * @param intent 意图信息
     * @return 填充后的 SQL
     */
    public String fillTemplate(QueryTemplate template, IntentAnalysisResponse intent) {
        // TODO: 实现参数提取和填充逻辑
        return null;
    }

    /**
     * 解析模板参数定义
     *
     * @param parametersJson 参数定义 JSON
     * @return 参数列表
     */
    private List<TemplateParameter> parseParameters(String parametersJson) {
        // TODO: 实现参数解析
        return null;
    }

    /**
     * 从意图中提取参数值
     *
     * @param param 参数定义
     * @param intent 意图信息
     * @return 参数值
     */
    private Object extractValue(TemplateParameter param, IntentAnalysisResponse intent) {
        // TODO: 实现值提取逻辑
        return null;
    }

    /**
     * 格式化参数值
     *
     * @param param 参数定义
     * @param value 原始值
     * @return 格式化后的值
     */
    private String formatValue(TemplateParameter param, Object value) {
        // TODO: 实现值格式化
        return null;
    }
}
```

- [ ] **Step 2: 实现 parseParameters 方法**

```java
/**
 * 解析模板参数定义
 *
 * @param parametersJson 参数定义 JSON
 * @return 参数列表
 */
private List<TemplateParameter> parseParameters(String parametersJson) {
    if (parametersJson == null || parametersJson.isEmpty()) {
        return List.of();
    }

    try {
        return objectMapper.readValue(
            parametersJson,
            objectMapper.getTypeFactory().constructCollectionType(List.class, TemplateParameter.class)
        );
    } catch (Exception e) {
        log.error("解析参数定义失败: {}", parametersJson, e);
        return List.of();
    }
}
```

- [ ] **Step 3: 实现 extractValue 方法**

```java
/**
 * 从意图中提取参数值
 *
 * @param param 参数定义
 * @param intent 意图信息
 * @return 参数值
 */
private Object extractValue(TemplateParameter param, IntentAnalysisResponse intent) {
    String source = param.getSource(); // 例如: "dimensions[0]", "metrics[0]", "filters.date"

    try {
        // 简单实现：支持 dimensions, metrics, filters
        if (source.startsWith("dimensions[")) {
            int index = Integer.parseInt(source.substring(11, source.indexOf(']')));
            return intent.getDimensions().get(index);
        } else if (source.startsWith("metrics[")) {
            int index = Integer.parseInt(source.substring(8, source.indexOf(']')));
            return intent.getMetrics().get(index);
        } else if (source.startsWith("filters.")) {
            String filterKey = source.substring(8);
            return intent.getFilters().get(filterKey);
        } else if (source.equals("entity")) {
            return intent.getEntity();
        }
    } catch (Exception e) {
        log.error("提取参数值失败: source={}", source, e);
    }

    return null;
}
```

- [ ] **Step 4: 实现 formatValue 方法**

```java
/**
 * 格式化参数值
 *
 * @param param 参数定义
 * @param value 原始值
 * @return 格式化后的值
 */
private String formatValue(TemplateParameter param, Object value) {
    if (value == null) {
        return "NULL";
    }

    String type = param.getType();

    switch (type) {
        case "DATE":
            // 日期格式转换
            try {
                LocalDate date = LocalDate.parse(value.toString());
                return "'" + date.format(DateTimeFormatter.ISO_DATE) + "'";
            } catch (Exception e) {
                log.error("日期格式化失败: {}", value, e);
                return "'" + value + "'";
            }

        case "ARRAY_TO_IN":
            // 数组转 IN 子句
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                String items = list.stream()
                    .map(item -> "'" + item + "'")
                    .collect(java.util.stream.Collectors.joining(", "));
                return "(" + items + ")";
            }
            return "'" + value + "'";

        case "STRING":
            // 字符串（加引号）
            return "'" + value.toString().replace("'", "''") + "'";

        case "NUMBER":
            // 数字（不加引号）
            return value.toString();

        case "DIMENSION":
        case "METRIC":
            // 字段名（不加引号）
            return value.toString();

        default:
            log.warn("未知参数类型: {}", type);
            return "'" + value + "'";
    }
}
```

- [ ] **Step 5: 实现 fillTemplate 方法**

```java
/**
 * 从意图中提取参数值并填充模板
 *
 * @param template SQL 模板
 * @param intent 意图信息
 * @return 填充后的 SQL
 */
public String fillTemplate(QueryTemplate template, IntentAnalysisResponse intent) {
    log.debug("填充模板: templateId={}", template.getId());

    String sql = template.getSqlTemplate();
    List<TemplateParameter> parameters = parseParameters(template.getParameters());

    if (parameters.isEmpty()) {
        log.debug("模板无参数，直接返回");
        return sql;
    }

    // 提取并格式化所有参数
    Map<String, String> paramValues = new HashMap<>();
    for (TemplateParameter param : parameters) {
        Object value = extractValue(param, intent);
        String formatted = formatValue(param, value);
        paramValues.put(param.getName(), formatted);
        log.debug("参数: {}={}", param.getName(), formatted);
    }

    // 替换占位符
    for (Map.Entry<String, String> entry : paramValues.entrySet()) {
        String placeholder = "{{" + entry.getKey() + "}}";
        sql = sql.replace(placeholder, entry.getValue());
    }

    log.info("模板填充完成: templateId={}", template.getId());
    return sql;
}
```

- [ ] **Step 6: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/TemplateParameterService.java
git commit -m "feat(service): 添加 TemplateParameterService 实现参数提取和填充"
```

---

## Chunk 4: Service 层 - QueryLogService 和 IntentFewShotService

### Task 14: 创建 QueryLogService

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/service/QueryLogService.java`

- [ ] **Step 1: 创建 QueryLogService 类**

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.dto.QueryLogFilter;
import com.tecdo.mac.sql2bot.dto.QueryLogStats;
import com.tecdo.mac.sql2bot.mapper.QueryLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 查询日志服务
 * 负责记录查询、更新评分、标注 few-shot 和统计分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryLogService {

    private final QueryLogMapper queryLogMapper;

    /**
     * 记录查询日志
     *
     * @param queryLog 查询日志
     * @return 保存的日志 ID
     */
    @Transactional
    public Long logQuery(QueryLog queryLog) {
        log.info("记录查询日志: userId={}, question={}",
            queryLog.getUserId(), queryLog.getQuestion());
        queryLogMapper.insert(queryLog);
        return queryLog.getId();
    }

    /**
     * 更新查询评分
     *
     * @param logId 日志 ID
     * @param rating 评分（1-5）
     */
    @Transactional
    public void updateRating(Long logId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分必须在 1-5 之间");
        }
        log.info("更新查询评分: logId={}, rating={}", logId, rating);
        queryLogMapper.updateRating(logId, rating);
    }

    /**
     * 标注为 few-shot 示例
     *
     * @param logId 日志 ID
     */
    @Transactional
    public void markAsLabeled(Long logId) {
        log.info("标注为 few-shot: logId={}", logId);
        queryLogMapper.markAsLabeled(logId);
    }

    /**
     * 根据条件查询日志列表
     *
     * @param filter 筛选条件
     * @return 日志列表
     */
    public List<QueryLog> findByFilter(QueryLogFilter filter) {
        log.debug("查询日志列表: filter={}", filter);
        return queryLogMapper.findByFilter(filter);
    }

    /**
     * 根据 ID 查询日志
     *
     * @param id 日志 ID
     * @return 日志详情
     */
    public QueryLog findById(Long id) {
        return queryLogMapper.findById(id);
    }

    /**
     * 获取查询统计信息
     *
     * @return 统计信息
     */
    public QueryLogStats getStats() {
        log.debug("获取查询统计信息");
        return queryLogMapper.getStats();
    }

    /**
     * 获取意图分布统计
     *
     * @return 意图 -> 数量的映射
     */
    public Map<String, Long> getIntentDistribution() {
        log.debug("获取意图分布统计");
        return queryLogMapper.getIntentDistribution();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/QueryLogService.java
git commit -m "feat(service): 添加 QueryLogService 实现查询日志管理"
```

---

### Task 15: 创建 IntentFewShotService

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/service/IntentFewShotService.java`

- [ ] **Step 1: 创建 IntentFewShotService 类**

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.IntentFewShot;
import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.dto.LabelFewShotRequest;
import com.tecdo.mac.sql2bot.dto.BatchLabelRequest;
import com.tecdo.mac.sql2bot.mapper.IntentFewShotMapper;
import com.tecdo.mac.sql2bot.mapper.QueryLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Few-shot 示例管理服务
 * 负责 few-shot 的增删改查和从查询日志创建 few-shot
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentFewShotService {

    private final IntentFewShotMapper fewShotMapper;
    private final QueryLogMapper queryLogMapper;
    private final QueryLogService queryLogService;

    /**
     * 获取所有 few-shot 示例
     *
     * @param intent 意图类型（可选）
     * @param datasourceId 数据源 ID（可选）
     * @return few-shot 列表
     */
    public List<IntentFewShot> findAll(String intent, Long datasourceId) {
        log.debug("查询 few-shot 列表: intent={}, datasourceId={}", intent, datasourceId);
        return fewShotMapper.findAll(intent, datasourceId);
    }

    /**
     * 获取启用的 few-shot 示例
     *
     * @param intent 意图类型（可选）
     * @param datasourceId 数据源 ID（可选）
     * @return 启用的 few-shot 列表
     */
    public List<IntentFewShot> findActiveExamples(String intent, Long datasourceId) {
        log.debug("查询启用的 few-shot: intent={}, datasourceId={}", intent, datasourceId);
        return fewShotMapper.selectActiveExamples(intent, datasourceId);
    }

    /**
     * 根据 ID 查询 few-shot
     *
     * @param id few-shot ID
     * @return few-shot 详情
     */
    public IntentFewShot findById(Long id) {
        return fewShotMapper.findById(id);
    }

    /**
     * 创建 few-shot 示例
     *
     * @param fewShot few-shot 对象
     * @return 保存的 few-shot ID
     */
    @Transactional
    public Long create(IntentFewShot fewShot) {
        log.info("创建 few-shot: intent={}, question={}",
            fewShot.getIntent(), fewShot.getQuestion());
        fewShotMapper.insert(fewShot);
        return fewShot.getId();
    }

    /**
     * 更新 few-shot 示例
     *
     * @param fewShot few-shot 对象
     */
    @Transactional
    public void update(IntentFewShot fewShot) {
        log.info("更新 few-shot: id={}", fewShot.getId());
        fewShotMapper.update(fewShot);
    }

    /**
     * 删除 few-shot 示例
     *
     * @param id few-shot ID
     */
    @Transactional
    public void delete(Long id) {
        log.info("删除 few-shot: id={}", id);
        fewShotMapper.deleteById(id);
    }

    /**
     * 切换 few-shot 启用状态
     *
     * @param id few-shot ID
     * @param isActive 是否启用
     */
    @Transactional
    public void toggleActive(Long id, boolean isActive) {
        log.info("切换 few-shot 状态: id={}, isActive={}", id, isActive);
        fewShotMapper.updateActive(id, isActive);
    }

    /**
     * 从查询日志创建 few-shot（支持修正意图）
     *
     * @param request 标注请求
     * @return 创建的 few-shot ID
     */
    @Transactional
    public Long labelFromQueryLog(LabelFewShotRequest request) {
        log.info("从查询日志创建 few-shot: logId={}", request.getLogId());

        // 1. 查询日志
        QueryLog queryLog = queryLogMapper.findById(request.getLogId());
        if (queryLog == null) {
            throw new IllegalArgumentException("查询日志不存在: " + request.getLogId());
        }

        // 2. 创建 few-shot
        IntentFewShot fewShot = new IntentFewShot();
        fewShot.setIntent(request.getCorrectedIntent() != null ?
            request.getCorrectedIntent() : queryLog.getIntent());
        fewShot.setQuestion(queryLog.getQuestion());
        fewShot.setIntentJson(request.getCorrectedIntentJson() != null ?
            request.getCorrectedIntentJson() : queryLog.getIntentJson());
        fewShot.setSkeleton(queryLog.getSkeleton());
        fewShot.setIsActive(true);
        fewShot.setDatasourceId(queryLog.getDatasourceId());
        fewShot.setCreatedBy(request.getCreatedBy());

        fewShotMapper.insert(fewShot);

        // 3. 标记日志已标注
        queryLogService.markAsLabeled(request.getLogId());

        log.info("Few-shot 创建成功: id={}", fewShot.getId());
        return fewShot.getId();
    }

    /**
     * 批量标注 few-shot
     *
     * @param request 批量标注请求
     * @return 创建的 few-shot ID 列表
     */
    @Transactional
    public List<Long> batchLabel(BatchLabelRequest request) {
        log.info("批量标注 few-shot: count={}", request.getLabels().size());

        return request.getLabels().stream()
            .map(this::labelFromQueryLog)
            .toList();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/IntentFewShotService.java
git commit -m "feat(service): 添加 IntentFewShotService 实现 few-shot 管理"
```

---

## Chunk 5: 集成到 TextToSQLService

### Task 16: 更新 TextToSQLService 集成意图分析和模板系统

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java`

- [ ] **Step 1: 添加新的服务依赖**

在 TextToSQLService 类中添加：

```java
private final IntentAnalysisService intentAnalysisService;
private final QueryTemplateService queryTemplateService;
private final TemplateParameterService templateParameterService;
private final QueryLogService queryLogService;
```

- [ ] **Step 2: 重构 processQuery 方法 - 添加意图分析**

在 processQuery 方法的第 4 步（生成语义上下文）之前插入意图分析逻辑。

- [ ] **Step 3: 重构 processQuery 方法 - 添加模板检索和验证**

在意图分析之后添加模板检索和验证逻辑。

- [ ] **Step 4: 重构 processQuery 方法 - 模板填充或 LLM 生成**

根据模板匹配结果选择填充模板或调用 LLM 生成 SQL。

- [ ] **Step 5: 重构 processQuery 方法 - 执行 SQL 和记录日志**

执行 SQL 并记录完整的查询日志，包括意图、模板使用情况等。

- [ ] **Step 6: 重构 processQuery 方法 - 保存新模板**

如果是 LLM 生成且执行成功，保存为新模板供后续使用。

- [ ] **Step 7: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/TextToSQLService.java
git commit -m "feat(service): 集成意图分析和模板系统到 TextToSQLService"
```

---

### Task 17: 更新 SemanticContextService 添加精确上下文生成

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/SemanticContextService.java`

- [ ] **Step 1: 添加 generateSystemPromptForTables 方法**

```java
/**
 * 根据字段列表生成精确的系统提示词
 * 用于意图分析后的精确上下文生成
 *
 * @param datasourceId 数据源 ID
 * @param fieldNames 字段名列表（维度 + 指标）
 * @return 系统提示词
 */
public String generateSystemPromptForTables(Long datasourceId, List<String> fieldNames) {
    log.info("根据字段列表生成精确上下文: datasourceId={}, fields={}",
        datasourceId, fieldNames);

    // 1. 查询包含这些字段的所有表
    Set<Long> modelIds = new HashSet<>();
    for (String fieldName : fieldNames) {
        List<ColumnDefinition> columns = columnDefinitionMapper.findByColumnName(fieldName);
        for (ColumnDefinition column : columns) {
            if (column.getModel().getDatasourceId().equals(datasourceId)) {
                modelIds.add(column.getModelId());
            }
        }
    }

    if (modelIds.isEmpty()) {
        log.warn("未找到包含指定字段的表，降级到 RAG 检索");
        return generateSystemPrompt(datasourceId, String.join(", ", fieldNames));
    }

    // 2. 生成这些表的完整上下文
    StringBuilder context = new StringBuilder();
    context.append("# 数据库表结构\n\n");

    for (Long modelId : modelIds) {
        Model model = modelMapper.findById(modelId);
        List<ColumnDefinition> columns = columnDefinitionMapper.findByModelId(modelId);

        context.append("## 表: ").append(model.getTableName()).append("\n");
        if (model.getDescription() != null) {
            context.append("描述: ").append(model.getDescription()).append("\n");
        }
        context.append("\n字段:\n");

        for (ColumnDefinition column : columns) {
            context.append("- ").append(column.getColumnName());
            if (column.getDisplayName() != null) {
                context.append(" (").append(column.getDisplayName()).append(")");
            }
            context.append(": ").append(column.getDataType());
            if (column.getDescription() != null) {
                context.append(" - ").append(column.getDescription());
            }
            context.append("\n");
        }
        context.append("\n");
    }

    // 3. 添加表关系
    List<Relationship> relationships = relationshipMapper.findByModelIds(new ArrayList<>(modelIds));
    if (!relationships.isEmpty()) {
        context.append("## 表关系\n\n");
        for (Relationship rel : relationships) {
            Model fromModel = modelMapper.findById(rel.getFromModelId());
            Model toModel = modelMapper.findById(rel.getToModelId());
            context.append("- ").append(fromModel.getTableName())
                   .append(" -> ").append(toModel.getTableName())
                   .append(" (").append(rel.getJoinType()).append(")\n");
        }
    }

    return context.toString();
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/SemanticContextService.java
git commit -m "feat(service): 添加基于字段列表的精确上下文生成"
```

---

## Chunk 6: Controller 层

### Task 18: 更新 QueryController 添加评分端点

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/controller/QueryController.java`

- [ ] **Step 1: 恢复 IntentAnalysisService 依赖**

取消注释：

```java
private final IntentAnalysisService intentAnalysisService;
```

- [ ] **Step 2: 添加 QueryLogService 和 QueryTemplateService 依赖**

```java
private final QueryLogService queryLogService;
private final QueryTemplateService queryTemplateService;
```

- [ ] **Step 3: 添加评分端点**

```java
/**
 * 对查询结果评分
 */
@PostMapping("/rate")
public Result<Void> rateQuery(@RequestBody RateQueryRequest request) {
    try {
        log.info("收到评分请求: logId={}, score={}",
            request.getLogId(), request.getScore());

        // 1. 更新查询日志评分
        queryLogService.updateRating(request.getLogId(), request.getScore());

        // 2. 如果查询使用了模板，更新模板评分
        QueryLog queryLog = queryLogService.findById(request.getLogId());
        if (queryLog.getTemplateId() != null) {
            queryTemplateService.updateRating(queryLog.getTemplateId(), request.getScore());
            log.info("已更新模板评分: templateId={}", queryLog.getTemplateId());
        }

        return Result.success(null);
    } catch (Exception e) {
        log.error("评分失败", e);
        return Result.error(e.getMessage());
    }
}
```

- [ ] **Step 4: 恢复意图分析端点**

取消注释并修改：

```java
@PostMapping("/analyze-intent")
public Result<IntentAnalysisResponse> analyzeIntent(@RequestBody IntentAnalysisRequest request) {
    try {
        log.info("收到意图分析请求: question={}", request.getQuestion());
        IntentAnalysisResponse response = intentAnalysisService.analyzeIntent(request);
        log.info("意图分析完成: intent={}, skeleton={}",
            response.getIntent(), response.getSkeleton());
        return Result.success(response);
    } catch (Exception e) {
        log.error("意图分析失败", e);
        return Result.error("意图分析失败: " + e.getMessage());
    }
}
```

- [ ] **Step 5: 删除旧的 intent-to-skeleton 端点**

删除注释中的 intentToSkeleton 方法（已被 analyzeIntent 覆盖）。

- [ ] **Step 6: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/controller/QueryController.java
git commit -m "feat(controller): 添加评分端点并恢复意图分析端点"
```

---

### Task 19: 创建 TemplateController

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/controller/TemplateController.java`

- [ ] **Step 1: 创建 TemplateController 类**

```java
package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.QueryTemplate;
import com.tecdo.mac.sql2bot.dto.TemplateStats;
import com.tecdo.mac.sql2bot.mapper.QueryTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模板管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final QueryTemplateMapper templateMapper;

    /**
     * 查询模板列表
     */
    @GetMapping
    public Result<List<QueryTemplate>> listTemplates(
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) String entity) {
        try {
            log.info("查询模板列表: intent={}, entity={}", intent, entity);
            List<QueryTemplate> templates = templateMapper.findAll(intent, entity);
            return Result.success(templates);
        } catch (Exception e) {
            log.error("查询模板列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取模板详情
     */
    @GetMapping("/{id}")
    public Result<QueryTemplate> getTemplate(@PathVariable Long id) {
        try {
            QueryTemplate template = templateMapper.findById(id);
            if (template == null) {
                return Result.error("模板不存在");
            }
            return Result.success(template);
        } catch (Exception e) {
            log.error("获取模板详情失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        try {
            log.info("删除模板: id={}", id);
            templateMapper.deleteById(id);
            return Result.success(null);
        } catch (Exception e) {
            log.error("删除模板失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取模板统计信息
     */
    @GetMapping("/stats")
    public Result<TemplateStats> getStats() {
        try {
            TemplateStats stats = templateMapper.getStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取模板统计失败", e);
            return Result.error(e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/controller/TemplateController.java
git commit -m "feat(controller): 添加 TemplateController 实现模板管理 API"
```

---

### Task 20: 创建 QueryLogController

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/controller/QueryLogController.java`

- [ ] **Step 1: 创建 QueryLogController 类**

```java
package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.QueryLog;
import com.tecdo.mac.sql2bot.dto.BatchLabelRequest;
import com.tecdo.mac.sql2bot.dto.LabelFewShotRequest;
import com.tecdo.mac.sql2bot.dto.QueryLogFilter;
import com.tecdo.mac.sql2bot.dto.QueryLogStats;
import com.tecdo.mac.sql2bot.service.IntentFewShotService;
import com.tecdo.mac.sql2bot.service.QueryLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 查询日志 API
 */
@Slf4j
@RestController
@RequestMapping("/api/query-logs")
@RequiredArgsConstructor
public class QueryLogController {

    private final QueryLogService queryLogService;
    private final IntentFewShotService fewShotService;

    /**
     * 查询日志列表
     */
    @GetMapping
    public Result<List<QueryLog>> listLogs(QueryLogFilter filter) {
        try {
            log.info("查询日志列表: filter={}", filter);
            List<QueryLog> logs = queryLogService.findByFilter(filter);
            return Result.success(logs);
        } catch (Exception e) {
            log.error("查询日志列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取日志详情
     */
    @GetMapping("/{id}")
    public Result<QueryLog> getLog(@PathVariable Long id) {
        try {
            QueryLog log = queryLogService.findById(id);
            if (log == null) {
                return Result.error("日志不存在");
            }
            return Result.success(log);
        } catch (Exception e) {
            log.error("获取日志详情失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 标注为 few-shot
     */
    @PostMapping("/{id}/label-as-few-shot")
    public Result<Long> labelAsFewShot(@PathVariable Long id,
                                       @RequestBody LabelFewShotRequest request) {
        try {
            request.setLogId(id);
            log.info("标注 few-shot: logId={}", id);
            Long fewShotId = fewShotService.labelFromQueryLog(request);
            return Result.success(fewShotId);
        } catch (Exception e) {
            log.error("标注 few-shot 失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量标注
     */
    @PostMapping("/batch-label")
    public Result<List<Long>> batchLabel(@RequestBody BatchLabelRequest request) {
        try {
            log.info("批量标注 few-shot: count={}", request.getLabels().size());
            List<Long> fewShotIds = fewShotService.batchLabel(request);
            return Result.success(fewShotIds);
        } catch (Exception e) {
            log.error("批量标注失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取查询统计
     */
    @GetMapping("/stats")
    public Result<QueryLogStats> getStats() {
        try {
            QueryLogStats stats = queryLogService.getStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取查询统计失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取意图分布统计
     */
    @GetMapping("/intent-distribution")
    public Result<Map<String, Long>> getIntentDistribution() {
        try {
            Map<String, Long> distribution = queryLogService.getIntentDistribution();
            return Result.success(distribution);
        } catch (Exception e) {
            log.error("获取意图分布失败", e);
            return Result.error(e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/controller/QueryLogController.java
git commit -m "feat(controller): 添加 QueryLogController 实现查询日志管理 API"
```

---

### Task 21: 创建 FewShotController

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/controller/FewShotController.java`

- [ ] **Step 1: 创建 FewShotController 类**

```java
package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.domain.IntentFewShot;
import com.tecdo.mac.sql2bot.service.IntentFewShotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Few-shot 示例管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/few-shots")
@RequiredArgsConstructor
public class FewShotController {

    private final IntentFewShotService fewShotService;

    /**
     * 获取 few-shot 列表
     */
    @GetMapping
    public Result<List<IntentFewShot>> listFewShots(
            @RequestParam(required = false) String intent,
            @RequestParam(required = false) Long datasourceId) {
        try {
            log.info("查询 few-shot 列表: intent={}, datasourceId={}", intent, datasourceId);
            List<IntentFewShot> fewShots = fewShotService.findAll(intent, datasourceId);
            return Result.success(fewShots);
        } catch (Exception e) {
            log.error("查询 few-shot 列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 添加 few-shot 示例
     */
    @PostMapping
    public Result<Long> createFewShot(@RequestBody IntentFewShot fewShot) {
        try {
            log.info("创建 few-shot: intent={}", fewShot.getIntent());
            Long id = fewShotService.create(fewShot);
            return Result.success(id);
        } catch (Exception e) {
            log.error("创建 few-shot 失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新 few-shot
     */
    @PutMapping("/{id}")
    public Result<Void> updateFewShot(@PathVariable Long id,
                                      @RequestBody IntentFewShot fewShot) {
        try {
            fewShot.setId(id);
            log.info("更新 few-shot: id={}", id);
            fewShotService.update(fewShot);
            return Result.success(null);
        } catch (Exception e) {
            log.error("更新 few-shot 失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除 few-shot
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteFewShot(@PathVariable Long id) {
        try {
            log.info("删除 few-shot: id={}", id);
            fewShotService.delete(id);
            return Result.success(null);
        } catch (Exception e) {
            log.error("删除 few-shot 失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 启用/禁用 few-shot
     */
    @PostMapping("/{id}/toggle")
    public Result<Void> toggleFewShot(@PathVariable Long id,
                                      @RequestParam boolean isActive) {
        try {
            log.info("切换 few-shot 状态: id={}, isActive={}", id, isActive);
            fewShotService.toggleActive(id, isActive);
            return Result.success(null);
        } catch (Exception e) {
            log.error("切换 few-shot 状态失败", e);
            return Result.error(e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/controller/FewShotController.java
git commit -m "feat(controller): 添加 FewShotController 实现 few-shot 管理 API"
```

---

## 完成

所有任务已完成！现在可以使用 superpowers:subagent-driven-development 来执行这个计划。
