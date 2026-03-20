# LLM 批量生成表描述 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 LLM 批量为 model 表中所有表自动生成中文业务描述，写回数据库，并触发全量重新索引，提升 KNN 向量搜索命中率。

**Architecture:** 新增 `DescriptionGeneratorService` 服务，通过固定线程池（5 并发）异步处理所有 model，每张表调用 AIService 生成描述后写回 DB，全部完成后自动触发 `SchemaIndexService.fullIndex()`。进度存 Redis，通过扩展现有 SchedulerController 提供 API 端点查询。

**Tech Stack:** Spring Boot 4.0.3, Java 17, MyBatis, Redis (JedisPooled), AIService (Claude API), ExecutorService

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `src/main/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorService.java` | 新建 | 异步批量生成描述，进度管理，线程池管理 |
| `src/main/java/com/tecdo/mac/sql2bot/controller/SchedulerController.java` | 修改 | 新增两个端点：触发生成、查询进度 |
| `src/test/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorServiceTest.java` | 新建 | 单元测试 |

---

### Task 0: 验证依赖和现有代码结构

**Files:**
- Read: `src/main/java/com/tecdo/mac/sql2bot/controller/SchedulerController.java`
- Read: `src/main/java/com/tecdo/mac/sql2bot/scheduler/SchemaIndexService.java`

- [ ] **Step 1: 验证 SchedulerController 结构**

Read: `src/main/java/com/tecdo/mac/sql2bot/controller/SchedulerController.java`
确认：使用 @RequiredArgsConstructor，现有依赖注入方式

- [ ] **Step 2: 验证 SchemaIndexService.fullIndex() 方法存在**

Read: `src/main/java/com/tecdo/mac/sql2bot/scheduler/SchemaIndexService.java`
确认：fullIndex() 方法存在且为 public

- [ ] **Step 3: 验证 AIService.generateSQL() 方法签名**

Read: `src/main/java/com/tecdo/mac/sql2bot/service/AIService.java`
确认：generateSQL(String systemPrompt, String userPrompt) 方法存在

- [ ] **Step 4: 提交依赖验证**

```bash
git add -A
git commit -m "docs: 验证现有代码结构和依赖"
```

---

### Task 1: 创建 DescriptionGeneratorService 核心服务

**Files:**
- Create: `src/main/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorService.java`

- [ ] **Step 1: 创建测试文件并写失败测试**

