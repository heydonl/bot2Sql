# CLAUDE.md
本文件为 Claude Code (claude.ai/code) 在此代码仓库中工作时提供指导。

每次输出都称呼我为L，请用中文和我交流

## 编码规范

**严格要求：所有涉及中文的地方必须使用 UTF-8 编码，绝对不允许出现乱码**

### 中文编码规则（必须遵守）

1. **数据库相关：**
   - 所有建表语句必须指定 `DEFAULT CHARSET=utf8mb4`
   - 所有中文注释必须使用 `COMMENT '中文注释'` 格式
   - 数据库连接 URL 必须包含 `?characterEncoding=utf8&useUnicode=true`
   - 示例：
     ```sql
     CREATE TABLE example (
       id BIGINT PRIMARY KEY COMMENT '主键ID',
       name VARCHAR(100) COMMENT '名称'
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='示例表';
     ```

2. **Java 代码：**
   - 所有 Java 文件必须使用 UTF-8 编码保存
   - 日志输出中文时使用 `log.info("中文日志: {}", value)` 格式
   - 注释中的中文必须正确显示，不允许出现乱码
   - 字符串常量中的中文必须使用 UTF-8 编码
   - 示例：
     ```java
     /**
      * 用户服务
      * 负责用户相关的业务逻辑处理
      */
     @Service
     public class UserService {
         log.info("创建用户成功: {}", userName);
     }
     ```

3. **前端代码：**
   - 所有 Vue/JS 文件必须使用 UTF-8 编码
   - HTML meta 标签必须包含 `<meta charset="UTF-8">`
   - 界面显示的中文文本必须正确渲染
   - API 响应中的中文数据必须正确解码

4. **配置文件：**
   - application.properties 必须使用 UTF-8 编码
   - 包含中文的配置项必须正确显示
   - 示例：
     ```properties
     spring.datasource.url=jdbc:mysql://localhost:3306/sql2bot?characterEncoding=utf8&useUnicode=true
     spring.messages.encoding=UTF-8
     server.servlet.encoding.charset=UTF-8
     server.servlet.encoding.force=true
     ```

5. **日志文件：**
   - logback.xml 或 log4j2.xml 必须配置 UTF-8 编码
   - 日志输出必须能正确显示中文
   - 示例：
     ```xml
     <encoder>
       <pattern>%d{yyyy-MM-dd HHmm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
       <charset>UTF-8</charset>
     </encoder>
     ```

6. **Git 提交：**
   - Git 提交信息中的中文必须正确显示
   - 确保 Git 配置：`git config --global core.quotepath false`
   - 确保 Git 配置：`git config --global i18n.commitencoding utf-8`

7. **IDE 设置：**
   - 所有文件的默认编码必须设置为 UTF-8
   - 项目编码必须设置为 UTF-8
   - 控制台输出编码必须设置为 UTF-8

### 验证规则

在创建或修改任何包含中文的内容时，必须：
- ✅ 检查文件编码是否为 UTF-8
- ✅ 检查数据库表是否指定了 utf8mb4 字符集
- ✅ 检查中文是否正确显示，没有出现乱码或问号
- ✅ 检查日志输出中的中文是否正确
- ❌ 绝对不允许出现类似 `\u4e2d\u6587`、`???`、`ä¸­æ–‡` 等乱码

### 常见乱码问题排查

如果出现中文乱码，按以下顺序检查：
1. 文件编码是否为 UTF-8
2. 数据库连接是否包含 characterEncoding=utf8
3. 数据库表是否使用 utf8mb4 字符集
4. Spring Boot 配置是否设置了 UTF-8 编码
5. 前端是否正确设置了 charset=UTF-8

## 文档规范

- 所有 Markdown 文档（`.md`）统一存放在 `docs/` 目录下
- 非文档性质文件（如配置、源码说明中的内联注释）不受此规则影响
- 新增或迁移文档时，优先按主题在 `docs/` 下分目录组织

## 部署规范
每次启动的时候都要关闭上一次启动过的端口，不然一直占用资源

