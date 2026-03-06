# 前端快速启动指南

## 1. 安装 Node.js

确保已安装 Node.js 16 或更高版本：

```bash
node --version
npm --version
```

## 2. 安装依赖

```bash
cd frontend
npm install
```

这将安装所有必要的依赖包，可能需要几分钟时间。

## 3. 启动开发服务器

```bash
npm run dev
```

看到以下输出表示启动成功：

```
VITE v5.0.0  ready in 1234 ms

➜  Local:   http://localhost:3000/
➜  Network: use --host to expose
➜  press h + enter to show help
```

## 4. 访问管理后台

打开浏览器访问：http://localhost:3000

## 5. 确保后端运行

前端需要后端 API 支持，确保后端服务运行在 8082 端口：

```bash
# 在另一个终端窗口
cd /c/tecdo2/sql2bot
./mvnw spring-boot:run
```

## 功能演示

### 仪表盘
- 访问 http://localhost:3000/dashboard
- 查看系统统计信息

### 数据源管理
- 访问 http://localhost:3000/datasources
- 点击"添加数据源"按钮
- 填写数据库连接信息
- 点击"测试连接"验证
- 点击"保存"创建数据源

### 查询测试
- 访问 http://localhost:3000/query
- 输入问题，例如："Count datasources"
- 点击"执行查询"
- 查看生成的 SQL 和查询结果

### 大模型配置
- 访问 http://localhost:3000/llm-config
- 配置 Claude API 参数
- 调整模型参数和 RAG 设置

## 常见问题

### 问题 1: npm install 失败

**解决方案**:
```bash
# 清除缓存
npm cache clean --force

# 使用国内镜像
npm config set registry https://registry.npmmirror.com

# 重新安装
npm install
```

### 问题 2: 端口 3000 被占用

**解决方案**:
修改 `vite.config.js` 中的端口：
```javascript
server: {
  port: 3001  // 改为其他端口
}
```

### 问题 3: API 请求失败

**解决方案**:
1. 确认后端服务运行在 8082 端口
2. 检查浏览器控制台的错误信息
3. 确认 vite.config.js 中的代理配置正确

### 问题 4: 页面空白

**解决方案**:
1. 打开浏览器开发者工具（F12）
2. 查看 Console 标签的错误信息
3. 确认所有依赖已正确安装

## 开发技巧

### 热重载
修改代码后，页面会自动刷新，无需手动刷新浏览器。

### 调试
使用 Vue DevTools 浏览器扩展进行调试：
- Chrome: https://chrome.google.com/webstore/detail/vuejs-devtools/
- Firefox: https://addons.mozilla.org/firefox/addon/vue-js-devtools/

### 查看网络请求
打开浏览器开发者工具 -> Network 标签，查看所有 API 请求。

## 生产部署

### 构建
```bash
npm run build
```

构建产物在 `dist` 目录。

### 部署到 Nginx

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /path/to/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8082;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 下一步

- 探索各个功能模块
- 尝试添加数据源和执行查询
- 根据需求定制界面
- 参考 README.md 了解更多功能

祝使用愉快！
