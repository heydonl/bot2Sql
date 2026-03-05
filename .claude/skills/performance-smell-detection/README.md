# 性能问题检测技能

> 识别潜在的代码级性能问题 - 细致入微，而非绝对

## 功能

帮助注意 Java 代码中的**潜在**性能问题：
- Stream API 使用模式
- 装箱/拆箱开销
- 正则表达式编译成本
- 集合低效问题
- 字符串操作

**理念**："先测量，后优化" - 现代 JVM 已经高度优化。

## 何时使用

- "检查性能问题"
- "审查这个热点路径"
- "这段代码高效吗？"
- 调查已测量的慢速问题

## Java 版本意识

此技能考虑了现代 Java 优化：

| 主题 | Java 9+ 变化 |
|-------|-------------------|
| 字符串 `+` | 使用 invokedynamic，循环外优化良好 |
| StringBuilder | 循环中仍然最佳 |
| 虚拟线程 | Java 21+ 用于 I/O 密集型工作 |
| String hashCode | Java 25 常量折叠 |

## 严重性级别

| 级别 | 含义 | 行动 |
|-------|---------|-----------|
| 🔴 高 | 通常值得修复 | 主动修复 |
| 🟡 中 | 先测量 | 更改前先分析 |
| 🟢 低 | 锦上添花 | 仅在关键路径 |

## 检查内容

1. **字符串** - 循环中的拼接（仍然有效的关注点）
2. **流** - 紧密循环中的开销、并行误用
3. **装箱** - 热点路径中的原始类型包装器
4. **正则** - 循环中的 Pattern.compile
5. **集合** - 错误类型、无界查询
6. **现代模式** - 虚拟线程、结构化并发

## 不检查内容

- **JPA/数据库** - 使用 `jpa-patterns` 技能
- **架构** - 使用 `architecture-review` 技能
- **JVM 调优** - 超出范围（GC、堆等）

## 使用示例

```
你：检查这段代码的性能问题

Claude：[识别潜在问题]
        [评估严重性：🔴/🟡/🟢]
        [建议更改前先测量]
        [如适用，建议现代替代方案]
```

## 相关技能

- `jpa-patterns` - 数据库性能（N+1、分页）
- `java-code-review` - 通用代码质量
- `concurrency-review` - 线程安全和异步模式

## 参考资料

- [Inside.java - JDK 25 性能](https://inside.java/2025/10/20/jdk-25-performance-improvements/)
- [Java 25 特性 - InfoQ](https://www.infoq.com/news/2025/09/java25-released/)
- [Baeldung - 流 vs 循环](https://www.baeldung.com/java-streams-vs-loops)
- [Baeldung - 字符串拼接](https://www.baeldung.com/java-string-concatenation-methods)
