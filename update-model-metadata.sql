-- 更新 datasource 表的元数据
UPDATE model SET 
  display_name = '数据源',
  description = '存储数据库连接配置信息，包括MySQL、PostgreSQL等数据源'
WHERE table_name = 'datasource';

-- 更新 model 表的元数据
UPDATE model SET 
  display_name = '表模型',
  description = '存储数据表的语义模型定义，包括表名、字段、关系等信息'
WHERE table_name = 'model';
