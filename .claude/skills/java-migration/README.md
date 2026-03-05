# Java 迁移

**加载**：`view .claude/skills/java-migration/SKILL.md`

---

## 描述

主要 LTS 版本（8→11→17→21→25）之间升级 Java 项目的分步指南。包括破坏性更改、已删除的 API、要采用的新功能以及特定于框架的迁移（Spring Boot、Hibernate）。

---

## 使用场景

- "升级项目到 Java 25"
- "从 Java 21 迁移到 25"
- "Spring Boot 3 迁移"
- "升级到 Java 25 时会破坏什么？"
- "修复 javax.xml.bind 未找到"

---

## 示例

```
> view .claude/skills/java-migration/SKILL.md
> "将此项目从 Java 11 升级到 21"
→ 分析代码，识别破坏性更改，提供分步修复
```

---

## 涵盖的迁移路径

| 从 | 到 | 关键更改 |
|------|-----|-------------|
| Java 8 | Java 11 | JAXB 已删除、模块系统、内部 API |
| Java 11 | Java 17 | Record、密封类、强封装 |
| Java 17 | Java 21 | 虚拟线程、模式匹配、有序集合 |
| Java 21 | Java 25 | Security Manager 已删除、Unsafe 已删除、Scoped Values 最终版 |
| Spring Boot 2.x | 3.x | javax.* → jakarta.*、需要 Java 17 |
| Hibernate 5 | 6 | 查询 API 更改、ID 生成 |

---

## 使用的工具

| 工具 | 目的 |
|------|---------|
| `grep` | 查找已弃用的 API 使用 |
| `mvn compile` | 识别编译错误 |
| OpenRewrite | 自动化 Spring Boot 3 迁移 |
| `--add-opens` | 修复反射访问问题 |

---

## 注意事项 / 提示

- 始终 LTS → LTS 迁移（8→11→17→21→25）
- 首先更新 Lombok、Mockito 到最新版本
- 使用 OpenRewrite 进行自动化迁移
- 每步后彻底测试
- Java 25 LTS 支持到 2033 年 9 月

## 参考资料

- [Oracle JDK 25 迁移指南](https://docs.oracle.com/en/java/javase/25/migrate/)
- [Oracle JDK 25 发布说明](https://www.oracle.com/java/technologies/javase/25-relnote-issues.html)
- [OpenRewrite Java 迁移配方](https://docs.openrewrite.org/recipes/java/migrate)
- [Spring Boot 3.0 迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)
