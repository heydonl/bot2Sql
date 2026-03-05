---
name: api-contract-review
description: 审查 REST API 契约的 HTTP 语义、版本控制、向后兼容性和响应一致性。当用户询问"审查 API"、"检查端点"、"REST 审查"或发布 API 更改之前使用。
---

# API 契约审查技能

审计 REST API 设计的正确性、一致性和兼容性。

## 何时使用
- 用户询问"审查这个 API" / "检查 REST 端点"
- 发布 API 更改之前
- 审查带控制器更改的 PR
- 检查向后兼容性

---

## 快速参考：常见问题

| 问题 | 症状 | 影响 |
|-------|---------|-----------|
| 错误的 HTTP 动词 | 幂等操作使用 POST | 混淆、缓存问题 |
| 缺少版本控制 | `/users` 而不是 `/v1/users` | 破坏性更改影响所有客户端 |
| 实体泄漏 | 响应中的 JPA 实体 | 暴露内部、N+1 风险 |
| 200 带错误 | `{"status": 200, "error": "..."}` | 破坏错误处理 |
| 命名不一致 | `/getUsers` vs `/users` | 难以学习 API |

---

## HTTP 动词语义

### 动词选择指南

| 动词 | 用于 | 幂等 | 安全 | 请求体 |
|------|---------|------------|------|----------|
| GET | 检索资源 | 是 | 是 | 否 |
| POST | 创建新资源 | 否 | 否 | 是 |
| PUT | 替换整个资源 | 是 | 否 | 是 |
| PATCH | 部分更新 | 否* | 否 | 是 |
| DELETE | 删除资源 | 是 | 否 | 可选 |

*PATCH 可以是幂等的，取决于实现

### 常见错误

```java
// ❌ 检索使用 POST
@PostMapping("/users/search")
public List<User> searchUsers(@RequestBody SearchCriteria criteria) { }

// ✅ 使用查询参数的 GET（或仅在条件非常复杂时使用 POST）
@GetMapping("/users")
public List<User> searchUsers(
    @RequestParam String name,
    @RequestParam(required = false) String email) { }

// ❌ 状态更改使用 GET
@GetMapping("/users/{id}/activate")
public void activateUser(@PathVariable Long id) { }

// ✅ 状态更改使用 POST 或 PATCH
@PostMapping("/users/{id}/activate")
public ResponseEntity<Void> activateUser(@PathVariable Long id) { }

// ❌ 幂等更新使用 POST
@PostMapping("/users/{id}")
public User updateUser(@PathVariable Long id, @RequestBody UserDto dto) { }

// ✅ 完全替换使用 PUT，部分更新使用 PATCH
@PutMapping("/users/{id}")
public User replaceUser(@PathVariable Long id, @RequestBody UserDto dto) { }

@PatchMapping("/users/{id}")
public User updateUser(@PathVariable Long id, @RequestBody UserPatchDto dto) { }
```

---

## API 版本控制

### 策略

| 策略 | 示例 | 优点 | 缺点 |
|----------|---------|------|------|
| URL 路径 | `/v1/users` | 清晰、易于路由 | URL 更改 |
| 头部 | `Accept: application/vnd.api.v1+json` | 干净的 URL | 隐藏、难以测试 |
| 查询参数 | `/users?version=1` | 易于添加 | 容易忘记 |

### 推荐：URL 路径

```java
// ✅ 版本化端点
@RestController
@RequestMapping("/api/v1/users")
public class UserControllerV1 { }

@RestController
@RequestMapping("/api/v2/users")
public class UserControllerV2 { }

// ❌ 无版本控制
@RestController
@RequestMapping("/api/users")  // 破坏性更改影响所有人
public class UserController { }
```

### 版本检查清单
- [ ] 所有公共 API 在路径中有版本
- [ ] 内部 API 记录为内部（或也版本化）
- [ ] 定义旧版本的弃用策略

---

## 请求/响应设计

### DTO vs 实体

```java
// ❌ 响应中的实体（泄漏内部）
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();
    // 暴露：密码哈希、内部 ID、延迟集合
}

// ✅ DTO 响应
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id) {
    User user = userService.findById(id);
    return UserResponse.from(user);  // 仅公共字段
}
```

### 响应一致性

```java
// ❌ 不一致的响应
@GetMapping("/users")
public List<User> getUsers() { }  // 返回数组

@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) { }  // 返回对象

@GetMapping("/users/count")
public int countUsers() { }  // 返回原始类型

// ✅ 一致的包装器（对于大型 API 可选但推荐）
@GetMapping("/users")
public ApiResponse<List<UserResponse>> getUsers() {
    return ApiResponse.success(userService.findAll());
}

// 或至少，一致的结构：
// - 集合：始终包装或始终原始（选择一个）
// - 单个项：始终对象
// - 计数/统计：始终对象 { "count": 42 }
```

### 分页

```java
// ❌ 集合上无分页
@GetMapping("/users")
public List<User> getAllUsers() {
    return userRepository.findAll();  // 可能有数百万
}

// ✅ 分页
@GetMapping("/users")
public Page<UserResponse> getUsers(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size) {
    return userService.findAll(PageRequest.of(page, size));
}
```

---

## HTTP 状态码

### 成功码

| 码 | 何时使用 | 响应体 |
|------|-------------|---------------|
| 200 OK | 成功的 GET、PUT、PATCH | 资源或结果 |
| 201 Created | 成功的 POST（已创建） | 已创建的资源 + Location 头 |
| 204 No Content | 成功的 DELETE，或无体的 PUT | 空 |

