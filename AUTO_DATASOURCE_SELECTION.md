# 自动数据源选择功能文档

## 功能概述

用户在进行自然语言查询时，**不需要手动指定数据源ID**，AI 会根据问题内容自动判断应该使用哪个数据源。

## 使用方式

### 方式 1：自动选择数据源（推荐）⭐

**请求**:
```json
{
  "question": "How many models are there in total?"
}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "success": true,
    "sql": "SELECT COUNT(*) as model_count FROM `sql2bot`.`model`;",
    "data": [{"model_count": 2}],
    "explanation": "This query counts the total number of records in the model table...\n\n**Data source selected**: SQL2Bot Metadata Database (ID: 1, mysql) - This is the appropriate data source because the question asks about models, and the `model` table is part of the SQL2Bot metadata database structure.",
    "executionTime": 6657,
    "rowCount": 1
  }
}
```

**特点**:
- ✅ 不需要指定 `datasourceId`
- ✅ AI 自动选择正确的数据源
- ✅ 在解释中说明选择了哪个数据源及原因
- ✅ 提高用户体验，减少认知负担

### 方式 2：手动指定数据源

**请求**:
```json
{
  "datasourceId": 1,
  "question": "How many models are there?"
}
```

**特点**:
- 强制在指定的数据源上查询
- 适用于需要精确控制的场景
- 不会加载其他数据源的信息，Prompt 更简洁

## 工作原理

### 1. Prompt 生成阶段

当 `datasourceId` 为 null 时：

```
## 可用的数据源和数据库结构

系统中有多个数据源，请根据用户问题自动选择合适的数据源。

### 数据源: SQL2Bot Metadata Database
- **ID**: 1
- **类型**: mysql
- **数据库**: sql2bot

**包含的表**:
#### 表: datasource
**描述**: 数据源配置表
**主键**: id
**字段列表**:
- `id` (BIGINT): 主键ID [dimension]
- `name` (VARCHAR): 数据源名称 [dimension]
...

#### 表: model
**描述**: 表模型定义
**主键**: id
**字段列表**:
- `id` (BIGINT): 主键ID [dimension]
- `table_name` (VARCHAR): 物理表名 [dimension]
...

---

### 数据源: E-commerce Database
- **ID**: 2
- **类型**: mysql
- **数据库**: ecommerce

**包含的表**:
#### 表: users
...
#### 表: orders
...
```

### 2. AI 决策阶段

AI 会：
1. 分析用户问题涉及的业务领域
2. 查看每个数据源包含的表
3. 选择最匹配的数据源
4. 生成 SQL 查询
5. 在解释中说明选择的数据源和原因

### 3. 数据源推断阶段

系统会：
1. 从生成的 SQL 中提取表名
2. 在所有模型中查找这些表
3. 统计每个数据源匹配的表数量
4. 选择匹配度最高的数据源
5. 在该数据源上执行 SQL

## 适用场景

### 场景 1：多业务系统

**数据源配置**:
- 订单系统数据库（包含 orders, order_items 表）
- 用户系统数据库（包含 users, profiles 表）
- 商品系统数据库（包含 products, categories 表）

**查询示例**:
```json
// 自动选择订单系统数据库
{"question": "今天有多少订单？"}

// 自动选择用户系统数据库
{"question": "有多少活跃用户？"}

// 自动选择商品系统数据库
{"question": "哪个分类的商品最多？"}
```

### 场景 2：多环境管理

**数据源配置**:
- 生产环境数据库
- 测试环境数据库
- 开发环境数据库

**查询示例**:
```json
// 自动选择生产环境
{"question": "生产环境的订单数量"}

// 自动选择测试环境
{"question": "测试环境的用户数据"}
```

### 场景 3：历史数据分析

**数据源配置**:
- 当前数据库（最近3个月）
- 历史数据库（3个月前）

**查询示例**:
```json
// 自动选择当前数据库
{"question": "本月的销售额"}

// 自动选择历史数据库
{"question": "去年同期的销售额"}
```

## 技术实现

### 核心代码

**SemanticContextService.java**:
```java
public String generateSystemPrompt(Long datasourceId) {
    if (datasourceId != null) {
        // 只加载指定数据源的模型
        appendDataSourceModels(prompt, datasourceId);
    } else {
        // 加载所有数据源的模型
        List<DataSource> dataSources = dataSourceService.listAll();
        for (DataSource ds : dataSources) {
            prompt.append("### 数据源: ").append(ds.getName());
            // ... 添加数据源信息和表结构
        }
    }
}

public Long inferDataSourceFromSQL(String sql) {
    // 从 SQL 中提取表名
    // 匹配表名到数据源
    // 返回匹配度最高的数据源
}
```

