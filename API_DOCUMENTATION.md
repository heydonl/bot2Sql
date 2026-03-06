# SQL2Bot API 文档

## 概述

SQL2Bot 是一个基于语义层的 Generative BI 平台，提供数据源管理、语义建模、表结构自动发现等功能。本文档描述了所有可用的 REST API 接口。

**Base URL**: `http://localhost:8082`

**响应格式**: 所有接口返回统一的 JSON 格式
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

---

## 1. 数据源管理 API

### 1.1 创建数据源

**接口**: `POST /api/datasources`

**功能**: 创建一个新的数据源连接配置

**使用场景**:
- 首次接入新的 MySQL 数据库
- 配置多个数据源用于不同的业务系统
- 为开发、测试、生产环境配置不同的数据源

**请求体**:
```json
{
  "name": "生产环境MySQL",
  "type": "mysql",
  "host": "localhost",
  "port": 3306,
  "databaseName": "ecommerce_db",
  "username": "root",
  "password": "password123"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "生产环境MySQL",
    "type": "mysql",
    "host": "localhost",
    "port": 3306,
    "databaseName": "ecommerce_db",
    "username": "root",
    "status": "active",
    "createdAt": "2026-03-05T10:00:00",
    "updatedAt": "2026-03-05T10:00:00"
  }
}
```

---

### 1.2 测试数据源连接

**接口**: `POST /api/datasources/test`

**功能**: 测试数据源连接是否可用

**使用场景**:
- 创建数据源前验证连接参数是否正确
- 排查数据源连接问题
- 定期检查数据源健康状态

**请求体**:
```json
{
  "type": "mysql",
  "host": "localhost",
  "port": 3306,
  "databaseName": "test_db",
  "username": "root",
  "password": "password123"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": true
}
```

---

### 1.3 查询所有数据源

**接口**: `GET /api/datasources`

**功能**: 获取所有已配置的数据源列表

**使用场景**:
- 在前端展示数据源选择列表
- 管理和查看所有数据源配置
- 数据源切换和选择

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "生产环境MySQL",
      "type": "mysql",
      "host": "localhost",
      "port": 3306,
      "databaseName": "ecommerce_db",
      "status": "active",
      "createdAt": "2026-03-05T10:00:00"
    }
  ]
}
```

---

### 1.4 根据ID查询数据源

**接口**: `GET /api/datasources/{id}`

**功能**: 获取指定数据源的详细信息

**使用场景**:
- 查看数据源详细配置
- 编辑数据源前获取当前配置
- 数据源详情页展示

**路径参数**:
- `id`: 数据源ID

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "生产环境MySQL",
    "type": "mysql",
    "host": "localhost",
    "port": 3306,
    "databaseName": "ecommerce_db",
    "username": "root",
    "status": "active",
    "createdAt": "2026-03-05T10:00:00",
    "updatedAt": "2026-03-05T10:00:00"
  }
}
```

---

### 1.5 更新数据源

**接口**: `PUT /api/datasources/{id}`

**功能**: 更新数据源配置信息

**使用场景**:
- 修改数据源连接参数（如密码变更）
- 更新数据源名称或描述
- 切换数据库名称

**路径参数**:
- `id`: 数据源ID

**请求体**:
```json
{
  "name": "生产环境MySQL-更新",
  "type": "mysql",
  "host": "localhost",
  "port": 3306,
  "databaseName": "ecommerce_db_v2",
  "username": "root",
  "password": "new_password"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success"
}
```

---

### 1.6 删除数据源

**接口**: `DELETE /api/datasources/{id}`

**功能**: 删除指定的数据源

**使用场景**:
- 移除不再使用的数据源
- 清理测试数据源
- 数据源迁移后删除旧配置

**路径参数**:
- `id`: 数据源ID

**响应示例**:
```json
{
  "code": 200,
  "message": "success"
}
```

**注意**: 删除数据源会级联删除关联的所有模型和字段定义

---

### 1.7 发现表结构 ⭐ (Phase 2)

**接口**: `GET /api/datasources/{id}/discover`

**功能**: 自动发现数据源中的所有表和字段结构

**使用场景**:
- 首次接入数据源后，快速了解数据库结构
- 预览数据库中有哪些表可以导入
- 查看表的字段信息、类型、注释等元数据
- 在导入前确认表结构是否符合预期

