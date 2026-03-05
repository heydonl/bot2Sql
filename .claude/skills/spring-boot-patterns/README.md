# Spring Boot 模式

**加载**: `view .claude/skills/spring-boot-patterns/SKILL.md`

---

## 描述

Spring Boot 应用的最佳实践和模式。涵盖项目结构、分层架构、DTO、异常处理、配置和测试。

---

## 使用场景

- "为产品创建 REST 控制器"
- "为用户管理添加服务层"
- "设置全局异常处理"
- "我应该如何组织这个 Spring Boot 项目的结构？"

---

## 示例

```
> view .claude/skills/spring-boot-patterns/SKILL.md
> "创建带 CRUD 端点的 UserController"
→ 生成遵循 REST 约定和正确状态码的控制器
```

---

## 涵盖的模式

| 层次 | 主题 |
|------|------|
| 控制器 | REST 约定、验证、状态码 |
| 服务 | 接口 + 实现、事务、映射器 |
| Mapper | MyBatis 接口、XML 映射、参数绑定 |
| DTO | 请求/响应 Record、MapStruct |
| 异常 | 自定义异常、全局处理器 |
| 配置 | 属性、环境、验证 |
| 测试 | MockMvc、Mockito、MyBatis Test |

---

## 注意事项 / 提示

- 使用构造器注入（Lombok `@RequiredArgsConstructor`）
- 服务类级别默认使用 `@Transactional(readOnly = true)`
- 永远不要直接暴露实体 - 使用 DTO
- DTO 优先使用 Record（Java 17+）
