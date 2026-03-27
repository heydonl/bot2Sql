1.用户提问->通过提问内容从rag里面获取执行的模板(备注1)
1.1 成功获取到执行模板,然后将执行模板和提问和执行模板对应的示例问答给到大模型(备注2)，大模型返回结果后,开始按照模板来执行sql
1.1.1 如果模板执行成功，则返回结果给到用户
1.1.2 如果模板执行失败，则降级走后续让大模型根据表结构，表关系信息生成sql的流程（流程2）
1.2 失败获取到执行模板,则降级走后续让大模型根据表结构，表关系信息生成sql的流程 (流程2)
2.用户根据表结构，表关系，数据源，数据库等信息到llm获取出对应的sql模板(备注3)

备注1:
问题:获取工单在2025年7月1号到现在（2026年3月16日），工单状态对应的工单数量排在前五的状态是哪些？我要输出这个前五名的工单状态对应的工单id
模板如下:
```json
[
  {
    "id":"step1",
    "sql_template":"SELECT CONCAT(stage, '-', status) AS `task_status`, COUNT(*) AS `task_count` FROM uni_agency.task WHERE create_time >= {{startDate}} AND create_time < {{endDate}} and type = {{type}} GROUP BY stage, status ORDER BY `工单数量` DESC LIMIT 5",
    "paramDescs":[
      {
        "field":"startDate",
        "desc":"起始创建时间"
      },
      {
        "field":"endDate",
        "desc":"结束创建时间"
      },
      {
        "field":"type",
        "desc":"工单类型"
      }
    ],
    "tables":[
      {
        "name":"task",
        "database":"uni_agency"
      }
    ],
    "outputParamsDesc":[
      {
        "field":"task_status",
        "desc":"工单状态"
      },
      {
        "field":"task_count",
        "desc":"工单数量"
      }
    ],
    "desc":"获取在某个创建时间范围的所有工单里面工单状态对应的工单数量排名前五的工单状态",
    "datasource_id":1
  },
  {
    "id":"step2",
    "sql_template":"select task_id AS 工单id from uni_agency.task where CONCAT(stage, '-', status) in ({{step1.工单状态}})",
    "paramDescs":[
      {
        "field":"step1.task_status",
        "desc":"步骤一返回的工单状态"
      }
    ],
    "tables":[
      {
        "name":"task",
        "database":"uni_agency"
      }
    ],
    "outputParamsDesc":[
      {
        "field":"task_id",
        "desc":"工单id"
      }
    ],
    "desc":"获取工单状态对应的工单数量排名前五的所有工单id",
    "datasource_id":1
  }
]
```
备注2:
给到llm的prompt如下:
"
# 角色
你是一名 SQL 专家，擅长根据用户问题生成多步骤的 SQL 执行计划。

# 任务
你将获得一组预定义的 SQL 模板（JSON 数组形式），每个模板包含一个 SQL 片段、参数说明、输出字段说明等信息。  
你需要根据用户的最新问题，从模板中选出合适的步骤，并为每个步骤填充具体的参数值（`params` 字段），最终输出一个 JSON 数组，结构与输入模板保持一致。

# 模板说明
- 模板中的 `sql_template` 包含占位符 `{{xxx}}`，你需要在params中写出实际值。
- `paramDescs` 列出了每个参数的含义，你需要从用户问题中提取对应的值。
- 如果某个参数依赖前一步的输出（如 `{{step1.工单状态}}`），在生成当前步骤的 `params` 时，不需要为它生成值。
- `outputParamsDesc` 说明该步骤返回的字段，供后续步骤引用。
- `tables`说明当前sql模板的表名和对应的数据库
- 时间范围类参数需根据用户描述进行合理转换，例如“到现在”应替换为当前日期（或次日，若 SQL 使用 `< endDate`）。

# 示例
**用户问题**  
帮我输出工单在 2024 年 5 月 1 号到 2024 年 9 月 1 号，充值工单状态对应的工单数量排在前五的状态的对应的工单 id 是什么？
### 数据源: uni-agency-db
- **ID**: 1
- **类型**: mysql
- **数据库**: uni_agency

**相关的表**:
#### 表: task
**描述**: 该表用于管理企业内部的任务流转与协作流程。核心存储任务的全生命周期数据，包括任务基本信息（标题、内容、类型、优先级）、人员关系（创建人、执行人、BOP用户）、流程状态（阶段、状态、申请状态）以及业务扩展字段（广告主ID、海外标识、区域、协作类型）。 主要应用场景包括：任务创建与分配、跨部门协作管理、任务进度跟踪、API接口对接状态监控、关联任务追溯。支持任务优先级管理、反馈记录、申请原因留存等功能。通过task_id和associate_task_id实现任务关联，通过request_id对接外部系统请求。特别支持广告业务场景（adv_id相关字段）和国际化业务（overseas_flag、region）。newest_data_sync_to_bpms字段用于与BPMS系统的数据同步标识，确保流程数据一致性。整体设计兼顾内部流程管理和外部系统集成需求。
**主键**: id

