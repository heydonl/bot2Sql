-- 调整 query_template 表结构，添加支持 RAG 和用户反馈的字段
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS name VARCHAR(200) COMMENT '模板名称';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS description TEXT COMMENT '模板描述';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS template_source VARCHAR(50) DEFAULT 'manual' COMMENT '模板来源: manual(手动), bfs_generated(BFS生成), llm_generated(LLM生成)';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS parent_template_id BIGINT COMMENT '父模板ID（用于BFS生成的模板）';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS bfs_context JSON COMMENT 'BFS发现的表上下文信息';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS similarity_threshold DECIMAL(3,2) DEFAULT 0.70 COMMENT '相似度阈值';
ALTER TABLE query_template ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用';

-- 新增用户反馈表
CREATE TABLE IF NOT EXISTS query_feedback (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    conversation_id BIGINT COMMENT '会话ID',
    query_log_id BIGINT NOT NULL COMMENT '查询日志ID',
    template_id BIGINT COMMENT '使用的模板ID',
    question VARCHAR(500) NOT NULL COMMENT '用户问题',
    generated_sql TEXT COMMENT '生成的SQL',
    rating INT NOT NULL COMMENT '用户评分: 1=满意, 0=不满意',
    feedback_reason VARCHAR(500) COMMENT '反馈原因',
    template_similarity DECIMAL(5,4) COMMENT '模板相似度',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_query_log_id (query_log_id),
    INDEX idx_template_id (template_id),
    INDEX idx_rating (rating),
    FOREIGN KEY (query_log_id) REFERENCES query_log(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询反馈表';

-- BFS 表发现记录表
CREATE TABLE IF NOT EXISTS bfs_discovery_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    feedback_id BIGINT NOT NULL COMMENT '反馈ID',
    question VARCHAR(500) NOT NULL COMMENT '用户问题',
    discovered_tables JSON NOT NULL COMMENT '发现的表列表',
    table_relationships JSON COMMENT '表关系信息',
    few_shot_examples JSON COMMENT '使用的few-shot示例',
    generated_template_id BIGINT COMMENT '生成的模板ID',
    success BOOLEAN DEFAULT FALSE COMMENT '是否成功生成模板',
    error_message TEXT COMMENT '错误信息',
    execution_time BIGINT COMMENT '执行时间（毫秒）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_feedback_id (feedback_id),
    INDEX idx_success (success),
    FOREIGN KEY (feedback_id) REFERENCES query_feedback(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BFS表发现日志';

-- 2026-03-23: drop datasource_id from query_template (now embedded in sql_template JSON array)
ALTER TABLE query_template DROP COLUMN IF EXISTS datasource_id;