-- SQL2Bot 初始数据
-- 插入示例数据源
INSERT INTO datasource (name, type, host, port, database_name, username, password, status) VALUES
('本地MySQL', 'mysql', 'localhost', 3306, 'ecommerce_demo', 'root', '123456', 'active'),
('测试PostgreSQL', 'postgresql', 'localhost', 5432, 'test_db', 'postgres', 'password', 'inactive');

-- 插入示例表模型
INSERT INTO model (datasource_id, table_name, display_name, description, is_visible, primary_key) VALUES
(1, 'users', '用户表', '存储用户基本信息的表', TRUE, 'id'),
(1, 'orders', '订单表', '存储订单信息的表', TRUE, 'id'),
(1, 'products', '商品表', '存储商品信息的表', TRUE, 'id'),
(1, 'order_items', '订单明细表', '存储订单商品明细的表', TRUE, 'id');

-- 插入字段定义
-- 用户表字段
INSERT INTO column_definition (model_id, column_name, display_name, description, data_type, column_type, is_nullable) VALUES
(1, 'id', '用户ID', '用户唯一标识', 'BIGINT', 'dimension', FALSE),
(1, 'username', '用户名', '用户登录名', 'VARCHAR', 'dimension', FALSE),
(1, 'email', '邮箱', '用户邮箱地址', 'VARCHAR', 'dimension', TRUE),
(1, 'phone', '手机号', '用户手机号码', 'VARCHAR', 'dimension', TRUE),
(1, 'age', '年龄', '用户年龄', 'INT', 'measure', TRUE),
(1, 'gender', '性别', '用户性别', 'VARCHAR', 'dimension', TRUE),
(1, 'city', '城市', '用户所在城市', 'VARCHAR', 'dimension', TRUE),
(1, 'created_at', '注册时间', '用户注册时间', 'TIMESTAMP', 'dimension', FALSE);

-- 订单表字段
INSERT INTO column_definition (model_id, column_name, display_name, description, data_type, column_type, is_nullable) VALUES
(2, 'id', '订单ID', '订单唯一标识', 'BIGINT', 'dimension', FALSE),
(2, 'user_id', '用户ID', '下单用户ID', 'BIGINT', 'dimension', FALSE),
(2, 'order_no', '订单号', '订单编号', 'VARCHAR', 'dimension', FALSE),
(2, 'total_amount', '订单总金额', '订单总金额', 'DECIMAL', 'measure', FALSE),
(2, 'status', '订单状态', '订单状态：pending/paid/shipped/completed/cancelled', 'VARCHAR', 'dimension', FALSE),
(2, 'payment_method', '支付方式', '支付方式：alipay/wechat/credit_card', 'VARCHAR', 'dimension', TRUE),
(2, 'created_at', '下单时间', '订单创建时间', 'TIMESTAMP', 'dimension', FALSE),
(2, 'updated_at', '更新时间', '订单最后更新时间', 'TIMESTAMP', 'dimension', FALSE);

-- 商品表字段
INSERT INTO column_definition (model_id, column_name, display_name, description, data_type, column_type, is_nullable) VALUES
(3, 'id', '商品ID', '商品唯一标识', 'BIGINT', 'dimension', FALSE),
(3, 'name', '商品名称', '商品名称', 'VARCHAR', 'dimension', FALSE),
(3, 'category', '商品分类', '商品分类', 'VARCHAR', 'dimension', TRUE),
(3, 'price', '商品价格', '商品单价', 'DECIMAL', 'measure', FALSE),
(3, 'stock', '库存数量', '商品库存数量', 'INT', 'measure', FALSE),
(3, 'brand', '品牌', '商品品牌', 'VARCHAR', 'dimension', TRUE),
(3, 'description', '商品描述', '商品详细描述', 'TEXT', 'dimension', TRUE),
(3, 'created_at', '创建时间', '商品创建时间', 'TIMESTAMP', 'dimension', FALSE);

-- 订单明细表字段
INSERT INTO column_definition (model_id, column_name, display_name, description, data_type, column_type, is_nullable) VALUES
(4, 'id', '明细ID', '订单明细唯一标识', 'BIGINT', 'dimension', FALSE),
(4, 'order_id', '订单ID', '关联订单ID', 'BIGINT', 'dimension', FALSE),
(4, 'product_id', '商品ID', '关联商品ID', 'BIGINT', 'dimension', FALSE),
(4, 'quantity', '购买数量', '商品购买数量', 'INT', 'measure', FALSE),
(4, 'unit_price', '单价', '商品单价', 'DECIMAL', 'measure', FALSE),
(4, 'total_price', '小计', '该商品的总价', 'DECIMAL', 'measure', FALSE);

