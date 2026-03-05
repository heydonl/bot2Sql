---
name: java-code-review
description: 系统化的 Java 代码审查，包含空值安全、异常处理、并发和性能检查。当用户说"审查代码"、"检查这个 PR"、"代码审查"，或在合并更改前使用。
---

# Java 代码审查技能

Java 项目的系统化代码审查清单。

## 何时使用
- 用户说"审查这段代码" / "检查这个 PR" / "代码审查"
- 合并 PR 之前
- 实现功能之后

## 审查策略

1. **快速扫描** - 理解意图，识别范围
2. **清单检查** - 遍历下面的每个类别
3. **总结** - 按严重程度列出发现（严重 → 轻微）

## 输出格式

```markdown
## 代码审查：[文件/功能名称]

### 严重问题
- [问题描述 + 行引用 + 建议]

### 改进建议
- [建议 + 理由]

### 轻微/风格
- [细节问题，可选改进]

### 观察到的良好实践
- [正面反馈 - 对士气很重要]
```

---

## 审查清单

### 1. 空值安全

**检查：**
```java
// ❌ NPE 风险
String name = user.getName().toUpperCase();

// ✅ 安全
String name = Optional.ofNullable(user.getName())
    .map(String::toUpperCase)
    .orElse("");

// ✅ 也安全（提前返回）
if (user.getName() == null) {
    return "";
}
return user.getName().toUpperCase();
```

**标记：**
- 链式方法调用没有空值检查
- 公共 API 缺少 `@Nullable` / `@NonNull` 注解
- `Optional.get()` 没有 `isPresent()` 检查
- 从可以返回 `Optional` 或空集合的方法返回 `null`

**建议：**
- 对可能缺失的返回类型使用 `Optional`
- 对构造器/方法参数使用 `Objects.requireNonNull()`
- 返回空集合而不是 null：`Collections.emptyList()`

### 2. 异常处理

**检查：**
```java
// ❌ 吞掉异常
try {
    process();
} catch (Exception e) {
    // 静默忽略
}

// ❌ 捕获过于宽泛
catch (Exception e) { }
catch (Throwable t) { }

// ❌ 丢失堆栈跟踪
catch (IOException e) {
    throw new RuntimeException(e.getMessage());
}

// ✅ 正确处理
catch (IOException e) {
    log.error("处理文件失败: {}", filename, e);
    throw new ProcessingException("文件处理失败", e);
}
```

**标记：**
- 空的 catch 块
- 宽泛地捕获 `Exception` 或 `Throwable`
- 丢失原始异常（未链接）
- 使用异常进行流程控制
- 检查异常泄漏到 API 边界

**建议：**
- 记录上下文和堆栈跟踪
- 使用特定的异常类型
- 使用 `cause` 链接异常
- 考虑为领域错误使用自定义异常

### 3. 集合与流

**检查：**
```java
// ❌ 迭代时修改
for (Item item : items) {
    if (item.isExpired()) {
        items.remove(item);  // ConcurrentModificationException
    }
}

// ✅ 使用 removeIf
items.removeIf(Item::isExpired);

// ❌ 简单操作使用流
list.stream().forEach(System.out::println);

// ✅ 简单循环更清晰
for (Item item : list) {
    System.out.println(item);
}

// ❌ 收集后修改
List<String> names = users.stream()
    .map(User::getName)
    .collect(Collectors.toList());
names.add("extra");  // 可能是不可变的！

// ✅ 显式可变列表
List<String> names = users.stream()
    .map(User::getName)
    .collect(Collectors.toCollection(ArrayList::new));
```

**标记：**
- 迭代期间修改集合
- 简单操作过度使用流
- 假设 `Collectors.toList()` 返回可变列表
- 不使用 `List.of()`、`Set.of()`、`Map.of()` 创建不可变集合
- 不理解影响就使用并行流

**建议：**
- 使用 `List.copyOf()` 进行防御性复制
- 使用 `removeIf()` 而不是迭代器删除
- 流用于转换，循环用于副作用

### 4. 并发

**检查：**
```java
// ❌ 非线程安全
private Map<String, User> cache = new HashMap<>();

// ✅ 线程安全
private Map<String, User> cache = new ConcurrentHashMap<>();

// ❌ 检查-然后-操作竞态条件
if (!map.containsKey(key)) {
    map.put(key, computeValue());
}

// ✅ 原子操作
map.computeIfAbsent(key, k -> computeValue());

// ❌ 双重检查锁定（没有 volatile 会出错）
if (instance == null) {
    synchronized(this) {
        if (instance == null) {
            instance = new Instance();
        }
    }
}
```

**标记：**
- 共享可变状态没有同步
- 检查-然后-操作模式没有原子性
- 共享变量缺少 `volatile`
- 在非 final 对象上同步
- 线程不安全的懒初始化

**建议：**
- 优先使用不可变对象
- 使用 `java.util.concurrent` 类
- 简单情况使用 `AtomicReference`、`AtomicInteger`
- 考虑 `@ThreadSafe` / `@NotThreadSafe` 注解

### 5. Java 惯用法

