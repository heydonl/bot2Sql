# 提示词管理和术语库功能说明

## 功能概述

新增了两个重要的功能模块，用于优化 Text-to-SQL 的准确性和用户体验。

---

## 1. 提示词管理 (Prompts)

### 功能说明

提示词管理允许你自定义和管理发送给 LLM 的提示词模板，从而优化 SQL 生成的质量和准确性。

### 核心特性

#### 1.1 提示词分类
- **系统提示词 (system)**: 定义 AI 的角色和行为规则
  - 例如：定义 AI 是一个 SQL 专家，需要遵循的规则等
- **用户提示词 (user)**: 用户查询的模板
  - 例如：如何格式化用户的问题
- **示例提示词 (example)**: 提供查询示例
  - 例如：Few-shot learning 的示例

#### 1.2 优先级管理
- 每个提示词可以设置优先级（0-100）
- 数字越大优先级越高
- 系统会按优先级顺序组合提示词

#### 1.3 模板变量
支持在提示词中使用变量：
- `{database_schema}` - 数据库结构信息
- `{user_question}` - 用户的问题
- `{conversation_history}` - 对话历史

#### 1.4 启用/禁用
- 可以临时禁用某个提示词而不删除
- 方便进行 A/B 测试

### 使用场景

**场景 1: 定义系统角色**
```
名称: SQL 专家角色定义
分类: system
优先级: 100
模板内容:
你是一个专业的 SQL 查询专家。你的任务是根据用户的自然语言问题，
生成准确的 SQL 查询语句。

规则：
1. 只生成 SELECT 查询
2. 使用表的别名
3. 添加适当的注释
4. 考虑性能优化
```

**场景 2: 提供查询示例**
```
名称: 聚合查询示例
分类: example
优先级: 50
模板内容:
示例 1:
问题: 上个月的总销售额是多少？
SQL: SELECT SUM(amount) as total_sales
     FROM orders
     WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 1 MONTH)

示例 2:
问题: 哪个用户购买最多？
SQL: SELECT u.name, COUNT(o.id) as order_count
     FROM users u
     JOIN orders o ON u.id = o.user_id
     GROUP BY u.id
     ORDER BY order_count DESC
     LIMIT 1
```

### 前端界面

- **列表页面**: 显示所有提示词，支持按分类筛选
- **创建/编辑**: 表单式编辑，支持多行文本输入
- **查看详情**: 以格式化方式展示提示词内容

---

## 2. 术语库 (Glossary)

### 功能说明

术语库用于定义业务术语和同义词，帮助 AI 理解特定领域的业务语言。

### 核心特性

#### 2.1 术语定义
- **术语**: 业务中使用的专业词汇
- **定义**: 详细解释术语的含义
- **同义词**: 该术语的其他表达方式
- **分类**: 业务/技术/指标/其他
- **使用示例**: 展示如何在查询中使用该术语

#### 2.2 同义词支持
- 支持为每个术语添加多个同义词
- AI 会将同义词映射到标准术语
- 提高查询的灵活性

#### 2.3 搜索功能
- 支持按术语或定义搜索
- 支持按分类筛选
- 快速查找相关术语

#### 2.4 分类管理
- **业务术语**: 业务领域的专业词汇
- **技术术语**: 技术相关的术语
- **指标术语**: 业务指标和 KPI
- **其他**: 其他类型的术语

### 使用场景

**场景 1: 定义业务指标**
```
术语: GMV
定义: Gross Merchandise Volume，商品交易总额，
      指一定时间内的成交总金额
同义词: ["交易总额", "成交总额", "总销售额"]
分类: 指标术语
使用示例:
- 查询上个月的 GMV
- GMV 环比增长率是多少
- 各类别的 GMV 分布
```

**场景 2: 定义业务术语**
```
术语: 活跃用户
定义: 在指定时间段内有过登录或交互行为的用户
同义词: ["DAU", "MAU", "日活", "月活"]
分类: 业务术语
使用示例:
- 今天有多少活跃用户
- 最近一周的日活趋势
- 月活用户数量
```

**场景 3: 定义技术术语**
```
术语: 转化率
定义: 完成目标行为的用户数占总用户数的比例
同义词: ["CVR", "Conversion Rate"]
分类: 指标术语
使用示例:
- 各渠道的转化率对比
- 转化率最高的产品是什么
```

