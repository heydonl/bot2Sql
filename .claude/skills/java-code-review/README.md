# Java 代码审查

**加载**: `view .claude/skills/java-code-review/SKILL.md`

---

## 描述

Java 项目的系统化代码审查清单。涵盖空值安全、异常处理、集合、并发、惯用法、资源管理、API 设计和性能。

---

## 使用场景

- "审查这个类"
- "检查这个 PR 的问题"
- "代码审查 PluginManager 中的更改"
- "这段代码有什么问题？"

---

## 示例

```
> view .claude/skills/java-code-review/SKILL.md
> "审查 src/main/java/org/example/UserService.java 中的更改"
→ 返回按严重程度分组的发现（严重 → 轻微）
```

---

## 清单类别

1. **空值安全** - NPE 风险、Optional 使用
2. **异常处理** - 吞掉的异常、堆栈跟踪
3. **集合与流** - 迭代、可变性
4. **并发** - 线程安全、竞态条件
5. **Java 惯用法** - equals/hashCode、建造者
6. **资源管理** - try-with-resources
7. **API 设计** - 布尔参数、验证
8. **性能** - 字符串连接、N+1 查询

---

## 注意事项 / 提示

- 最适合聚焦的更改（单个类或 PR）
- 包含良好实践的正面反馈部分
- 为审查期间发现的边界情况建议测试
