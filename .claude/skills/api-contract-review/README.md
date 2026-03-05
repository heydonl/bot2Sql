# API 契约审查技能

> 审计 REST API 的 HTTP 语义、版本控制和一致性

## 功能

审查 REST API 设计：
- HTTP 动词正确性（GET vs POST vs PUT vs PATCH）
- API 版本控制策略
- 请求/响应结构（DTO vs 实体）
- 状态码使用（不要 200 带错误体）
- 向后兼容性问题

## 何时使用

- "审查这个 API" / "检查 REST 端点"
- 发布 API 更改之前
- 审查控制器 PR
- 检查 API 是否遵循 REST 最佳实践

## 关键概念

### 审计 vs 模板

| spring-boot-patterns | api-contract-review |
|---------------------|---------------------|
| 如何编写控制器 | 审查现有 API |
| 模板和示例 | 清单和反模式 |
| 创建新代码 | 审计现有代码 |

### 捕获的常见问题

| 问题 | 示例 |
|-------|---------|
| 错误的动词 | 搜索使用 POST 而不是 GET |
| 无版本控制 | `/users` 而不是 `/v1/users` |
| 实体泄漏 | 直接返回 JPA 实体 |
| 200 带错误 | `{"status": "error"}` 带 HTTP 200 |
| 破坏性更改 | 向请求添加必需字段 |

## 使用示例

```
你：审查 UserController 中的 API

Claude：[检查 HTTP 动词使用]
        [验证版本控制]
        [查找实体泄漏]
        [审查错误处理]
        [识别破坏性更改]
```

## 检查内容

1. **HTTP 语义** - 操作的正确动词
2. **URL 设计** - 版本控制、命名约定
3. **请求处理** - 验证、DTO
4. **响应设计** - DTO、分页、一致性
5. **错误处理** - 状态码、错误格式
6. **兼容性** - 破坏性 vs 非破坏性更改

## 相关技能

- `spring-boot-patterns` - 编写控制器的模板（此技能审计它们）
- `security-audit` - API 的安全方面
- `java-code-review` - 通用代码审查（此技能是 API 特定的）

## 参考资料

- [REST API 设计最佳实践](https://restfulapi.net/)
- [HTTP 状态码](https://httpstatuses.com/)
- [API 版本控制](https://www.baeldung.com/rest-versioning)
