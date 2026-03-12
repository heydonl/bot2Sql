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
}
