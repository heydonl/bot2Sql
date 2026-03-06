# SQL2Bot - 智能 Text-to-SQL 平台

基于 Spring Boot 和 Vue 3 的 Generative BI 平台，通过语义建模和大语言模型实现准确的自然语言转 SQL 查询。

## 核心特性

### 1. 语义建模
- 📊 **可视化表模型管理**: 通过图形界面定义表的业务含义
- 🔗 **表关系配置**: 定义表之间的 JOIN 关系（一对一、一对多、多对多）
- 📝 **字段语义定义**: 为每个字段添加业务描述，区分维度和度量
- 🎯 **提高查询准确性**: 让 AI 理解业务数据结构，生成更精确的 SQL

### 2. Text-to-SQL
- 💬 **自然语言查询**: 用日常语言提问，自动生成 SQL
- 🤖 **多轮对话支持**: 支持上下文理解和追问
- 🔍 **向量 RAG 增强**: 基于语义相似度检索相关模型信息
- ✅ **SQL 验证**: 自动验证 SQL 语法和安全性

### 3. 数据源管理
- 🗄️ **MySQL 支持**: 第一期支持 MySQL 数据源
- 🔌 **连接测试**: 实时测试数据库连接
- 🔍 **表结构发现**: 自动发现数据库表结构
- 📦 **多数据源**: 支持管理多个数据源

### 4. 智能配置
- ⚙️ **大模型配置**: 支持 Claude、OpenAI 等主流 LLM
- 🎛️ **参数调优**: 可调整 Temperature、Max Tokens 等参数
- 🧠 **向量 RAG**: 可选的向量检索增强生成
- 📚 **嵌入模型**: 支持自定义 Embedding 模型

## 技术栈

### 后端
- **框架**: Spring Boot 4.0.3
- **语言**: Java 17
- **数据访问**: MyBatis
- **数据库**: MySQL 8.0+
- **缓存**: Redis (用于向量存储)
- **AI**: Claude API / OpenAI API

### 前端
- **框架**: Vue 3 (Composition API)
- **构建工具**: Vite 5.0
- **UI 组件**: Element Plus 2.5
- **路由**: Vue Router 4
- **状态管理**: Pinia 2
- **HTTP 客户端**: Axios

## 快速开始

> **注意**: 推荐将前端项目独立为 `sql2bot-frontend` 仓库，通过 git submodule 引入。详见 [前端迁移指南](./FRONTEND_MIGRATION_GUIDE.md)

### 前置要求
- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+ (可选，用于向量 RAG)
- Node.js 16+ (前端开发)

### 后端启动

1. 克隆项目
```bash
git clone <repository-url>
cd sql2bot
```

2. 配置数据库
```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE sql2bot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. 修改配置文件
```bash
# 编辑 src/main/resources/application.yml
# 配置数据库连接信息
```

4. 启动应用
```bash
./mvnw spring-boot:run
```

后端将在 `http://localhost:8082` 启动

### 前端启动

> **推荐**: 使用独立的 `sql2bot-frontend` 仓库。如果使用 submodule，确保已初始化：
> ```bash
> git submodule init
> git submodule update
> ```

1. 进入前端目录
```bash
cd frontend
```

2. 安装依赖
```bash
npm install
```

3. 启动开发服务器
```bash
npm run dev
```

前端将在 `http://localhost:3000` 启动

## 使用指南

### 1. 配置数据源

1. 访问 `http://localhost:3000`
2. 进入"数据源管理"页面
3. 点击"新建数据源"
4. 填写数据库连接信息
5. 测试连接并保存

### 2. 创建语义模型

1. 进入"表模型管理"页面
2. 选择数据源
3. 创建表模型，定义表的业务含义
4. 为每个表添加字段定义：
   - 设置字段的显示名称和描述
   - 标记字段类型（维度/度量）
5. 配置表之间的关系

详细指南请参考：[语义建模使用指南](./SEMANTIC_MODELING_GUIDE.md)

### 3. 配置大模型

1. 进入"大模型配置"页面
2. 配置 API 信息：
   - Base URL
   - API Key
   - 模型名称
3. 调整模型参数（Temperature、Max Tokens）
4. 可选：启用向量 RAG 功能

### 4. 开始查询

1. 进入"查询"页面
2. 在输入框中输入自然语言问题
3. 系统将自动：
   - 理解您的问题
   - 生成 SQL 查询
   - 执行查询并返回结果
   - 用自然语言解释结果
4. 支持多轮对话和追问

## 项目结构

> **推荐结构**: 前端作为独立仓库，通过 git submodule 引入

