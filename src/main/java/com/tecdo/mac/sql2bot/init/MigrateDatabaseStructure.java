package com.tecdo.mac.sql2bot.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库结构迁移：添加 database 表和 model.database_id 字段
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class MigrateDatabaseStructure implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            // 1. 创建 database 表
            createDatabaseTable();

            // 2. 为 model 表添加 database_id 字段
            addDatabaseIdToModel();

            // 3. 从现有数据迁移：为每个 model 创建对应的 database 记录
            migrateExistingData();

            // 4. 为 query_log 表添加用户反馈字段
            addUserFeedbackColumnsToQueryLog();

            // 5. 创建 user_query_template 表
            createUserQueryTemplateTable();

            // 6. 为 query_log 表添加 error_message 字段
            addErrorMessageToQueryLog();

            // 7. 创建 query_step_log 表
            createQueryStepLogTable();

            // 8. 迁移 query_template 表结构
            migrateQueryTemplateStructure();

            log.info("Database structure migration completed successfully");
        } catch (Exception e) {
            log.error("Database structure migration failed", e);
        }
    }

    private void createDatabaseTable() {
        try {
            // 检查 database 表是否已存在
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'database'",
                    Integer.class
            );

            if (count != null && count > 0) {
                log.info("Table 'database' already exists, skipping creation");
                return;
            }

            // 创建 database 表
            String sql = """
                CREATE TABLE `database` (
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库表'
                """;

            jdbcTemplate.execute(sql);
            log.info("Created table 'database'");
        } catch (Exception e) {
            log.warn("Failed to create database table: {}", e.getMessage());
        }
    }

    private void addDatabaseIdToModel() {
        try {
            // 检查 database_id 字段是否已存在
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'model' AND column_name = 'database_id'",
                    Integer.class
            );

            if (count != null && count > 0) {
                log.info("Column 'database_id' already exists in model table, skipping");
                return;
            }

            // 添加 database_id 字段
            jdbcTemplate.execute("ALTER TABLE model ADD COLUMN database_id BIGINT COMMENT '数据库ID' AFTER datasource_id");
            log.info("Added column 'database_id' to model table");

            // 添加外键约束
            jdbcTemplate.execute("ALTER TABLE model ADD CONSTRAINT fk_model_database FOREIGN KEY (database_id) REFERENCES `database`(id) ON DELETE SET NULL");
            log.info("Added foreign key constraint for database_id");

            // 添加索引
            jdbcTemplate.execute("ALTER TABLE model ADD INDEX idx_database (database_id)");
            log.info("Added index for database_id");

            // 删除旧的唯一约束
            try {
                jdbcTemplate.execute("ALTER TABLE model DROP INDEX uk_datasource_table");
                log.info("Dropped old unique constraint uk_datasource_table");
            } catch (Exception e) {
                log.warn("Failed to drop old constraint: {}", e.getMessage());
            }

            // 添加新的唯一约束
            jdbcTemplate.execute("ALTER TABLE model ADD UNIQUE KEY uk_database_table (database_id, table_name)");
            log.info("Added new unique constraint uk_database_table");

        } catch (Exception e) {
            log.warn("Failed to add database_id column: {}", e.getMessage());
        }
    }

    private void migrateExistingData() {
        try {
            // 查询所有现有的 model 记录
            var models = jdbcTemplate.queryForList(
                    "SELECT id, datasource_id, table_name FROM model WHERE database_id IS NULL"
            );

            if (models.isEmpty()) {
                log.info("No models need migration");
                return;
            }

            log.info("Migrating {} models to use database table", models.size());

            for (var model : models) {
                Long modelId = ((Number) model.get("id")).longValue();
                Long datasourceId = ((Number) model.get("datasource_id")).longValue();

                // 获取数据源的默认数据库名
                String databaseName = jdbcTemplate.queryForObject(
                        "SELECT database_name FROM datasource WHERE id = ?",
                        String.class,
                        datasourceId
                );

                if (databaseName == null) {
                    log.warn("Datasource {} has no database_name, skipping model {}", datasourceId, modelId);
                    continue;
                }

                // 获取或创建 database 记录
                Long databaseId = getOrCreateDatabase(datasourceId, databaseName);

                // 更新 model 的 database_id
                jdbcTemplate.update(
                        "UPDATE model SET database_id = ? WHERE id = ?",
                        databaseId,
                        modelId
                );
            }

            log.info("Successfully migrated {} models", models.size());
        } catch (Exception e) {
            log.error("Failed to migrate existing data", e);
        }
    }

    private Long getOrCreateDatabase(Long datasourceId, String databaseName) {
        // 先查询是否已存在
        var existing = jdbcTemplate.queryForList(
                "SELECT id FROM `database` WHERE datasource_id = ? AND database_name = ?",
                datasourceId,
                databaseName
        );

        if (!existing.isEmpty()) {
            return ((Number) existing.get(0).get("id")).longValue();
        }

        // 不存在则创建
        jdbcTemplate.update(
                "INSERT INTO `database` (datasource_id, database_name, display_name) VALUES (?, ?, ?)",
                datasourceId,
                databaseName,
                databaseName
        );

        // 获取新创建的 ID
        Long id = jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()",
                Long.class
        );

        log.info("Created database record: datasourceId={}, databaseName={}, id={}", datasourceId, databaseName, id);
        return id;
    }

    private void addUserFeedbackColumnsToQueryLog() {
        String[] columns = {"satisfied", "retry_from_id", "source_type", "source_template_id"};
        String[] addSqls = {
            "ALTER TABLE query_log ADD COLUMN satisfied BOOLEAN COMMENT '用户是否满意'",
            "ALTER TABLE query_log ADD COLUMN retry_from_id BIGINT COMMENT '重试来源的query_log ID'",
            "ALTER TABLE query_log ADD COLUMN source_type VARCHAR(20) COMMENT '来源类型'",
            "ALTER TABLE query_log ADD COLUMN source_template_id BIGINT COMMENT '来源模板ID'"
        };

        for (int i = 0; i < columns.length; i++) {
            String col = columns[i];
            try {
                Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'query_log' AND column_name = ?",
                    Integer.class, col
                );
                if (count != null && count > 0) {
                    log.info("Column '{}' already exists in query_log, skipping", col);
                } else {
                    jdbcTemplate.execute(addSqls[i]);
                    log.info("Added column '{}' to query_log", col);
                }
            } catch (Exception e) {
                log.warn("Failed to add column '{}' to query_log: {}", col, e.getMessage());
            }
        }

        // 添加索引
        try {
            jdbcTemplate.execute("ALTER TABLE query_log ADD INDEX idx_retry_from (retry_from_id)");
        } catch (Exception e) {
            log.debug("Index idx_retry_from may already exist: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("ALTER TABLE query_log ADD INDEX idx_source (source_type, source_template_id)");
        } catch (Exception e) {
            log.debug("Index idx_source may already exist: {}", e.getMessage());
        }
    }

    private void createUserQueryTemplateTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'user_query_template'",
                Integer.class
            );
            if (count != null && count > 0) {
                log.info("Table 'user_query_template' already exists, skipping creation");
                return;
            }

            jdbcTemplate.execute("""
                CREATE TABLE user_query_template (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
                    question VARCHAR(500) NOT NULL COMMENT '用户问题',
                    generated_sql TEXT NOT NULL COMMENT '生成的SQL',
                    datasource_id BIGINT COMMENT '数据源ID',
                    total_score INT DEFAULT 0 COMMENT '总评分（满意次数）',
                    rating_count INT DEFAULT 0 COMMENT '评分次数',
                    avg_score DECIMAL(3,2) DEFAULT 0.00 COMMENT '平均分',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    UNIQUE KEY uk_question_sql (question(255), generated_sql(255)),
                    INDEX idx_avg_score (avg_score DESC),
                    INDEX idx_datasource (datasource_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户查询模板'
                """);
            log.info("Created table 'user_query_template'");
        } catch (Exception e) {
            log.warn("Failed to create user_query_template table: {}", e.getMessage());
        }
    }

    private void addErrorMessageToQueryLog() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'query_log' AND column_name = 'error_message'",
                Integer.class
            );
            if (count != null && count > 0) {
                log.info("Column 'error_message' already exists in query_log, skipping");
                return;
            }

            jdbcTemplate.execute("ALTER TABLE query_log ADD COLUMN error_message TEXT COMMENT 'SQL执行错误信息'");
            log.info("Added column 'error_message' to query_log");
        } catch (Exception e) {
            log.warn("Failed to add column 'error_message' to query_log: {}", e.getMessage());
        }
    }

    private void createQueryStepLogTable() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'query_step_log'",
                Integer.class
            );
            if (count != null && count > 0) {
                log.info("Table 'query_step_log' already exists, skipping creation");
                return;
            }

            jdbcTemplate.execute("""
                CREATE TABLE query_step_log (
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询步骤执行日志表'
                """);
            log.info("Created table 'query_step_log'");
        } catch (Exception e) {
            log.warn("Failed to create query_step_log table: {}", e.getMessage());
        }
    }

    /**
     * 迁移 query_template 表结构
     * 1. 备份现有数据
     * 2. 迁移 intent_few_shot 数据到 query_template
     * 3. 删除旧字段，添加新字段
     * 4. 数据迁移
     * 5. 删除 intent_few_shot 表
     */
    private void migrateQueryTemplateStructure() {
        try {
            log.info("开始迁移 query_template 表结构");

            // 步骤1：检查是否已经迁移过（通过检查 question 字段是否存在）
            Integer questionColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'query_template' AND column_name = 'question'",
                Integer.class
            );
            if (questionColumnCount != null && questionColumnCount > 0) {
                log.info("query_template 表已经迁移过，跳过");
                return;
            }

            // 步骤2：备份现有数据
            backupQueryTemplate();

            // 步骤3：迁移 intent_few_shot 数据到 query_template
            migrateIntentFewShotData();

            // 步骤4：调整表结构
            alterQueryTemplateStructure();

            // 步骤5：数据迁移（将 sql_template 复制到 generated_sql）
            migrateQueryTemplateData();

            // 步骤6：删除临时字段
            dropTemporaryColumns();

            // 步骤7：删除 intent_few_shot 表
            dropIntentFewShotTable();

            log.info("query_template 表结构迁移完成");
        } catch (Exception e) {
            log.error("迁移 query_template 表结构失败", e);
        }
    }

    private void backupQueryTemplate() {
        try {
            // 检查备份表是否已存在
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'query_template_backup'",
                Integer.class
            );
            if (count != null && count > 0) {
                log.info("备份表 query_template_backup 已存在，跳过备份");
                return;
            }

            jdbcTemplate.execute("CREATE TABLE query_template_backup AS SELECT * FROM query_template");
            log.info("已备份 query_template 表到 query_template_backup");
        } catch (Exception e) {
            log.warn("备份 query_template 表失败: {}", e.getMessage());
        }
    }

    private void migrateIntentFewShotData() {
        try {
            // 检查 intent_few_shot 表是否存在
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'intent_few_shot'",
                Integer.class
            );
            if (count == null || count == 0) {
                log.info("intent_few_shot 表不存在，跳过数据迁移");
                return;
            }

            // 迁移数据
            jdbcTemplate.execute("""
                INSERT INTO query_template (skeleton, sql_template, entity, intent, score, usage_count, created_at, updated_at)
                SELECT
                    COALESCE(skeleton, '') as skeleton,
                    CONCAT('-- Intent: ', intent, '\\n-- Question: ', question, '\\n', intent_json) as sql_template,
                    '' as entity,
                    intent,
                    1.0 as score,
                    0 as usage_count,
                    created_at,
                    updated_at
                FROM intent_few_shot
                WHERE is_active = TRUE
                """);
            log.info("已迁移 intent_few_shot 数据到 query_template");
        } catch (Exception e) {
            log.warn("迁移 intent_few_shot 数据失败: {}", e.getMessage());
        }
    }

    private void alterQueryTemplateStructure() {
        try {
            log.info("开始调整 query_template 表结构");

            // 添加新字段
            jdbcTemplate.execute("ALTER TABLE query_template ADD COLUMN question VARCHAR(500) NULL AFTER id");
            log.info("已添加 question 字段");

            jdbcTemplate.execute("ALTER TABLE query_template ADD COLUMN generated_sql TEXT NULL AFTER question");
            log.info("已添加 generated_sql 字段");

            jdbcTemplate.execute("ALTER TABLE query_template ADD COLUMN datasource_id BIGINT NULL AFTER generated_sql");
            log.info("已添加 datasource_id 字段");

        } catch (Exception e) {
            log.warn("调整 query_template 表结构失败: {}", e.getMessage());
        }
    }

    private void migrateQueryTemplateData() {
        try {
            // 将 example_question 复制到 question
            jdbcTemplate.execute("""
                UPDATE query_template
                SET question = COALESCE(example_question, CONCAT('查询', id))
                WHERE question IS NULL
                """);
            log.info("已迁移 example_question 到 question");

            // 将 sql_template 复制到 generated_sql
            jdbcTemplate.execute("""
                UPDATE query_template
                SET generated_sql = COALESCE(sql_template, '')
                WHERE generated_sql IS NULL OR generated_sql = ''
                """);
            log.info("已迁移 sql_template 到 generated_sql");

            // 设置 question 和 generated_sql 为 NOT NULL
            jdbcTemplate.execute("ALTER TABLE query_template MODIFY COLUMN question VARCHAR(500) NOT NULL");
            jdbcTemplate.execute("ALTER TABLE query_template MODIFY COLUMN generated_sql TEXT NOT NULL");
            log.info("已设置 question 和 generated_sql 为 NOT NULL");

        } catch (Exception e) {
            log.warn("迁移 query_template 数据失败: {}", e.getMessage());
        }
    }

    private void dropTemporaryColumns() {
        try {
            log.info("开始删除旧字段");

            // 删除旧字段
            String[] columnsToDelete = {
                "skeleton", "sql_template", "entity", "intent",
                "supported_dimensions", "supported_metrics", "parameters",
                "example_question", "example_intent_json", "rating_count"
            };

            for (String column : columnsToDelete) {
                try {
                    jdbcTemplate.execute("ALTER TABLE query_template DROP COLUMN " + column);
                    log.info("已删除字段: {}", column);
                } catch (Exception e) {
                    log.debug("删除字段 {} 失败（可能已不存在）: {}", column, e.getMessage());
                }
            }

            // 添加唯一索引
            try {
                jdbcTemplate.execute("ALTER TABLE query_template ADD UNIQUE KEY uk_question_sql (question(255), generated_sql(255))");
                log.info("已添加唯一索引 uk_question_sql");
            } catch (Exception e) {
                log.debug("添加唯一索引失败（可能已存在）: {}", e.getMessage());
            }

            // 添加 datasource_id 索引
            try {
                jdbcTemplate.execute("ALTER TABLE query_template ADD INDEX idx_datasource (datasource_id)");
                log.info("已添加索引 idx_datasource");
            } catch (Exception e) {
                log.debug("添加索引失败（可能已存在）: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.warn("删除旧字段失败: {}", e.getMessage());
        }
    }

    private void dropIntentFewShotTable() {
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS intent_few_shot");
            log.info("已删除 intent_few_shot 表");
        } catch (Exception e) {
            log.warn("删除 intent_few_shot 表失败: {}", e.getMessage());
        }
    }
}
