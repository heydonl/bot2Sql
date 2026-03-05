---
name: concurrency-review
description: 审查 Java 并发代码的线程安全、竞态条件、死锁和现代模式（虚拟线程、CompletableFuture、@Async）。当用户询问"检查线程安全"、"并发审查"、"异步代码审查"或审查多线程代码时使用。
---

# 并发审查技能

审查 Java 并发代码的正确性、安全性和现代最佳实践。

## 为什么重要

> 近 60% 的多线程应用程序由于不当管理共享资源而遇到问题。- ACM 研究

并发 bug：
- **难以重现** - 依赖时序
- **难以测试** - 可能仅在负载下出现
- **难以调试** - 非确定性行为

此技能帮助在问题到达生产**之前**捕获它们。

## 何时使用
- 审查带 `synchronized`、`volatile`、`Lock` 的代码
- 检查 `@Async`、`CompletableFuture`、`ExecutorService`
- 验证共享状态的线程安全
- 审查虚拟线程 / 结构化并发代码
- 任何被多个线程访问的代码

---

## 现代 Java（21/25）：虚拟线程

### 何时使用虚拟线程

```java
// ✅ 非常适合 I/O 密集型任务（HTTP、DB、文件 I/O）
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (Request request : requests) {
        executor.submit(() -> callExternalApi(request));
    }
}

// ❌ 对 CPU 密集型任务无益
// 改用平台线程 / ForkJoinPool
```

**经验法则**：如果你的应用从未有 10,000+ 并发任务，虚拟线程可能不会提供显著好处。

### Java 25：Synchronized 固定已修复

在 Java 21-23 中，虚拟线程在进入带阻塞操作的 `synchronized` 块时会被"固定"。**Java 25 修复了这个问题**（JEP 491）。

```java
// 在 Java 21-23 中：⚠️ 可能导致固定
synchronized (lock) {
    blockingIoCall();  // 虚拟线程固定到载体
}

// 在 Java 25 中：✅ 不再是问题
// 但无论如何考虑 ReentrantLock 以获得显式控制
```

### ScopedValue 优于 ThreadLocal

```java
// ❌ ThreadLocal 在虚拟线程中有问题
private static final ThreadLocal<User> currentUser = new ThreadLocal<>();

// ✅ ScopedValue（Java 21+ 预览，在 25 中改进）
private static final ScopedValue<User> CURRENT_USER = ScopedValue.newInstance();

ScopedValue.where(CURRENT_USER, user).run(() -> {
    // CURRENT_USER.get() 在这里和子虚拟线程中可用
    processRequest();
});
```

### 结构化并发（Java 25 预览）

```java
// ✅ 结构化并发 - 任务绑定到作用域生命周期
try (StructuredTaskScope.ShutdownOnFailure scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<User> userTask = scope.fork(() -> fetchUser(id));
    Subtask<Orders> ordersTask = scope.fork(() -> fetchOrders(id));

    scope.join();            // 等待所有
    scope.throwIfFailed();   // 传播异常

    return new Profile(userTask.get(), ordersTask.get());
}
// 如果作用域退出，所有子任务自动取消
```

---

## Spring @Async 陷阱

### 1. 忘记 @EnableAsync

```java
// ❌ @Async 被静默忽略
@Service
public class EmailService {
    @Async
    public void sendEmail(String to) { }
}

// ✅ 启用异步处理
@Configuration
@EnableAsync
public class AsyncConfig { }
```

### 2. 从同一类调用 Async

```java
@Service
public class OrderService {

    // ❌ 绕过代理 - 同步运行！
    public void processOrder(Order order) {
        sendConfirmation(order);  // 直接调用，不是异步
    }

    @Async
    public void sendConfirmation(Order order) { }
}

// ✅ 注入自身或使用单独的服务
@Service
public class OrderService {
    @Autowired
    private EmailService emailService;  // 单独的 bean

    public void processOrder(Order order) {
        emailService.sendConfirmation(order);  // 代理调用，异步工作
    }
}
```

### 3. 非公共方法上的 @Async

