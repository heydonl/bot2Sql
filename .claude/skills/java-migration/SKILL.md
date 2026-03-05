---
name: java-migration
description: 主要版本（8→11→17→21→25）之间升级 Java 项目的指南。当用户说"升级 Java"、"迁移到 Java 25"、"更新 Java 版本"或现代化遗留项目时使用。
---

# Java 迁移技能

主要版本之间升级 Java 项目的分步指南。

## 何时使用
- 用户说"升级到 Java 25" / "从 Java 8 迁移" / "更新 Java 版本"
- 现代化遗留项目
- Spring Boot 2.x → 3.x → 4.x 迁移
- 准备采用 LTS 版本

## 迁移路径

```
Java 8 (LTS) → Java 11 (LTS) → Java 17 (LTS) → Java 21 (LTS) → Java 25 (LTS)
     │              │               │              │               │
     └──────────────┴───────────────┴──────────────┴───────────────┘
                         始终 LTS → LTS 迁移
```

---

## 快速参考：会破坏什么

| 从 → 到 | 主要破坏性更改 |
|-----------|------------------------|
| 8 → 11 | 已删除 `javax.xml.bind`、模块系统、内部 API |
| 11 → 17 | 密封类（预览→最终）、强封装 |
| 17 → 21 | 模式匹配更改、`finalize()` 弃用待删除 |
| 21 → 25 | Security Manager 已删除、Unsafe 方法已删除、放弃 32 位 |

---

## 迁移工作流

### 步骤 1：评估当前状态

```bash
# 检查当前 Java 版本
java -version

# 检查 Maven 中的编译器目标
grep -r "maven.compiler" pom.xml

# 查找已删除 API 的使用
grep -r "sun\." --include="*.java" src/
grep -r "javax\.xml\.bind" --include="*.java" src/
```

### 步骤 2：更新构建配置

**Maven：**
```xml
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>${java.version}</maven.compiler.source>
    <maven.compiler.target>${java.version}</maven.compiler.target>
</properties>

<!-- 或使用编译器插件 -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.12.1</version>
    <configuration>
        <release>21</release>
    </configuration>
</plugin>
```

**Gradle：**
```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

### 步骤 3：修复编译错误

迭代运行编译并修复错误：
```bash
mvn clean compile 2>&1 | head -50
```

### 步骤 4：运行测试

```bash
mvn test
```

### 步骤 5：检查运行时警告

```bash
# 运行时显示非法访问警告
java --illegal-access=warn -jar app.jar
```

---

## Java 8 → 11 迁移

### 已删除的 API

| 已删除 | 替代 |
|---------|-------------|
| `javax.xml.bind` (JAXB) | 添加依赖：`jakarta.xml.bind-api` + `jaxb-runtime` |
| `javax.activation` | 添加依赖：`jakarta.activation-api` |
| `javax.annotation` | 添加依赖：`jakarta.annotation-api` |
| `java.corba` | 无替代（很少使用） |
| `java.transaction` | 添加依赖：`jakarta.transaction-api` |
| `sun.misc.Base64*` | 使用 `java.util.Base64` |
| `sun.misc.Unsafe`（部分） | 尽可能使用 `VarHandle` |

### 添加缺失的依赖（Maven）

```xml
<!-- JAXB（如需要） -->
<dependency>
    <groupId>jakarta.xml.bind</groupId>
    <artifactId>jakarta.xml.bind-api</artifactId>
    <version>4.0.1</version>
</dependency>
<dependency>
    <groupId>org.glassfish.jaxb</groupId>
    <artifactId>jaxb-runtime</artifactId>
    <version>4.0.4</version>
    <scope>runtime</scope>
</dependency>

<!-- Annotation API -->
<dependency>
    <groupId>jakarta.annotation</groupId>
    <artifactId>jakarta.annotation-api</artifactId>
    <version>2.1.1</version>
