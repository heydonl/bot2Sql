# SQL2Bot 完整系统快速启动指南

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    用户浏览器                            │
│              http://localhost:3000                       │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│              Vue 3 前端 (Port 3000)                      │
│  - 仪表盘、数据源管理、查询测试、大模型配置              │
└─────────────────────────────────────────────────────────┘
                            │ API Proxy
                            ▼
┌─────────────────────────────────────────────────────────┐
│         Spring Boot 后端 (Port 8082)                     │
│  - REST API、Text-to-SQL、RAG、会话管理                 │
└─────────────────────────────────────────────────────────┘
                            │
                ┌───────────┴───────────┐
                ▼                       ▼
        ┌──────────────┐        ┌──────────────┐
        │ MySQL 8.0    │        │ Claude API   │
        │ (sql2bot)    │        │ (localhost)  │
        └──────────────┘        └──────────────┘
```

## 一、环境准备

### 1. 必需软件
- ✅ Java 17
- ✅ Maven 3.6+
- ✅ MySQL 8.0
- ✅ Node.js 16+
- ✅ npm 8+

### 2. 可选软件
- Redis 7.0+（用于向量 RAG）
- Docker（用于快速部署 Redis）

## 二、后端启动

### 1. 数据库准备
```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE sql2bot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置文件
检查 `src/main/resources/application.properties`:
```properties
# 数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/sql2bot
spring.datasource.username=root
spring.datasource.password=123456

# Claude API 配置
claude.api.base-url=http://localhost:5580
claude.api.key=qwe23hy
```

### 3. 启动后端
```bash
cd /c/tecdo2/sql2bot
./mvnw spring-boot:run
```

看到以下输出表示启动成功：
```
Started Sql2botApplication in 3.185 seconds
Tomcat started on port 8082 (http)
```

### 4. 验证后端
```bash
curl http://localhost:8082/api/datasources
```

## 三、前端启动

### 1. 安装依赖
```bash
cd frontend
npm install
```

### 2. 启动开发服务器
```bash
npm run dev
```

看到以下输出表示启动成功：
```
VITE v5.0.0  ready in 1234 ms
➜  Local:   http://localhost:3000/
```

### 3. 访问应用
打开浏览器访问：http://localhost:3000

## 四、功能演示

### 1. 查看仪表盘
- 访问 http://localhost:3000/dashboard
- 查看系统统计信息
- 点击快速操作按钮

### 2. 添加数据源
1. 点击左侧菜单"数据源管理"
2. 点击"添加数据源"按钮
3. 填写表单：
   - 名称：Test Database
   - 类型：MySQL
   - 主机：localhost
   - 端口：3306
   - 数据库名：test
   - 用户名：root
   - 密码：123456
4. 点击"测试连接"验证
5. 点击"保存"

### 3. 发现表结构
1. 在数据源列表中找到刚添加的数据源
2. 点击"发现表"按钮
3. 系统自动扫描数据库中的表
4. 表模型自动创建

### 4. 执行查询
1. 点击左侧菜单"查询测试"
2. 输入问题：
   - 英文："Count datasources"
   - 英文："List all datasources"
3. 点击"执行查询"
4. 查看结果：
   - 生成的 SQL
   - 查询解释
   - 数据表格
   - 执行信息

### 5. 配置大模型
1. 点击左侧菜单"大模型配置"
2. 设置 API 信息：
   - API Base URL: http://localhost:5580
   - API Key: qwe23hy
3. 调整模型参数：
   - 模型：Claude 3.5 Sonnet
   - Max Tokens: 4096
   - Temperature: 0.0
4. 配置 RAG：
   - 启用向量 RAG：关闭（需要 Redis）
   - Top-K: 10
5. 点击"保存配置"

## 五、常见问题

### Q1: 后端启动失败 - 端口被占用
```bash
# 查找占用 8082 端口的进程
netstat -ano | grep :8082

# 终止进程（Windows）
taskkill /F /PID <PID>
```

