---
name: performance-smell-detection
description: 检测 Java 代码中潜在的性能问题 - 流、集合、装箱、正则、对象创建。提供意识而非绝对 - 优化前始终先测量。对于 JPA/数据库性能，请使用 jpa-patterns。
---

# 性能问题检测技能

识别 Java 代码中**潜在的**代码级性能问题。

## 理念

> "过早优化是万恶之源" - Donald Knuth

此技能帮助你**注意到**潜在的性能问题，而不是盲目地"修复"它们。现代 JVM（Java 21/25）已经高度优化。始终：

1. **先测量** - 使用 JMH、性能分析器或生产指标
2. **关注热点路径** - 90% 的时间花在 10% 的代码上
3. **考虑可读性** - 清晰的代码通常比微优化更重要

## 何时使用
- 审查性能关键代码路径
- 调查已测量的性能问题
- 学习 Java 性能模式
- 具有性能意识的代码审查

## 范围

**此技能：** 代码级性能（流、集合、对象）
**数据库：** 使用 `mybatis-patterns` 技能（N+1、延迟加载、分页）
**架构：** 使用 `architecture-review` 技能

---

## 快速参考：潜在问题

| 问题 | 严重性 | 上下文 |
|-------|----------|------------|
| 循环中编译正则 | 🔴 高 | 总是值得修复 |
| 循环中字符串拼接 | 🟡 中 | 在 Java 21/25 中仍然有效 |
| 紧密循环中的流 | 🟡 中 | 取决于集合大小 |
| 热点路径中的装箱 | 🟡 中 | 先测量 |
| 无界集合 | 🔴 高 | 内存风险 |
| 缺少集合容量 | 🟢 低 | 轻微，关键时测量 |

---

## 字符串操作（Java 9+ / 21 / 25）

### 变化内容

自 **Java 9**（JEP 280）起，使用 `+` 的字符串拼接使用 `invokedynamic`，而不是 StringBuilder。JVM 能很好地优化简单拼接。

**Java 25** 为 String::hashCode 添加了常量折叠，以便在使用 String 键的 Map 查找中进行额外优化。

### 仍然有效：循环中的 StringBuilder

```java
// 🔴 仍然有问题 - 每次迭代创建新字符串
String result = "";
for (String s : items) {
    result += s;  // O(n²) - 创建 n 个字符串
}

// ✅ 循环中使用 StringBuilder
StringBuilder sb = new StringBuilder();
for (String s : items) {
    sb.append(s);
}
String result = sb.toString();

// ✅ 或使用 String.join / Collectors.joining
String result = String.join("", items);
```

### 现在没问题：简单拼接

```java
// ✅ Java 9+ 中没问题 - JVM 会优化
String message = "User " + name + " logged in at " + timestamp;

// ✅ 也没问题
return "Error: " + code + " - " + description;
```

### 热点路径中避免：String.format

```java
// 🟡 String.format 有解析开销
log.debug(String.format("Processing %s with id %d", name, id));

// ✅ 参数化日志（SLF4J）
log.debug("Processing {} with id {}", name, id);
```

---

## Stream API（细致观点）

### 现实情况

流有开销，但**通常可以接受**：
- **< 100 项**：流可能慢 2-5 倍（但仍然是微秒级）
- **1K-10K 项**：差异显著缩小
- **> 10K 项**：通常在循环的 50% 以内
- **GraalVM**：可以优化流以匹配循环

**建议**：为了可读性优先使用流。仅当性能分析显示瓶颈时才优化为循环。

### 流有问题的情况

```java
// 🔴 热点循环中每次迭代创建流
for (int i = 0; i < 1_000_000; i++) {
    boolean found = items.stream()
        .anyMatch(item -> item.getId() == i);
}

// ✅ 预先计算查找结构
Set<Integer> itemIds = items.stream()
    .map(Item::getId)
    .collect(Collectors.toSet());

for (int i = 0; i < 1_000_000; i++) {
    boolean found = itemIds.contains(i);
}
```

### 流没问题的情况

```java
// ✅ 单次遍历，可读，不在紧密循环中
List<String> names = users.stream()
    .filter(User::isActive)
    .map(User::getName)
    .sorted()
    .collect(Collectors.toList());

// ✅ 原始类型流避免装箱
int sum = numbers.stream()
    .mapToInt(Integer::intValue)
    .sum();
```

### 并行流：谨慎使用

```java
// 🔴 小集合上的并行 - 开销 > 收益
smallList.parallelStream().map(...);  // < 10K 项

// 🔴 并行与共享可变状态
List<String> results = new ArrayList<>();
items.parallelStream()
    .forEach(results::add);  // 竞态条件！

// ✅ CPU 密集型 + 大集合的并行
List<Result> results = largeDataset.parallelStream()  // > 10K 项
    .map(this::expensiveCpuComputation)
    .collect(Collectors.toList());
```

