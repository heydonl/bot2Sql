-- 创建工作区表
CREATE TABLE IF NOT EXISTS workspace (
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

-- 插入默认工作区
INSERT INTO workspace (name, description, priority, is_active)
VALUES ('默认工作区', '系统默认工作区', 100, TRUE)
ON DUPLICATE KEY UPDATE name=name;
