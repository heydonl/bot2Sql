# 日志模式

**加载**：`view .claude/skills/logging-patterns/SKILL.md`

---

## 描述

Java 日志最佳实践，包括 SLF4J、结构化日志（JSON）和用于请求追踪的 MDC。包含为 Claude Code 分析优化的 AI 友好日志格式。

---

## 使用场景

- "为这个服务添加日志"
- "调试这个流程"（AI 读取日志）
- "设置结构化日志"
- "为什么这个请求失败？"（分析日志）
- "添加请求追踪"

---

## 关键洞察：JSON 用于 AI

**JSON 日志更适合 AI/Claude Code 分析：**

| 方面 | 文本日志 | JSON 日志 |
|--------|-----------|-----------|
| 解析 | 正则解释 | 直接字段访问 |
| Token | 更高 | 更低 |
| 过滤 | grep 模式 | jq 查询 |

```bash
# AI 可以轻松过滤 JSON
cat app.log | jq 'select(.requestId == "abc123")'
```

---

## 涵盖的主题

| 主题 | 描述 |
|-------|-------------|
| **AI 友好日志** | 为 Claude Code 优化的 JSON 格式 |
| **Spring Boot 3.4+** | 原生结构化日志支持 |
| **Logstash 编码器** | 用于 Spring Boot < 3.4 |
| **SLF4J/MDC** | 请求上下文、关联 ID |
| **日志级别** | 何时使用 ERROR、WARN、INFO、DEBUG |
| **记录什么** | 业务事件、时间、流程步骤 |
| **不记录什么** | 密码、PII、敏感数据 |

---

## 快速设置（Spring Boot 3.4+）

```yaml
logging:
  structured:
    format:
      console: logstash
```

无需额外依赖！

---

## 相关技能

- `spring-boot-patterns` - Spring 配置
- `jpa-patterns` - 数据库日志

---

## 资源

- [Spring Boot 3.4 中的结构化日志（spring.io）](https://spring.io/blog/2024/08/23/structured-logging-in-spring-boot-3-4/)
- [Spring Boot 中的结构化日志（Baeldung）](https://www.baeldung.com/spring-boot-structured-logging)
- [Java 日志的 10 个最佳实践（Better Stack）](https://betterstack.com/community/guides/logging/how-to-start-logging-with-java/)
- [Booking.com - 结构化日志](https://medium.com/booking-com-development/unlocking-observability-structured-logging-in-spring-boot-c81dbabfb9e7)
- [SLF4J 手册](https://www.slf4j.org/manual.html)
