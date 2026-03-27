package com.tecdo.mac.sql2bot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public class CreateTablesTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void createTables() {
        String[] sqls = {
            "CREATE TABLE IF NOT EXISTS query_template (id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID', skeleton VARCHAR(1000) NOT NULL COMMENT '骨架字符串', sql_template TEXT NOT NULL COMMENT 'SQL模板', entity VARCHAR(100) COMMENT '实体类型', intent VARCHAR(50) NOT NULL COMMENT '意图类型', supported_dimensions JSON COMMENT '支持的维度字段列表', supported_metrics JSON COMMENT '支持的指标列表', parameters JSON COMMENT '参数定义列表', example_question VARCHAR(500) COMMENT '示例问题', example_intent_json TEXT COMMENT '示例意图JSON', score DECIMAL(3,2) DEFAULT 0.00 COMMENT '平均评分', rating_count INT DEFAULT 0 COMMENT '评分次数', usage_count INT DEFAULT 0 COMMENT '使用次数', datasource_id BIGINT COMMENT '关联数据源', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', UNIQUE KEY uk_skeleton (skeleton(255)), INDEX idx_intent (intent), INDEX idx_entity (entity), INDEX idx_score (score DESC)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL查询模板'",

            "CREATE TABLE IF NOT EXISTS template_rating (id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID', template_id BIGINT NOT NULL COMMENT '模板ID', user_id BIGINT NOT NULL COMMENT '用户ID', score INT NOT NULL COMMENT '评分', conversation_id BIGINT COMMENT '关联会话ID', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', UNIQUE KEY uk_template_user (template_id, user_id), INDEX idx_template_id (template_id), INDEX idx_user_id (user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板评分记录'",

            "CREATE TABLE IF NOT EXISTS intent_few_shot (id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID', intent VARCHAR(50) NOT NULL COMMENT '目标意图类型', question VARCHAR(500) NOT NULL COMMENT '示例问题', intent_json TEXT NOT NULL COMMENT '正确的意图JSON', skeleton VARCHAR(1000) COMMENT '骨架字符串', is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用', datasource_id BIGINT COMMENT '关联数据源', created_by BIGINT COMMENT '创建人', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间', INDEX idx_intent (intent), INDEX idx_active (is_active), INDEX idx_datasource (datasource_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图分类示例'",

            "CREATE TABLE IF NOT EXISTS query_log (id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID', user_id BIGINT NOT NULL COMMENT '用户ID', conversation_id BIGINT COMMENT '会话ID', question VARCHAR(500) NOT NULL COMMENT '用户问题', intent VARCHAR(50) COMMENT '识别的意图', intent_json TEXT COMMENT '意图JSON', skeleton VARCHAR(1000) COMMENT '骨架字符串', template_id BIGINT COMMENT '使用的模板ID', is_from_template BOOLEAN DEFAULT FALSE COMMENT '是否使用模板', generated_sql TEXT COMMENT '生成的SQL', execution_success BOOLEAN COMMENT '执行是否成功', execution_time BIGINT COMMENT '执行时间', result_count INT COMMENT '结果行数', rating INT COMMENT '用户评分', is_labeled BOOLEAN DEFAULT FALSE COMMENT '是否已标注', datasource_id BIGINT COMMENT '数据源ID', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', INDEX idx_user_id (user_id), INDEX idx_template_id (template_id), INDEX idx_created_at (created_at), INDEX idx_intent (intent)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询日志')"
        };

        for (int i = 0; i < sqls.length; i++) {
            jdbcTemplate.execute(sqls[i]);
            System.out.println("Table " + (i+1) + " created successfully");
        }
        System.out.println("All 4 tables created successfully!");
    }
}