## Superpowers 技能使用规则

**【强制规则】在执行任何任务之前，必须先检查是否有适用的 Superpowers 技能**

### 核心原则

1. **优先级最高**：收到用户任务后，第一步是检查是否有适用的 Superpowers 技能
2. **即使 1% 可能性也要调用**：如果认为某个技能有哪怕 1% 的可能性适用，就必须调用它
3. **不要跳过**：绝对不允许因为"任务简单"、"我已经知道怎么做"等理由跳过技能调用
4. **不要合理化**：不要用"我先探索一下"、"让我先看看代码"等理由推迟技能调用

### 必须使用的场景

**创建新功能或修改现有功能时：**
- ✅ 必须先使用 `superpowers:brainstorming` 探索需求
- ✅ 然后使用 `superpowers:writing-plans` 编写实施计划
- ✅ 最后使用 `superpowers:executing-plans` 或 `superpowers:subagent-driven-development` 执行

**实现任何功能或修复 bug 时：**
- ✅ 使用 `superpowers:test-driven-development` 先写测试

**遇到 bug、测试失败或意外行为时：**
- ✅ 使用 `superpowers:systematic-debugging` 系统化调试

**完成任务、实现主要功能或准备合并时：**
- ✅ 使用 `superpowers:requesting-code-review` 请求代码审查

**声称工作完成、修复或通过测试之前：**
- ✅ 使用 `superpowers:verification-before-completion` 验证

**需要隔离工作环境时：**
- ✅ 使用 `superpowers:using-git-worktrees` 创建 worktree

**面对 2 个以上独立任务时：**
- ✅ 使用 `superpowers:dispatching-parallel-agents` 并行处理

### 错误的思维模式（必须避免）

❌ "这个任务很简单，不需要技能"
❌ "我先看看代码再决定"
❌ "让我先探索一下项目"
❌ "我已经知道怎么做了"
❌ "这只是一个小改动"
❌ "我记得这个技能的内容"
❌ "技能太重了，不适合这个任务"
❌ "我先做一件事，然后再用技能"

### 正确的工作流程

```
用户请求 → 检查 Superpowers 技能 → 调用适用技能 → 按技能指导执行
```

**不是：**
```
用户请求 → 直接开始实现 ❌
用户请求 → 先探索代码 → 然后实现 ❌
用户请求 → 问几个问题 → 然后实现 ❌
```

### 可用的 Superpowers 技能

- `superpowers:brainstorming` - 创意工作前的需求探索（必须用于任何新功能）
- `superpowers:writing-plans` - 编写实施计划
- `superpowers:executing-plans` - 执行实施计划
- `superpowers:subagent-driven-development` - 当前会话中执行独立任务
- `superpowers:test-driven-development` - TDD 开发
- `superpowers:systematic-debugging` - 系统化调试
- `superpowers:verification-before-completion` - 完成前验证
- `superpowers:requesting-code-review` - 请求代码审查
- `superpowers:receiving-code-review` - 接收代码审查反馈
- `superpowers:finishing-a-development-branch` - 完成开发分支
- `superpowers:using-git-worktrees` - 使用 Git worktrees
- `superpowers:dispatching-parallel-agents` - 派发并行代理
- `superpowers:writing-skills` - 创建和编辑技能

## 可用技能

任何操作前都先看skill的规范

技能从 `.claude/skills/` 目录加载（链接自 claude-code-java）。

### 自动技能选择

Claude 会根据任务上下文自动选择并使用合适的技能。你不需要明确指定使用哪个技能。

### 可用技能及其使用场景

**代码质量与审查：**
- `java-code-review` - 自动用于代码审查、发现潜在问题和改进建议
- `clean-code` - 自动用于代码重构、提升代码可读性和可维护性
- `solid-principles` - 自动用于检查SOLID原则遵循情况
- `design-patterns` - 自动用于识别和应用设计模式

