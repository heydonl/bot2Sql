# 整洁代码

**加载**: `view .claude/skills/clean-code/SKILL.md`

---

## 描述

整洁代码原则及 Java 示例：DRY、KISS、YAGNI、命名约定、函数设计、代码异味和重构技术。

---

## 使用场景

- "整理这段代码"
- "重构这个方法"
- "提高可读性"
- "这个函数太长了"
- "我应该如何命名这个变量？"
- "这段代码是否太复杂？"

---

## 示例

```
> view .claude/skills/clean-code/SKILL.md
> "这个方法有 100 行，帮我重构"
→ 识别代码异味，建议提取方法、卫语句
```

---

## 涵盖的原则

| 原则 | 关键问题 |
|------|---------|
| **DRY** | 这个逻辑在其他地方重复了吗？ |
| **KISS** | 有更简单的方法吗？ |
| **YAGNI** | 我们现在需要这个还是"以防万一"？ |

---

## 主题

- 命名约定（变量、方法、类）
- 函数设计（大小、参数、抽象）
- 注释（何时好、何时坏）
- 代码异味检测
- 重构技术

---

## 相关技能

- `solid-principles` - 类设计原则
- `design-patterns` - 常见解决方案
- `java-code-review` - 完整审查清单

---

## 资源

- [整洁代码 - Robert C. Martin](https://www.oreilly.com/library/view/clean-code-a/9780136083238/)
- [重构 - Martin Fowler](https://refactoring.com/)
- [Refactoring Guru - 代码异味](https://refactoring.guru/refactoring/smells)