**equals/hashCode：**
```java
// ❌ 只有 equals 没有 hashCode
@Override
public boolean equals(Object o) { ... }
// 缺少 hashCode！

// ❌ hashCode 中使用可变字段
@Override
public int hashCode() {
    return Objects.hash(id, mutableField);  // 破坏 HashMap
}

// ✅ 使用不可变字段，两者都实现
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User user)) return false;
    return Objects.equals(id, user.id);
}

@Override
public int hashCode() {
    return Objects.hash(id);
}
```

**toString：**
```java
// ❌ 缺失 - 难以调试
// 没有 toString()

// ❌ 包含敏感数据
return "User{password='" + password + "'}";

// ✅ 对调试有用
@Override
public String toString() {
    return "User{id=" + id + ", name='" + name + "'}";
}
```

**建造者：**
```java
// ✅ 用于有多个可选参数的类
User user = User.builder()
    .name("John")
    .email("john@example.com")
    .build();
```

**标记：**
- `equals` 没有 `hashCode`
- `hashCode` 中使用可变字段
- 领域对象缺少 `toString`
- 构造器有 > 3-4 个参数（建议使用建造者）
- 不使用 `instanceof` 模式匹配（Java 16+）

### 6. 资源管理

**检查：**
```java
// ❌ 资源泄漏
FileInputStream fis = new FileInputStream(file);
// ... 可能在关闭前抛出异常

// ✅ Try-with-resources
try (FileInputStream fis = new FileInputStream(file)) {
    // ...
}

// ❌ 多个资源，顺序错误
try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
    // 如果 BufferedWriter 失败，FileWriter 可能不会关闭
}

// ✅ 分开声明
try (FileWriter fw = new FileWriter(file);
     BufferedWriter writer = new BufferedWriter(fw)) {
    // 两者都正确关闭
}
```

**标记：**
- 对 `Closeable`/`AutoCloseable` 不使用 try-with-resources
- 资源已打开但不在 try-with-resources 中
- 数据库连接/语句未正确关闭

### 7. API 设计

**检查：**
```java
// ❌ 布尔参数
process(data, true, false);  // 这些是什么意思？

// ✅ 使用枚举或建造者
process(data, ProcessMode.ASYNC, ErrorHandling.STRICT);

// ❌ "未找到"返回 null
public User findById(Long id) {
    return users.get(id);  // 未找到时为 null
}

// ✅ 返回 Optional
public Optional<User> findById(Long id) {
    return Optional.ofNullable(users.get(id));
}

// ❌ 接受 null 集合
public void process(List<Item> items) {
    if (items == null) items = Collections.emptyList();
}

// ✅ 要求非 null，接受空
public void process(List<Item> items) {
    Objects.requireNonNull(items, "items 不能为 null");
}
```

**标记：**
- 布尔参数（优先使用枚举）
- 方法有 > 3 个参数（考虑参数对象）
- 类似方法间的空值处理不一致
- 公共 API 输入缺少验证

### 8. 性能考虑

**检查：**
```java
// ❌ 循环中的字符串连接
String result = "";
for (String s : strings) {
    result += s;  // 每次迭代创建新 String
}

// ✅ StringBuilder
StringBuilder sb = new StringBuilder();
for (String s : strings) {
    sb.append(s);
}

// ❌ 循环中编译正则表达式
for (String line : lines) {
    if (line.matches("pattern.*")) { }  // 每次编译正则
}

// ✅ 预编译模式
private static final Pattern PATTERN = Pattern.compile("pattern.*");
for (String line : lines) {
    if (PATTERN.matcher(line).matches()) { }
}

// ❌ 循环中的 N+1
for (User user : users) {
    List<Order> orders = orderRepo.findByUserId(user.getId());
}

// ✅ 批量获取
Map<Long, List<Order>> ordersByUser = orderRepo.findByUserIds(userIds);
```

**标记：**
- 循环中的字符串连接
- 循环中编译正则表达式
- N+1 查询模式
- 在紧密循环中创建可重用的对象
- 不使用原始流（`IntStream`、`LongStream`）

### 9. 测试提示

**建议测试：**
- Null 输入
- 空集合
- 边界值
- 异常情况
- 并发访问（如适用）

---

## 严重程度指南

| 严重程度 | 标准 |
|---------|------|
| **严重** | 安全漏洞、数据丢失风险、生产崩溃 |
| **高** | 可能的 Bug、重大性能问题、破坏 API 契约 |
| **中** | 代码异味、可维护性问题、缺少最佳实践 |
| **低** | 风格、小优化、建议 |

## Token 优化

- 关注更改的行（使用 `git diff`）
- 不要重复明显的问题 - 将类似发现分组
- 引用行号，而不是完整代码引用
- 跳过自动生成的文件或测试固件

## 快速参考卡

| 类别 | 关键检查 |
|------|---------|
| 空值安全 | 链式调用、Optional 误用、null 返回 |
| 异常 | 空 catch、宽泛 catch、丢失堆栈跟踪 |
| 集合 | 迭代时修改、流 vs 循环 |
| 并发 | 共享可变状态、检查-然后-操作 |
| 惯用法 | equals/hashCode 对、toString、建造者 |
| 资源 | try-with-resources、连接泄漏 |
| API | 布尔参数、null 处理、验证 |
| 性能 | 字符串连接、循环中的正则、N+1 |