**相关字段**:
- `id` (INT) [dimension]
- `task_id` (BIGINT) [dimension]
- `creator_id` (INT): 创建者ID [dimension]
- `executor_id` (INT): 操作者ID [dimension]
- `create_bop_user_id` (INT): bop用户id [dimension]
- `title` (VARCHAR) [dimension]
- `type` (TINYINT): 工单类型：10：开户申请，30：充值申请 [measure]
- `stage` (INT): 工单阶段 [measure]
- `status` (INT): 工单状态（不同工单可能有不同的工单状态） [measure]
- `feed_back` (VARCHAR): 外部反馈信息 [dimension]
- `note` (TEXT) [dimension]
- `apply_reason` (TEXT): 申请原因 [dimension]
- `content` (TEXT) [dimension]
- `priority` (INT): 优先级 [measure]
- `create_time` (TIMESTAMP) [dimension]
- `update_time` (TIMESTAMP) [dimension]
- `api_request_status` (INT): api请求状态 0:成功 1:失败 9:未请求 [measure]
- `api_note` (TEXT): api请求日志 [dimension]
- `special_field` (INT): 0:正常工单，1：批量清零产生的工单 [measure]
- `request_id` (CHAR): OE工单ID [dimension]
- `associate_task_id` (BIGINT): 关联工单号 [dimension]
- `level` (TINYINT): 工单层级,默认0 0:父工单 [measure]
- `cooperation_type` (VARCHAR): 等于MB时表示MB业务的工单 [dimension]
- `adv_id` (INT) [dimension]
- `add_adv_id_flag` (TINYINT): 是否需要客户补充advId(1=是,0=否) [measure]
- `overseas_flag` (TINYINT): 0为非海外户、1为海外户 [measure]
- `region` (TINYINT): TT媒体，地理区域 国内户:0,北美:1,东南亚:2 [measure]
- `process_type` (INT): 工单选择类型(0->人工, 1->API) [measure]
- `application_status` (INT): 账户下户状态 null-未扫描，1-审核中，2-下户成功，3-下户待充值授权，4-下户失败 [measure]
- `task_number` (VARCHAR): 外部工单的id [dimension]
- `newest_data_sync_to_bpms` (TIMESTAMP): 最新数据是否已同步到bpms(null=是,其他=否) [dimension]

初始模板如下:
```json
[
  {
    "id":"step1",
    "sql_template":"SELECT CONCAT(stage, '-', status) AS `task_status`, COUNT(*) AS `task_count` FROM uni_agency.task WHERE create_time >= {{startDate}} AND create_time < {{endDate}} and type = {{type}} GROUP BY stage, status ORDER BY `工单数量` DESC LIMIT 5",
    "paramDescs":[
      {
        "field":"startDate",
        "desc":"起始创建时间"
      },
      {
        "field":"endDate",
        "desc":"结束创建时间"
      },
      {
        "field":"type",
        "desc":"工单类型"
      }
    ],
    "tables":[
      {
        "name":"task",
        "database":"uni_agency"
      }
    ],
    "outputParamsDesc":[
      {
        "field":"task_status",
        "desc":"工单状态"
      },
      {
        "field":"task_count",
        "desc":"工单数量"
      }
    ],
    "desc":"获取在某个创建时间范围的所有工单里面工单状态对应的工单数量排名前五的工单状态",
    "datasource_id":1
  },
  {
    "id":"step2",
    "sql_template":"select task_id AS 工单id from uni_agency.task where CONCAT(stage, '-', status) in ({{step1.工单状态}}) and type = {{type}}",
    "paramDescs":[
      {
        "field":"step1.task_status",
        "desc":"步骤一返回的工单状态"
      },
      {
        "field":"type",
        "desc":"工单类型"
      }
    ],
    "tables":[
      {
        "name":"task",
        "database":"uni_agency"
      }
    ],
    "outputParamsDesc":[
      {
        "field":"task_id",
        "desc":"工单id"
      }
    ],
    "desc":"获取工单状态对应的工单数量排名前五的所有工单id",
    "datasource_id":1
  }
]
```

**预期输出**
```json
[
  {
    "id": "step1",
    "sql_template": "SELECT CONCAT(t.stage, '-', t.status) AS `task_status`, COUNT(*) AS `task_count` FROM uni_agency.task t WHERE t.create_time >= {{startDate}} AND t.create_time < {{endDate}} and t.type = {{type}} GROUP BY t.stage, t.status ORDER BY `task_count` DESC LIMIT 5",
    "paramDescs": [
      { "field": "startDate", "desc": "起始创建时间" },
      { "field": "endDate", "desc": "结束创建时间" },
      { "field": "type", "desc": "工单类型" }
    ],
    "params": {
      "startDate": "2024-05-01",
      "endDate": "2024-09-01",
      "type": 30
    },
    "outputParamsDesc": [
      { "field": "task_status", "desc": "工单状态" },
      { "field": "task_count", "desc": "工单数量" }
    ],
    "tables": [{
      "name": "task",
      "database": "uni_agency"
    }],
    "desc": "获取在某个创建时间范围的所有工单里面工单状态对应的工单数量排名前五的工单状态",
    "datasource_id": 1
  },
  {
    "id": "step2",
    "sql_template": "select t.task_id from uni_agency.task t where CONCAT(t.stage, '-', t.status) in ({{step1.工单状态}}) and t.type = {{type}}",
    "paramDescs": [
      { "field": "step1.task_status", "desc": "步骤一返回的task_status" },
      { "field": "type", "desc": "工单类型" }
    ],
    "params": {
      "type": 30
    },
    "tables": [{
      "name": "task",
      "database": "uni_agency"
    }],
    "outputParamsDesc": [
      { "field": "task_id", "desc": "工单id" }
    ],
    "desc": "获取充值工单状态对应的工单数量排名前五的所有工单id",
    "datasource_id": 1
  }
]
```

"

**现在的问题**:

**预期的输出**:
```json
```
# 预期的输出说明:
- 请使用```json```包裹着，并且被包裹着的的确是个json数组


备注3:

示例问题在intent_few_shot表中，里面有intent_json且json里面的tables和datasource是对应的表和数据库和数据源，示例问题里面的数据源在datasource表中，数据库在database中,表在model表中,字段在column_definition中,表关系在relationship表中
反例问题在few_shot_negative_intent表中(需要你帮忙新建这个表)还有在query_log表中(当前用户点击了不满意的查询)

给到llm的prompt如下:

你作为一个sql专家，你帮我生成一下满足我需求的sql:

示例问题: 帮我输出工单在 2024 年 5 月 1 号到 2024 年 9 月 1 号，充值工单状态对应的工单数量排在前五的状态的对应的工单 id 是什么？
### 数据源: uni-agency-db
- **ID**: 1
- **类型**: mysql

#### 数据库: uni_agency

**相关的表**:
##### 表: task
**描述**: 该表用于管理企业内部的任务流转与协作流程。核心存储任务的全生命周期数据，包括任务基本信息（标题、内容、类型、优先级）、人员关系（创建人、执行人、BOP用户）、流程状态（阶段、状态、申请状态）以及业务扩展字段（广告主ID、海外标识、区域、协作类型）。 主要应用场景包括：任务创建与分配、跨部门协作管理、任务进度跟踪、API接口对接状态监控、关联任务追溯。支持任务优先级管理、反馈记录、申请原因留存等功能。通过task_id和associate_task_id实现任务关联，通过request_id对接外部系统请求。特别支持广告业务场景（adv_id相关字段）和国际化业务（overseas_flag、region）。newest_data_sync_to_bpms字段用于与BPMS系统的数据同步标识，确保流程数据一致性。整体设计兼顾内部流程管理和外部系统集成需求。
**主键**: id

**相关字段**:
- `id` (INT) [dimension]
- `task_id` (BIGINT) [dimension]
- `creator_id` (INT): 创建者ID [dimension]
- `executor_id` (INT): 操作者ID [dimension]
- `create_bop_user_id` (INT): bop用户id [dimension]
- `title` (VARCHAR) [dimension]
- `type` (TINYINT): 工单类型：10：开户申请，30：充值申请 [measure]
- `stage` (INT): 工单阶段 [measure]
- `status` (INT): 工单状态（不同工单可能有不同的工单状态） [measure]
- `feed_back` (VARCHAR): 外部反馈信息 [dimension]
- `note` (TEXT) [dimension]
- `apply_reason` (TEXT): 申请原因 [dimension]
- `content` (TEXT) [dimension]
- `priority` (INT): 优先级 [measure]
- `create_time` (TIMESTAMP) [dimension]
- `update_time` (TIMESTAMP) [dimension]
- `api_request_status` (INT): api请求状态 0:成功 1:失败 9:未请求 [measure]
- `api_note` (TEXT): api请求日志 [dimension]
- `special_field` (INT): 0:正常工单，1：批量清零产生的工单 [measure]
- `request_id` (CHAR): OE工单ID [dimension]
- `associate_task_id` (BIGINT): 关联工单号 [dimension]
- `level` (TINYINT): 工单层级,默认0 0:父工单 [measure]
- `cooperation_type` (VARCHAR): 等于MB时表示MB业务的工单 [dimension]
- `adv_id` (INT) [dimension]
- `add_adv_id_flag` (TINYINT): 是否需要客户补充advId(1=是,0=否) [measure]
- `overseas_flag` (TINYINT): 0为非海外户、1为海外户 [measure]
- `region` (TINYINT): TT媒体，地理区域 国内户:0,北美:1,东南亚:2 [measure]
- `process_type` (INT): 工单选择类型(0->人工, 1->API) [measure]
- `application_status` (INT): 账户下户状态 null-未扫描，1-审核中，2-下户成功，3-下户待充值授权，4-下户失败 [measure]
- `task_number` (VARCHAR): 外部工单的id [dimension]
- `newest_data_sync_to_bpms` (TIMESTAMP): 最新数据是否已同步到bpms(null=是,其他=否) [dimension]

##### 表: recharge_application
**描述**: 该表用于管理广告投放的充值申请业务。核心存储用户针对特定媒体账户提交的充值请求信息，包括充值金额、每日预算设置以及相关的财务换算数据。 主要业务场景包括：广告主通过系统提交充值申请时，记录原始币种的充值金额、服务费和每日预算，同时根据汇率自动计算转换后的目标币种金额。表中关联任务ID和媒体账户ID，便于追踪充值流程和账户资金管理。时间戳字段支持审计充值申请的创建和更新历史。 该表是广告投放资金管理的关键环节，为财务对账、预算控制、多币种结算提供数据基础，同时支持充值审批流程的状态跟踪和异常处理。通过汇率和服务费字段，系统能够透明化展示跨币种充值的成本构成，帮助广告主理解实际到账金额与预算分配情况。。
**主键**: id

**相关字段**:
- `create_time` (TIMESTAMP): 创建时间 [dimension]
- `currency` (VARCHAR): 货币种类 [dimension]
- `daily_budget` (DECIMAL): 每日预算 [measure]
- `exchange_rate` (DECIMAL): 汇率(原币到adv币种) [measure]
- `id` (BIGINT) [dimension]
- `media_account_id` (BIGINT): 媒体账号id [dimension]
- `origin_daily_budget` (DECIMAL): 原始每日预算 [measure]
- `origin_recharge_amount` (DECIMAL): 原始充值金额(原币) [measure]
- `origin_recharge_currency` (VARCHAR): 原始货币种类(原币) [dimension]
- `origin_service_fee` (DECIMAL): 原始服务费(原币) [measure]
- `recharge_amount` (DECIMAL): 充值金额 [measure]
- `sevice_fee` (DECIMAL): 服务费 [measure]
- `task_id` (BIGINT): 申请批次ID [dimension]
- `update_time` (TIMESTAMP): 更新时间 [dimension]
- `user_id` (BIGINT): 用户账号ID [dimension]