**Spring Boot 与框架：**
- `spring-boot-patterns` - 自动用于Spring Boot最佳实践检查和实现
- `mybatis-patterns` - 自动用于MyBatis使用规范和优化建议
- `logging-patterns` - 自动用于日志记录规范和改进

**性能与并发：**
- `performance-smell-detection` - 自动用于性能问题检测和优化建议
- `concurrency-review` - 自动用于并发代码审查和线程安全检查

**安全与依赖：**
- `security-audit` - 自动用于安全漏洞扫描和修复建议
- `maven-dependency-audit` - 自动用于依赖版本检查和漏洞扫描

**架构与API：**
- `architecture-review` - 自动用于架构设计审查和改进建议
- `api-contract-review` - 自动用于API设计审查和RESTful规范检查

**测试：**
- `test-quality` - 自动用于测试代码质量检查和覆盖率分析

**版本控制与文档：**
- `git-commit` - 自动用于生成规范的Git提交信息
- `changelog-generator` - 自动用于生成变更日志
- `issue-triage` - 自动用于问题分类和优先级评估

**迁移：**
- `java-migration` - 自动用于Java版本升级和迁移指导

### 技能使用指南

1. **代码审查场景**: 当提交代码、创建PR或请求代码审查时，自动使用 `java-code-review`、`clean-code`、`security-audit`
2. **性能优化场景**: 当讨论性能问题时，自动使用 `performance-smell-detection`、`concurrency-review`
3. **架构设计场景**: 当设计新功能或重构时，自动使用 `architecture-review`、`design-patterns`、`solid-principles`
4. **API开发场景**: 当创建或修改API时，自动使用 `api-contract-review`、`spring-boot-patterns`
5. **提交代码场景**: 当需要提交代码时，自动使用 `git-commit` 生成规范的提交信息
6. **依赖管理场景**: 当更新依赖或检查安全性时，自动使用 `maven-dependency-audit`

## 项目概述

SQL2Bot 是一个基于 Spring Boot 4.0.3 和 Java 17 构建的综合性生成式 BI 平台。它提供语义建模能力和 Text-to-SQL 功能，允许用户通过直观的 Web 界面使用自然语言查询数据库。

**核心功能：**
- 带可视化界面的语义数据建模
- 使用 LLM 集成的 Text-to-SQL 转换
- 用于优化 AI 响应的提示词模板管理
- 用于领域特定理解的业务术语表
- 带聊天历史的对话式查询界面
- 基于向量的 RAG（检索增强生成）用于上下文增强

## 架构

**后端技术栈：**
- Spring Boot 4.0.3 with Java 17
- MyBatis 用于数据库操作
- H2 内存数据库（开发环境）/ MySQL（生产环境）
- Redis 用于向量存储和缓存
- Claude API 集成用于 LLM 能力

**前端技术栈：**
- Vue 3 with Composition API
- Element Plus UI 组件
- Vite 开发服务器
- Git submodule 架构

**包结构**: `com.tecdo.mac.sql2bot`

### 核心模块

**领域实体：**
- `DataSource` - 数据库连接配置
- `Database` - 数据库记录（关联到数据源）
- `Model` - 表语义模型
- `ColumnDefinition` - 带业务上下文的字段定义
- `Relationship` - 表间关系
- `PromptTemplate` - LLM 提示词管理
- `Glossary` - 业务术语定义
- `Conversation` & `Message` - 聊天界面数据
- `Workspace` - 工作区管理

**服务层：**
- `TextToSQLService` - 核心 Text-to-SQL 转换
- `SemanticContextService` - 语义模型上下文生成
- `VectorRAGService` - 基于向量的检索增强生成
- `EmbeddingService` - 文本嵌入生成
- `AIService` - LLM API 集成
- `QueryExecutorService` - SQL 执行和结果处理
- `SchemaDiscoveryService` - 数据库表结构发现
- `DatabaseService` - 数据库记录管理
- `WorkspaceService` - 工作区管理

