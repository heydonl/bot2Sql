# 技能（Skills）

技能是可重用的提示词，用于教导 Claude 特定的 Java 开发模式。

## 结构约定

每个技能文件夹包含：

| 文件 | 用途 | 受众 |
|------|------|------|
| `SKILL.md` | Claude 的指令 | AI（通过 `view` 加载） |
| `README.md` | 文档、示例、提示 | 人类（入门指南） |

## 可用技能

### 工作流
| 技能 | 描述 |
|------|------|
| [git-commit](git-commit/) | Java 项目的约定式提交消息 |
| [changelog-generator](changelog-generator/) | 从 git 提交生成变更日志 |
| [issue-triage](issue-triage/) | GitHub 问题分类和归类 |

### 代码质量
| 技能 | 描述 |
|------|------|
| [java-code-review](java-code-review/) | 系统化的 Java 代码审查清单 |
| [api-contract-review](api-contract-review/) | REST API 审计：HTTP 语义、版本控制、兼容性 |
| [concurrency-review](concurrency-review/) | 线程安全、竞态条件、@Async、虚拟线程 |
| [performance-smell-detection](performance-smell-detection/) | 代码级性能问题检测（流、装箱、正则表达式） |
| [test-quality](test-quality/) | JUnit 5 + AssertJ 测试模式 |
| [maven-dependency-audit](maven-dependency-audit/) | 审计依赖的更新和漏洞 |
| [security-audit](security-audit/) | OWASP Top 10、输入验证、注入防护 |

### 架构与设计
| 技能 | 描述 |
|------|------|
| [architecture-review](architecture-review/) | 宏观层面审查：包、模块、层次、边界 |
| [solid-principles](solid-principles/) | S.O.L.I.D. 原则及 Java 示例 |
| [design-patterns](design-patterns/) | 工厂、建造者、策略、观察者、装饰器等模式 |
| [clean-code](clean-code/) | DRY、KISS、YAGNI、命名、重构 |

### 框架与数据
| 技能 | 描述 |
|------|------|
| [spring-boot-patterns](spring-boot-patterns/) | Spring Boot 最佳实践 |
| [java-migration](java-migration/) | Java 版本升级指南（8→11→17→21） |
| [mybatis-patterns](mybatis-patterns/) | MyBatis 模式（SQL 映射、动态 SQL、N+1、性能优化） |
| [logging-patterns](logging-patterns/) | 结构化日志（JSON）、SLF4J、MDC、AI 友好格式 |

## 添加新技能

### 开始之前

根据现有技能验证你的技能想法：

- [ ] **无重大重叠** - 检查上表中是否有类似技能
- [ ] **明确层级** - 微观（函数）/ 中观（类）/ 宏观（包）/ 框架 / 横切关注点
- [ ] **明确类型** - 审计（审查现有代码）或模板（展示如何编写）
- [ ] **独特价值** - 它提供了哪些不存在的内容？
- [ ] **聚焦范围** - 可在一次会话中应用（<15 个检查项）

> 📖 **完整指南：** [docs/SKILL_GUIDELINES.md](../../docs/SKILL_GUIDELINES.md)

### 实施步骤

1. 创建文件夹：`.claude/skills/<skill-name>/`
2. 创建 `SKILL.md`，包含 Claude 的指令
3. 创建 `README.md`，包含人类文档（使用现有 README 作为模板）
4. 更新此表格
5. 更新主 README.md

## 使用方法

技能会根据上下文由 Claude Code 自动加载。你也可以直接调用它们：

```bash
# 自动 - Claude 检测何时使用技能
> "提交这些更改"              # 加载 git-commit
> "审查此代码的 SOLID 原则"   # 加载 solid-principles

# 手动 - 使用斜杠命令调用
> /git-commit
> /solid-principles
```

## 了解更多

- [Claude Code 技能文档](https://code.claude.com/docs/en/skills) - 创建和使用技能的官方指南
