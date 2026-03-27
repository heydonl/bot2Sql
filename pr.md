请帮我调整一下以下的逻辑，我需要这样的效果:
1.根据用户输入的需求，分析出他的意图:
你是一个广告账户管理系统的意图理解模块。你的任务是将用户的自然语言问题转换为结构化的意图表示，输出严格的 JSON 格式。

### 意图类型（intent）
必须是以下之一：
- SINGLE_METRIC_QUERY：单指标查询（如工单数、消耗金额）
- COMPARISON_QUERY：对比查询（两个时间段对比）
- RANKING_QUERY：排名查询（如 top N）
- TREND_QUERY：趋势查询（如月度趋势）
- DETAIL_QUERY：明细查询（列出符合条件的记录）
- OTHER：其他不属于以上类型的查询

### 工单类型与数字编码映射（taskType）
当用户提到以下工单类型时，请使用对应的数字编码（字符串形式）：
- 开户工单 → "10"
- 广告账户充值工单 → "30"
- 广告账户减款工单 → "50"   （清零工单也使用"50"）
- 广告账户清零工单 → "50"
- 广告账户绑定BM/MCC/BC工单 → "20"
- 广告账户解绑BM/MCC/BC工单 → "21"
- 广告账户绑定邮箱工单 → "25"
- 广告账户解绑邮箱工单 → "26"
- 广告账户绑定主页工单 → "90"
- 广告账户解绑主页工单 → "100"
- 广告账户绑定pixel工单 → "70"
- 其他工单类型 → null

### 输出 JSON 结构
输出必须包含以下两个顶级字段：
- "intent": 意图类型（字符串）
- "entity": 对象，包含所有可能实体字段（缺失或无法确定时填 null）

entity 对象包含以下字段（顺序无关，但必须全部列出）：

{
"intent": "COMPARISON_QUERY",
"entity": "work_order",
"dimensions": [],
"metrics": [
{ "field": "*", "aggregation": "COUNT" }
],
"dateRanges": null,
"comparisonPeriods": [
{ "startDate": "2026-01-01", "endDate": "2026-03-16" },
{ "startDate": "2025-01-01", "endDate": "2025-03-16" }
],
"dimensionFilter": null,
"metricFilter": null,
"orderBys": null,
"limit": null
}

注意：
- 所有时间范围请尽量转换为具体的 YYYY-MM-DD 格式（例如“2025年第一季度”应转换为 "2025-01-01" 到 "2025-03-31"）。
- 如果问题中没有提到某字段，填 null。
- 对于工单类型，优先使用上述数字编码；如果无法确定，填 null。
- 输出必须是合法的 JSON，不要添加任何额外解释或标记。

### 示例（Few-shot）

以下是几个示例，展示如何从用户问题映射到意图和实体。

#### 示例 1：SINGLE_METRIC_QUERY - 查询不同状态工单的数量
用户： 帮我输出工单在2025年7月1号到现在（2026年3月16日），不同状态的工单数量？
输出：
{
"intent": "SINGLE_METRIC_QUERY",
"entity": "work_order",
"dimensions": ["advertisers.name"],
"metrics": [
{ "field": "amount", "aggregation": "SUM" },
{ "field": "*", "aggregation": "COUNT" }
],
"dateRanges": { "startDate": "2025-06-01", "endDate": "2025-06-30" },
"dimensionFilter": null,
"metricFilter": null,
"orderBys": null,
"limit": null
}

示例 2：SINGLE_METRIC_QUERY - 查询每个广告主的充值金额和工单数量
用户：帮我输出工单在2025年6月1号到2025年6月30号每个广告主对应的充值工单的总金额和工单数量
输出：
{
"intent": "SINGLE_METRIC_QUERY",
"entity": "work_order",
"dimensions": ["advertisers.name"],
"metrics": [
{ "field": "amount", "aggregation": "SUM" },
{ "field": "*", "aggregation": "COUNT" }
],
"dateRanges": { "startDate": "2025-06-01", "endDate": "2025-06-30" },
"dimensionFilter": {
"filter": {
"fieldName": "taskType",
"operator": "=",
"value": "30"
}
},
"metricFilter": null,
"orderBys": null,
"limit": null
}


示例 3：RANKING_QUERY - 查询工单数量排名前五的状态
用户：帮我输出工单在2025年7月1号到现在（2026年3月16日），工单状态对应的工单数量排在前五的状态是哪些？
输出：

json
{
"intent": "RANKING_QUERY",
"entity": "work_order",
"dimensions": ["status"],
"metrics": [
{ "field": "*", "aggregation": "COUNT" }
],
"dateRanges": { "startDate": "2025-07-01", "endDate": "2026-03-16" },
"dimensionFilter": null,
"metricFilter": null,
"orderBys": [
{ "field": "COUNT(*)", "sortOrder": "DESCENDING" }
],
"limit": 5
}

示例 4：COMPARISON_QUERY - 查询工单总数的同比增长
用户：帮我输出2026年1月1日到现在（2026年3月16日）的工单总数，并与去年同期（2025年1月1日到2025年3月16日）的工单总数进行同比增长比较。
输出：

json
{
"intent": "COMPARISON_QUERY",
"entity": "work_order",
"dimensions": [],
"metrics": [
{ "field": "*", "aggregation": "COUNT" }
],
"dateRanges": null,
"comparisonPeriods": [
{ "startDate": "2026-01-01", "endDate": "2026-03-16" },
{ "startDate": "2025-01-01", "endDate": "2025-03-16" }
],
"dimensionFilter": null,
"metricFilter": null,
"orderBys": null,
"limit": null
}

### 现在，请分析以下用户问题，输出 JSON：

用户：[实际用户问题]
输出：


2.然后将上面分析出来的意图json，转化成以下形式INTENT={intent} | ENTITY={entity} | DIMS={dimensions} | METRICS={metrics} | DATERANGES={dateRanges} | DIMFILTERS={dimFilterFields} | METFILTERS={metFilterFields} | ORDER={orderByFields} | LIMIT={hasLimit} | COMPPERIODS={hasComparisonPeriods}


示例 1：查询不同状态的工单数量（SINGLE_METRIC_QUERY）
JSON：
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
骨架：
INTENT=SINGLE_METRIC_QUERY | ENTITY=work_order | DIMS=status | METRICS=star_COUNT | DATERANGES=yes | DIMFILTERS=none | METFILTERS=none | ORDER=none | LIMIT=none | COMPPERIODS=none

示例 2：每个广告主的充值金额和工单数量（SINGLE_METRIC_QUERY，带过滤）
JSON：
{
"intent": "SINGLE_METRIC_QUERY",
"entity": "work_order",
"dimensions": ["advertisers.name"],
"metrics": [
{"field": "amount", "aggregation": "SUM"},
{"field": "*", "aggregation": "COUNT"}
],
"dateRanges": {"startDate": "2025-06-01", "endDate": "2025-06-30"},
"dimensionFilter": {
"filter": {
"fieldName": "taskType",
"operator": "=",
"value": "30"
}
},
"metricFilter": null,
"orderBys": null,
"limit": null,
"comparisonPeriods": null
}
骨架：
INTENT=SINGLE_METRIC_QUERY | ENTITY=work_order | DIMS=advertisers.name | METRICS=amount_SUM,star_COUNT | DATERANGES=yes | DIMFILTERS=taskType | METFILTERS=none | ORDER=none | LIMIT=none | COMPPERIODS=none

示例 3：工单数量排名前五的状态（RANKING_QUERY）
JSON：
{
"intent": "RANKING_QUERY",
"entity": "work_order",
"dimensions": ["status"],
"metrics": [{"field": "*", "aggregation": "COUNT"}],
"dateRanges": {"startDate": "2025-07-01", "endDate": "2026-03-16"},
"dimensionFilter": null,
"metricFilter": null,
"orderBys": [{"field": "COUNT(*)", "sortOrder": "DESCENDING"}],
"limit": 5,
"comparisonPeriods": null
}
骨架：
INTENT=RANKING_QUERY | ENTITY=work_order | DIMS=status | METRICS=star_COUNT | DATERANGES=yes | DIMFILTERS=none | METFILTERS=none | ORDER=COUNT(*) | LIMIT=yes | COMPPERIODS=none

示例 4：工单总数同比增长（COMPARISON_QUERY）
JSON：
{
"intent": "COMPARISON_QUERY",
"entity": "work_order",
"dimensions": [],
"metrics": [{"field": "*", "aggregation": "COUNT"}],
"dateRanges": null,
"dimensionFilter": null,
"metricFilter": null,
"orderBys": null,
"limit": null,
"comparisonPeriods": [
{"startDate": "2026-01-01", "endDate": "2026-03-16"},
{"startDate": "2025-01-01", "endDate": "2025-03-16"}
]
}
骨架：
INTENT=COMPARISON_QUERY | ENTITY=work_order | DIMS=none | METRICS=star_COUNT | DATERANGES=none | DIMFILTERS=none | METFILTERS=none | ORDER=none | LIMIT=none | COMPPERIODS=yes



用户自然语言提问->embedding->以高准确性为目标,根据向量到rag找到相似的sql模板->然后提交prompt到llm让llm生成模板对应的入参值->然后程序根据llm返回的入参值填充sql模板然后执行sql->将执行结果返回给用户
->用户根据结果点击满意或者不满意->评分结果会记录到rag中，会有问题到sql模板的关系还有评分(满意是1，不满意是0)->如果用户点击不满意->则根据用户的的问题到rag里面搜索对应的表（以高召回率为基准）->根据搜索出来的表，然后再到数据库里面找到它关联的另外一个表，根据bfs两层深度找到涉及的表->然后将表，表关系,数据库，数据源，预先由专家写好的在数据库里的问答对(few-shot),用户提问的问题给到llm，由llm生成对应的sql模板，和对应的入参值,最后由程序来执行获取结果，给到用户->用户还可以打分->直到用户满意
1.可能会出现以下几个问题
1.1 占位符对不上(这种要剔除掉)
1.2 模板不对(这种也要剔除掉)

select * from task where task_id = {{task_id}}

如果用户反馈说模板不对->评分低->建立提问到模板的映射评分->


一开始rag里面有
专家的一问一答的不能被修改（few-shot）(也要放到rag里面去)
用户的一问一答可以被修改 （rag）(被打分次数多的，且评分高的可以进入到few-shot里面去。评分低的作为反例)


如果从rag里面拿出来的sql模板给到llm,举例则为如下 (语义相似，所以大模型容易提取)：
sql模板 + sql模板中的参数描述,few-shot问题和我提出的问题语义是相似的
原始问题: 专家的问题
答案:  sql模板中的参数值
问题: [用户问题]
输出: sql模板中的参数值

还要记录下用户提问-》答案的评分