-- 插入表关系
INSERT INTO relationship (name, from_model_id, to_model_id, join_type, join_condition, description) VALUES
('用户-订单关系', 2, 1, 'many_to_one', '[{"from": "user_id", "to": "id"}]', '订单表关联用户表'),
('订单-订单明细关系', 4, 2, 'many_to_one', '[{"from": "order_id", "to": "id"}]', '订单明细表关联订单表'),
('商品-订单明细关系', 4, 3, 'many_to_one', '[{"from": "product_id", "to": "id"}]', '订单明细表关联商品表');

-- 插入提示词模板
INSERT INTO prompt_template (name, description, template, category, is_active, priority) VALUES
('系统角色定义', '定义AI助手的角色和能力',
'你是一个专业的数据分析师和SQL专家，擅长将自然语言转换为准确的SQL查询。你需要：
1. 理解用户的业务需求
2. 基于提供的数据模型生成准确的SQL
3. 确保查询结果符合用户预期
4. 提供清晰的数据解释', 'system', TRUE, 100),

('SQL生成规范', 'SQL查询生成的基本规范',
'生成SQL时请遵循以下规范：
1. 使用标准SQL语法
2. 字段名使用反引号包围
3. 表名使用完整的物理表名
4. 适当使用JOIN连接相关表
5. 添加必要的WHERE条件进行数据过滤
6. 使用GROUP BY进行数据聚合
7. 使用ORDER BY进行结果排序', 'system', TRUE, 90),

('数据模型上下文', '提供当前可用的数据模型信息',
'当前可用的数据表和字段信息：
{model_context}

表关系信息：
{relationship_context}', 'system', TRUE, 80),

('查询示例', '提供常见查询的示例',
'以下是一些常见查询示例：

1. 统计用户数量：
SELECT COUNT(*) as user_count FROM users;

2. 查询订单总金额：
SELECT SUM(total_amount) as total_revenue FROM orders WHERE status = ''completed'';

3. 查询用户订单信息：
SELECT u.username, o.order_no, o.total_amount, o.created_at
FROM users u
JOIN orders o ON u.id = o.user_id
WHERE o.status = ''completed''
ORDER BY o.created_at DESC;', 'example', TRUE, 70),

('用户查询处理', '处理用户自然语言查询的模板',
'用户查询：{user_query}

请基于以上数据模型，将用户的自然语言查询转换为SQL语句。
要求：
1. 生成准确的SQL查询
2. 确保查询逻辑正确
3. 使用合适的聚合函数和过滤条件
4. 返回用户期望的结果格式', 'user', TRUE, 60);

-- 插入术语库
INSERT INTO glossary (term, definition, synonyms, category, examples, is_active) VALUES
('用户', '在系统中注册的个人或实体，具有唯一标识和基本信息', '["客户", "会员", "注册用户"]', '业务实体', '查询活跃用户数量、分析用户行为', TRUE),
('订单', '用户购买商品或服务的交易记录，包含商品信息、金额、状态等', '["交易", "购买记录", "订单记录"]', '业务实体', '统计订单总数、分析订单趋势', TRUE),
('商品', '系统中可供销售的产品或服务项目', '["产品", "货品", "物品"]', '业务实体', '查询热销商品、商品库存统计', TRUE),
('GMV', '商品交易总额，即Gross Merchandise Volume', '["交易总额", "成交总额"]', '业务指标', 'GMV = 所有订单金额之和', TRUE),
('转化率', '用户从浏览到购买的转换比例', '["转换率", "成交率"]', '业务指标', '转化率 = 下单用户数 / 访问用户数', TRUE),
('客单价', '平均每个订单的金额', '["平均订单金额", "ARPU"]', '业务指标', '客单价 = 总交易金额 / 订单数量', TRUE),
('活跃用户', '在特定时间段内有购买行为的用户', '["有效用户", "付费用户"]', '用户分类', '月活跃用户、日活跃用户', TRUE),
('复购率', '用户重复购买的比例', '["回购率", "重复购买率"]', '业务指标', '复购率 = 重复购买用户数 / 总购买用户数', TRUE);