**路径参数**:
- `id`: 数据源ID

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "tableName": "users",
      "tableComment": "用户表",
      "tableType": "TABLE",
      "columns": [
        {
          "columnName": "id",
          "dataType": "BIGINT",
          "columnComment": "用户ID",
          "isNullable": false,
          "isPrimaryKey": true,
          "columnSize": 19
        },
        {
          "columnName": "username",
          "dataType": "VARCHAR",
          "columnComment": "用户名",
          "isNullable": false,
          "isPrimaryKey": false,
          "columnSize": 50
        },
        {
          "columnName": "created_at",
          "dataType": "TIMESTAMP",
          "columnComment": "创建时间",
          "isNullable": true,
          "isPrimaryKey": false,
          "defaultValue": "CURRENT_TIMESTAMP"
        }
      ]
    },
    {
      "tableName": "orders",
      "tableComment": "订单表",
      "tableType": "TABLE",
      "columns": [...]
    }
  ]
}
```

**技术实现**:
- 使用 JDBC DatabaseMetaData 读取表和字段元数据
- 自动识别主键字段
- 读取数据库注释信息
- 获取字段的完整属性（类型、长度、默认值等）

---

### 1.8 导入表结构 ⭐ (Phase 2)

**接口**: `POST /api/datasources/{id}/import`

**功能**: 将数据源中的表结构导入到语义模型中

**使用场景**:
- 快速批量导入多个表到语义模型
- 自动创建表的 Model 和字段的 ColumnDefinition
- 避免手动逐个创建模型和字段的繁琐操作
- 新项目初始化时快速搭建语义层

**路径参数**:
- `id`: 数据源ID

**请求体** (可选):
```json
["users", "orders", "products"]
```

**说明**:
- 如果不传请求体或传空数组，则导入所有表
- 如果传入表名数组，则只导入指定的表

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "tableCount": 3,
    "columnCount": 25
  }
}
```

**自动处理**:
- 自动创建 Model 实体（表模型）
- 批量创建 ColumnDefinition（字段定义）
- 自动识别主键并设置到 Model 中
- 智能推断字段类型：
  - 数值类型 → measure（度量）
  - ID 字段 → dimension（维度）
  - 其他类型 → dimension（维度）

---

## 2. 模型管理 API

### 2.1 创建模型

**接口**: `POST /api/models`

**功能**: 手动创建一个表模型

**使用场景**:
- 为特定表创建语义模型
- 定义表的业务含义和显示名称
- 设置表的可见性（是否对查询开放）

**请求体**:
```json
{
  "datasourceId": 1,
  "tableName": "users",
  "displayName": "用户表",
  "description": "存储系统用户信息",
  "isVisible": true,
  "primaryKey": "id"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "datasourceId": 1,
    "tableName": "users",
    "displayName": "用户表",
    "description": "存储系统用户信息",
    "isVisible": true,
    "primaryKey": "id",
    "createdAt": "2026-03-05T10:00:00"
  }
}
```

---

### 2.2 查询所有模型

**接口**: `GET /api/models`

**功能**: 获取所有表模型列表

**使用场景**:
- 在建模画布中展示所有表节点
- 查看已建模的表列表
- 模型管理页面展示

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "datasourceId": 1,
      "tableName": "users",
      "displayName": "用户表",
      "isVisible": true,
      "primaryKey": "id"
    }
  ]
}
```

---

### 2.3 根据数据源ID查询模型

**接口**: `GET /api/models/datasource/{datasourceId}`

**功能**: 获取指定数据源下的所有模型

**使用场景**:
- 按数据源过滤模型列表
- 数据源切换时加载对应的模型
- 多数据源场景下的模型隔离

**路径参数**:
- `datasourceId`: 数据源ID

---

### 2.4 查询可见的模型

**接口**: `GET /api/models/visible`

**功能**: 获取所有对查询开放的模型

**使用场景**:
- Text-to-SQL 时只使用可见的表
- 向用户展示可查询的表列表
- 权限控制：隐藏敏感表

---

### 2.5 根据ID查询模型

**接口**: `GET /api/models/{id}`

**功能**: 获取指定模型的详细信息

**路径参数**:
- `id`: 模型ID

---

### 2.6 更新模型

**接口**: `PUT /api/models/{id}`

**功能**: 更新模型信息

**使用场景**:
- 修改表的显示名称和描述
- 更新业务含义
- 调整表的可见性

**路径参数**:
- `id`: 模型ID

---

### 2.7 删除模型

**接口**: `DELETE /api/models/{id}`

**功能**: 删除指定的模型

**路径参数**:
- `id`: 模型ID

**注意**: 删除模型会级联删除关联的所有字段定义

---

## 3. 字段定义管理 API

### 3.1 创建字段定义

**接口**: `POST /api/columns`

**功能**: 为模型添加字段定义

**使用场景**:
- 手动添加计算字段
- 为导入的字段补充业务描述
- 定义字段的维度/度量类型

**请求体**:
```json
{
  "modelId": 1,
  "columnName": "username",
  "displayName": "用户名",
  "description": "用户的登录名称",
  "dataType": "VARCHAR",
  "columnType": "dimension",
  "isNullable": false
}
```

---

### 3.2 批量创建字段定义

**接口**: `POST /api/columns/batch`

**功能**: 批量创建多个字段定义

**使用场景**:
- 导入表结构后批量创建字段
- 快速初始化模型的所有字段
- 减少 API 调用次数

**请求体**:
```json
[
  {
    "modelId": 1,
    "columnName": "id",
    "displayName": "用户ID",
    "dataType": "BIGINT",
    "columnType": "dimension"
  },
  {
    "modelId": 1,
    "columnName": "username",
    "displayName": "用户名",
    "dataType": "VARCHAR",
    "columnType": "dimension"
  }
]
```

---

### 3.3 根据模型ID查询字段

**接口**: `GET /api/columns/model/{modelId}`

**功能**: 获取指定模型的所有字段定义

**使用场景**:
- 在建模画布中展示表的字段列表
- 查看表的完整结构
- Text-to-SQL 时获取可用字段

**路径参数**:
- `modelId`: 模型ID

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "modelId": 1,
      "columnName": "id",
      "displayName": "用户ID",
      "description": "主键ID",
      "dataType": "BIGINT",
      "columnType": "dimension",
      "isNullable": false
    }
  ]
}
```

