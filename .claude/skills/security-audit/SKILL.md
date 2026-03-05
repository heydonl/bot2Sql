---
name: security-audit
description: Java 安全检查清单，涵盖 OWASP Top 10、输入验证、注入防护和安全编码。适用于 Spring、Quarkus、Jakarta EE 和纯 Java。在审查代码安全、发布前或用户询问漏洞时使用。
---

# 安全审计技能

基于 OWASP Top 10 和安全编码实践的 Java 应用安全检查清单。

## 何时使用
- 安全代码审查
- 生产发布之前
- 用户询问"安全"、"漏洞"、"OWASP"
- 审查身份验证/授权代码
- 检查注入漏洞

---

## OWASP Top 10 快速参考

| # | 风险 | Java 缓解措施 |
|---|------|--------------|
| A01 | 访问控制失效 | 基于角色的检查，默认拒绝 |
| A02 | 加密失败 | 使用强算法，不硬编码密钥 |
| A03 | 注入 | 参数化查询，输入验证 |
| A04 | 不安全设计 | 威胁建模，安全默认值 |
| A05 | 安全配置错误 | 禁用调试，安全头 |
| A06 | 易受攻击的组件 | 依赖扫描，更新 |
| A07 | 身份验证失败 | 强密码，MFA，会话管理 |
| A08 | 数据完整性失败 | 验证签名，安全反序列化 |
| A09 | 日志记录失败 | 记录安全事件，不记录敏感数据 |
| A10 | 服务器端请求伪造 | 验证 URL，白名单域 |

---

## 输入验证（所有框架）

### Bean Validation (JSR 380)

适用于 Spring、Quarkus、Jakarta EE 和独立应用。

```java
// ✅ 好：在边界验证
public class CreateUserRequest {

    @NotNull(message = "用户名是必需的")
    @Size(min = 3, max = 50, message = "用户名必须是 3-50 个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;

    @NotNull
    @Email(message = "无效的邮箱格式")
    private String email;

    @NotNull
    @Size(min = 8, max = 100)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
             message = "密码必须包含大写、小写和数字")
    private String password;

    @Min(value = 0, message = "年龄不能为负数")
    @Max(value = 150, message = "无效的年龄")
    private Integer age;
}

// 控制器/资源 - 触发验证
public Response createUser(@Valid CreateUserRequest request) {
    // request 已经验证
}
```

### 白名单 vs 黑名单

```java
// ❌ 不好：黑名单（攻击者会找到绕过方法）
if (input.contains("<script>")) {
    throw new ValidationException("无效输入");
}

// ✅ 好：白名单（只允许已知安全的）
private static final Pattern SAFE_NAME = Pattern.compile("^[a-zA-Z\\s'-]{1,100}$");

if (!SAFE_NAME.matcher(input).matches()) {
    throw new ValidationException("无效的名称格式");
}
```

---

## SQL 注入防护

### JPA/Hibernate（所有框架）

```java
// ✅ 好：参数化查询
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// ✅ 好：命名参数
TypedQuery<User> query = entityManager.createQuery(
    "SELECT u FROM User u WHERE u.status = :status", User.class);
query.setParameter("status", status);  // 安全

// ❌ 不好：字符串拼接
String jpql = "SELECT u FROM User u WHERE u.email = '" + email + "'";  // 易受攻击！
```

### JDBC（纯 Java）

```java
// ✅ 好：PreparedStatement
String sql = "SELECT * FROM users WHERE email = ? AND status = ?";
try (PreparedStatement stmt = connection.prepareStatement(sql)) {
    stmt.setString(1, email);
    stmt.setString(2, status);
    ResultSet rs = stmt.executeQuery();
}

// ❌ 不好：拼接的 Statement
String sql = "SELECT * FROM users WHERE email = '" + email + "'";  // 易受攻击！
```

---

## XSS 防护

### 输出编码

```java
// ✅ 好：使用模板引擎的自动转义

// Thymeleaf - 默认自动转义
<p th:text="${userInput}">...</p>  // 安全

// ✅ 好：需要时手动编码
import org.owasp.encoder.Encode;

String safe = Encode.forHtml(userInput);
String safeJs = Encode.forJavaScript(userInput);
String safeUrl = Encode.forUriComponent(userInput);
```

### 内容安全策略

```java
// Spring Boot
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
            .contentSecurityPolicy(csp -> csp
                .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'")
            )
        );
        return http.build();
    }
}
```

---

## 身份验证与授权

### 密码存储