Create: `src/test/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorServiceTest.java`

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.mapper.ColumnDefinitionMapper;
import com.tecdo.mac.sql2bot.mapper.ModelMapper;
import com.tecdo.mac.sql2bot.scheduler.SchemaIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DescriptionGeneratorServiceTest {

    @Mock
    private ModelMapper modelMapper;
    @Mock
    private ColumnDefinitionMapper columnDefinitionMapper;
    @Mock
    private AIService aiService;
    @Mock
    private SchemaIndexService schemaIndexService;
    @Mock
    private JedisPooled jedisPooled;

    @InjectMocks
    private DescriptionGeneratorService service;

    @Test
    void generateAll_shouldReturnFalseWhenAlreadyRunning() {
        // Given: 任务已在运行
        when(jedisPooled.set(eq("schema:desc_gen:running"), eq("true"), any(SetParams.class)))
            .thenReturn(null); // SETNX 失败

        // When
        boolean result = service.generateAll();

        // Then
        assertFalse(result);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dtest=DescriptionGeneratorServiceTest#generateAll_shouldReturnFalseWhenAlreadyRunning`
Expected: FAIL with "DescriptionGeneratorService cannot be resolved"

- [ ] **Step 3: 创建 DescriptionGeneratorService 基础结构**

```java
package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.mapper.ColumnDefinitionMapper;
import com.tecdo.mac.sql2bot.mapper.ModelMapper;
import com.tecdo.mac.sql2bot.scheduler.SchemaIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DescriptionGeneratorService {

    private final ModelMapper modelMapper;
    private final ColumnDefinitionMapper columnDefinitionMapper;
    private final AIService aiService;
    private final SchemaIndexService schemaIndexService;
    private final JedisPooled jedisPooled;

    private ExecutorService executor;

    @PostConstruct
    public void init() {
        this.executor = Executors.newFixedThreadPool(5);
    }

    @PreDestroy
    public void destroy() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    public boolean generateAll() {
        // 互斥锁检查
        String result = jedisPooled.set("schema:desc_gen:running", "true",
            SetParams.setParams().nx().ex(7200));
        return result != null; // SETNX 成功返回 "OK"，失败返回 null
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dtest=DescriptionGeneratorServiceTest#generateAll_shouldReturnFalseWhenAlreadyRunning`
Expected: PASS

- [ ] **Step 5: 提交基础结构**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorService.java
git commit -m "feat: 添加 DescriptionGeneratorService 基础结构和互斥锁"
```

---

### Task 2: 实现异步任务生成逻辑

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorService.java`
- Modify: `src/test/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorServiceTest.java`

- [ ] **Step 1: 添加完整的异步任务生成测试**

```java
@Test
void generateAll_shouldStartAsyncTaskWhenNotRunning() {
    // Given
    when(jedisPooled.set(eq("schema:desc_gen:running"), eq("true"), any(SetParams.class)))
        .thenReturn("OK"); // SETNX 成功
    when(modelMapper.selectAll()).thenReturn(List.of(
        createModel(1L, "test_table", "Test Table")
    ));

    // When
    boolean result = service.generateAll();

    // Then
    assertTrue(result);
    verify(jedisPooled).set("schema:desc_gen:total", "1", SetParams.setParams().ex(86400));
    verify(jedisPooled).set("schema:desc_gen:done", "0", SetParams.setParams().ex(86400));
    verify(jedisPooled).set("schema:desc_gen:failed", "0", SetParams.setParams().ex(86400));
}

@Test
void generateAll_shouldHandleEmptyModelList() {
    // Given
    when(jedisPooled.set(eq("schema:desc_gen:running"), eq("true"), any(SetParams.class)))
        .thenReturn("OK");
    when(modelMapper.selectAll()).thenReturn(List.of());

    // When
    boolean result = service.generateAll();

    // Then
    assertTrue(result);
    verify(jedisPooled).set("schema:desc_gen:total", "0", SetParams.setParams().ex(86400));
    verify(jedisPooled).del("schema:desc_gen:running");
}

@Test
void generateAll_shouldHandleExceptionDuringStartup() {
    // Given
    when(jedisPooled.set(eq("schema:desc_gen:running"), eq("true"), any(SetParams.class)))
        .thenReturn("OK");
    when(modelMapper.selectAll()).thenThrow(new RuntimeException("DB error"));

    // When
    boolean result = service.generateAll();

    // Then
    assertFalse(result);
    verify(jedisPooled).del("schema:desc_gen:running");
}

private Model createModel(Long id, String tableName, String displayName) {
    Model model = new Model();
    model.setId(id);
    model.setTableName(tableName);
    model.setDisplayName(displayName);
    return model;
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dtest=DescriptionGeneratorServiceTest#generateAll_shouldStartAsyncTaskWhenNotRunning`
Expected: FAIL with method not implemented

- [ ] **Step 3: 实现完整的 generateAll 方法**

```java
public boolean generateAll() {
    // 1. 互斥锁检查
    String lockResult = jedisPooled.set("schema:desc_gen:running", "true",
        SetParams.setParams().nx().ex(7200));
    if (lockResult == null) {
        log.warn("描述生成任务已在运行中");
        return false;
    }

    try {
        // 2. 获取所有 model
        List<Model> allModels = modelMapper.selectAll();
        int total = allModels.size();
        log.info("开始批量生成描述，总计 {} 张表", total);

        // 3. 重置 Redis 进度
        jedisPooled.set("schema:desc_gen:total", String.valueOf(total), SetParams.setParams().ex(86400));
        jedisPooled.set("schema:desc_gen:done", "0", SetParams.setParams().ex(86400));
        jedisPooled.set("schema:desc_gen:failed", "0", SetParams.setParams().ex(86400));

        // 4. 特殊情况：total == 0
        if (total == 0) {
            jedisPooled.del("schema:desc_gen:running");
            log.info("无表需要处理，任务完成");
            return true;
        }

        // 5. 异步启动任务
        CompletableFuture.runAsync(() -> processAllModels(allModels), executor);
        return true;

    } catch (Exception e) {
        log.error("启动描述生成任务失败", e);
        jedisPooled.del("schema:desc_gen:running");
        return false;
    }
}

private void processAllModels(List<Model> models) {
    try {
        // 提交所有任务到线程池
        List<CompletableFuture<Void>> futures = models.stream()
            .map(model -> CompletableFuture.runAsync(() -> processModel(model), executor))
            .toList();

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 触发全量索引
        log.info("所有表处理完成，开始全量索引");
        schemaIndexService.fullIndex();
        log.info("全量索引完成");

    } catch (Exception e) {
        log.error("批量生成描述过程中发生异常", e);
    } finally {
        // 清理运行标志
        jedisPooled.del("schema:desc_gen:running");
        log.info("描述生成任务结束");
    }
}

private void processModel(Model model) {
    try {
        // 1. 获取完整 model 对象
        Model fullModel = modelMapper.selectById(model.getId());
        if (fullModel == null) {
            log.warn("表已被删除，跳过: modelId={}", model.getId());
            jedisPooled.incr("schema:desc_gen:done");
            return;
        }

        // 2. 获取字段信息
        List<ColumnDefinition> columns = columnDefinitionMapper.selectByModelId(model.getId());

        // 3. 构建 prompt
        String systemPrompt = "你是一个数据库业务分析专家。根据以下表结构信息，生成一段简洁的中文业务描述（50-100字），" +
                "描述该表的业务用途、存储的核心数据和主要使用场景。只输出描述文字，不要加任何前缀或解释。";

        String userPrompt = buildUserPrompt(fullModel.getTableName(), columns);

        // 4. 调用 AI 生成描述
        String description = aiService.generateSQL(systemPrompt, userPrompt);
        if (description == null || description.trim().isEmpty()) {
            log.error("AI 返回空描述: modelId={}, tableName={}", model.getId(), model.getTableName());
            jedisPooled.incr("schema:desc_gen:done");
            jedisPooled.incr("schema:desc_gen:failed");
            return;
        }

        // 5. 写回数据库
        fullModel.setDescription(description.trim());
        modelMapper.update(fullModel);

        log.info("成功生成描述: modelId={}, tableName={}, description={}",
            model.getId(), model.getTableName(), description.substring(0, Math.min(50, description.length())));

        jedisPooled.incr("schema:desc_gen:done");

    } catch (Exception e) {
        log.error("处理表失败: modelId={}, tableName={}", model.getId(), model.getTableName(), e);
        jedisPooled.incr("schema:desc_gen:done");
        jedisPooled.incr("schema:desc_gen:failed");
    }
}

private String buildUserPrompt(String tableName, List<ColumnDefinition> columns) {
    StringBuilder sb = new StringBuilder();
    sb.append("表名: ").append(tableName).append("\n");
    sb.append("字段: ");

    if (columns.isEmpty()) {
        sb.append("（无字段信息）");
    } else {
        // 最多取前 50 个字段
        List<ColumnDefinition> limitedColumns = columns.size() > 50 ?
            columns.subList(0, 50) : columns;

        for (int i = 0; i < limitedColumns.size(); i++) {
            ColumnDefinition col = limitedColumns.get(i);
            if (i > 0) sb.append(", ");
            sb.append(col.getColumnName()).append("(").append(col.getColumnType()).append(")");
        }

        if (columns.size() > 50) {
            sb.append("（仅展示前50个字段）");
        }
    }

    return sb.toString();
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dtest=DescriptionGeneratorServiceTest#generateAll_shouldStartAsyncTaskWhenNotRunning`
Expected: PASS

- [ ] **Step 5: 提交异步任务逻辑**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorService.java src/test/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorServiceTest.java
git commit -m "feat: 实现 DescriptionGeneratorService 异步批量生成描述逻辑"
```

---

### Task 3: 添加进度查询方法

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorService.java`
- Modify: `src/test/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorServiceTest.java`

- [ ] **Step 1: 添加进度查询测试**

```java
@Test
void getProgress_shouldReturnDefaultWhenNoKeys() {
    // Given: Redis 中没有相关 key
    when(jedisPooled.get("schema:desc_gen:running")).thenReturn(null);
    when(jedisPooled.get("schema:desc_gen:total")).thenReturn(null);
    when(jedisPooled.get("schema:desc_gen:done")).thenReturn(null);
    when(jedisPooled.get("schema:desc_gen:failed")).thenReturn(null);

    // When
    Map<String, Object> progress = service.getProgress();

    // Then
    assertEquals(false, progress.get("running"));
    assertEquals(0, progress.get("total"));
    assertEquals(0, progress.get("done"));
    assertEquals(0, progress.get("failed"));
    assertEquals(0, progress.get("percent"));
}

@Test
void getProgress_shouldCalculatePercentCorrectly() {
    // Given
    when(jedisPooled.get("schema:desc_gen:running")).thenReturn("true");
    when(jedisPooled.get("schema:desc_gen:total")).thenReturn("100");
    when(jedisPooled.get("schema:desc_gen:done")).thenReturn("25");
    when(jedisPooled.get("schema:desc_gen:failed")).thenReturn("5");

    // When
    Map<String, Object> progress = service.getProgress();

    // Then
    assertEquals(true, progress.get("running"));
    assertEquals(100, progress.get("total"));
    assertEquals(25, progress.get("done"));
    assertEquals(5, progress.get("failed"));
    assertEquals(25, progress.get("percent"));
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dtest=DescriptionGeneratorServiceTest#getProgress_shouldReturnDefaultWhenNoKeys`
Expected: FAIL with method not found

- [ ] **Step 3: 实现 getProgress 方法**

```java
import java.util.HashMap;
import java.util.Map;

public Map<String, Object> getProgress() {
    Map<String, Object> progress = new HashMap<>();

    // 读取 Redis 中的进度信息
    String runningStr = jedisPooled.get("schema:desc_gen:running");
    String totalStr = jedisPooled.get("schema:desc_gen:total");
    String doneStr = jedisPooled.get("schema:desc_gen:done");
    String failedStr = jedisPooled.get("schema:desc_gen:failed");

    // 解析并设置默认值
    boolean running = "true".equals(runningStr);
    int total = parseIntOrDefault(totalStr, 0);
    int done = parseIntOrDefault(doneStr, 0);
    int failed = parseIntOrDefault(failedStr, 0);

    // 计算百分比，cap 到 100
    int percent = total == 0 ? 0 : Math.min(100, (done * 100) / total);

    progress.put("running", running);
    progress.put("total", total);
    progress.put("done", done);
    progress.put("failed", failed);
    progress.put("percent", percent);

    return progress;
}

private int parseIntOrDefault(String str, int defaultValue) {
    if (str == null || str.trim().isEmpty()) {
        return defaultValue;
    }
    try {
        return Integer.parseInt(str);
    } catch (NumberFormatException e) {
        return defaultValue;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dtest=DescriptionGeneratorServiceTest`
Expected: PASS

- [ ] **Step 5: 提交进度查询功能**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorService.java src/test/java/com/tecdo/mac/sql2bot/service/DescriptionGeneratorServiceTest.java
git commit -m "feat: 添加 DescriptionGeneratorService 进度查询功能"
```

---

### Task 4: 扩展 SchedulerController 添加新端点

**Files:**
- Modify: `src/main/java/com/tecdo/mac/sql2bot/controller/SchedulerController.java`

- [ ] **Step 1: 创建 Controller 测试并写失败测试**

Create: `src/test/java/com/tecdo/mac/sql2bot/controller/SchedulerControllerTest.java`

```java
package com.tecdo.mac.sql2bot.controller;

import com.tecdo.mac.sql2bot.common.Result;
import com.tecdo.mac.sql2bot.scheduler.SchemaIndexScheduler;
import com.tecdo.mac.sql2bot.scheduler.SchemaIndexService;
import com.tecdo.mac.sql2bot.service.DescriptionGeneratorService;
import com.tecdo.mac.sql2bot.service.SchemaVectorStoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerControllerTest {

    @Mock
    private SchemaIndexScheduler schemaIndexScheduler;
    @Mock
    private SchemaIndexService schemaIndexService;
    @Mock
    private SchemaVectorStoreService schemaVectorStoreService;
    @Mock
    private DescriptionGeneratorService descriptionGeneratorService;

    @InjectMocks
    private SchedulerController controller;

    @Test
    void generateDescriptions_shouldReturnSuccessWhenStarted() {
        // Given
        when(descriptionGeneratorService.generateAll()).thenReturn(true);
        when(descriptionGeneratorService.getProgress()).thenReturn(Map.of("total", 490));

        // When
        Result<Map<String, Object>> result = controller.generateDescriptions();

        // Then
        assertEquals(200, result.getCode());
        assertEquals("started", ((Map<String, Object>) result.getData()).get("status"));
        assertEquals(490, ((Map<String, Object>) result.getData()).get("total"));
    }

    @Test
    void generateDescriptions_shouldReturn400WhenAlreadyRunning() {
        // Given
        when(descriptionGeneratorService.generateAll()).thenReturn(false);

        // When
        Result<Map<String, Object>> result = controller.generateDescriptions();

        // Then
        assertEquals(400, result.getCode());
        assertEquals("描述生成任务正在运行中，请稍后再试", result.getMessage());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dtest=SchedulerControllerTest#generateDescriptions_shouldReturnSuccessWhenStarted`
Expected: FAIL with method not found

- [ ] **Step 3: 在 SchedulerController 中添加依赖和端点**

首先检查现有构造函数，然后添加依赖注入：

```java
// 在现有的 @RequiredArgsConstructor 类中，添加新的 final 字段
private final DescriptionGeneratorService descriptionGeneratorService;

// 添加新端点
/** 触发批量生成表描述 */
@PostMapping("/generate-descriptions")
public Result<Map<String, Object>> generateDescriptions() {
    log.info("手动触发批量生成表描述");

    boolean started = descriptionGeneratorService.generateAll();
    if (!started) {
        return Result.error(400, "描述生成任务正在运行中，请稍后再试");
    }

    // 获取 total 信息
    Map<String, Object> progress = descriptionGeneratorService.getProgress();
    Map<String, Object> response = new HashMap<>();
    response.put("status", "started");
    response.put("total", progress.get("total"));

    return Result.success(response);
}

/** 查询批量生成表描述进度 */
@GetMapping("/generate-descriptions/status")
public Result<Map<String, Object>> generateDescriptionsStatus() {
    Map<String, Object> progress = descriptionGeneratorService.getProgress();
    return Result.success(progress);
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dtest=SchedulerControllerTest#generateDescriptions_shouldReturnSuccessWhenStarted`
Expected: PASS

- [ ] **Step 5: 运行完整测试套件**

Run: `mvn test`
Expected: 所有测试通过

- [ ] **Step 6: 提交 Controller 扩展**

```bash
git add src/main/java/com/tecdo/mac/sql2bot/controller/SchedulerController.java
git commit -m "feat: 扩展 SchedulerController 添加批量生成表描述端点"
```

---

### Task 5: 集成测试和验证

**Files:**
- Create: `src/test/java/com/tecdo/mac/sql2bot/integration/DescriptionGeneratorIntegrationTest.java`

- [ ] **Step 1: 创建集成测试**

```java
package com.tecdo.mac.sql2bot.integration;

import com.tecdo.mac.sql2bot.service.DescriptionGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DescriptionGeneratorIntegrationTest {

    @Autowired
    private DescriptionGeneratorService descriptionGeneratorService;

    @Test
    void contextLoads() {
        assertNotNull(descriptionGeneratorService);
    }

    @Test
    void getProgress_shouldReturnValidStructure() {
        Map<String, Object> progress = descriptionGeneratorService.getProgress();

        assertTrue(progress.containsKey("running"));
        assertTrue(progress.containsKey("total"));
        assertTrue(progress.containsKey("done"));
        assertTrue(progress.containsKey("failed"));
        assertTrue(progress.containsKey("percent"));

        assertInstanceOf(Boolean.class, progress.get("running"));
        assertInstanceOf(Integer.class, progress.get("total"));
        assertInstanceOf(Integer.class, progress.get("done"));
        assertInstanceOf(Integer.class, progress.get("failed"));
        assertInstanceOf(Integer.class, progress.get("percent"));
    }
}
```

- [ ] **Step 2: 运行集成测试**

Run: `mvn test -Dtest=DescriptionGeneratorIntegrationTest`
Expected: PASS

- [ ] **Step 3: 启动应用验证端点**

Run: `mvn spring-boot:run`
Wait for startup, then test endpoints:

```bash
# 查询初始状态
curl "http://localhost:8082/api/scheduler/schema-index/generate-descriptions/status"

# 预期响应: {"code":200,"data":{"running":false,"total":0,"done":0,"failed":0,"percent":0}}
```

- [ ] **Step 4: 验证端点响应格式**

```bash
# 触发生成（应该启动任务）
curl -X POST "http://localhost:8082/api/scheduler/schema-index/generate-descriptions"

# 预期响应: {"code":200,"data":{"status":"started","total":490},"message":"success"}
```

- [ ] **Step 5: 验证进度查询**

```bash
# 查询进度（任务运行中）
curl "http://localhost:8082/api/scheduler/schema-index/generate-descriptions/status"

# 预期响应: {"code":200,"data":{"running":true,"total":490,"done":123,"failed":2,"percent":25}}
```

- [ ] **Step 6: 提交集成测试**

```bash
git add src/test/java/com/tecdo/mac/sql2bot/integration/DescriptionGeneratorIntegrationTest.java
git commit -m "test: 添加 DescriptionGeneratorService 集成测试"
```

---

### Task 6: 文档和最终验证

**Files:**
- Create: `docs/description-generator-api.md`

- [ ] **Step 1: 创建 API 文档**

```markdown
# 批量生成表描述 API

## 触发生成

**POST** `/api/scheduler/schema-index/generate-descriptions`

触发批量为所有 model 表生成中文业务描述的任务。

### 响应

成功启动：
```json
{
  "code": 200,
  "data": {
    "status": "started",
    "total": 490
  },
  "message": "success"
}
```

任务已在运行：
```json
{
  "code": 400,
  "message": "描述生成任务正在运行中，请稍后再试"
}
```

## 查询进度

**GET** `/api/scheduler/schema-index/generate-descriptions/status`

查询当前批量生成任务的进度。

### 响应

```json
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

### 字段说明

- `running`: 是否正在运行
- `total`: 总表数
- `done`: 已处理数（成功+失败）
- `failed`: 失败数
- `percent`: 完成百分比（0-100）

## 使用流程

1. 调用 POST 端点触发任务
2. 定期调用 GET 端点查询进度
3. 当 `running` 为 `false` 且 `done == total` 时任务完成
4. 任务完成后会自动触发全量索引重建
```

- [ ] **Step 2: 运行完整测试套件**

Run: `mvn clean test`
Expected: 所有测试通过

- [ ] **Step 3: 验证编译**

Run: `mvn clean compile`
Expected: 编译成功，无警告

- [ ] **Step 4: 最终功能验证**

启动应用并验证完整流程：

```bash
# 1. 启动应用
mvn spring-boot:run

# 2. 查询初始状态
curl "http://localhost:8082/api/scheduler/schema-index/generate-descriptions/status"

# 3. 触发任务
curl -X POST "http://localhost:8082/api/scheduler/schema-index/generate-descriptions"

# 4. 监控进度
watch -n 5 'curl -s "http://localhost:8082/api/scheduler/schema-index/generate-descriptions/status" | jq'

# 5. 等待任务完成（running: false, done == total）
```

- [ ] **Step 5: 提交文档**

```bash
git add docs/description-generator-api.md
git commit -m "docs: 添加批量生成表描述 API 文档"
```

- [ ] **Step 6: 创建功能完成的总结提交**

```bash
git add .
git commit -m "feat: 完成 LLM 批量生成表描述功能

- 新增 DescriptionGeneratorService 异步批量处理服务
- 扩展 SchedulerController 提供触发和进度查询端点
- 支持 5 并发线程池处理，Redis 进度追踪
- 自动调用 Claude API 生成中文业务描述
- 完成后自动触发全量索引重建
- 包含完整单元测试和集成测试"
```

---

## 验证清单

完成所有任务后，验证以下功能：

- [ ] 服务启动无错误，所有 Bean 正确注入
- [ ] POST `/api/scheduler/schema-index/generate-descriptions` 返回正确格式
- [ ] GET `/api/scheduler/schema-index/generate-descriptions/status` 返回进度信息
- [ ] 重复调用 POST 端点返回 400 错误（互斥锁生效）
- [ ] 任务完成后 `running` 变为 `false`，可以重新触发
- [ ] 生成的描述写入数据库 `model.description` 字段
- [ ] 任务完成后自动触发 `fullIndex()`
- [ ] 所有单元测试和集成测试通过