---

## 装箱/拆箱

### 仍然是真正的问题

装箱在堆上创建对象，增加 GC 压力。JVM 缓存小值（-128 到 127），但不缓存更大的值。

> **未来**：Project Valhalla 将显著改善这一点。

```java
// 🔴 紧密循环中的装箱 - 创建数百万个对象
Long sum = 0L;
for (int i = 0; i < 1_000_000; i++) {
    sum += i;  // 拆箱、相加、装箱
}

// ✅ 原始类型
long sum = 0L;
for (int i = 0; i < 1_000_000; i++) {
    sum += i;
}
```

### 使用原始类型流

```java
// 🟡 装箱开销
int sum = list.stream()
    .reduce(0, Integer::sum);

// ✅ 原始类型流
int sum = list.stream()
    .mapToInt(Integer::intValue)
    .sum();
```

---

## 正则表达式

### 循环中始终预编译

此建议**没有过时** - Pattern.compile 很昂贵。

```java
// 🔴 每次迭代编译模式
for (String input : inputs) {
    if (input.matches("\\d{3}-\\d{4}")) {  // 编译正则！
        process(input);
    }
}

// ✅ 预编译
private static final Pattern PHONE = Pattern.compile("\\d{3}-\\d{4}");

for (String input : inputs) {
    if (PHONE.matcher(input).matches()) {
        process(input);
    }
}
```

---

## 集合

### 容量提示（轻微优化）

```java
// 🟢 低严重性 - 但如果知道大小是免费优化
List<User> users = new ArrayList<>(expectedSize);
Map<String, User> map = new HashMap<>(expectedSize * 4 / 3 + 1);
```

### 选择正确的集合

```java
// 🟡 循环中 O(n) 查找
List<String> allowed = getAllowed();
for (Request r : requests) {
    if (allowed.contains(r.getId())) { }  // 每次 O(n)
}

// ✅ O(1) 查找
Set<String> allowed = new HashSet<>(getAllowed());
for (Request r : requests) {
    if (allowed.contains(r.getId())) { }  // O(1)
}
```

### 无界集合

```java
// 🔴 内存风险 - 可能无限增长
@GetMapping("/users")
public List<User> getAllUsers() {
    return userRepository.findAll();  // 数百万行？
}

// ✅ 分页
@GetMapping("/users")
public Page<User> getUsers(Pageable pageable) {
    return userRepository.findAll(pageable);
}
```

---

## 现代 Java（21/25）模式

### I/O 的虚拟线程（Java 21+）

```java
// 🟡 I/O 的传统线程池 - 浪费 OS 线程
ExecutorService executor = Executors.newFixedThreadPool(100);
for (Request request : requests) {
    executor.submit(() -> callExternalApi(request));  // 阻塞 OS 线程
}

// ✅ 虚拟线程 - 数百万并发 I/O 操作
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (Request request : requests) {
        executor.submit(() -> callExternalApi(request));
    }
}
```

### 结构化并发（Java 21+ 预览）

```java
// ✅ 并行 I/O 的结构化并发
try (StructuredTaskScope.ShutdownOnFailure scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Future<User> user = scope.fork(() -> fetchUser(id));
    Future<Orders> orders = scope.fork(() -> fetchOrders(id));

    scope.join();
    scope.throwIfFailed();

    return new UserProfile(user.resultNow(), orders.resultNow());
}
```

---

## 性能审查清单

### 🔴 高严重性（通常值得修复）
- [ ] 循环中的正则 Pattern.compile
- [ ] 无分页的无界查询
- [ ] 循环中的字符串拼接（StringBuilder 仍然有效）
- [ ] 带共享可变状态的并行流

### 🟡 中严重性（先测量）
- [ ] 紧密循环中的流（>100K 次迭代）
- [ ] 热点路径中的装箱
- [ ] 循环中的 List.contains()（使用 Set）
- [ ] I/O 的传统线程（考虑虚拟线程）

### 🟢 低严重性（锦上添花）
- [ ] 集合初始容量
- [ ] 轻微的流优化
- [ ] toArray(new T[0]) vs toArray(new T[size])

---

## 何时不优化

- **不是热点路径** - 设置代码、配置、管理端点
- **没有测量到的问题** - "看起来慢"不是测量
- **可读性受损** - 清晰代码 > 微优化
- **小集合** - 100 项处理反正是微秒级

---

## 分析命令

```bash
# 查找循环中的正则（潜在编译开销）
grep -rn "\.matches(\|\.split(" --include="*.java"

# 查找潜在装箱（Long/Integer 作为变量）
grep -rn "Long\s\|Integer\s\|Double\s" --include="*.java" | grep "= 0\|+="

# 查找没有容量的 ArrayList
grep -rn "new ArrayList<>()" --include="*.java"

# 查找没有分页的 findAll
grep -rn "findAll()" --include="*.java"
```