##### 表: ua_orders
**描述**: 该表是广告投放系统的订单管理核心表，用于记录广告主充值和消费的完整交易流程。表中存储了订单基础信息（订单号、交易流水号）、支付相关数据（支付平台、支付渠道、币种、汇率、实付金额）、用户关联信息（用户ID、广告主ID、账户ID）以及订单状态追踪（订单状态、授权状态、余额状态）。 主要业务场景包括：广告主通过不同支付渠道进行账户充值，系统记录多币种交易并按汇率换算实际到账金额；支持OAuth授权的第三方支付平台对接，管理访问令牌的有效期；追踪订单从创建到支付完成的全生命周期，包括支付超时控制；关联BPMS渠道和转账账户进行财务对账；记录用户IP和会话信息用于风控分析。该表是广告投放业务的资金流转枢纽，为财务结算、用户充值管理和数据分析提供基础数据支撑，同时通过订单数据标识字段支持数据质量管理和异常订单追溯。
**主键**: id

**相关字段**:

- `access_token` (VARCHAR): 平台用户的access token [dimension]
- `access_token_valid_end_time` (TIMESTAMP): Access token失效日期 [dimension]
- `access_token_valid_start_time` (TIMESTAMP): Access token生效日期 [dimension]
- `account_id` (BIGINT): 支付平台的用户账号id [dimension]
- `actual_amount` (BIGINT): 际充值金额 单位分(回写) [measure]
- `adv_id` (BIGINT): 广告主ID [dimension]
- `amount` (BIGINT): 支付金额单位(默认美分) [measure]
- `amount_receivable` (BIGINT): 到账金额(默认美分) [measure]
- `balance_status` (TINYINT): 用户在平台的余额情况(0=充足,1=不充足) [measure]
- `bpms_channel_id` (TINYINT): bpms渠道id，1-汇丰 2-空中云汇 3-Paypal打款  4-汇付天下 5-银行转账  6-银联 7-payoneer 8-pingpong 9-万里汇 [dimension]
- `commit_id` (VARCHAR): 平台订单对应的commit id [dimension]
- `create_time` (TIMESTAMP): 创建订单时间 [dimension]
- `currency` (VARCHAR): 币种 (回写) [dimension]
- `exchange_rate` (DECIMAL): 实时汇率 [measure]
- `id` (BIGINT) [dimension]
- `ip` (VARCHAR): 下单IP地址 [dimension]
- `oauth_status` (TINYINT): 平台授权状态(0=pending,1=success,-1=fail) [measure]
- `order_data_flag` (VARCHAR): 订单的标识 [dimension]
- `order_id` (VARCHAR): UA平台订单号 [dimension]
- `pay_channel` (VARCHAR): 支付渠道 [dimension]
- `pay_platform` (VARCHAR): 支付平台 [dimension]
- `pay_platform_expired_time` (TIMESTAMP): 平台订单过期时间 [dimension]
- `pay_time` (TIMESTAMP): 支付时间 [dimension]
- `product_id` (VARCHAR): 商品ID [dimension]
- `product_num` (INT): 商品数量 [measure]
- `refresh_token` (VARCHAR): 平台用户的refresh token [dimension]
- `remark` (TEXT): 备注 [dimension]
- `session_id` (VARCHAR): 平台订单对应的session id [dimension]
- `status` (TINYINT): 0创建订单，1支付成功,-1支付失败 [measure]
- `trade_order_id` (VARCHAR): 渠道方交易号(成功回写) [dimension]
- `transfer_account_id` (BIGINT): 转账账户id [dimension]
- `user_id` (BIGINT): 用户ID [dimension]

#### 数据库: media_buy
**相关的表**:

##### 表: payment_account_application
**描述**: 该表用于管理广告主的付款账户申请流程，记录从申请提交到审核完成的全生命周期数据。核心存储内容包括申请人身份(adv_id)、申请金额(amount)、原始币种金额(original_amount/original_currency)及组织币种转换金额(org_amount/org_currency)、服务费(service_charge)、交易流水号(trade_id/transactions_id)等财务信息，同时追踪工作流时间节点(workflow_start_time/workflow_end_time)、审核状态(audit_status)、申请状态(state)和反馈信息(feedback_info)。 主要应用场景包括：广告主充值或提现申请的提交与审批管理、多币种交易的汇率转换与对账、付款流程的时效监控与异常追踪、财务审计中的交易溯源与合规检查。该表支持按申请类型(type)区分不同业务场景，通过关联项目(item_id)实现费用归属分析，并记录完整的时间戳(create_time/update_time/payment_time)以满足财务对账和流程优化需求。适用于财务结算系统、广告投放平台的资金管理模块。
**主键**: id

**相关字段**:
- `adv_id` (BIGINT): 广告主id [dimension]
- `amount` (DECIMAL): 实际到账（美金） [measure]
- `audit_status` (TINYINT): 拉取数据的状态（0:已完成，1:其它状态） [measure]
- `content` (TEXT): 返回数据存储 [dimension]
- `create_time` (TIMESTAMP): 创建时间 [dimension]
- `currency` (VARCHAR): 打款币种 [dimension]
- `feedback_info` (TEXT): 反馈信息 [dimension]
- `id` (BIGINT): 主键 [dimension]
- `item_id` (VARCHAR): 媒体平台主键 [dimension]
- `org_amount` (DECIMAL): 原始金额 [measure]
- `org_currency` (VARCHAR): 原始币种 [dimension]
- `org_service_charge` (DECIMAL): 原始服务费 [measure]
- `original_amount` (DECIMAL): 原币金额 [measure]
- `original_currency` (VARCHAR): 原币币种 [dimension]
- `payment_time` (TIMESTAMP): 打款时间 [dimension]
- `service_charge` (DECIMAL): 服务费 [measure]
- `state` (TINYINT): 状态：0正常，1.api拉取异常 [measure]
- `trade_id` (VARCHAR): 交易流水号 [dimension]
- `transactions_id` (VARCHAR): airwallex的转账的编号 [dimension]
- `type` (TINYINT): 1:银行转账,2:cm线下打款 [dimension]
- `update_time` (TIMESTAMP): 更新时间 [dimension]
- `workflow_end_time` (TIMESTAMP): 工作流程结束的时间（拉取数据以此字段为准） [dimension]
- `workflow_start_time` (TIMESTAMP): 工作流程开始的时间 [dimension]


### 数据源: adplatform-db
- **ID**: 2
- **类型**: mysql

#### 数据库: adplatform

**相关的表**:
##### 表: cps_order
**描述**: CPS订单表
**主键**: id
**相关字段**:

- `id` (INT): 主键 [dimension]
- `transaction_id` (VARCHAR): 点击id [dimension]
- `order_status` (VARCHAR): 订单状态, "Refund Orders"和"Completed Orders"不会被更新，其他状态会被更新 [dimension]
- `order_number` (BIGINT): 订单号 [dimension]
- `offer_id` (INT): offer标识 [dimension]
- `affiliate_id` (INT): 渠道id [dimension]
- `order_country` (VARCHAR): 订单国家 [dimension]
- `aff_sub5` (VARCHAR): 子渠道 [dimension]
- `order_amount` (DECIMAL) [measure]
- `estimated_commission` (DECIMAL) [measure]
- `final_order_amount` (DECIMAL) [measure]
- `ad_commission_rate` (DECIMAL) [measure]
- `commission` (DECIMAL) [measure]
- `create_time` (TIMESTAMP): 创建时间 [dimension]
- `update_time` (TIMESTAMP): 修改时间 [dimension]
- `ad_order_number` (VARCHAR): 广告主的订单号 [dimension]
- `platform` (VARCHAR): OS [dimension]
- `paid_time` (TIMESTAMP): 付款时间，默认取当前时间 [dimension]
- `order_time` (TIMESTAMP): 下单时间，创建订单时间 [dimension]
- `completed_time` (TIMESTAMP): 交易完成时间 [dimension]
- `completed_type` (VARCHAR): 交易完成类型：正常、超时完成类型 [dimension]
- `ad_estimated_commission` (DECIMAL): 上游预期佣金 [measure]
- `ad_commission` (DECIMAL): 上游最终佣金 [measure]
- `commission_rate` (DECIMAL): 下游佣金比例 [measure]
- `provider` (VARCHAR): 根据offer的provider写入 [dimension]
- `ad_currency` (VARCHAR): 上游结算货币 [dimension]
- `platform_currency` (VARCHAR): 平台货币 [dimension]
- `bid` (DECIMAL): 上游佣金数字额度，单位为ad_currency对应单位 [measure]


### 表关系 (备注:.第一位是数据源,.第二位是数据库,.第三位是表,.第四位是字段)
- uni-agency-db.uni_agency.task.task_id -> uni-agency-db.uni_agency.recharge_application.task_id (one_to_one)


示例输出:
```json
[
  {
    "id": "step1",
    "sql_template": "SELECT CONCAT(t.stage, '-', t.status) AS `task_status`, COUNT(*) AS `task_count` FROM uni_agency.task t WHERE t.create_time >= {{startDate}} AND t.create_time < {{endDate}} and t.type = {{type}} GROUP BY t.stage, t.status ORDER BY `task_count` DESC LIMIT 5",
    "paramDescs": [
      { "field": "startDate", "desc": "起始创建时间" },
      { "field": "endDate", "desc": "结束创建时间" },
      { "field": "type", "desc": "工单类型" }
    ],
    "params": {
      "startDate": "2024-05-01",
      "endDate": "2024-09-01",
      "type": 30
    },
    "outputParamsDesc": [
      { "field": "task_status", "desc": "工单状态" },
      { "field": "task_count", "desc": "工单数量" }
    ],
    "tables": [{
      "name": "task",
      "database": "uni_agency"
    }],
    "desc": "获取在某个创建时间范围的所有工单里面工单状态对应的工单数量排名前五的工单状态",
    "datasource_id": 1
  },
  {
    "id": "step2",
    "sql_template": "select t.task_id from uni_agency.task t where CONCAT(t.stage, '-', t.status) in ({{step1.工单状态}}) and t.type = {{type}}",
    "paramDescs": [
      { "field": "step1.task_status", "desc": "步骤一返回的task_status" },
      { "field": "type", "desc": "工单类型" }
    ],
    "params": {
      "type": 30
    },
    "tables": [{
      "name": "task",
      "database": "uni_agency"
    }],
    "outputParamsDesc": [
      { "field": "task_id", "desc": "工单id" }
    ],
    "desc": "获取充值工单状态对应的工单数量排名前五的所有工单id",
    "datasource_id": 1
  }
]
```