</dependency>
```

### 模块系统问题

如果对 JDK 内部使用反射，添加 JVM 标志：
```bash
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
```

### 要采用的新功能

```java
// var（局部变量类型推断）
var list = new ArrayList<String>();  // 而不是 ArrayList<String> list = ...

// String 方法
"  hello  ".isBlank();      // 仅空白为 true
"  hello  ".strip();        // 更好的 trim()（Unicode 感知）
"line1\nline2".lines();     // Stream<String>
"ha".repeat(3);             // "hahaha"

// 集合工厂方法（Java 9+）
List.of("a", "b", "c");     // 不可变列表
Set.of(1, 2, 3);            // 不可变集合
Map.of("k1", "v1");         // 不可变映射

// HTTP 客户端（替代 HttpURLConnection）
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com"))
    .build();
HttpResponse<String> response = client.send(request,
    HttpResponse.BodyHandlers.ofString());
```

---

## Java 11 → 17 迁移

### 要采用的新功能

```java
// Record（不可变数据类）
public record User(String name, String email) {}
// 自动生成：构造函数、getter、equals、hashCode、toString

// 密封类
public sealed class Shape permits Circle, Rectangle {}
public final class Circle extends Shape {}
public final class Rectangle extends Shape {}

// instanceof 的模式匹配
if (obj instanceof String s) {
    System.out.println(s.length());  // s 已转换
}

// Switch 表达式
String result = switch (day) {
    case MONDAY, FRIDAY -> "Work";
    case SATURDAY, SUNDAY -> "Rest";
    default -> "Midweek";
};

// 文本块
String json = """
    {
        "name": "John",
        "age": 30
    }
    """;
```

---

## Java 17 → 21 迁移

### 要采用的新功能

```java
// 虚拟线程（Project Loom）- 重大
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> handleRequest());
}
// 或简单地：
Thread.startVirtualThread(() -> doWork());

// switch 中的模式匹配
String formatted = switch (obj) {
    case Integer i -> "int: " + i;
    case String s -> "string: " + s;
    case null -> "null value";
    default -> "unknown";
};

// Record 模式
record Point(int x, int y) {}
if (obj instanceof Point(int x, int y)) {
    System.out.println(x + ", " + y);
}

// 有序集合
List<String> list = new ArrayList<>();
list.addFirst("first");    // 新方法
list.addLast("last");      // 新方法
list.reversed();           // 反转视图
```

---

## Java 21 → 25 迁移

### 破坏性更改

| 更改 | 影响 |
|--------|--------|
| Security Manager 已删除 | 依赖它的应用需要替代安全方法 |
| `sun.misc.Unsafe` 方法已删除 | 改用 `VarHandle` 或 FFM API |
| 放弃 32 位平台 | 不再支持 x86-32 |
| Record 模式变量 final | 无法在 switch 中重新分配模式变量 |
| 不允许 `ScopedValue.orElse(null)` | 必须提供非 null 默认值 |
| 动态代理受限 | 需要 `-XX:+EnableDynamicAgentLoading` 标志 |

### 检查 Unsafe 使用

```bash
# 查找 sun.misc.Unsafe 使用
grep -rn "sun\.misc\.Unsafe" --include="*.java" src/

# 查找 Security Manager 使用
grep -rn "SecurityManager\|System\.getSecurityManager" --include="*.java" src/
```

### 要采用的新功能

```java
// Scoped Values（Java 25 中最终版）- 替代 ThreadLocal
private static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();

public void handleRequest(User user) {
    ScopedValue.where(CURRENT_USER, user).run(() -> {
        processRequest();  // CURRENT_USER.get() 在这里和子线程中可用
    });
}

// 结构化并发（预览，25 中重新设计的 API）
try (StructuredTaskScope.ShutdownOnFailure scope = StructuredTaskScope.open()) {
    Subtask<User> userTask = scope.fork(() -> fetchUser(id));
    Subtask<Orders> ordersTask = scope.fork(() -> fetchOrders(id));

    scope.join();
    scope.throwIfFailed();

    return new Profile(userTask.get(), ordersTask.get());
}

