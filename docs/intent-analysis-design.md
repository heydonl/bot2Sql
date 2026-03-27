# 意图分析系统设计文档

## 概述

本设计实现了一个两阶段的意图理解系统，用于将用户的自然语言查询转换为结构化的意图表示。

## 架构设计

```
用户输入 → 意图分析(JSON) → 骨架转换 → SQL生成 → 执行返回
```

## 核心组件

### 1. DTO 类（`com.tecdo.mac.sql2bot.dto.intent`）

#### 1.1 QueryIntent（枚举）
- 定义了 6 种查询意图类型
- SINGLE_METRIC_QUERY：单指标查询
- COMPARISON_QUERY：对比查询
- RANKING_QUERY：排名查询
- TREND_QUERY：趋势查询
- DETAIL_QUERY：明细查询
- OTHER：其他类型

#### 1.2 IntentAnalysisResponse
完整的意图分析响应，包含所有顶级字段：
- `intent`: 意图类型（QueryIntent 枚举）
- `entity`: 实体类型字符串（如 "work_order"）
- `dimensions`: 维度字段列表
- `metrics`: 指标列表（字段 + 聚合函数）
- `dateRanges`: 日期范围
- `comparisonPeriods`: 对比时间段
- `dimensionFilter`: 维度过滤条件
- `metricFilter`: 指标过滤条件
- `orderBys`: 排序规则
- `limit`: 限制行数
- `skeleton`: 骨架格式（后端生成，不在 JSON 中）
- `rawJson`: 原始 JSON 字符串（后端生成，不在 JSON 中）

内部类：
- `MetricDefinition`: 指标定义（field + aggregation）
- `DateRange`: 日期范围（startDate + endDate）
- `FilterCondition`: 过滤条件（支持嵌套）
- `Filter`: 单个过滤器（fieldName + operator + value）
- `OrderBy`: 排序规则（field + sortOrder）

#### 1.3 IntentAnalysisRequest
- `question`: 用户的自然语言问题
- `currentDate`: 当前日期（用于相对时间转换）
- `includeSkeleton`: 是否返回骨架格式

### 2. Service 类

#### 2.1 IntentAnalysisService
核心方法：

**analyzeIntent(IntentAnalysisRequest)**
- 构建意图分析的 prompt
- 调用 AI 服务进行意图分析
- 解析 AI 返回的 JSON
- 生成骨架格式

**convertToSkeleton(IntentAnalysisResponse)**
- 将 JSON 转换为骨架格式
- 格式：`INTENT={intent} | ENTITY={entity} | DIMS={dimensions} | ...`

辅助方法：
- `buildIntentAnalysisPrompt`: 构建 prompt（包含完整的意图分析指令）
- `parseIntentJson`: 解析 AI 返回的 JSON
- `formatDimensions`: 格式化维度字段
- `formatMetrics`: 格式化指标字段
- `formatFilterFields`: 格式化过滤字段
- `formatOrderBy`: 格式化排序字段

### 3. Controller 更新

#### QueryController 新增端点：

**POST /api/query/analyze-intent**
- 功能：意图分析
- 输入：IntentAnalysisRequest
- 输出：IntentAnalysisResponse（包含 JSON 和骨架格式）

**POST /api/query/intent-to-skeleton**
- 功能：JSON 转骨架格式
- 输入：IntentAnalysisResponse
- 输出：骨架格式字符串

## 工作流程

### 阶段 1：意图分析
1. 用户提交自然语言问题
2. 系统构建包含完整指令的 prompt
3. 调用 AI 服务（Claude API）
4. AI 返回结构化的 JSON
5. 系统解析 JSON 为 IntentAnalysisResponse

### 阶段 2：骨架转换
1. 提取 JSON 中的关键信息
2. 转换为简洁的骨架格式
3. 格式示例：
   ```
   INTENT=SINGLE_METRIC_QUERY | ENTITY=work_order | DIMS=status |
   METRICS=star_COUNT | DATERANGES=yes | DIMFILTERS=none |
   METFILTERS=none | ORDER=none | LIMIT=none | COMPPERIODS=none
   ```

## 工单类型编码映射

系统内置了工单类型到数字编码的映射：
- 开户工单 → "10"
- 充值工单 → "30"
- 减款/清零工单 → "50"
- 绑定BC工单 → "20"
- 解绑BC工单 → "21"
- 绑定邮箱工单 → "25"
- 解绑邮箱工单 → "26"
- 绑定主页工单 → "90"
- 解绑主页工单 → "100"
- 绑定pixel工单 → "70"

