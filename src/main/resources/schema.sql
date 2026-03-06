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

-- 2. 表模型
CREATE TABLE IF NOT EXISTS model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    datasource_id BIGINT NOT NULL COMMENT '数据源ID',
    table_name VARCHAR(100) NOT NULL COMMENT '物理表名',
    display_name VARCHAR(100) COMMENT '显示名称',
    description TEXT COMMENT '业务描述',
    is_visible BOOLEAN DEFAULT TRUE COMMENT '是否对查询开放',
    primary_key VARCHAR(100) COMMENT '主键字段名',
    properties TEXT COMMENT '额外属性(JSON格式)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE,
    UNIQUE KEY uk_datasource_table (datasource_id, table_name),
    INDEX idx_datasource (datasource_id),
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