**不好的例子**:

问题:帮我输出在 2024 年 5 月 1 号到 2024 年 9 月 1 号，充值工单状态对应的工单数量排在前五的状态的对应的工单 id 是什么？
### 数据源: uni-agency-db
- **ID**: 1
- **类型**: mysql

#### 数据库: uni_agency

**相关的表**:
##### 表: task
**描述**: 该表用于管理企业内部的任务流转与协作流程。核心存储任务的全生命周期数据，包括任务基本信息（标题、内容、类型、优先级）、人员关系（创建人、执行人、BOP用户）、流程状态（阶段、状态、申请状态）以及业务扩展字段（广告主ID、海外标识、区域、协作类型）。 主要应用场景包括：任务创建与分配、跨部门协作管理、任务进度跟踪、API接口对接状态监控、关联任务追溯。支持任务优先级管理、反馈记录、申请原因留存等功能。通过task_id和associate_task_id实现任务关联，通过request_id对接外部系统请求。特别支持广告业务场景（adv_id相关字段）和国际化业务（overseas_flag、region）。newest_data_sync_to_bpms字段用于与BPMS系统的数据同步标识，确保流程数据一致性。整体设计兼顾内部流程管理和外部系统集成需求。
**主键**: id

**相关字段**:
- `id` (INT) [dimension]
- `task_id` (BIGINT) [dimension]
- `creator_id` (INT): 创建者ID [dimension]
- `executor_id` (INT): 操作者ID [dimension]
- `create_bop_user_id` (INT): bop用户id [dimension]
- `title` (VARCHAR) [dimension]
- `type` (TINYINT): 工单类型：10：开户申请，30：充值申请 [measure]
- `stage` (INT): 工单阶段 [measure]
- `status` (INT): 工单状态（不同工单可能有不同的工单状态） [measure]
- `feed_back` (VARCHAR): 外部反馈信息 [dimension]
- `note` (TEXT) [dimension]
- `apply_reason` (TEXT): 申请原因 [dimension]
- `content` (TEXT) [dimension]
- `priority` (INT): 优先级 [measure]
- `create_time` (TIMESTAMP) [dimension]
- `update_time` (TIMESTAMP) [dimension]
- `api_request_status` (INT): api请求状态 0:成功 1:失败 9:未请求 [measure]
- `api_note` (TEXT): api请求日志 [dimension]
- `special_field` (INT): 0:正常工单，1：批量清零产生的工单 [measure]
- `request_id` (CHAR): OE工单ID [dimension]
- `associate_task_id` (BIGINT): 关联工单号 [dimension]
- `level` (TINYINT): 工单层级,默认0 0:父工单 [measure]
- `cooperation_type` (VARCHAR): 等于MB时表示MB业务的工单 [dimension]
- `adv_id` (INT) [dimension]
- `add_adv_id_flag` (TINYINT): 是否需要客户补充advId(1=是,0=否) [measure]
- `overseas_flag` (TINYINT): 0为非海外户、1为海外户 [measure]
- `region` (TINYINT): TT媒体，地理区域 国内户:0,北美:1,东南亚:2 [measure]
- `process_type` (INT): 工单选择类型(0->人工, 1->API) [measure]
- `application_status` (INT): 账户下户状态 null-未扫描，1-审核中，2-下户成功，3-下户待充值授权，4-下户失败 [measure]
- `task_number` (VARCHAR): 外部工单的id [dimension]
- `newest_data_sync_to_bpms` (TIMESTAMP): 最新数据是否已同步到bpms(null=是,其他=否) [dimension]

##### 表: recharge_application
**描述**: 该表用于管理广告投放的充值申请业务。核心存储用户针对特定媒体账户提交的充值请求信息，包括充值金额、每日预算设置以及相关的财务换算数据。 主要业务场景包括：广告主通过系统提交充值申请时，记录原始币种的充值金额、服务费和每日预算，同时根据汇率自动计算转换后的目标币种金额。表中关联任务ID和媒体账户ID，便于追踪充值流程和账户资金管理。时间戳字段支持审计充值申请的创建和更新历史。 该表是广告投放资金管理的关键环节，为财务对账、预算控制、多币种结算提供数据基础，同时支持充值审批流程的状态跟踪和异常处理。通过汇率和服务费字段，系统能够透明化展示跨币种充值的成本构成，帮助广告主理解实际到账金额与预算分配情况。。
**主键**: id

**相关字段**:
- `create_time` (TIMESTAMP): 创建时间 [dimension]
- `currency` (VARCHAR): 货币种类 [dimension]
- `daily_budget` (DECIMAL): 每日预算 [measure]
- `exchange_rate` (DECIMAL): 汇率(原币到adv币种) [measure]
- `id` (BIGINT) [dimension]
- `media_account_id` (BIGINT): 媒体账号id [dimension]
- `origin_daily_budget` (DECIMAL): 原始每日预算 [measure]
- `origin_recharge_amount` (DECIMAL): 原始充值金额(原币) [measure]
- `origin_recharge_currency` (VARCHAR): 原始货币种类(原币) [dimension]
- `origin_service_fee` (DECIMAL): 原始服务费(原币) [measure]
- `recharge_amount` (DECIMAL): 充值金额 [measure]
- `sevice_fee` (DECIMAL): 服务费 [measure]
- `task_id` (BIGINT): 申请批次ID [dimension]
- `update_time` (TIMESTAMP): 更新时间 [dimension]
- `user_id` (BIGINT): 用户账号ID [dimension]

##### 表: ua_orders
**描述**: 该表是广告投放系统的订单管理核心表，用于记录广告主充值和消费的完整交易流程。表中存储了订单基础信息（订单号、交易流水号）、支付相关数据（支付平台、支付渠道、币种、汇率、实付金额）、用户关联信息（用户ID、广告主ID、账户ID）以及订单状态追踪（订单状态、授权状态、余额状态）。 主要业务场景包括：广告主通过不同支付渠道进行账户充值，系统记录多币种交易并按汇率换算实际到账金额；支持OAuth授权的第三方支付平台对接，管理访问令牌的有效期；追踪订单从创建到支付完成的全生命周期，包括支付超时控制；关联BPMS渠道和转账账户进行财务对账；记录用户IP和会话信息用于风控分析。该表是广告投放业务的资金流转枢纽，为财务结算、用户充值管理和数据分析提供基础数据支撑，同时通过订单数据标识字段支持数据质量管理和异常订单追溯。
**主键**: id

**相关字段**:

- `access_token` (VARCHAR): 平台用户的access token [dimension]
- `access_token_valid_end_time` (TIMESTAMP): Access token失效日期 [dimension]
- `access_token_valid_start_time` (TIMESTAMP): Access token生效日期 [dimension]
- `account_id` (BIGINT): 支付平台的用户账号id [dimension]
- `actual_amount` (BIGINT): 际充值金额 单位分(回写) [measure]
- `adv_id` (BIGINT): 广告主ID [dimension]
- `amount` (BIGINT): 支付金额单位(默认美分) [measure]
- `amount_receivable` (BIGINT): 到账金额(默认美分) [measure]
- `balance_status` (TINYINT): 用户在平台的余额情况(0=充足,1=不充足) [measure]
- `bpms_channel_id` (TINYINT): bpms渠道id，1-汇丰 2-空中云汇 3-Paypal打款  4-汇付天下 5-银行转账  6-银联 7-payoneer 8-pingpong 9-万里汇 [dimension]
- `commit_id` (VARCHAR): 平台订单对应的commit id [dimension]
- `create_time` (TIMESTAMP): 创建订单时间 [dimension]
- `currency` (VARCHAR): 币种 (回写) [dimension]
- `exchange_rate` (DECIMAL): 实时汇率 [measure]
- `id` (BIGINT) [dimension]
- `ip` (VARCHAR): 下单IP地址 [dimension]
- `oauth_status` (TINYINT): 平台授权状态(0=pending,1=success,-1=fail) [measure]
- `order_data_flag` (VARCHAR): 订单的标识 [dimension]
- `order_id` (VARCHAR): UA平台订单号 [dimension]
- `pay_channel` (VARCHAR): 支付渠道 [dimension]
- `pay_platform` (VARCHAR): 支付平台 [dimension]
- `pay_platform_expired_time` (TIMESTAMP): 平台订单过期时间 [dimension]
- `pay_time` (TIMESTAMP): 支付时间 [dimension]
- `product_id` (VARCHAR): 商品ID [dimension]
- `product_num` (INT): 商品数量 [measure]
- `refresh_token` (VARCHAR): 平台用户的refresh token [dimension]
- `remark` (TEXT): 备注 [dimension]
- `session_id` (VARCHAR): 平台订单对应的session id [dimension]
- `status` (TINYINT): 0创建订单，1支付成功,-1支付失败 [measure]
- `trade_order_id` (VARCHAR): 渠道方交易号(成功回写) [dimension]
- `transfer_account_id` (BIGINT): 转账账户id [dimension]
- `user_id` (BIGINT): 用户ID [dimension]

#### 数据库: media_buy
**相关的表**:

##### 表: payment_account_application
**描述**: 该表用于管理广告主的付款账户申请流程，记录从申请提交到审核完成的全生命周期数据。核心存储内容包括申请人身份(adv_id)、申请金额(amount)、原始币种金额(original_amount/original_currency)及组织币种转换金额(org_amount/org_currency)、服务费(service_charge)、交易流水号(trade_id/transactions_id)等财务信息，同时追踪工作流时间节点(workflow_start_time/workflow_end_time)、审核状态(audit_status)、申请状态(state)和反馈信息(feedback_info)。 主要应用场景包括：广告主充值或提现申请的提交与审批管理、多币种交易的汇率转换与对账、付款流程的时效监控与异常追踪、财务审计中的交易溯源与合规检查。该表支持按申请类型(type)区分不同业务场景，通过关联项目(item_id)实现费用归属分析，并记录完整的时间戳(create_time/update_time/payment_time)以满足财务对账和流程优化需求。适用于财务结算系统、广告投放平台的资金管理模块。
**主键**: id

