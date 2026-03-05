# SOLID 原则

**加载**：`view .claude/skills/solid-principles/SKILL.md`

---

## 描述

SOLID 原则清单及详细 Java 示例。每个原则包括违规示例、重构解决方案和检测模式。

---

## 使用场景

- "检查这个类的 SOLID 违规"
- "这个类做的太多了吗？"（SRP）
- "如何在不修改代码的情况下添加新类型？"（OCP）
- "为什么 Square 不应该继承 Rectangle？"（LSP）
- "这个接口太大了"（ISP）
- "如何使其可测试？"（DIP）

---

## 示例

```
> view .claude/skills/solid-principles/SKILL.md
> "审查这个 UserService 的 SOLID 原则"
→ 识别 SRP 违规，建议提取验证和通知
```

---

## 涵盖的原则

| 原则 | 关键问题 |
|-----------|------------------|
| **S**ingle Responsibility（单一职责） | 它是否只有一个改变的理由？ |
| **O**pen/Closed（开闭） | 我能否在不修改的情况下扩展？ |
| **L**iskov Substitution（里氏替换） | 子类型能否替换基类型？ |
| **I**nterface Segregation（接口隔离） | 客户端是否被迫实现未使用的方法？ |
| **D**ependency Inversion（依赖倒置） | 它是否依赖于抽象？ |

---

## 相关技能

- `design-patterns` - 实现模式
- `clean-code` - DRY、KISS、YAGNI
- `java-code-review` - 完整审查清单

---

## 资源

- [SOLID（维基百科）](https://en.wikipedia.org/wiki/SOLID)
- [代码整洁之道 - Robert C. Martin](https://www.oreilly.com/library/view/clean-code-a/9780136083238/)
- [Java 中的 SOLID 原则（Baeldung）](https://www.baeldung.com/solid-principles)