### 错误码

| 码 | 何时使用 | 常见错误 |
|------|-------------|----------------|
| 400 Bad Request | 无效输入、验证失败 | 用于"未找到" |
| 401 Unauthorized | 未认证 | 与 403 混淆 |
| 403 Forbidden | 已认证但不允许 | 使用 401 代替 |
| 404 Not Found | 资源不存在 | 使用 400 |
| 409 Conflict | 重复、并发修改 | 使用 400 |
| 422 Unprocessable | 语义错误（语法有效，含义无效） | 使用 400 |
| 500 Internal Error | 意外的服务器错误 | 暴露堆栈跟踪 |

### 反模式：200 带错误体

```java
// ❌ 永远不要这样做
@GetMapping("/{id}")
public ResponseEntity<Map<String, Object>> getUser(@PathVariable Long id) {
    try {
        User user = userService.findById(id);
        return ResponseEntity.ok(Map.of("status", "success", "data", user));
    } catch (NotFoundException e) {
        return ResponseEntity.ok(Map.of(  // 仍然是 200！
            "status", "error",
            "message", "User not found"
        ));
    }
}

// ✅ 使用正确的状态码
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    return userService.findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}
```

---

## 错误响应格式

### 一致的错误结构

```java
// ✅ 标准错误响应
public class ErrorResponse {
    private String code;        // 机器可读："USER_NOT_FOUND"
    private String message;     // 人类可读："User with ID 123 not found"
    private Instant timestamp;
    private String path;
    private List<FieldError> errors;  // 用于验证错误
}

// 在 GlobalExceptionHandler 中
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<ErrorResponse> handleNotFound(
        ResourceNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse.builder()
            .code("RESOURCE_NOT_FOUND")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .build());
}
```

### 安全：不要暴露内部

```java
// ❌ 暴露堆栈跟踪
@ExceptionHandler(Exception.class)
public ResponseEntity<String> handleAll(Exception ex) {
    return ResponseEntity.status(500)
        .body(ex.getStackTrace().toString());  // 安全风险！
}

// ✅ 通用消息，服务器端记录详细信息
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleAll(Exception ex) {
    log.error("Unexpected error", ex);  // 日志中的完整详细信息
    return ResponseEntity.status(500)
        .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
}
```

---

## 向后兼容性

### 破坏性更改（在同一版本中避免）

| 更改 | 破坏性？ | 迁移 |
|--------|-----------|-----------|
| 删除端点 | 是 | 先弃用，在下一版本中删除 |
| 从响应中删除字段 | 是 | 保留字段，返回 null/默认值 |
| 向请求添加必需字段 | 是 | 使其可选并带默认值 |
| 更改字段类型 | 是 | 添加新字段，弃用旧字段 |
| 重命名字段 | 是 | 暂时支持两者 |
| 更改 URL 路径 | 是 | 将旧路径重定向到新路径 |

### 非破坏性更改（安全）

- 向请求添加可选字段
- 向响应添加字段
- 添加新端点
- 添加新的可选查询参数

### 弃用模式

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserControllerV1 {

    @Deprecated
    @GetMapping("/by-email")  // 旧端点
    public UserResponse getByEmailOld(@RequestParam String email) {
        return getByEmail(email);  // 委托给新的
    }

    @GetMapping(params = "email")  // 新模式
    public UserResponse getByEmail(@RequestParam String email) {
        return userService.findByEmail(email);
    }
}
```

---

## API 审查清单

### 1. HTTP 语义
- [ ] GET 仅用于检索（无副作用）
- [ ] POST 用于创建（返回 201 + Location）
- [ ] PUT 用于完全替换（幂等）
- [ ] PATCH 用于部分更新
- [ ] DELETE 用于删除（幂等）

### 2. URL 设计
- [ ] 版本化（`/v1/`、`/v2/`）
- [ ] 名词，不是动词（`/users`，不是 `/getUsers`）
- [ ] 集合使用复数（`/users`，不是 `/user`）
- [ ] 关系使用层次结构（`/users/{id}/orders`）
- [ ] 一致的命名（kebab-case 或 camelCase，选择一个）

### 3. 请求处理
- [ ] 使用 `@Valid` 验证
- [ ] 验证失败的清晰错误消息
- [ ] 请求 DTO（不是实体）
- [ ] 合理的大小限制

### 4. 响应设计
- [ ] 响应 DTO（不是实体）
- [ ] 端点间一致的结构
- [ ] 集合分页
- [ ] 正确的状态码（错误不使用 200）

### 5. 错误处理
- [ ] 一致的错误格式
- [ ] 机器可读的错误码
- [ ] 人类可读的消息
- [ ] 不暴露堆栈跟踪
- [ ] 正确区分 4xx vs 5xx

### 6. 兼容性
- [ ] 当前版本中无破坏性更改
- [ ] 记录已弃用的端点
- [ ] 破坏性更改的迁移路径

---

## Token 优化

对于大型 API：
1. 列出所有控制器：`find . -name "*Controller.java"`
2. 采样 2-3 个控制器进行模式分析
3. 检查 `@ExceptionHandler` 配置一次
4. Grep 特定反模式：
   ```bash
   # 查找潜在的实体泄漏
   grep -r "public.*Entity.*@GetMapping" --include="*.java"

   # 查找 200 带错误模式
   grep -r "ResponseEntity.ok.*error" --include="*.java"

   # 查找未版本化的 API
   grep -r "@RequestMapping.*api" --include="*.java" | grep -v "/v[0-9]"
   ```