```java
// ✅ 好：使用 BCrypt 或 Argon2
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// BCrypt（广泛支持）
PasswordEncoder encoder = new BCryptPasswordEncoder(12);  // 强度 12
String hash = encoder.encode(rawPassword);
boolean matches = encoder.matches(rawPassword, hash);

// ❌ 不好：MD5、SHA1、SHA256 不加盐
String hash = DigestUtils.md5Hex(password);  // 绝不用于密码！
```

### 授权检查

```java
// ✅ 好：在服务层检查授权
@Service
public class DocumentService {

    public Document getDocument(Long documentId, User currentUser) {
        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("文档未找到"));

        // 授权检查
        if (!doc.getOwnerId().equals(currentUser.getId()) &&
            !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("无权访问此文档");
        }

        return doc;
    }
}

// ❌ 不好：只在控制器层检查，信任用户输入
@GetMapping("/documents/{id}")
public Document getDocument(@PathVariable Long id) {
    return documentRepository.findById(id).orElseThrow();  // 没有授权检查！
}
```

---

## 密钥管理

### 永远不要硬编码密钥

```java
// ❌ 不好：硬编码密钥
private static final String API_KEY = "sk-1234567890abcdef";
private static final String DB_PASSWORD = "admin123";

// ✅ 好：环境变量
String apiKey = System.getenv("API_KEY");

// ✅ 好：外部配置
@Value("${api.key}")
private String apiKey;
```

### 配置文件

```yaml
# ✅ 好：引用环境变量
spring:
  datasource:
    password: ${DB_PASSWORD}

api:
  key: ${API_KEY}

# ❌ 不好：在 application.yml 中硬编码
spring:
  datasource:
    password: admin123  # 绝不！
```

---

## 安全反序列化

### 避免 Java 序列化

```java
// ❌ 危险：Java ObjectInputStream
ObjectInputStream ois = new ObjectInputStream(untrustedInput);
Object obj = ois.readObject();  // 远程代码执行风险！

// ✅ 好：使用 Jackson 的 JSON
ObjectMapper mapper = new ObjectMapper();
// 禁用危险功能
mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

User user = mapper.readValue(json, User.class);
```

---

## 依赖安全

### OWASP Dependency Check

**Maven:**
```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>9.0.7</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>  <!-- 高严重性时失败 -->
    </configuration>
</plugin>
```

**运行:**
```bash
mvn dependency-check:check
# 报告：target/dependency-check-report.html
```

---

## 安全头

### 推荐的头

| 头 | 值 | 目的 |
|----|----|----|
| `Content-Security-Policy` | `default-src 'self'` | 防止 XSS |
| `X-Content-Type-Options` | `nosniff` | 防止 MIME 嗅探 |
| `X-Frame-Options` | `DENY` | 防止点击劫持 |
| `Strict-Transport-Security` | `max-age=31536000` | 强制 HTTPS |
| `X-XSS-Protection` | `1; mode=block` | 旧版 XSS 过滤器 |

---

## 记录安全事件

```java
// ✅ 记录安全相关事件
log.info("用户登录成功", kv("userId", userId), kv("ip", clientIp));
log.warn("登录尝试失败", kv("username", username), kv("ip", clientIp));
log.warn("访问被拒绝", kv("userId", userId), kv("resource", resourceId));

// ❌ 绝不记录敏感数据
log.info("登录: user={}, password={}", username, password);  // 绝不！
log.debug("请求体: {}", requestWithCreditCard);  // 绝不！
```

---

## 安全检查清单

### 代码审查

- [ ] 使用白名单模式验证输入
- [ ] SQL 查询使用参数（不拼接）
- [ ] 输出根据上下文编码（HTML、JS、URL）
- [ ] 在服务层检查授权
- [ ] 没有硬编码密钥
- [ ] 密码使用 BCrypt/Argon2 哈希
- [ ] 不记录敏感数据
- [ ] CSRF 保护已启用（浏览器应用）

### 配置

- [ ] 强制 HTTPS
- [ ] 配置安全头
- [ ] 生产环境禁用调试/开发功能
- [ ] 更改默认凭据
- [ ] 错误消息不泄露内部细节

### 依赖

- [ ] 没有已知漏洞（OWASP 检查）
- [ ] 依赖是最新的
- [ ] 删除不必要的依赖

---

## 相关技能

- `java-code-review` - 通用代码审查
- `maven-dependency-audit` - 依赖漏洞扫描
- `logging-patterns` - 安全日志实践
