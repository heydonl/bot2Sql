# 设计模式

**加载**：`view .claude/skills/design-patterns/SKILL.md`

---

## 描述

常见设计模式及实用 Java 示例。涵盖创建型、行为型和结构型模式，使用现代 Java 语法和 Spring 集成。

---

## 使用场景

- "为通知实现工厂模式"
- "为这个复杂对象使用建造者"
- "如何在不修改类的情况下添加功能？"（装饰器）
- "多种支付方式，运行时切换"（策略）
- "订单下单时通知多个服务"（观察者）

---

## 示例

```
> view .claude/skills/design-patterns/SKILL.md
> "我需要创建不同的报告类型（PDF、Excel、CSV）"
→ 建议工厂模式及实现示例
```

---

## 涵盖的模式

| 类别 | 模式 |
|----------|-------------|
| **创建型** | 建造者、工厂方法、单例 |
| **行为型** | 策略、观察者、模板方法 |
| **结构型** | 装饰器、适配器 |

---

## 快速选择指南

| 问题 | 模式 |
|---------|----------|
| 多个构造函数参数 | 建造者 |
| 创建时不指定类 | 工厂 |
| 运行时切换算法 | 策略 |
| 动态添加行为 | 装饰器 |
| 通知多个对象 | 观察者 |
| 集成遗留代码 | 适配器 |

---

## 相关技能

- `solid-principles` - 模式实现的原则
- `clean-code` - 代码级实践
- `spring-boot-patterns` - Spring 实现

---

## 资源

- [Refactoring Guru - 设计模式](https://refactoring.guru/design-patterns)
- [设计模式（四人帮）](https://www.oreilly.com/library/view/design-patterns-elements/0201633612/)
- [Java 设计模式](https://java-design-patterns.com/)
