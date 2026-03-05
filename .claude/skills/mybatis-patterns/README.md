# MyBatis 模式

**加载**：`view .claude/skills/mybatis-patterns/SKILL.md`

---

## 描述

Spring 应用的 MyBatis 模式和最佳实践。涵盖 SQL 映射、动态 SQL、结果映射、N+1 问题、性能优化和安全防护。

---

## 使用场景

- "执行了太多 SQL 查询"
- "MyBatis 结果映射问题"
- "如何防止 SQL 注入？"
- "MyBatis N+1 问题"
- "如何优化 MyBatis 查询？"
- "动态 SQL 怎么写？"
- "批量插入/更新"

---

## 示例

```
> view .claude/skills/mybatis-patterns/SKILL.md
> "查询订单时看到 100 个 SQL"
→ 识别 N+1 问题，建议使用 JOIN 或批量查询
```

---

## 涵盖的主题

| 主题 | 关键点 |
|-------|------------|
| **SQL 映射** | Mapper 接口、XML 配置、注解方式 |
| **动态 SQL** | if、where、set、choose、foreach |
| **结果映射** | resultMap、association、collection |
| **N+1 问题** | JOIN 查询、批量查询 |
| **分页** | PageHelper、手动分页 |
| **批量操作** | foreach、ExecutorType.BATCH |
| **缓存** | 一级缓存、二级缓存 |
| **事务** | @Transactional、编程式事务 |
| **安全** | #{} vs ${}、SQL 注入防护 |

---

## 解决的常见错误

- 使用 ${} 导致 SQL 注入
- N+1 查询性能问题
- 结果映射字段为 null
- 忘记使用 @Param 注解
- 循环中执行查询

---

## 相关技能

- `spring-boot-patterns` - Spring Boot 模式
- `java-code-review` - 代码审查清单
- `security-audit` - 安全审计

---

## 资源

- [MyBatis 官方文档](https://mybatis.org/mybatis-3/zh/index.html)
- [MyBatis-Spring-Boot-Starter](https://mybatis.org/spring-boot-starter/)
- [PageHelper 分页插件](https://github.com/pagehelper/Mybatis-PageHelper)
- [Druid 连接池](https://github.com/alibaba/druid)