### Q2: 前端启动失败 - 依赖安装失败
```bash
# 清除缓存
npm cache clean --force

# 使用国内镜像
npm config set registry https://registry.npmmirror.com

# 重新安装
npm install
```

### Q3: API 请求失败 - 跨域问题
确认 vite.config.js 中的代理配置：
```javascript
proxy: {
  '/api': {
    target: 'http://localhost:8082',
    changeOrigin: true
  }
}
```

### Q4: 查询返回空结果
原因：RAG 无法匹配中文表名

解决方案：
1. 使用英文查询
2. 为表添加中文显示名和描述
3. 启用向量 RAG（需要 Redis）

### Q5: SQL 验证失败 - 误判关键词
已修复：使用词边界匹配，不再误判 `updated_at` 为 UPDATE

## 六、高级功能

### 1. 启用向量 RAG

#### 安装 Redis
```bash
# 使用 Docker
docker run -d --name redis -p 6379:6379 redis:latest

# 或下载安装 Redis
```

#### 配置应用
修改 `application.properties`:
```properties
# Redis 配置
spring.data.redis.host=localhost
spring.data.redis.port=6379

# 启用向量 RAG
rag.use-vector=true
```

#### 建立向量索引
```bash
curl -X POST http://localhost:8082/api/vector-index/rebuild-all
```

### 2. 多轮对话
1. 在查询测试页面执行第一个查询
2. 记住返回的 conversationId
3. 在"会话"下拉框中选择该会话
4. 输入后续问题
5. 系统会基于上下文理解

### 3. 自动数据源选择
不指定数据源，系统会自动根据问题选择合适的数据源：
```json
{
  "userId": 1,
  "question": "Count datasources",
  "createNewConversation": true
}
```

## 七、开发建议

### 前端开发
1. 使用 Vue DevTools 调试
2. 查看浏览器控制台
3. 参考 Element Plus 文档
4. 热重载自动刷新

### 后端开发
1. 查看日志输出
2. 使用 Postman 测试 API
3. 参考 Spring Boot 文档
4. 使用 MyBatis 日志调试 SQL

### 数据库管理
1. 使用 MySQL Workbench
2. 定期备份数据
3. 优化查询性能
4. 监控连接池

## 八、生产部署

### 后端部署
```bash
# 打包
./mvnw clean package

# 运行
java -jar target/sql2bot-0.0.1-SNAPSHOT.jar
```

### 前端部署
```bash
# 构建
cd frontend
npm run build

# 部署到 Nginx
cp -r dist/* /var/www/html/
```

### Nginx 配置
```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /var/www/html;
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

## 九、性能优化

### 后端优化
1. 启用查询缓存
2. 优化数据库连接池
3. 使用 Redis 缓存
4. 异步处理长查询

### 前端优化
1. 路由懒加载
2. 组件按需加载
3. 图片压缩
4. CDN 加速

## 十、监控和维护

### 日志查看
```bash
# 后端日志
tail -f logs/spring.log

# 前端日志
# 浏览器开发者工具 -> Console
```

### 健康检查
```bash
# 后端健康检查
curl http://localhost:8082/actuator/health

# 前端健康检查
curl http://localhost:3000
```

### 数据备份
```bash
# 备份数据库
mysqldump -u root -p sql2bot > backup.sql

# 恢复数据库
mysql -u root -p sql2bot < backup.sql
```

## 十一、相关文档

- [后端 API 文档](API_DOCUMENTATION.md)
- [前端 README](frontend/README.md)
- [前端启动指南](START_FRONTEND.md)
- [前端项目总览](FRONTEND_OVERVIEW.md)
- [RAG 实现指南](RAG_IMPLEMENTATION.md)
- [向量 RAG 指南](VECTOR_RAG_GUIDE.md)

## 十二、获取帮助

### 常见问题
查看各个文档中的"常见问题"章节

### 技术支持
- GitHub Issues
- 项目文档
- 社区论坛

---

**祝使用愉快！**

**最后更新**: 2026-03-06
