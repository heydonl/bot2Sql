-- 给relationship表添加workspace_id字段
ALTER TABLE relationship
ADD COLUMN workspace_id BIGINT COMMENT '工作区ID',
ADD INDEX idx_workspace (workspace_id),
ADD FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE;

-- 将现有的关系关联到默认工作区
UPDATE relationship
SET workspace_id = (SELECT id FROM workspace WHERE name = '默认工作区' LIMIT 1)
WHERE workspace_id IS NULL;
