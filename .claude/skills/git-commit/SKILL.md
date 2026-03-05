---
name: git-commit
description: 为 Java 项目生成约定式提交消息。当用户说"提交"、"创建提交"、"提交更改"，或在完成需要提交的代码更改后使用。
---

# Git 提交消息技能

为 Java 项目生成约定式、信息丰富的提交消息。

## 何时使用
- 进行代码更改后
- 用户说"提交这个" / "提交更改" / "创建提交"
- 创建 PR 之前

## 格式标准

使用约定式提交格式：
```
<type>(<scope>): <subject>

<body>

<footer>
```

### 类型（Java 上下文）
- **feat**: 新功能（新 API、新功能）
- **fix**: Bug 修复
- **refactor**: 代码重构（无功能变更）
- **test**: 添加/更新测试
- **docs**: 仅文档
- **perf**: 性能改进
- **build**: Maven/Gradle 更改
- **chore**: 维护（依赖更新等）

### 范围示例（Java 特定）
- 模块名称：`core`、`api`、`plugin-loader`
- 组件：`PluginManager`、`ExtensionFactory`
- 区域：`lifecycle`、`dependencies`、`security`

### 主题规则
- 祈使语气："添加支持"而不是"已添加支持"
- 末尾无句号
- 最多 50 个字符
- 类型后小写

### 正文（可选但推荐）
- 解释"是什么"和"为什么"，而不是"怎么做"
- 72 字符换行
- 引用问题："Fixes #123" / "Relates to #456"

## 示例

### 简单修复
```
fix(plugin-loader): 防止插件目录缺失时的 NPE

在访问插件目录前检查 null，避免初始化期间的
NullPointerException。

Fixes #234
```

### 带破坏性变更的功能
```
feat(api): 添加插件依赖版本控制支持

BREAKING CHANGE: PluginDescriptor 现在要求语义化版本
格式（x.y.z）而不是自由格式的版本字符串。

Closes #567
```

### 重构
```
refactor(core): 提取插件验证逻辑

将验证逻辑从 PluginManager 移至独立的
PluginValidator 类，以提高可测试性和关注点分离。
```

### 测试添加
```
test(plugin-loader): 添加插件加载的集成测试

添加全面的集成测试，涵盖：
- 从目录加载
- 从 JAR 加载
- 无效插件的错误处理
```

### 构建/依赖更新
```
build(deps): 升级 Spring Boot 至 3.2.1

将 Spring Boot 从 3.1.0 更新至 3.2.1，获取安全补丁
和性能改进。
```

## 工作流程

1. **分析更改** 使用 `git diff --staged`
2. **识别范围** 从修改的文件中
3. **确定类型** 基于更改性质
4. **生成消息** 遵循格式
5. **执行提交**：`git commit -m "message"`

## Token 优化

- 读取暂存更改一次：`git diff --staged --stat` + 针对性的文件差异
- 除非必要，不要读取整个文件
- 使用简洁的正文 - 目标 2-3 行最多
- 将多个小更改批量处理为逻辑提交

## 反模式

❌ 避免：
- "修复东西" / "更新代码" / "更改"
- "WIP" 提交（除非明确要求）
- 混合不相关的更改（使用单独的提交）
- 消息中过于详细的技术实现

✅ 好的提交：
- 单一逻辑更改
- 清晰、可搜索的主题
- 适用时引用问题
- 解释业务价值

## 与 GitHub 集成

提交后，建议下一步：
- "推送更改？"
- "为问题 #X 创建 PR？"
- "继续下一个任务？"

## Java 项目的常见模式

### 添加新功能
```
feat(extension): 添加优先级扩展支持

允许扩展指定执行的优先级顺序。
优先级更高的扩展先运行。

Closes #123
```

### 修复 Bug
```
fix(classloader): 解决嵌套 JAR 中的资源查找

ClassLoader.getResource() 对于从插件 JAR 加载的
JAR 中的资源（嵌套 JAR）失败。通过实现适当的
资源解析链修复。

Fixes #456
```

### 依赖更新
```
build(deps): 将 slf4j 从 1.7.30 升级至 2.0.9

将 SLF4J 更新至最新稳定版本。无需 API 更改，
因为我们仅使用稳定的 API。
```

### 文档改进
```
docs(readme): 添加插件开发快速入门指南

添加创建第一个插件的分步指南：
- 项目设置
- 实现 Plugin 接口
- 构建和测试
```

### 性能优化
```
perf(plugin-loader): 缓存插件描述符

缓存已解析的插件描述符以避免重复的 I/O
和解析。将插件加载时间减少约 40%。

Related to #789
```

## 多文件更改

当更改跨越多个组件时：

```
refactor(core): 重组插件生命周期管理

- 将生命周期状态机提取到单独的类
- 将验证逻辑移至 validators 包
- 更新测试以反映新结构

此重构在不更改外部 API 的情况下提高了
可测试性和关注点分离。

Related to #111, #222
```

## 破坏性变更

始终使用 BREAKING CHANGE 页脚：

```
feat(api)!: 用 Plugin.initialize() 替换 Plugin.start()

BREAKING CHANGE: Plugin.start() 方法已重命名为
Plugin.initialize() 以获得更好的语义清晰度。所有
插件实现必须更新其代码。

迁移指南：在所有 Plugin 实现中将 @Override start()
替换为 @Override initialize()。

Closes #999
```

## 快速参考卡

| 更改类型 | 类型 | 示例范围 |
|---------|------|---------|
| 新功能 | feat | api, core, loader |
| Bug 修复 | fix | plugin-loader, lifecycle |
| 重构 | refactor | core, utils |
| 测试 | test | integration, unit |
| 文档 | docs | readme, javadoc |
| 构建 | build | maven, deps |
| 性能 | perf | classloader, cache |
| 维护 | chore | ci, tooling |

## 参考资料

- [约定式提交规范](https://www.conventionalcommits.org/)