```
sql2bot/                          # 后端项目
├── src/main/java/com/tecdo/mac/sql2bot/
│   ├── controller/               # REST API 控制器
│   │   ├── DataSourceController.java
│   │   ├── ModelController.java
│   │   ├── ColumnDefinitionController.java
│   │   ├── RelationshipController.java
│   │   └── QueryController.java
│   ├── service/                  # 业务逻辑层
│   │   ├── DataSourceService.java
│   │   ├── ModelService.java
│   │   ├── TextToSQLService.java
│   │   ├── AIService.java
│   │   └── VectorRAGService.java
│   ├── mapper/                   # MyBatis 数据访问层
│   ├── domain/                   # 实体类
│   ├── dto/                      # 数据传输对象
│   └── config/                   # 配置类
├── .gitmodules                   # submodule 配置
├── frontend/                     # 前端项目 (git submodule)
│   ├── src/
│   │   ├── views/                # 页面组件
│   │   │   ├── Dashboard.vue
│   │   │   ├── DataSources.vue
│   │   │   ├── Models.vue
│   │   │   ├── Relationships.vue
│   │   │   ├── LLMConfig.vue
│   │   │   └── Query.vue
│   │   ├── api/                  # API 调用
│   │   ├── router/               # 路由配置
│   │   └── App.vue
│   └── package.json
├── README.md
└── FRONTEND_MIGRATION_GUIDE.md  # 前端迁移指南
```

**如何设置 Submodule**:
```bash
# 添加前端 submodule
git submodule add <sql2bot-frontend-repo-url> frontend

# 克隆项目时包含 submodule
git clone --recurse-submodules <sql2bot-repo-url>
```

详见：[前端迁移指南](./FRONTEND_MIGRATION_GUIDE.md)

## API 文档

### 数据源管理
- `GET /api/datasources` - 获取所有数据源
- `POST /api/datasources` - 创建数据源
- `PUT /api/datasources/{id}` - 更新数据源
- `DELETE /api/datasources/{id}` - 删除数据源
- `POST /api/datasources/test` - 测试连接
- `POST /api/datasources/{id}/discover` - 发现表结构

### 表模型管理
- `GET /api/models` - 获取所有模型
- `GET /api/models/datasource/{datasourceId}` - 按数据源查询
- `POST /api/models` - 创建模型
- `PUT /api/models/{id}` - 更新模型
- `DELETE /api/models/{id}` - 删除模型

### 字段定义管理
- `GET /api/columns/model/{modelId}` - 获取模型的所有字段
- `POST /api/columns` - 创建字段
- `PUT /api/columns/{id}` - 更新字段
- `DELETE /api/columns/{id}` - 删除字段

### 关系管理
- `GET /api/relationships` - 获取所有关系
- `POST /api/relationships` - 创建关系
- `PUT /api/relationships/{id}` - 更新关系
- `DELETE /api/relationships/{id}` - 删除关系

### 查询
- `POST /api/query` - 执行自然语言查询

## 开发计划

- [x] Phase 1: 基础架构
  - [x] Spring Boot 项目初始化
  - [x] 数据库设计和创建
  - [x] 数据源连接管理
  - [x] 基础 CRUD API

- [x] Phase 2: 语义建模核心
  - [x] Model/Column/Relationship 完整 API
  - [x] 前端建模界面
  - [x] 语义模型管理

- [x] Phase 3: Text-to-SQL 引擎
  - [x] LLM Service 实现
  - [x] Prompt 工程
  - [x] SQL 生成和验证
  - [x] 向量 RAG 支持

- [x] Phase 4: 对话式界面
  - [x] 对话管理 API
  - [x] 前端聊天界面
  - [x] 多轮对话支持
  - [x] 结果展示

- [ ] Phase 5: 高级功能
  - [ ] 计算字段定义
  - [ ] 指标定义和查询
  - [ ] 智能图表选择
  - [ ] 查询建议和自动补全
  - [ ] 权限控制
  - [ ] 性能优化

## 配置说明

### application.yml 配置项

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/sql2bot
    username: root
    password: your_password

  redis:
    host: localhost
    port: 6379

ai:
  api:
    base-url: https://api.anthropic.com
    api-key: your_api_key
    model: claude-3-5-sonnet-20241022
    max-tokens: 4096
    temperature: 0.7

  rag:
    enabled: true
    top-k: 5
```

## 常见问题

### Q: 如何更换 LLM 提供商？
A: 在"大模型配置"页面修改 Base URL 和 API Key，支持任何兼容 OpenAI API 格式的服务。

### Q: 向量 RAG 是必需的吗？
A: 不是必需的。向量 RAG 可以提高复杂查询的准确性，但对于简单场景可以禁用。

### Q: 支持哪些数据库？
A: 第一期支持 MySQL，后续将支持 PostgreSQL、Oracle 等。

### Q: 如何提高查询准确性？
A:
1. 完善语义模型定义
2. 为字段添加详细描述
3. 正确配置表关系
4. 使用自定义提示词
5. 添加业务术语库

## 贡献指南

欢迎贡献代码、报告问题或提出建议！

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

[MIT License](LICENSE)

## 联系方式

如有问题或建议，请提交 Issue 或联系项目维护者。

## 致谢

本项目受 [Wren AI](https://github.com/Canner/WrenAI) 启发，感谢 Wren AI 团队在语义层和 Text-to-SQL 领域的探索。