// Compact Object Headers - 自动，无需代码更改
// 对象现在使用 64 位头而不是 128 位（更少内存）
```

### 性能改进（自动）

Java 25 包括几个自动性能改进：
- **Compact Object Headers**：每个对象 8 字节而不是 16 字节
- **String.hashCode() 常量折叠**：使用 String 键的 Map 查找更快
- **AOT 类加载**：使用提前缓存更快启动
- **分代 Shenandoah GC**：更好的吞吐量、更低的暂停

---

## Spring Boot 迁移

### Spring Boot 2.x → 3.x

**要求：**
- Java 17+（强制）
- Jakarta EE 9+（javax.* → jakarta.*）

**包重命名：**
```java
// 之前（Spring Boot 2.x）
import javax.persistence.*;
import javax.validation.*;
import javax.servlet.*;

// 之后（Spring Boot 3.x）
import jakarta.persistence.*;
import jakarta.validation.*;
import jakarta.servlet.*;
```

**查找和替换：**
```bash
# 查找需要迁移的所有 javax 导入
grep -r "import javax\." --include="*.java" src/ | grep -v "javax.crypto" | grep -v "javax.net"
```

**自动化迁移：**
```bash
# 使用 OpenRewrite
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:LATEST \
  -Drewrite.activeRecipes=org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0
```

---

## 常见迁移问题

### 问题：反射访问被拒绝

**症状：**
```
java.lang.reflect.InaccessibleObjectException: Unable to make field accessible
```

**修复：**
```bash
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
```

### 问题：JAXB ClassNotFoundException

**症状：**
```
java.lang.ClassNotFoundException: javax.xml.bind.JAXBContext
```

**修复：** 添加 JAXB 依赖（见 Java 8→11 部分）

---

## 迁移清单

### 迁移前
- [ ] 记录当前 Java 版本
- [ ] 列出所有依赖及其版本
- [ ] 识别内部 API 使用（`sun.*`、`com.sun.*`）
- [ ] 检查框架兼容性（Spring、Hibernate 等）
- [ ] 备份 / 创建分支

### 迁移期间
- [ ] 更新构建工具配置
- [ ] 添加缺失的 Jakarta 依赖
- [ ] 修复 `javax.*` → `jakarta.*` 导入（如果 Spring Boot 3）
- [ ] 如需要添加 `--add-opens` 标志
- [ ] 更新 Lombok、Mockito、其他工具
- [ ] 修复编译错误
- [ ] 运行测试

### 迁移后
- [ ] 删除不必要的 `--add-opens` 标志
- [ ] 采用新语言功能（record、var 等）
- [ ] 更新 CI/CD 管道
- [ ] 记录所做的更改

---

## 版本兼容性矩阵

| 框架 | Java 8 | Java 11 | Java 17 | Java 21 | Java 25 |
|-----------|--------|---------|---------|---------|---------|
| Spring Boot 2.7.x | ✅ | ✅ | ✅ | ⚠️ | ❌ |
| Spring Boot 3.2.x | ❌ | ❌ | ✅ | ✅ | ✅ |
| Spring Boot 3.4+ | ❌ | ❌ | ✅ | ✅ | ✅ |
| Hibernate 5.6 | ✅ | ✅ | ✅ | ⚠️ | ❌ |
| Hibernate 6.4+ | ❌ | ❌ | ✅ | ✅ | ✅ |
| JUnit 5.10+ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Mockito 5+ | ❌ | ✅ | ✅ | ✅ | ✅ |
| Lombok 1.18.34+ | ✅ | ✅ | ✅ | ✅ | ✅ |

**LTS 支持时间线：**
- Java 21：Oracle 免费支持到 2028 年 9 月
- Java 25：Oracle 免费支持到 2033 年 9 月
