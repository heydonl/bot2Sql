# 会话隔离功能测试文档

## 功能概述

实现了完整的会话管理和隔离功能，支持：
1. **用户会话隔离** - 不同用户的会话完全独立
2. **多轮对话** - 支持上下文理解
3. **对话历史** - 保存所有查询和结果
4. **会话管理** - 创建、查询、删除会话

## 测试场景

### 场景 1：用户 A 创建会话并查询

```bash
# 1. 用户 A (userId=1) 创建新会话并查询
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "question": "How many datasources are there?",
    "createNewConversation": true
  }'

# 响应包含 conversationId
{
  "code": 200,
  "data": {
    "conversationId": 1,
    "sql": "SELECT COUNT(*) FROM datasource;",
    "data": [{"count": 1}],
    "success": true
  }
}

# 2. 用户 A 在同一会话中继续提问（多轮对话）
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "conversationId": 1,
    "question": "Show me their names"
  }'

# AI 会理解"their"指的是上一个问题中的 datasources
```

### 场景 2：用户 B 创建独立会话

```bash
# 用户 B (userId=2) 创建自己的会话
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "question": "How many models are there?",
    "createNewConversation": true
  }'

# 用户 B 的会话与用户 A 完全隔离
```

### 场景 3：会话隔离验证

```bash
# 用户 B 尝试访问用户 A 的会话（应该失败）
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "conversationId": 1,
    "question": "继续查询"
  }'

# 响应：
{
  "code": 500,
  "message": "无权访问该会话"
}
```

## API 文档

### 1. 查询 API（支持会话）

**接口**: `POST /api/query`

**请求参数**:
```json
{
  "userId": 1,                      // 必填：用户ID
  "question": "查询问题",            // 必填：自然语言问题
  "conversationId": 1,              // 可选：会话ID（多轮对话）
  "datasourceId": 1,                // 可选：数据源ID（不指定则自动选择）
  "createNewConversation": true     // 可选：是否创建新会话
}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "conversationId": 1,
    "sql": "SELECT ...",
    "explanation": "...",
    "data": [...],
    "rowCount": 10,
    "executionTime": 1234,
    "success": true
  }
}
```

### 2. 创建会话

**接口**: `POST /api/conversations?userId=1&title=新对话`

**响应**:
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "userId": 1,
    "title": "新对话",
    "createdAt": "2026-03-05T19:40:00"
  }
}
```

### 3. 查询用户的所有会话

**接口**: `GET /api/conversations/user/{userId}`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "userId": 1,
      "title": "数据源查询",
      "createdAt": "2026-03-05T19:40:00"
    },
    {
      "id": 2,
      "userId": 1,
      "title": "模型统计",
      "createdAt": "2026-03-05T19:41:00"
    }
  ]
}
```

### 4. 查询会话的所有消息

**接口**: `GET /api/conversations/{id}/messages`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "conversationId": 1,
      "role": "user",
      "content": "How many datasources?",
      "createdAt": "2026-03-05T19:40:00"
    },
    {
      "id": 2,
      "conversationId": 1,
      "role": "assistant",
      "content": "查询结果...",
      "sqlQuery": "SELECT COUNT(*) FROM datasource;",
      "resultData": "[{\"count\":1}]",
      "createdAt": "2026-03-05T19:40:05"
    }
  ]
}
```

### 5. 删除会话

**接口**: `DELETE /api/conversations/{id}?userId=1`

**功能**: 删除会话及其所有消息（需要验证用户权限）

## 核心特性

### 1. 会话隔离

- ✅ 每个用户只能访问自己的会话
- ✅ 尝试访问其他用户的会话会被拒绝
- ✅ 查询时自动验证会话所有权

### 2. 多轮对话

- ✅ 支持上下文理解（"再看上个月的"、"它们的名称"）
- ✅ 自动加载最近5条对话历史
- ✅ AI 可以理解代词和省略的信息

### 3. 自动会话管理

- ✅ 首次查询自动创建会话
- ✅ 根据问题自动生成会话标题
- ✅ 自动保存所有查询和结果

### 4. 对话历史

- ✅ 保存用户问题
- ✅ 保存生成的 SQL
- ✅ 保存查询结果
- ✅ 保存错误信息

## 数据库表结构

### conversation 表
```sql
CREATE TABLE conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT COMMENT '用户ID',
    title VARCHAR(255) COMMENT '会话标题',
    datasource_id BIGINT COMMENT '关联数据源ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
);
```

### message 表
```sql
CREATE TABLE message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(20) NOT NULL COMMENT '角色: user, assistant',
    content TEXT COMMENT '消息内容',
    sql_query TEXT COMMENT '生成的SQL查询',
    result_data JSON COMMENT '查询结果数据',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    INDEX idx_conversation (conversation_id)
);
```

## 使用流程

### 完整的对话流程

```bash
# 1. 用户首次查询（自动创建会话）
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "userId": 1,
  "question": "How many datasources are there?",
  "createNewConversation": true
}
EOF

# 2. 继续在同一会话中提问
curl -X POST http://localhost:8082/api/query \
  -H "Content-Type: application/json" \
  -d @- <<EOF
{
  "userId": 1,
  "conversationId": 1,
  "question": "Show me their details"
}
EOF

# 3. 查看对话历史
curl http://localhost:8082/api/conversations/1/messages

# 4. 查看用户的所有会话
curl http://localhost:8082/api/conversations/user/1

# 5. 删除会话
curl -X DELETE "http://localhost:8082/api/conversations/1?userId=1"
```

## 最佳实践

### 1. 用户ID 管理

在实际应用中，userId 应该来自：
- JWT Token 中的用户信息
- Session 中的用户ID
- OAuth 认证后的用户ID

### 2. 会话标题

- 自动生成：取问题的前30个字符
- 用户可以手动修改标题
- 建议使用有意义的标题便于管理

### 3. 对话历史长度

- 默认加载最近5条消息
- 可以根据需要调整
- 避免加载过多历史导致 Prompt 过长

### 4. 会话清理

建议定期清理：
- 长时间未使用的会话
- 用户已删除的会话
- 错误过多的会话

## 安全考虑

### 1. 权限验证

- ✅ 每次查询都验证 userId
- ✅ 访问会话时验证所有权
- ✅ 删除会话时验证权限

### 2. 数据隔离

- ✅ 用户只能看到自己的会话
- ✅ 用户只能看到自己的消息
- ✅ 数据库级别的外键约束

### 3. 防止滥用

建议添加：
- 每个用户的会话数量限制
- 每个会话的消息数量限制
- 查询频率限制

## 未来优化

1. **会话分享** - 允许用户分享会话给其他人
2. **会话导出** - 导出对话历史为 PDF/Markdown
3. **会话搜索** - 在历史对话中搜索
4. **会话标签** - 为会话添加标签分类
5. **会话统计** - 统计用户的查询习惯

---

**版本**: v1.2
**更新日期**: 2026-03-05
**功能状态**: ✅ 已实现