**API 控制器：**
- `/api/datasources` - 数据源管理
- `/api/databases` - 数据库管理
- `/api/models` - 语义模型 CRUD
- `/api/relationships` - 表关系配置
- `/api/prompts` - 提示词模板管理
- `/api/glossary` - 术语管理
- `/api/query` - 自然语言查询处理
- `/api/conversations` - 聊天历史管理
- `/api/workspaces` - 工作区管理

## 构建与开发命令

```bash
# 构建项目
./mvnw clean install

# 运行测试
./mvnw test

# 运行单个测试类
./mvnw test -Dtest=Sql2botApplicationTests

# 运行后端应用
./mvnw spring-boot:run

# 打包为 JAR
./mvnw package

# 前端开发（在 frontend/ 目录中）
cd frontend
npm install
npm run dev
```

## 开发环境设置

**后端：** 运行在 8082 端口
**前端：** 运行在 3001 端口（或下一个可用端口）

**数据库配置：**
- 开发环境：H2 内存数据库（自动设置）
- 生产环境：MySQL 带自动模式初始化
- Schema 文件：`schema.sql`（MySQL）、`schema-h2.sql`（H2）

**必需服务：**
- Redis（用于向量存储）- localhost:6379
- Claude API 端点（在 application.properties 中配置）

## MCP Toolbox 集成

项目集成了 MCP toolbox 函数用于广告和媒体账户管理：
- 广告账户管理（配额、消费、返点）
- 媒体账户操作（账户信息、BC 授权、刷新任务）
- 订单处理（充值、清零、开户）
- 用户查询（UA 用户、BOP 用户、报表访问）
- 财务操作（退款、付款、特批）

在实现功能时，考虑使用这些 MCP 工具进行数据检索，而不是直接数据库查询。

## 代码规范

- 遵循 Spring Boot 最佳实践的 controller/service/repository 分层
- 使用现有包结构：`com.tecdo.mac.sql2bot`
- 利用 Spring Boot 4.0.3 特性（最新稳定版本）
- MyBatis XML 映射器位于 `src/main/resources/mapper/`
- 领域实体使用 Lombok 注解减少样板代码
- RESTful API 设计使用一致的 `Result<T>` 响应包装器
- 前端使用 Vue 3 Composition API 和 `<script setup>` 语法

## 关键功能实现

**语义建模：**
- 表关系的可视化拖放界面
- 业务友好的字段描述和别名
- 支持计算字段和业务指标

**Text-to-SQL 引擎：**
- 使用语义模型的上下文感知提示词生成
- 与 Claude API 集成进行 SQL 生成
- 带错误处理的查询验证和执行

**提示词管理：**
- 分类模板（系统、用户、示例）
- 基于优先级的提示词组合
- 用于动态上下文注入的模板变量

**术语表系统：**
- 带同义词的业务术语定义
- 跨术语和定义的搜索功能
- 基于类别的领域知识组织

**工作区系统：**
- 多工作区支持，隔离不同的表关系配置
- 工作区布局数据持久化（表位置、字段连接）
- 工作区切换时自动加载对应的表和关系

**数据库发现：**
- 支持从数据源列出所有数据库
- 多选数据库批量发现表结构
- 自动创建 database 记录并关联到 model

## 前端结构

**主要视图：**
- `Dashboard.vue` - 概览和指标
- `DataSources.vue` - 数据库连接管理
- `Models.vue` - 语义模型配置
- `Relationships.vue` - 表关系可视化（已弃用）
- `RelationshipsVisual.vue` - 可视化表关系配置（新版）
- `Query.vue` - 自然语言查询界面
- `Prompts.vue` - 提示词模板管理
- `Glossary.vue` - 术语管理

**API 集成：**
- 集中式 API 客户端在 `src/api/index.js`
- 一致的错误处理和响应处理
- 对话界面的实时更新

**可视化表关系配置功能：**
- 拖拽表到工作区画布
- 通过拖拽字段创建表间关系
- 支持多种关系类型（1:1、1:N、N:1、N:N）
- 工作区管理（创建、切换、保存、清空）
- 表和关系的搜索过滤
- 布局数据自动保存到数据库