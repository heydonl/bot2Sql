# SQL2Bot 前端管理界面 - 项目总览

## 项目简介

SQL2Bot 前端管理界面是一个基于 Vue 3 的现代化 Web 应用，为 SQL2Bot 后端服务提供可视化的配置和管理功能。

## 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    浏览器 (Browser)                      │
│                  http://localhost:3000                   │
└─────────────────────────────────────────────────────────┘
                            │
                            │ HTTP/HTTPS
                            ▼
┌─────────────────────────────────────────────────────────┐
│              Vite Dev Server (开发环境)                  │
│                   Port: 3000                             │
│  ┌───────────────────────────────────────────────────┐  │
│  │  API Proxy: /api -> http://localhost:8082/api    │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                            │
                            │ Proxy
                            ▼
┌─────────────────────────────────────────────────────────┐
│           Spring Boot Backend (后端服务)                 │
│                   Port: 8082                             │
│  ┌───────────────────────────────────────────────────┐  │
│  │  REST API: /api/*                                 │  │
│  │  - /api/datasources                               │  │
│  │  - /api/models                                    │  │
│  │  - /api/query                                     │  │
│  │  - /api/relationships                             │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## 已实现功能

### 1. 仪表盘 (Dashboard)
**路由**: `/dashboard`
**文件**: `src/views/Dashboard.vue`

**功能**:
- 📊 显示系统统计卡片（数据源、表模型、表关系、查询次数）
- 🚀 快速操作按钮（添加数据源、创建表模型、测试查询）
- ℹ️ 系统信息展示

**特点**:
- 响应式卡片设计
- 悬停动画效果
- 实时数据加载

### 2. 数据源管理 (DataSources)
**路由**: `/datasources`
**文件**: `src/views/DataSources.vue`

**功能**:
- ➕ 添加新数据源（MySQL、PostgreSQL）
- ✏️ 编辑现有数据源
- 🗑️ 删除数据源
- 🔌 测试数据库连接
- 🔍 自动发现表结构

**表单字段**:
- 名称、类型、主机、端口
- 数据库名、用户名、密码

**特点**:
- 表单验证
- 连接测试功能
- 确认删除对话框

### 3. 大模型配置 (LLMConfig)
**路由**: `/llm-config`
**文件**: `src/views/LLMConfig.vue`

**功能**:
- 🤖 配置 Claude API（Base URL、API Key）
- ⚙️ 模型参数设置（模型选择、Max Tokens、Temperature）
- 🔍 RAG 配置（启用向量 RAG、Top-K）
- 📊 Embedding 配置（API URL、API Key、向量维度）

**配置项**:
- **API 配置**: Base URL, API Key
- **模型参数**: Model, Max Tokens, Temperature
- **RAG 配置**: Use Vector RAG, Top-K
- **Embedding**: URL, Key, Dimension

**特点**:
- 滑块控制 Temperature
- 开关控制向量 RAG
- 配置说明和提示

### 4. 查询测试 (Query)
**路由**: `/query`
**文件**: `src/views/Query.vue`

**功能**:
- 💬 交互式查询界面
- 🔄 多轮对话支持
- 📊 实时结果展示
- 📜 查询历史记录

**查询流程**:
1. 输入用户 ID
2. 选择数据源（可选，自动检测）
3. 选择会话（可选，新建会话）
4. 输入自然语言问题
5. 执行查询
6. 查看结果

**结果展示**:
- 生成的 SQL 语句
- 查询解释
- 数据表格
- 执行信息（会话 ID、执行时间、返回行数）

**特点**:
- 左右分栏布局
- SQL 复制功能
- 时间线展示历史
- 空状态提示

### 5. 其他页面（开发中）
- **表模型管理** (`/models`)
- **表关系配置** (`/relationships`)
- **提示词管理** (`/prompts`)
- **术语库** (`/glossary`)

## 核心组件

### App.vue
**主应用组件**

**结构**:
- 侧边栏导航（Logo + 菜单）
- 顶部面包屑导航
- 主内容区域（路由视图）

**样式**:
- 深色侧边栏（#001529）
- 白色主内容区
- 响应式布局

### API 服务

**文件**: `src/api/index.js`

**API 模块**:
- `datasourceAPI`: 数据源 CRUD
- `modelAPI`: 表模型 CRUD
- `columnAPI`: 字段定义 CRUD
- `relationshipAPI`: 表关系 CRUD
- `queryAPI`: 查询执行和会话管理
- `vectorIndexAPI`: 向量索引管理

**请求拦截器**:
- 自动处理响应错误
- 统一错误提示

## 目录结构

```
frontend/
├── public/                 # 静态资源
├── src/
│   ├── api/               # API 接口层
│   │   ├── index.js       # API 定义
│   │   └── request.js     # Axios 封装
│   ├── components/        # 公共组件（待扩展）
│   ├── router/            # 路由配置
│   │   └── index.js       # 路由定义
│   ├── stores/            # Pinia 状态管理（待扩展）
│   ├── views/             # 页面组件
│   │   ├── Dashboard.vue      # ✅ 仪表盘
│   │   ├── DataSources.vue    # ✅ 数据源管理
│   │   ├── Models.vue          # 🚧 表模型管理
│   │   ├── Relationships.vue   # 🚧 表关系配置
│   │   ├── LLMConfig.vue       # ✅ 大模型配置
│   │   ├── Prompts.vue         # 🚧 提示词管理
│   │   ├── Glossary.vue        # 🚧 术语库
│   │   └── Query.vue           # ✅ 查询测试
│   ├── App.vue            # 根组件
│   └── main.js            # 入口文件
├── index.html             # HTML 模板
├── vite.config.js         # Vite 配置
├── package.json           # 项目配置
├── README.md              # 项目文档
└── START_FRONTEND.md      # 快速启动指南
```

## 使用流程

### 1. 首次使用

```bash
# 1. 安装依赖
cd frontend
npm install

# 2. 启动后端（另一个终端）
cd ..
./mvnw spring-boot:run

# 3. 启动前端
npm run dev

# 4. 访问
# 打开浏览器访问 http://localhost:3000
```

### 2. 配置数据源

1. 访问"数据源管理"页面
2. 点击"添加数据源"
3. 填写数据库连接信息
4. 点击"测试连接"验证
5. 点击"保存"

### 3. 配置大模型

1. 访问"大模型配置"页面
2. 设置 Claude API 信息
3. 调整模型参数
4. 配置 RAG 设置
5. 点击"保存配置"

### 4. 测试查询

1. 访问"查询测试"页面
2. 输入自然语言问题
3. 点击"执行查询"
4. 查看生成的 SQL 和结果

## 开发计划

### Phase 1: 核心功能 ✅
- [x] 项目搭建
- [x] 路由配置
- [x] API 封装
- [x] 仪表盘
- [x] 数据源管理
- [x] 大模型配置
- [x] 查询测试

### Phase 2: 数据建模 🚧
- [ ] 表模型管理（CRUD）
- [ ] 字段定义管理
- [ ] 表关系可视化配置
- [ ] ER 图展示

### Phase 3: 高级功能 📋
- [ ] 提示词模板库
- [ ] 术语库和同义词管理
- [ ] 查询历史和统计
- [ ] 用户权限管理

### Phase 4: 优化增强 📋
- [ ] 主题切换（亮色/暗色）
- [ ] 国际化支持
- [ ] 数据可视化图表
- [ ] 性能监控

## 技术特点

### 1. 现代化技术栈
- Vue 3 Composition API
- Vite 快速构建
- Element Plus 企业级 UI

### 2. 良好的代码组织
- 模块化 API 设计
- 组件化开发
- 路由懒加载

### 3. 用户体验优化
- 响应式设计
- 加载状态提示
- 错误处理
- 表单验证

### 4. 开发体验
- 热模块替换（HMR）
- TypeScript 支持（可选）
- ESLint 代码规范（可选）

## 部署方案

### 开发环境
```bash
npm run dev
```

### 生产构建
```bash
npm run build
```

### Nginx 部署
```nginx
server {
    listen 80;
    root /path/to/dist;

    location / {
        try_files $uri /index.html;
    }

    location /api {
        proxy_pass http://localhost:8082;
    }
}
```

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 项目
2. 创建功能分支
3. 提交代码
4. 发起 Pull Request

## 许可证

MIT License

---

**项目状态**: 🚀 活跃开发中

**最后更新**: 2026-03-06