```java
// ❌ 非公共方法 - 代理无法拦截
@Async
private void processInBackground() { }

@Async
protected void processInBackground() { }

// ✅ 必须是公共的
@Async
public void processInBackground() { }
```

### 4. 默认执行器每个任务创建线程

```java
// ❌ 默认 SimpleAsyncTaskExecutor - 每次创建新线程！
// 负载下可能导致 OutOfMemoryError

// ✅ 配置适当的线程池
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### 5. SecurityContext 不传播

```java
// ❌ SecurityContextHolder 绑定到 ThreadLocal
@Async
public void auditAction() {
    // SecurityContextHolder.getContext() 在这里是 NULL！
    String user = SecurityContextHolder.getContext().getAuthentication().getName();
}

// ✅ 使用 DelegatingSecurityContextAsyncTaskExecutor
@Bean
public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // ... 配置 ...
    return new DelegatingSecurityContextAsyncTaskExecutor(executor);
}
```

---

## CompletableFuture 模式

### 错误处理

```java
// ❌ 异常被静默吞没
CompletableFuture.supplyAsync(() -> riskyOperation());
// 如果 riskyOperation 抛出异常，没人知道

// ✅ 始终处理异常
CompletableFuture.supplyAsync(() -> riskyOperation())
    .exceptionally(ex -> {
        log.error("Operation failed", ex);
        return fallbackValue;
    });

// ✅ 或使用 handle() 处理成功和失败
CompletableFuture.supplyAsync(() -> riskyOperation())
    .handle((result, ex) -> {
        if (ex != null) {
            log.error("Failed", ex);
            return fallbackValue;
        }
        return result;
    });
```

### 超时处理（Java 9+）

```java
// ✅ 超时后失败
CompletableFuture.supplyAsync(() -> slowOperation())
    .orTimeout(5, TimeUnit.SECONDS);  // 抛出 TimeoutException

// ✅ 超时后返回默认值
CompletableFuture.supplyAsync(() -> slowOperation())
    .completeOnTimeout(defaultValue, 5, TimeUnit.SECONDS);
```

### 组合 Future

```java
// ✅ 等待所有
CompletableFuture.allOf(future1, future2, future3)
    .thenRun(() -> log.info("All completed"));

// ✅ 等待第一个
CompletableFuture.anyOf(future1, future2, future3)
    .thenAccept(result -> log.info("First result: {}", result));

// ✅ 组合结果
future1.thenCombine(future2, (r1, r2) -> merge(r1, r2));
```

### 使用适当的执行器

```java
// ❌ ForkJoinPool.commonPool（默认）中的 CPU 密集型任务
CompletableFuture.supplyAsync(() -> cpuIntensiveWork());

// ✅ 阻塞/I/O 操作的自定义执行器
ExecutorService ioExecutor = Executors.newFixedThreadPool(20);
CompletableFuture.supplyAsync(() -> blockingIoCall(), ioExecutor);

// ✅ 在 Java 21+ 中，I/O 使用虚拟线程
ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
CompletableFuture.supplyAsync(() -> blockingIoCall(), virtualExecutor);
```

---

## 经典并发问题

### 竞态条件：检查后操作

```java
// ❌ 竞态条件
if (!map.containsKey(key)) {
    map.put(key, computeValue());  // 另一个线程可能已添加
}

// ✅ 原子操作
map.computeIfAbsent(key, k -> computeValue());

// ❌ 计数器的竞态条件
if (count < MAX) {
    count++;  // 读-检查-写不是原子的
}

// ✅ 原子计数器
AtomicInteger count = new AtomicInteger();
count.updateAndGet(c -> c < MAX ? c + 1 : c);
```

### 可见性：缺少 volatile

```java
// ❌ 其他线程可能永远看不到更新
private boolean running = true;

public void stop() {
    running = false;  // 可能对其他线程不可见
}

public void run() {
    while (running) { }  // 可能永远循环
}

// ✅ Volatile 确保可见性
private volatile boolean running = true;
```

### 非原子 long/double

```java
// ❌ 32 位 JVM 上的 64 位读/写不是原子的
private long counter;

public void increment() {
    counter++;  // 不是原子的！
}

// ✅ 使用 AtomicLong 或同步
private AtomicLong counter = new AtomicLong();