### 前端界面

- **列表页面**: 显示所有术语，支持搜索和筛选
- **创建/编辑**: 表单式编辑，支持动态添加同义词
- **详情查看**: 完整展示术语的所有信息

---

## 3. 数据库表结构

### 3.1 prompt_template 表
```sql
CREATE TABLE prompt_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    template TEXT NOT NULL,
    category VARCHAR(50) DEFAULT 'user',
    is_active BOOLEAN DEFAULT TRUE,
    priority INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 3.2 glossary 表
```sql
CREATE TABLE glossary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    term VARCHAR(100) NOT NULL,
    definition TEXT NOT NULL,
    synonyms TEXT,
    category VARCHAR(50),
    examples TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_term (term)
);
```

---

## 4. API 接口

### 4.1 提示词管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/prompts | 获取所有提示词 |
| GET | /api/prompts/active | 获取启用的提示词 |
| GET | /api/prompts/category/{category} | 按分类获取 |
| GET | /api/prompts/{id} | 获取单个提示词 |
| POST | /api/prompts | 创建提示词 |
| PUT | /api/prompts/{id} | 更新提示词 |
| DELETE | /api/prompts/{id} | 删除提示词 |

### 4.2 术语库 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/glossary | 获取所有术语 |
| GET | /api/glossary/active | 获取启用的术语 |
| GET | /api/glossary/category/{category} | 按分类获取 |
| GET | /api/glossary/search?keyword={keyword} | 搜索术语 |
| GET | /api/glossary/{id} | 获取单个术语 |
| POST | /api/glossary | 创建术语 |
| PUT | /api/glossary/{id} | 更新术语 |
| DELETE | /api/glossary/{id} | 删除术语 |

---

## 5. 使用建议

### 5.1 提示词管理最佳实践

1. **系统提示词**
   - 设置最高优先级（90-100）
   - 定义清晰的角色和规则
   - 保持简洁明了

2. **示例提示词**
   - 提供 3-5 个典型示例
   - 覆盖常见查询场景
   - 优先级设置为中等（40-60）

3. **用户提示词**
   - 格式化用户输入
   - 添加必要的上下文
   - 优先级设置为较低（10-30）

### 5.2 术语库最佳实践

1. **完整性**
   - 收集所有业务相关术语
   - 定期更新和维护
   - 添加详细的定义

2. **同义词**
   - 收集用户的不同表达方式
   - 包含缩写和全称
   - 考虑中英文对照

3. **分类**
   - 合理分类便于管理
   - 保持分类体系一致
   - 定期审查和调整

4. **示例**
   - 提供真实的使用场景
   - 展示多种表达方式
   - 帮助用户理解如何使用

---

## 6. 与 Text-to-SQL 的集成

### 6.1 提示词的使用流程

1. 用户输入自然语言问题
2. 系统加载所有启用的提示词
3. 按优先级排序
4. 替换模板变量
5. 组合成完整的 Prompt
6. 发送给 LLM
7. 获取 SQL 结果

### 6.2 术语库的使用流程

1. 用户输入包含业务术语的问题
2. 系统在术语库中查找匹配的术语
3. 将同义词映射到标准术语
4. 在 Prompt 中添加术语定义
5. 帮助 LLM 理解业务含义
6. 生成更准确的 SQL

---

## 7. 后续优化方向

### 7.1 提示词管理
- [ ] 支持提示词模板的版本管理
- [ ] 支持 A/B 测试功能
- [ ] 提供提示词效果分析
- [ ] 支持提示词的导入导出

### 7.2 术语库
- [ ] 支持术语的层级关系
- [ ] 自动从查询中学习新术语
- [ ] 提供术语使用频率统计
- [ ] 支持术语的批量导入

---

## 8. 总结

提示词管理和术语库是提升 Text-to-SQL 准确性的关键功能：

- **提示词管理**: 通过精心设计的提示词，引导 LLM 生成更准确的 SQL
- **术语库**: 通过定义业务术语，帮助 AI 理解特定领域的语言

这两个功能相辅相成，共同提升系统的智能化水平和用户体验。

---

**功能状态**: ✅ 已完成
**版本**: v1.2
**更新日期**: 2026-03-06
