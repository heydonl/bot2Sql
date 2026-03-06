# SQL2Bot 管理后台

基于 Vue 3 + Vite + Element Plus 的可视化管理界面。

## 功能特性

### ✅ 已实现
- 🎨 **仪表盘** - 系统概览和快速操作
- 💾 **数据源管理** - 添加、编辑、删除数据源，测试连接，发现表结构
- 🤖 **大模型配置** - 配置 Claude API、模型参数、RAG 设置
- 🔍 **查询测试** - 交互式查询界面，实时查看结果

### 🚧 开发中
- 📊 **表模型管理** - 管理表的语义模型
- 🔗 **表关系配置** - 可视化配置表之间的关系
- 📝 **提示词管理** - 自定义系统提示词
- 📚 **术语库** - 管理业务术语和同义词

## 技术栈

- **框架**: Vue 3 (Composition API)
- **构建工具**: Vite 5
- **UI 组件**: Element Plus 2.5
- **路由**: Vue Router 4
- **状态管理**: Pinia 2
- **HTTP 客户端**: Axios 1.6

## 快速开始

### 1. 安装依赖

```bash
cd frontend
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:3000

### 3. 构建生产版本

```bash
npm run build
```

## 项目结构

```
frontend/
├── public/              # 静态资源
├── src/
│   ├── api/            # API 接口
│   │   ├── index.js    # API 定义
│   │   └── request.js  # Axios 封装
│   ├── components/     # 公共组件
│   ├── router/         # 路由配置
│   ├── stores/         # Pinia 状态管理
│   ├── views/          # 页面组件
│   │   ├── Dashboard.vue      # 仪表盘
│   │   ├── DataSources.vue    # 数据源管理
│   │   ├── Models.vue          # 表模型管理
│   │   ├── Relationships.vue   # 表关系配置
│   │   ├── LLMConfig.vue       # 大模型配置
│   │   ├── Prompts.vue         # 提示词管理
│   │   ├── Glossary.vue        # 术语库
│   │   └── Query.vue           # 查询测试
│   ├── App.vue         # 根组件
│   └── main.js         # 入口文件
├── index.html          # HTML 模板
├── vite.config.js      # Vite 配置
└── package.json        # 项目配置
```

## 功能说明

### 仪表盘
- 显示系统统计信息（数据源、表模型、表关系、查询次数）
- 提供快速操作入口
- 显示系统信息

### 数据源管理
- **添加数据源**: 支持 MySQL、PostgreSQL
- **测试连接**: 验证数据库连接是否正常
- **发现表结构**: 自动扫描数据库中的表
- **编辑/删除**: 管理现有数据源

### 大模型配置
- **API 配置**: 设置 Claude API 的 Base URL 和 API Key
- **模型参数**: 选择模型、设置 Max Tokens 和 Temperature
- **RAG 配置**: 启用向量 RAG、设置 Top-K
- **Embedding 配置**: 配置 Embedding API 和向量维度

### 查询测试
- **交互式查询**: 输入自然语言问题，实时获取结果
- **会话管理**: 支持多轮对话
- **结果展示**: 显示生成的 SQL、查询解释和数据表格
- **查询历史**: 记录最近的查询历史

## API 代理配置

开发环境下，前端通过 Vite 代理访问后端 API：

```javascript
// vite.config.js
export default defineConfig({
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true
      }
    }
  }
})
```

## 环境要求

- Node.js >= 16
- npm >= 8

## 开发建议

### 添加新页面

1. 在 `src/views/` 创建页面组件
2. 在 `src/router/index.js` 添加路由
3. 在 `src/App.vue` 的菜单中添加入口

### 添加新 API

1. 在 `src/api/index.js` 添加 API 定义
2. 在页面组件中导入并使用

### 样式规范

- 使用 Element Plus 的主题色
- 保持统一的间距和布局
- 响应式设计

## 常见问题

### Q: 启动后无法访问后端 API？
A: 确保后端服务运行在 8082 端口，检查 vite.config.js 中的代理配置。

### Q: 如何修改端口？
A: 修改 vite.config.js 中的 `server.port` 配置。

### Q: 如何部署到生产环境？
A: 运行 `npm run build`，将 `dist` 目录部署到 Web 服务器。

## 后续开发计划

- [ ] 完善表模型管理功能
- [ ] 实现可视化表关系配置（类似 ER 图）
- [ ] 添加提示词模板库
- [ ] 实现术语库的同义词管理
- [ ] 添加用户权限管理
- [ ] 支持主题切换
- [ ] 添加数据可视化图表

## 贡献指南

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT
