-- SQL2Bot Database Schema
-- Phase 1: Core Tables for Semantic Modeling

-- 1. 数据源表
CREATE TABLE IF NOT EXISTS datasource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '数据源名称',
    type VARCHAR(20) NOT NULL COMMENT '数据源类型: mysql, postgresql',
    host VARCHAR(255) NOT NULL COMMENT '主机地址',
    port INT NOT NULL COMMENT '端口号',
    database_name VARCHAR(100) NOT NULL COMMENT '数据库名',
    username VARCHAR(100) COMMENT '用户名',
    password VARCHAR(255) COMMENT '密码(加密存储)',
    properties TEXT COMMENT '额外配置(JSON格式)',
    status VARCHAR(20) DEFAULT 'active' COMMENT '状态: active, inactive',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_name (name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置表';

-- 1.5 数据库表
CREATE TABLE IF NOT EXISTS `database` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id BIGINT NOT NULL COMMENT '数据源ID',
    database_name VARCHAR(100) NOT NULL COMMENT '数据库名',
    display_name VARCHAR(100) COMMENT '显示名称',
    description TEXT COMMENT '描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE,
    UNIQUE KEY uk_datasource_database (datasource_id, database_name),
    INDEX idx_datasource (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库表';

-- 2. 表模型
CREATE TABLE IF NOT EXISTS model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id BIGINT NOT NULL COMMENT '数据源ID',
    database_id BIGINT COMMENT '数据库ID',
    table_name VARCHAR(100) NOT NULL COMMENT '物理表名',
    display_name VARCHAR(100) COMMENT '显示名称',
    description TEXT COMMENT '业务描述',
    is_visible BOOLEAN DEFAULT TRUE COMMENT '是否对查询开放',
    primary_key VARCHAR(100) COMMENT '主键字段名',
    properties TEXT COMMENT '额外属性(JSON格式)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE,
    FOREIGN KEY (database_id) REFERENCES `database`(id) ON DELETE SET NULL,
    UNIQUE KEY uk_database_table (database_id, table_name),
    INDEX idx_datasource (datasource_id),
    INDEX idx_database (database_id),
    INDEX idx_visible (is_visible)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表模型定义';

-- 3. 字段定义
CREATE TABLE IF NOT EXISTS column_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    model_id BIGINT NOT NULL COMMENT '模型ID',
    column_name VARCHAR(100) NOT NULL COMMENT '字段名',
    display_name VARCHAR(100) COMMENT '显示名称',
    description TEXT COMMENT '业务描述',
    data_type VARCHAR(50) COMMENT '数据类型',
    column_type VARCHAR(20) COMMENT '字段类型: dimension(维度), measure(度量)',
    is_nullable BOOLEAN DEFAULT TRUE COMMENT '是否可为空',
    default_value VARCHAR(255) COMMENT '默认值',
    properties TEXT COMMENT '额外属性(JSON格式)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (model_id) REFERENCES model(id) ON DELETE CASCADE,
    UNIQUE KEY uk_model_column (model_id, column_name),
    INDEX idx_model (model_id),
    INDEX idx_column_type (column_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段定义表';

-- 4. 表关系
CREATE TABLE IF NOT EXISTS relationship (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) COMMENT '关系名称',
    from_model_id BIGINT NOT NULL COMMENT '源模型ID',
    to_model_id BIGINT NOT NULL COMMENT '目标模型ID',
    join_type VARCHAR(20) NOT NULL COMMENT '关系类型: one_to_one, one_to_many, many_to_many',
    join_condition TEXT NOT NULL COMMENT 'JOIN条件(JSON格式): [{"from": "user_id", "to": "id"}]',
    description TEXT COMMENT '关系描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (from_model_id) REFERENCES model(id) ON DELETE CASCADE,
    FOREIGN KEY (to_model_id) REFERENCES model(id) ON DELETE CASCADE,
    INDEX idx_from_model (from_model_id),
    INDEX idx_to_model (to_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表关系定义';

-- 5. 计算字段
CREATE TABLE IF NOT EXISTS calculated_field (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    model_id BIGINT NOT NULL COMMENT '模型ID',
    name VARCHAR(100) NOT NULL COMMENT '字段名',
    display_name VARCHAR(100) COMMENT '显示名称',
    expression TEXT NOT NULL COMMENT '计算表达式',
    description TEXT COMMENT '字段描述',
    data_type VARCHAR(50) COMMENT '返回数据类型',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (model_id) REFERENCES model(id) ON DELETE CASCADE,
    UNIQUE KEY uk_model_name (model_id, name),
    INDEX idx_model (model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计算字段定义';

-- 6. 指标定义
CREATE TABLE IF NOT EXISTS metric (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '指标名称',
    display_name VARCHAR(100) COMMENT '显示名称',
    description TEXT COMMENT '指标描述',
    model_id BIGINT NOT NULL COMMENT '关联模型ID',
    aggregation_type VARCHAR(20) NOT NULL COMMENT '聚合类型: sum, avg, count, max, min',
    base_column_id BIGINT COMMENT '基础字段ID',
    filter_condition TEXT COMMENT '过滤条件(SQL WHERE子句)',
    expression TEXT COMMENT '复合指标表达式',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (model_id) REFERENCES model(id) ON DELETE CASCADE,
    FOREIGN KEY (base_column_id) REFERENCES column_definition(id) ON DELETE SET NULL,
    UNIQUE KEY uk_name (name),
    INDEX idx_model (model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义表';

-- 7. 对话会话
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT COMMENT '用户ID',
    title VARCHAR(255) COMMENT '会话标题',
    datasource_id BIGINT COMMENT '关联数据源ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user (user_id),
    INDEX idx_datasource (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- 8. 对话消息
CREATE TABLE IF NOT EXISTS message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(20) NOT NULL COMMENT '角色: user, assistant',
    content TEXT NOT NULL COMMENT '消息内容',
    sql_query TEXT COMMENT '生成的SQL查询',
    result_data JSON COMMENT '查询结果数据',
    chart_config JSON COMMENT '图表配置',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    INDEX idx_conversation (conversation_id),
    INDEX idx_role (role),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';

-- 9. 提示词模板
CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    description TEXT COMMENT '模板描述',
    template TEXT NOT NULL COMMENT '提示词模板内容',
    category VARCHAR(50) DEFAULT 'user' COMMENT '分类: system(系统), user(用户), example(示例)',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    priority INT DEFAULT 0 COMMENT '优先级，数字越大优先级越高',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category (category),
    INDEX idx_active (is_active),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板表';

-- 10. 术语库
CREATE TABLE IF NOT EXISTS glossary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    term VARCHAR(100) NOT NULL COMMENT '术语',
    definition TEXT NOT NULL COMMENT '定义',
    synonyms TEXT COMMENT '同义词(JSON数组)',
    category VARCHAR(50) COMMENT '分类',
    examples TEXT COMMENT '使用示例',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_term (term),
    INDEX idx_category (category),
    INDEX idx_active (is_active),
    FULLTEXT INDEX ft_term_definition (term, definition)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='术语库表';

-- 11. 工作区表
CREATE TABLE workspace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '工作区名称',
    description TEXT COMMENT '工作区描述',
    priority INT DEFAULT 0 COMMENT '优先级，数值越大优先级越高',
    layout_data TEXT COMMENT '工作区布局数据(JSON格式)',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_name (name),
    INDEX idx_priority (priority),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作区表';

-- 意图分析与模板系统表

-- 12. SQL 查询模板表
CREATE TABLE IF NOT EXISTS query_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    question VARCHAR(500) NOT NULL COMMENT '用户问题',
    generated_sql TEXT NOT NULL COMMENT '生成的SQL',
    datasource_id BIGINT COMMENT '数据源ID',
    score DECIMAL(3,2) DEFAULT 1.00 COMMENT '模板评分（0-1）',
    usage_count INT DEFAULT 0 COMMENT '使用次数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_question_sql (question(255), generated_sql(255)),
    INDEX idx_score (score DESC),
    INDEX idx_datasource (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统模板表';

-- 13. 模板评分记录表
CREATE TABLE IF NOT EXISTS template_rating (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    template_id BIGINT NOT NULL COMMENT '模板ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    score INT NOT NULL COMMENT '评分（1-5）',
    conversation_id BIGINT COMMENT '关联会话ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_template_user (template_id, user_id),
    INDEX idx_template_id (template_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板评分记录';

-- 14. 查询日志表
CREATE TABLE IF NOT EXISTS query_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    conversation_id BIGINT COMMENT '会话ID',
    question VARCHAR(500) NOT NULL COMMENT '用户问题',
    intent VARCHAR(50) COMMENT '识别的意图',
    intent_json TEXT COMMENT '意图JSON',
    skeleton VARCHAR(1000) COMMENT '骨架字符串',
    template_id BIGINT COMMENT '使用的模板ID',
    is_from_template BOOLEAN DEFAULT FALSE COMMENT '是否使用模板',
    generated_sql TEXT COMMENT '生成的SQL',
    execution_success BOOLEAN COMMENT '执行是否成功',
    execution_time BIGINT COMMENT '执行时间（毫秒）',
    result_count INT COMMENT '结果行数',
    rating INT COMMENT '用户评分（1-5）',
    score DECIMAL(3,2) DEFAULT 0.00 COMMENT '查询得分（0-5.00）',
    is_labeled BOOLEAN DEFAULT FALSE COMMENT '是否已标注为few-shot',
    datasource_id BIGINT COMMENT '数据源ID',
    satisfied BOOLEAN COMMENT '用户是否满意（NULL=未评价，TRUE=满意，FALSE=不满意）',
    retry_from_id BIGINT COMMENT '重试来源的query_log ID',
    source_type VARCHAR(20) COMMENT '来源类型（user_template, system_template, bfs）',
    source_template_id BIGINT COMMENT '来源模板ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_template_id (template_id),
    INDEX idx_created_at (created_at),
    INDEX idx_intent (intent),
    INDEX idx_retry_from (retry_from_id),
    INDEX idx_source (source_type, source_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询日志';

-- 16. 查询步骤执行日志表
CREATE TABLE IF NOT EXISTS query_step_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  query_log_id BIGINT NOT NULL COMMENT '关联的query_log主键',
  step_id VARCHAR(50) NOT NULL COMMENT '步骤ID（如step1、step2）',
  step_index INT NOT NULL COMMENT '步骤序号（从0开始）',
  sql_template TEXT COMMENT 'SQL模板',
  filled_sql TEXT COMMENT '填充参数后的完整SQL',
  datasource_id BIGINT COMMENT '数据源ID',
  execution_success BOOLEAN COMMENT '执行是否成功',
  result_count INT COMMENT '结果行数',
  execution_time BIGINT COMMENT '执行耗时（毫秒）',
  error_message TEXT COMMENT '错误信息',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_query_step_log_query_log_id (query_log_id),
  INDEX idx_query_step_log_step_id (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询步骤执行日志表';

-- 17. 用户查询模板表
CREATE TABLE IF NOT EXISTS user_query_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    question VARCHAR(500) NOT NULL COMMENT '用户问题',
    generated_sql TEXT NOT NULL COMMENT '生成的SQL',
    datasource_id BIGINT COMMENT '数据源ID',
    total_score INT DEFAULT 0 COMMENT '总评分（满意次数）',
    rating_count INT DEFAULT 0 COMMENT '评分次数（满意+不满意）',
    avg_score DECIMAL(3,2) DEFAULT 0.00 COMMENT '平均分',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_question_sql (question(255), generated_sql(255)),
    INDEX idx_avg_score (avg_score DESC),
    INDEX idx_datasource (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户查询模板';
