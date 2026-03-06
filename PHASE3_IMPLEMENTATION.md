# Phase 3 - Text-to-SQL 引擎实现文档

## 功能概述

实现了基于 Claude API 的 Text-to-SQL 引擎，可以将自然语言问题转换为 SQL 查询并执行。

## 核心组件

### 1. ClaudeService
**功能**: 封装 Claude API 调用
- 使用 OkHttp 客户端调用 Anthropic API
- 支持自定义模型、温度、最大 token 数
- 自动提取 SQL 代码块

**配置**:
```properties
claude.api.key=${CLAUDE_API_KEY:your-api-key-here}
claude.api.model=claude-3-5-sonnet-20241022
claude.api.max-tokens=4096
claude.api.temperature=0.0
```

### 2. SemanticContextService
**功能**: 生成语义上下文（Prompt 工程）
- 自动读取数据源的所有表和字段信息
- 生成结构化的数据库 Schema 描述
- 包含表关系信息
- 添加 SQL 生成规则和示例

**生成的 Prompt 包含**:
- 数据库结构（表名、字段、类型、描述）
- 表关系（JOIN 条件）
- SQL 生成规则
- 示例查询

### 3. QueryExecutorService
**功能**: 安全地执行 SQL 查询
- 只允许 SELECT 查询
- 禁止 INSERT、UPDATE、DELETE 等修改操作
- 防止 SQL 注入
- 返回结构化的查询结果

**安全检查**:
- 验证 SQL 以 SELECT 开头
- 检查禁止的关键字
- 防止多条语句执行

### 4. TextToSQLService
**功能**: Text-to-SQL 核心流程编排
1. 生成语义上下文
2. 调用 Claude API 生成 SQL
3. 提取 SQL 语句和解释
4. 执行 SQL 查询
5. 返回结果

## API 接口

### 自然语言查询

**接口**: `POST /api/query`

**请求体**:
```json
{
  "datasourceId": 1,
  "question": "查询所有数据源的数量"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "sql": "SELECT COUNT(*) as datasource_count FROM `datasource`;",
    "explanation": "这个查询统计了 datasource 表中的总记录数。",
    "data": [
      {
        "datasource_count": 1
      }
    ],
    "rowCount": 1,
    "executionTime": 1523
  }
}
```

## 使用流程

### 1. 配置 Claude API Key

**方式一：环境变量**
```bash
export CLAUDE_API_KEY=your-api-key-here
```

**方式二：修改 application.properties**
```properties
claude.api.key=your-api-key-here
```

### 2. 确保已导入语义模型

使用 Phase 2 的功能导入表结构：
```bash
# 发现表结构
GET /api/datasources/1/discover

# 导入表结构
POST /api/datasources/1/import
```

### 3. 发起自然语言查询

```bash
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "datasourceId": 1,
    "question": "有多少个数据源？"
  }'
```

## 测试用例

### 测试用例 1: 简单计数查询
**问题**: "查询所有数据源的数量"
**预期 SQL**: `SELECT COUNT(*) FROM datasource;`

### 测试用例 2: 带条件查询
**问题**: "查询状态为 active 的数据源"
**预期 SQL**: `SELECT * FROM datasource WHERE status = 'active';`

### 测试用例 3: 多表 JOIN 查询
**问题**: "查询每个数据源有多少个模型"
**预期 SQL**:
```sql
SELECT d.name, COUNT(m.id) as model_count
FROM datasource d
LEFT JOIN model m ON d.id = m.datasource_id
GROUP BY d.id, d.name;
```

### 测试用例 4: 聚合查询
**问题**: "每个模型有多少个字段"
**预期 SQL**:
```sql
SELECT m.table_name, COUNT(c.id) as column_count
FROM model m
LEFT JOIN column_definition c ON m.id = c.model_id
GROUP BY m.id, m.table_name;
```

## 技术特点

### 1. Prompt 工程
- 动态生成包含完整数据库结构的 Prompt
- 包含业务描述和字段含义
- 提供 SQL 生成规则和示例
- 使用 Claude 3.5 Sonnet 模型（高准确率）

### 2. 安全性
- 只允许 SELECT 查询
- 禁止修改数据的操作
- SQL 注入防护
- 多语句执行防护

### 3. 性能
- 记录查询执行时间
- 支持连接池（HikariCP）
- 超时控制（60秒）

### 4. 可扩展性
- 支持多数据源
- 可添加查询缓存
- 可集成对话历史
- 可添加查询优化建议

## 依赖项

```xml
<!-- OkHttp for HTTP Client -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>

<!-- Gson for JSON Processing -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
</dependency>
```

## 注意事项

1. **API Key 安全**: 不要将 API Key 提交到代码仓库
2. **成本控制**: Claude API 按 token 计费，注意控制调用频率
3. **超时设置**: 复杂查询可能需要更长时间
4. **结果限制**: 建议在 SQL 中添加 LIMIT 限制返回行数
5. **错误处理**: 需要处理 API 调用失败、SQL 执行失败等情况

## 下一步优化

1. **查询缓存**: 缓存相同问题的 SQL 和结果
2. **多轮对话**: 支持上下文理解和追问
3. **SQL 优化**: 自动添加索引建议
4. **结果可视化**: 自动选择合适的图表类型
5. **查询历史**: 保存查询记录供学习和审计
6. **权限控制**: 基于用户角色限制可查询的表和字段

## 故障排查

### 问题 1: Claude API 调用失败
- 检查 API Key 是否正确
- 检查网络连接
- 查看 API 配额是否用完

### 问题 2: 生成的 SQL 不正确
- 检查语义模型是否完整
- 优化 Prompt 描述
- 调整温度参数（降低随机性）

### 问题 3: SQL 执行失败
- 检查数据库连接
- 验证 SQL 语法
- 检查表和字段是否存在

---

**版本**: v1.0
**完成日期**: 2026-03-05
