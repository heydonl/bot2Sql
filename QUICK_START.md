# SQL2Bot 快速开始指南

## Phase 3 已完成 - Text-to-SQL 功能

### 🎉 已实现的功能

1. ✅ **数据源管理** - 连接和管理 MySQL 数据库
2. ✅ **表结构自动发现** - 自动读取数据库表和字段信息
3. ✅ **语义建模** - 定义表、字段、关系
4. ✅ **Text-to-SQL** - 自然语言转 SQL（使用 Spring AI Alibaba）
5. ✅ **SQL 执行** - 安全执行查询并返回结果

---

## 快速开始（3 步）

### 步骤 1: 配置 AI API Key

**推荐：使用通义千问（国内访问快，价格便宜）**

1. 访问 https://dashscope.console.aliyun.com/
2. 注册并创建 API Key
3. 配置环境变量：

```bash
export AI_API_KEY=your-dashscope-api-key
```

或者直接修改 `application.properties`:
```properties
spring.ai.alibaba.api-key=your-dashscope-api-key
```

### 步骤 2: 启动应用

```bash
cd /c/tecdo2/sql2bot
./mvnw spring-boot:run
```

应用将在 `http://localhost:8082` 启动

### 步骤 3: 测试 Text-to-SQL

#### 3.1 创建数据源
```bash
curl -X POST http://localhost:8082/api/datasources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "SQL2Bot元数据库",
    "type": "mysql",
    "host": "localhost",
    "port": 3306,
    "databaseName": "sql2bot",
    "username": "root",
    "password": "123456"
  }'
```

#### 3.2 导入表结构
```bash
curl -X POST http://localhost:8082/api/datasources/1/import \
  -H "Content-Type: application/json" \
  -d '["datasource", "model", "column_definition"]'
```

#### 3.3 自然语言查询
```bash
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "datasourceId": 1,
    "question": "查询所有数据源的数量"
  }'
```

**预期响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "sql": "SELECT COUNT(*) as count FROM `datasource`;",
    "explanation": "这个查询统计了数据源表中的总记录数",
    "data": [{"count": 1}],
    "rowCount": 1,
    "executionTime": 1234
  }
}
```

---

## 更多测试用例

### 简单查询
```bash
# 查询所有数据源
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "datasourceId": 1,
    "question": "列出所有数据源的名称和类型"
  }'
```

### 聚合查询
```bash
# 统计每个数据源有多少个模型
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "datasourceId": 1,
    "question": "每个数据源有多少个模型？"
  }'
```

### 多表 JOIN 查询
```bash
# 查询每个模型有多少个字段
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "datasourceId": 1,
    "question": "每个模型有多少个字段？按字段数量降序排列"
  }'
```

---

## 支持的 AI 模型

### 1. 通义千问（推荐）
- **优势**: 国内访问快、价格便宜、中文理解好
- **配置**:
```properties
spring.ai.alibaba.api-key=your-key
spring.ai.alibaba.chat.options.model=qwen-plus
```

### 2. Claude
- **优势**: SQL 生成准确率高
- **配置**:
```properties
spring.ai.alibaba.chat.client=anthropic
spring.ai.alibaba.anthropic.api-key=your-key
spring.ai.alibaba.anthropic.chat.options.model=claude-3-5-sonnet-20241022
```

### 3. 智谱 AI
- **优势**: 中文理解好、价格适中
- **配置**:
```properties
spring.ai.alibaba.chat.client=zhipu
spring.ai.alibaba.zhipu.api-key=your-key
spring.ai.alibaba.zhipu.chat.options.model=glm-4
```

---

## API 文档

完整的 API 文档请查看：`API_DOCUMENTATION.md`

主要接口：
- `POST /api/datasources` - 创建数据源
- `GET /api/datasources/{id}/discover` - 发现表结构
- `POST /api/datasources/{id}/import` - 导入表结构
- `POST /api/query` - 自然语言查询 ⭐

---

## 项目结构

```
sql2bot/
├── src/main/java/com/tecdo/mac/sql2bot/
│   ├── controller/          # REST API 控制器
│   │   ├── DataSourceController.java
│   │   ├── ModelController.java
│   │   ├── ColumnDefinitionController.java
│   │   ├── RelationshipController.java
│   │   └── QueryController.java ⭐
│   ├── service/             # 业务逻辑层
│   │   ├── DataSourceService.java
│   │   ├── ModelService.java
│   │   ├── SchemaDiscoveryService.java
│   │   ├── SemanticContextService.java ⭐
│   │   ├── AIService.java ⭐
│   │   ├── TextToSQLService.java ⭐
│   │   └── QueryExecutorService.java ⭐
│   ├── mapper/              # MyBatis 数据访问层
│   ├── domain/              # 实体类
│   └── dto/                 # 数据传输对象
├── src/main/resources/
│   ├── mapper/              # MyBatis XML 映射文件
│   ├── schema.sql           # 数据库 Schema
│   └── application.properties
└── docs/
    ├── API_DOCUMENTATION.md
    ├── SPRING_AI_ALIBABA_GUIDE.md
    └── PHASE3_IMPLEMENTATION.md
```

---

## 故障排查

### 问题 1: 编译失败
**解决**: 确保 Java 17 和 Maven 已安装

### 问题 2: 依赖下载慢
**解决**: 配置 Maven 国内镜像

### 问题 3: AI API 调用失败
**解决**:
- 检查 API Key 是否正确
- 检查网络连接
- 查看日志中的详细错误信息

### 问题 4: SQL 生成不准确
**解决**:
- 确保语义模型已正确导入
- 为表和字段添加详细的描述
- 尝试更换 AI 模型

---

## 下一步

1. **添加对话历史** - 支持多轮对话
2. **查询缓存** - 缓存相同问题的结果
3. **结果可视化** - 自动生成图表
4. **权限控制** - 基于角色的访问控制
5. **前端界面** - React 可视化建模画布

---

## 文档索引

- `API_DOCUMENTATION.md` - 完整的 API 文档
- `SPRING_AI_ALIBABA_GUIDE.md` - Spring AI Alibaba 使用指南
- `PHASE3_IMPLEMENTATION.md` - Phase 3 实现文档
- `PHASE2_TEST_REPORT.md` - Phase 2 测试报告

---

**版本**: v1.0
**完成日期**: 2026-03-05
**技术栈**: Spring Boot 4.0.3 + MyBatis + Spring AI Alibaba + MySQL