## 使用示例

### 示例 1：单指标查询

**输入：**
```json
{
  "question": "帮我输出工单在2025年7月1号到现在（2026年3月16日），不同状态的工单数量？",
  "currentDate": "2026-03-16"
}
```

**输出 JSON：**
```json
{
  "intent": "SINGLE_METRIC_QUERY",
  "entity": "work_order",
  "dimensions": ["status"],
  "metrics": [{"field": "*", "aggregation": "COUNT"}],
  "dateRanges": {"startDate": "2025-07-01", "endDate": "2026-03-16"},
  "dimensionFilter": null,
  "metricFilter": null,
  "orderBys": null,
  "limit": null,
  "comparisonPeriods": null
}
```

**骨架格式：**
```
INTENT=SINGLE_METRIC_QUERY | ENTITY=work_order | DIMS=status |
METRICS=star_COUNT | DATERANGES=yes | DIMFILTERS=none |
METFILTERS=none | ORDER=none | LIMIT=none | COMPPERIODS=none
```

### 示例 2：排名查询

**输入：**
```json
{
  "question": "帮我输出工单在2025年7月1号到现在（2026年3月16日），工单状态对应的工单数量排在前五的状态是哪些？"
}
```

**骨架格式：**
```
INTENT=RANKING_QUERY | ENTITY=work_order | DIMS=status |
METRICS=star_COUNT | DATERANGES=yes | DIMFILTERS=none |
METFILTERS=none | ORDER=COUNT(*) | LIMIT=yes | COMPPERIODS=none
```

### 示例 3：对比查询

**输入：**
```json
{
  "question": "帮我输出2026年1月1日到现在（2026年3月16日）的工单总数，并与去年同期（2025年1月1日到2025年3月16日）的工单总数进行同比增长比较。"
}
```

**骨架格式：**
```
INTENT=COMPARISON_QUERY | ENTITY=work_order | DIMS=none |
METRICS=star_COUNT | DATERANGES=none | DIMFILTERS=none |
METFILTERS=none | ORDER=none | LIMIT=none | COMPPERIODS=yes
```

## API 测试

### 测试意图分析
```bash
curl -X POST http://localhost:8082/api/query/analyze-intent \
  -H "Content-Type: application/json" \
  -d '{
    "question": "帮我输出工单在2025年7月1号到现在，不同状态的工单数量？",
    "currentDate": "2026-03-16",
    "includeSkeleton": true
  }'
```

### 测试骨架转换
```bash
curl -X POST http://localhost:8082/api/query/intent-to-skeleton \
  -H "Content-Type: application/json" \
  -d '{
    "intent": "SINGLE_METRIC_QUERY",
    "entity": "work_order",
    "dimensions": ["status"],
    "metrics": [{"field": "*", "aggregation": "COUNT"}],
    "dateRanges": {"startDate": "2025-07-01", "endDate": "2026-03-16"},
    "dimensionFilter": null,
    "metricFilter": null,
    "orderBys": null,
    "limit": null,
    "comparisonPeriods": null
  }'
```

## 后续集成建议

1. **与现有查询流程集成**
   - 在 TextToSQLService 中调用 IntentAnalysisService
   - 使用意图信息优化 SQL 生成
   - 根据意图类型选择不同的 prompt 模板

2. **缓存优化**
   - 对相似问题的意图分析结果进行缓存
   - 使用 Redis 存储常见问题的意图

3. **监控和日志**
   - 记录意图分析的准确率
   - 收集用户反馈，优化 prompt

4. **前端集成**
   - 在查询界面显示识别的意图
   - 允许用户修正意图识别结果
   - 提供意图类型的可视化展示

## 技术栈

- Spring Boot 4.0.3
- Java 17
- Jackson（JSON 处理）
- Lombok（减少样板代码）
- Claude API（意图分析）

## 注意事项

1. **编码规范**：所有中文必须使用 UTF-8 编码
2. **日期格式**：统一使用 YYYY-MM-DD 格式
3. **错误处理**：完善的异常捕获和日志记录
4. **AI 响应解析**：处理可能的 markdown 代码块标记
5. **空值处理**：所有可选字段都正确处理 null 值