// ✅ 或 volatile（用于单写入者场景）
private volatile long counter;
```

### 双重检查锁定

```java
// ❌ 没有 volatile 会损坏
private static Singleton instance;

public static Singleton getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) {
                instance = new Singleton();  // 可能被看到部分构造
            }
        }
    }
    return instance;
}

// ✅ 使用 volatile 正确
private static volatile Singleton instance;

// ✅ 或使用持有者类习语
private static class Holder {
    static final Singleton INSTANCE = new Singleton();
}

public static Singleton getInstance() {
    return Holder.INSTANCE;
}
```

### 死锁：锁顺序

```java
// ❌ 潜在死锁
// 线程 1：lock(A) -> lock(B)
// 线程 2：lock(B) -> lock(A)

public void transfer(Account from, Account to, int amount) {
    synchronized (from) {
        synchronized (to) {
            // 转账逻辑
        }
    }
}

// ✅ 一致的锁顺序
public void transfer(Account from, Account to, int amount) {
    Account first = from.getId() < to.getId() ? from : to;
    Account second = from.getId() < to.getId() ? to : from;

    synchronized (first) {
        synchronized (second) {
            // 转账逻辑
        }
    }
}
```

---

## 线程安全集合

### 选择正确的集合

| 用例 | 错误 | 正确 |
|----------|-------|-------|
| 并发读/写 | `HashMap` | `ConcurrentHashMap` |
| 频繁迭代 | `ConcurrentHashMap` | `CopyOnWriteArrayList` |
| 生产者-消费者 | `ArrayList` | `BlockingQueue` |
| 排序并发 | `TreeMap` | `ConcurrentSkipListMap` |

### ConcurrentHashMap 陷阱

```java
// ❌ 非原子复合操作
if (!map.containsKey(key)) {
    map.put(key, value);
}

// ✅ 原子
map.putIfAbsent(key, value);
map.computeIfAbsent(key, k -> createValue());

// ❌ 嵌套 compute 可能死锁
map.compute(key1, (k, v) -> {
    return map.compute(key2, ...);  // 死锁风险！
});
```

---

## 并发审查清单

### 🔴 高严重性（可能的 Bug）
- [ ] 共享状态上无同步的检查后操作
- [ ] 无 `synchronized` 调用外部/未知代码（死锁风险）
- [ ] 双重检查锁定存在 `volatile`
- [ ] 循环中等待更新的非 volatile 字段
- [ ] `ConcurrentHashMap.compute()` 不调用其他 map 操作
- [ ] @Async 方法是公共的并从不同 bean 调用

### 🟡 中严重性（潜在问题）
- [ ] 线程池适当调整大小和命名
- [ ] CompletableFuture 异常已处理（exceptionally/handle）
- [ ] 如需要，SecurityContext 传播到异步任务
- [ ] `ExecutorService` 适当关闭
- [ ] `Lock.unlock()` 在 finally 块中
- [ ] 共享数据使用线程安全集合

### 🟢 现代模式（Java 21/25）
- [ ] I/O 密集型并发任务使用虚拟线程
- [ ] 考虑 ScopedValue 而非 ThreadLocal
- [ ] 相关子任务的结构化并发
- [ ] CompletableFuture 操作的超时

### 📝 文档
- [ ] 共享类上记录线程安全
- [ ] 嵌套锁的锁顺序已记录
- [ ] 每个 `volatile` 使用已证明合理

---

## 分析命令

```bash
# 查找 synchronized 块
grep -rn "synchronized" --include="*.java"

# 查找 @Async 方法
grep -rn "@Async" --include="*.java"

# 查找 volatile 字段
grep -rn "volatile" --include="*.java"

# 查找线程池创建
grep -rn "Executors\.\|ThreadPoolExecutor\|ExecutorService" --include="*.java"

# 查找没有错误处理的 CompletableFuture
grep -rn "CompletableFuture\." --include="*.java" | grep -v "exceptionally\|handle\|whenComplete"

# 查找 ThreadLocal（在 Java 21+ 中考虑 ScopedValue）
grep -rn "ThreadLocal" --include="*.java"
```