**相关字段**:
- `adv_id` (BIGINT): 广告主id [dimension]
- `amount` (DECIMAL): 实际到账（美金） [measure]
- `audit_status` (TINYINT): 拉取数据的状态（0:已完成，1:其它状态） [measure]
- `content` (TEXT): 返回数据存储 [dimension]
- `create_time` (TIMESTAMP): 创建时间 [dimension]
- `currency` (VARCHAR): 打款币种 [dimension]
- `feedback_info` (TEXT): 反馈信息 [dimension]
- `id` (BIGINT): 主键 [dimension]
- `item_id` (VARCHAR): 媒体平台主键 [dimension]
- `org_amount` (DECIMAL): 原始金额 [measure]
- `org_currency` (VARCHAR): 原始币种 [dimension]
- `org_service_charge` (DECIMAL): 原始服务费 [measure]
- `original_amount` (DECIMAL): 原币金额 [measure]
- `original_currency` (VARCHAR): 原币币种 [dimension]
- `payment_time` (TIMESTAMP): 打款时间 [dimension]
- `service_charge` (DECIMAL): 服务费 [measure]
- `state` (TINYINT): 状态：0正常，1.api拉取异常 [measure]
- `trade_id` (VARCHAR): 交易流水号 [dimension]
- `transactions_id` (VARCHAR): airwallex的转账的编号 [dimension]
- `type` (TINYINT): 1:银行转账,2:cm线下打款 [dimension]
- `update_time` (TIMESTAMP): 更新时间 [dimension]
- `workflow_end_time` (TIMESTAMP): 工作流程结束的时间（拉取数据以此字段为准） [dimension]
- `workflow_start_time` (TIMESTAMP): 工作流程开始的时间 [dimension]


### 数据源: adplatform-db
- **ID**: 2
- **类型**: mysql

#### 数据库: adplatform

**相关的表**:
##### 表: cps_order
**描述**: CPS订单表
**主键**: id
**相关字段**:

- `id` (INT): 主键 [dimension]
- `transaction_id` (VARCHAR): 点击id [dimension]
- `order_status` (VARCHAR): 订单状态, "Refund Orders"和"Completed Orders"不会被更新，其他状态会被更新 [dimension]
- `order_number` (BIGINT): 订单号 [dimension]
- `offer_id` (INT): offer标识 [dimension]
- `affiliate_id` (INT): 渠道id [dimension]
- `order_country` (VARCHAR): 订单国家 [dimension]
- `aff_sub5` (VARCHAR): 子渠道 [dimension]
- `order_amount` (DECIMAL) [measure]
- `estimated_commission` (DECIMAL) [measure]
- `final_order_amount` (DECIMAL) [measure]
- `ad_commission_rate` (DECIMAL) [measure]
- `commission` (DECIMAL) [measure]
- `create_time` (TIMESTAMP): 创建时间 [dimension]
- `update_time` (TIMESTAMP): 修改时间 [dimension]
- `ad_order_number` (VARCHAR): 广告主的订单号 [dimension]
- `platform` (VARCHAR): OS [dimension]
- `paid_time` (TIMESTAMP): 付款时间，默认取当前时间 [dimension]
- `order_time` (TIMESTAMP): 下单时间，创建订单时间 [dimension]
- `completed_time` (TIMESTAMP): 交易完成时间 [dimension]
- `completed_type` (VARCHAR): 交易完成类型：正常、超时完成类型 [dimension]
- `ad_estimated_commission` (DECIMAL): 上游预期佣金 [measure]
- `ad_commission` (DECIMAL): 上游最终佣金 [measure]
- `commission_rate` (DECIMAL): 下游佣金比例 [measure]
- `provider` (VARCHAR): 根据offer的provider写入 [dimension]
- `ad_currency` (VARCHAR): 上游结算货币 [dimension]
- `platform_currency` (VARCHAR): 平台货币 [dimension]
- `bid` (DECIMAL): 上游佣金数字额度，单位为ad_currency对应单位 [measure]


### 表关系 (备注:.第一位是数据源,.第二位是数据库,.第三位是表,.第四位是字段)
- uni-agency-db.uni_agency.task.task_id -> uni-agency-db.uni_agency.recharge_application.task_id (one_to_one)

不好的输出:
```json
[
  {
    "id": "step1",
    "sql_template": "SELECT CAST(t.status AS CHAR) AS `order_status`, COUNT(*) AS `order_count` FROM uni_agency.ua_orders t WHERE t.create_time >= {{startDate}} AND t.create_time < {{endDate}} GROUP BY t.status ORDER BY `order_count` DESC LIMIT 5",
    "paramDescs": [
      { "field": "startDate", "desc": "起始创建时间" },
      { "field": "endDate", "desc": "结束创建时间" }
    ],
    "params": {
      "startDate": "2024-05-01",
      "endDate": "2024-09-01"
    },
    "outputParamsDesc": [
      { "field": "order_status", "desc": "订单状态（0创建订单，1支付成功，-1支付失败）" },
      { "field": "order_count", "desc": "订单数量" }
    ],
    "tables": [{
      "name": "ua_orders",
      "database": "uni_agency"
    }],
    "desc": "获取在指定创建时间范围内，订单状态对应的订单数量排名前五的订单状态",
    "datasource_id": 1
  },
  {
    "id": "step2",
    "sql_template": "SELECT t.id AS `order_id` FROM uni_agency.ua_orders t WHERE CAST(t.status AS CHAR) IN ({{step1.order_status}}) AND t.create_time >= {{startDate}} AND t.create_time < {{endDate}}",
    "paramDescs": [
      { "field": "step1.order_status", "desc": "步骤一返回的订单状态" },
      { "field": "startDate", "desc": "起始创建时间" },
      { "field": "endDate", "desc": "结束创建时间" }
    ],
    "params": {
      "startDate": "2024-05-01",
      "endDate": "2024-09-01"
    },
    "tables": [{
      "name": "ua_orders",
      "database": "uni_agency"
    }],
    "outputParamsDesc": [
      { "field": "order_id", "desc": "订单ID" }
    ],
    "desc": "获取前五订单状态对应的所有订单ID",
    "datasource_id": 1
  }
]
```

现在的问题:

输出:
```json

```

备注: 输出使用```json```包裹着,里面要确实是json来的