---

### 3.4 根据字段类型查询

**接口**: `GET /api/columns/model/{modelId}/type/{columnType}`

**功能**: 获取指定模型的特定类型字段

**使用场景**:
- 只获取维度字段用于分组
- 只获取度量字段用于聚合计算
- 构建查询时按类型筛选字段

**路径参数**:
- `modelId`: 模型ID
- `columnType`: 字段类型（dimension 或 measure）

---

### 3.5 根据ID查询字段

**接口**: `GET /api/columns/{id}`

**功能**: 获取指定字段的详细信息

**路径参数**:
- `id`: 字段ID

---

### 3.6 更新字段定义

**接口**: `PUT /api/columns/{id}`

**功能**: 更新字段定义信息

**使用场景**:
- 修改字段的显示名称和描述
- 调整字段类型（维度/度量）
- 补充业务含义

**路径参数**:
- `id`: 字段ID

---

### 3.7 删除字段定义

**接口**: `DELETE /api/columns/{id}`

**功能**: 删除指定的字段定义

**路径参数**:
- `id`: 字段ID

---

## 4. 关系管理 API

### 4.1 创建关系

**接口**: `POST /api/relationships`

**功能**: 定义两个表之间的关系

**使用场景**:
- 定义表之间的 JOIN 关系
- 建立外键关联
- 支持跨表查询

**请求体**:
```json
{
  "name": "用户-订单关系",
  "fromModelId": 1,
  "toModelId": 2,
  "joinType": "one_to_many",
  "joinCondition": "[{\"from\": \"id\", \"to\": \"user_id\"}]",
  "description": "一个用户可以有多个订单"
}
```

**字段说明**:
- `joinType`: 关系类型
  - `one_to_one`: 一对一
  - `one_to_many`: 一对多
  - `many_to_many`: 多对多
- `joinCondition`: JOIN 条件（JSON 格式）

---

### 4.2 查询所有关系

**接口**: `GET /api/relationships`

**功能**: 获取所有表关系

**使用场景**:
- 在建模画布中绘制表之间的连线
- 查看完整的数据模型关系图
- Text-to-SQL 时确定 JOIN 路径

---

### 4.3 根据模型ID查询关系

**接口**: `GET /api/relationships/model/{modelId}`

**功能**: 获取指定模型相关的所有关系

**使用场景**:
- 查看某个表与其他表的关联关系
- 确定可以 JOIN 的表
- 构建查询路径

**路径参数**:
- `modelId`: 模型ID

---

### 4.4 根据ID查询关系

**接口**: `GET /api/relationships/{id}`

**功能**: 获取指定关系的详细信息

**路径参数**:
- `id`: 关系ID

---

### 4.5 更新关系

**接口**: `PUT /api/relationships/{id}`

**功能**: 更新关系定义

**路径参数**:
- `id`: 关系ID

---

### 4.6 删除关系

**接口**: `DELETE /api/relationships/{id}`

**功能**: 删除指定的关系

**路径参数**:
- `id`: 关系ID

---

## 5. 典型使用流程

### 5.1 快速开始流程

```
1. 创建数据源
   POST /api/datasources

2. 测试连接
   POST /api/datasources/test

3. 发现表结构
   GET /api/datasources/{id}/discover

4. 导入表结构
   POST /api/datasources/{id}/import

5. 查看导入的模型
   GET /api/models

6. 查看模型的字段
   GET /api/columns/model/{modelId}

7. 创建表关系
   POST /api/relationships
```

### 5.2 手动建模流程

```
1. 创建数据源
   POST /api/datasources

2. 手动创建模型
   POST /api/models

3. 批量创建字段定义
   POST /api/columns/batch

4. 定义表关系
   POST /api/relationships
```

---

## 6. 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 7. 注意事项

1. **数据源密码安全**: 生产环境建议对密码进行加密存储
2. **级联删除**: 删除数据源会删除关联的所有模型、字段和关系
3. **表结构发现**: 需要数据库用户有读取元数据的权限
4. **批量导入**: 导入大量表时可能需要较长时间
5. **字段类型推断**: 自动推断的字段类型可能需要手动调整

---

## 8. 下一步功能预告

- **Phase 3**: Text-to-SQL API（自然语言转 SQL）
- **Phase 4**: 对话式查询 API（多轮对话）
- **Phase 5**: 图表生成 API（自动可视化）

---

**文档版本**: v1.0
**最后更新**: 2026-03-05
**联系方式**: 如有问题请提交 Issue