**TextToSQLService.java**:
```java
public QueryResponse processQuery(QueryRequest request) {
    Long datasourceId = request.getDatasourceId();

    // 生成 Prompt（支持 null datasourceId）
    String systemPrompt = semanticContextService.generateSystemPrompt(datasourceId);

    // 调用 AI 生成 SQL
    String sql = aiService.generateSQL(systemPrompt, userPrompt);

    // 如果未指定数据源，自动推断
    if (datasourceId == null) {
        datasourceId = semanticContextService.inferDataSourceFromSQL(sql);
    }

    // 在推断出的数据源上执行查询
    List<Map<String, Object>> data = queryExecutorService.executeQuery(datasourceId, sql);
}
```

## 优势

### 1. 用户体验提升
- **简化操作**：用户不需要知道数据在哪个数据源
- **自然交互**：像和人对话一样提问
- **减少错误**：避免选错数据源

### 2. 智能化
- **上下文理解**：AI 理解问题涉及的业务领域
- **自动匹配**：根据表名和描述自动选择数据源
- **透明解释**：说明选择的原因

### 3. 灵活性
- **支持两种模式**：自动选择 + 手动指定
- **向后兼容**：原有的指定数据源方式仍然可用
- **可扩展**：支持任意数量的数据源

## 注意事项

### 1. 表名冲突

如果多个数据源有同名的表，AI 会根据：
- 表的描述
- 字段的业务含义
- 用户问题的上下文

来判断应该使用哪个数据源。

**建议**：为表添加清晰的业务描述，帮助 AI 做出正确判断。

### 2. 性能考虑

- 不指定数据源时，Prompt 会包含所有数据源的信息
- 如果数据源很多（>10个），Prompt 可能会很长
- 建议：对于已知数据源的场景，仍然指定 datasourceId

### 3. Token 消耗

- 加载所有数据源会增加 Prompt 长度
- 导致 AI API 调用的 token 消耗增加
- 建议：合理控制数据源数量和表数量

## 最佳实践

### 1. 为数据源添加清晰的名称
```json
{
  "name": "生产环境-订单系统",  // ✅ 清晰
  "name": "Database 1"          // ❌ 不清晰
}
```

### 2. 为表添加业务描述
```sql
CREATE TABLE orders (
  ...
) COMMENT='订单表，存储所有订单信息';  -- ✅ 有描述
```

### 3. 为字段添加注释
```sql
CREATE TABLE orders (
  id BIGINT COMMENT '订单ID',           -- ✅ 有注释
  user_id BIGINT COMMENT '用户ID',
  total_amount DECIMAL COMMENT '订单总金额'
);
```

### 4. 合理组织数据源

**推荐**：按业务领域划分
- 订单系统数据库
- 用户系统数据库
- 商品系统数据库

**不推荐**：按技术划分
- MySQL 数据库 1
- MySQL 数据库 2

## 测试用例

### 测试 1：单数据源场景
```bash
# 只有一个数据源时，自动选择该数据源
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "How many records?"}'
```

### 测试 2：多数据源场景
```bash
# 有多个数据源时，AI 根据问题选择
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{"question": "How many orders in the order system?"}'
```

### 测试 3：手动指定数据源
```bash
# 强制使用指定的数据源
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{"datasourceId": 1, "question": "How many records?"}'
```

## 故障排查

### 问题 1：AI 选择了错误的数据源

**原因**：表名或描述不够清晰

**解决**：
1. 为表添加详细的业务描述
2. 为字段添加注释
3. 在问题中明确提到数据源名称

### 问题 2：无法推断数据源

**错误**：`无法从 SQL 中推断出数据源`

**原因**：生成的 SQL 中的表名在系统中不存在

**解决**：
1. 检查表是否已导入到语义模型
2. 手动指定 datasourceId
3. 检查 AI 生成的 SQL 是否正确

### 问题 3：Prompt 太长

**现象**：AI API 调用超时或失败

**原因**：数据源太多，Prompt 超过 token 限制

**解决**：
1. 减少数据源数量
2. 只导入必要的表
3. 手动指定 datasourceId

## 未来优化

1. **智能缓存**：缓存数据源选择结果
2. **学习用户偏好**：记住用户常用的数据源
3. **多数据源联合查询**：支持跨数据源的 JOIN
4. **数据源推荐**：主动推荐可能相关的数据源

---

**版本**: v1.1
**更新日期**: 2026-03-05
**功能状态**: ✅ 已实现并测试通过
