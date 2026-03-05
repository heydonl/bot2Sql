# 并发审查技能

> 审查 Java 并发代码的线程安全、竞态条件和现代模式

## 功能

审查多线程 Java 代码：
- 竞态条件和可见性问题
- 死锁潜在风险
- 现代模式（虚拟线程、结构化并发）
- Spring @Async 陷阱
- CompletableFuture 错误处理
- 线程池配置

## 为什么重要

> 近 60% 的多线程应用程序由于不当管理共享资源而遇到问题。

并发 bug 难以重现、难以测试、难以调试。在代码审查中捕获它们远比在生产中发现它们要好。

## 何时使用

- "审查线程安全"
- "检查并发问题"
- "这个异步代码正确吗？"
- 审查带 `synchronized`、`volatile`、`@Async` 的代码
- 检查 `CompletableFuture` 或 `ExecutorService` 使用

## 涵盖的关键主题

### 现代 Java（21/25）
| 主题 | 检查内容 |
|-------|---------------|
| 虚拟线程 | 用于 I/O 密集型，不是 CPU 密集型 |
| 结构化并发 | 适当的作用域管理 |
| ScopedValue | 优先于 ThreadLocal |

### Spring @Async
| 陷阱 | 问题 |
|---------|-------|
| 同类调用 | 绕过代理，同步运行 |
| 非公共方法 | 代理无法拦截 |
| 默认执行器 | 每个任务创建线程（OOM 风险） |
| SecurityContext | ThreadLocal 不传播 |

### 经典问题
| 问题 | 示例 |
|-------|---------|
| 竞态条件 | 无同步的检查后操作 |
| 可见性 | 缺少 volatile |
| 死锁 | 不一致的锁顺序 |

## 使用示例

```
你：审查这个服务的线程安全

Claude：[检查共享可变状态]
        [验证同步]
        [审查 @Async 配置]
        [检查 CompletableFuture 错误处理]
        [如适用，建议现代替代方案]
```

## 严重性级别

| 级别 | 含义 |
|-------|----------|
| 🔴 高 | 可能的 bug - 竞态条件、死锁风险 |
| 🟡 中 | 潜在问题 - 测量/验证 |
| 🟢 现代 | Java 21/25 模式的机会 |

## 相关技能

- `performance-smell-detection` - 性能问题（不是线程安全）
- `java-code-review` - 通用代码审查（包括基本并发）
- `spring-boot-patterns` - Spring 模式（包括 @Async 基础）

## 参考资料

- [Java 并发代码审查清单](https://github.com/code-review-checklists/java-concurrency)
- [Baeldung - 常见并发陷阱](https://www.baeldung.com/java-common-concurrency-pitfalls)
- [Oracle - 虚拟线程](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [JavaPro - Java 25 虚拟线程](https://javapro.io/2025/12/23/java-25-getting-the-most-out-of-virtual-threads-with-structured-task-scopes-and-scoped-values/)
- [Spring @Async 问题](https://serdaralkancode.medium.com/problems-and-solutions-when-using-async-in-spring-boot-e383f9d3b45d)
- 书籍：《Java 并发编程实战》Brian Goetz 著
