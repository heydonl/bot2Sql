-- SQL2Bot Database Schema for H2
-- H2-compatible version without MySQL-specific syntax

-- 1. 数据源表
CREATE TABLE IF NOT EXISTS datasource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    database_name VARCHAR(100) NOT NULL,
    username VARCHAR(100),
    password VARCHAR(255),
    properties TEXT,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_datasource_name ON datasource(name);
CREATE INDEX IF NOT EXISTS idx_datasource_status ON datasource(status);

-- 2. 表模型
CREATE TABLE IF NOT EXISTS model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    datasource_id BIGINT NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    description TEXT,
    is_visible BOOLEAN DEFAULT TRUE,
    primary_key VARCHAR(100),
    properties TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (datasource_id) REFERENCES datasource(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_model_datasource_table ON model(datasource_id, table_name);
CREATE INDEX IF NOT EXISTS idx_model_datasource ON model(datasource_id);
CREATE INDEX IF NOT EXISTS idx_model_visible ON model(is_visible);

-- 3. 字段定义
CREATE TABLE IF NOT EXISTS column_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_id BIGINT NOT NULL,
    column_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    description TEXT,
    data_type VARCHAR(50),
    column_type VARCHAR(20),
    is_nullable BOOLEAN DEFAULT TRUE,
    default_value VARCHAR(255),
    properties TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (model_id) REFERENCES model(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_column_model_column ON column_definition(model_id, column_name);
CREATE INDEX IF NOT EXISTS idx_column_model ON column_definition(model_id);
CREATE INDEX IF NOT EXISTS idx_column_type ON column_definition(column_type);

-- 4. 表关系
CREATE TABLE IF NOT EXISTS relationship (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100),
    from_model_id BIGINT NOT NULL,
    to_model_id BIGINT NOT NULL,
    join_type VARCHAR(20) NOT NULL,
    join_condition TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (from_model_id) REFERENCES model(id) ON DELETE CASCADE,
    FOREIGN KEY (to_model_id) REFERENCES model(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_relationship_from_model ON relationship(from_model_id);
CREATE INDEX IF NOT EXISTS idx_relationship_to_model ON relationship(to_model_id);

-- 5. 计算字段
CREATE TABLE IF NOT EXISTS calculated_field (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    expression TEXT NOT NULL,
    description TEXT,
    data_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (model_id) REFERENCES model(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_calculated_field_model_name ON calculated_field(model_id, name);
CREATE INDEX IF NOT EXISTS idx_calculated_field_model ON calculated_field(model_id);

-- 6. 指标定义
CREATE TABLE IF NOT EXISTS metric (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    description TEXT,
    model_id BIGINT NOT NULL,
    aggregation_type VARCHAR(20) NOT NULL,
    base_column_id BIGINT,
    filter_condition TEXT,
    expression TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (model_id) REFERENCES model(id) ON DELETE CASCADE,
    FOREIGN KEY (base_column_id) REFERENCES column_definition(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_metric_name ON metric(name);
CREATE INDEX IF NOT EXISTS idx_metric_model ON metric(model_id);

-- 7. 对话会话
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    title VARCHAR(255),
    datasource_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conversation_user ON conversation(user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_datasource ON conversation(datasource_id);

-- 8. 对话消息
CREATE TABLE IF NOT EXISTS message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sql_query TEXT,
    result_data TEXT,
    chart_config TEXT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_message_conversation ON message(conversation_id);
CREATE INDEX IF NOT EXISTS idx_message_role ON message(role);
CREATE INDEX IF NOT EXISTS idx_message_created ON message(created_at);

-- 9. 提示词模板
CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    template TEXT NOT NULL,
    category VARCHAR(50) DEFAULT 'user',
    is_active BOOLEAN DEFAULT TRUE,
    priority INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prompt_category ON prompt_template(category);
CREATE INDEX IF NOT EXISTS idx_prompt_active ON prompt_template(is_active);
CREATE INDEX IF NOT EXISTS idx_prompt_priority ON prompt_template(priority);

-- 10. 术语库
CREATE TABLE IF NOT EXISTS glossary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    term VARCHAR(100) NOT NULL,
    definition TEXT NOT NULL,
    synonyms TEXT,
    category VARCHAR(50),
    examples TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_glossary_term ON glossary(term);
CREATE INDEX IF NOT EXISTS idx_glossary_category ON glossary(category);
CREATE INDEX IF NOT EXISTS idx_glossary_active ON glossary(is_active);

-- 11. 工作区
CREATE TABLE IF NOT EXISTS workspace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    priority INT DEFAULT 0,
    layout_data TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_name ON workspace(name);
CREATE INDEX IF NOT EXISTS idx_workspace_priority ON workspace(priority);
CREATE INDEX IF NOT EXISTS idx_workspace_active ON workspace(is_active);