package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.DataSource;
import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.dto.ColumnInfo;
import com.tecdo.mac.sql2bot.dto.ImportResult;
import com.tecdo.mac.sql2bot.dto.TableImportRequest;
import com.tecdo.mac.sql2bot.dto.TableInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据库表结构发现服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaDiscoveryService {

    private final DataSourceService dataSourceService;
    private final ModelService modelService;
    private final ColumnDefinitionService columnDefinitionService;
    private final DatabaseService databaseService;

    /**
     * 列出数据源下的所有数据库
     */
    public List<String> listDatabases(Long datasourceId) throws Exception {
        DataSource dataSource = dataSourceService.getById(datasourceId);
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource not found: " + datasourceId);
        }

        // 连接到 MySQL 服务器（不指定数据库）
        String jdbcUrl = buildJdbcUrlWithoutDatabase(dataSource);
        List<String> databases = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, dataSource.getUsername(), dataSource.getPassword())) {
            try (ResultSet rs = conn.getMetaData().getCatalogs()) {
                while (rs.next()) {
                    String dbName = rs.getString("TABLE_CAT");
                    // 过滤掉系统数据库
                    if (!isSystemDatabase(dbName)) {
                        databases.add(dbName);
                    }
                }
            }
        }

        log.info("Found {} databases in datasource: {}", databases.size(), dataSource.getName());
        return databases;
    }

    /**
     * 判断是否为系统数据库
     */
    private boolean isSystemDatabase(String dbName) {
        return "information_schema".equalsIgnoreCase(dbName)
                || "mysql".equalsIgnoreCase(dbName)
                || "performance_schema".equalsIgnoreCase(dbName)
                || "sys".equalsIgnoreCase(dbName);
    }

    /**
     * 构建不指定数据库的 JDBC URL
     */
    private String buildJdbcUrlWithoutDatabase(DataSource dataSource) {
        return String.format("jdbc:%s://%s:%d/",
                dataSource.getType(),
                dataSource.getHost(),
                dataSource.getPort());
    }

    /**
     * 发现数据源中的所有表结构
     */
    public List<TableInfo> discoverTables(Long datasourceId) throws Exception {
        DataSource dataSource = dataSourceService.getById(datasourceId);
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource not found: " + datasourceId);
        }

        // 使用配置的数据库名
        return discoverTablesFromDatabases(datasourceId, List.of(dataSource.getDatabaseName()));
    }

    /**
     * 从指定的多个数据库中发现表结构
     */
    public List<TableInfo> discoverTablesFromDatabases(Long datasourceId, List<String> databaseNames) throws Exception {
        DataSource dataSource = dataSourceService.getById(datasourceId);
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource not found: " + datasourceId);
        }

        List<TableInfo> allTables = new ArrayList<>();

        for (String databaseName : databaseNames) {
            String jdbcUrl = buildJdbcUrlForDatabase(dataSource, databaseName);

            try (Connection conn = DriverManager.getConnection(jdbcUrl, dataSource.getUsername(), dataSource.getPassword())) {
                DatabaseMetaData metaData = conn.getMetaData();

                // 获取所有表
                try (ResultSet rs = metaData.getTables(databaseName, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        // 从结果集读取实际的数据库名，过滤掉跨库返回的表
                        String actualDatabase = rs.getString("TABLE_CAT");
                        if (!databaseName.equalsIgnoreCase(actualDatabase)) {
                            continue;
                        }

                        TableInfo tableInfo = new TableInfo();
                        tableInfo.setTableName(rs.getString("TABLE_NAME"));
                        tableInfo.setTableComment(rs.getString("REMARKS"));
                        tableInfo.setTableType(rs.getString("TABLE_TYPE"));
                        tableInfo.setDatabaseName(actualDatabase);

                        // 获取表的字段信息
                        List<ColumnInfo> columns = discoverColumns(conn, databaseName, tableInfo.getTableName());
                        tableInfo.setColumns(columns);

                        allTables.add(tableInfo);
                        log.info("Discovered table: {}.{} with {} columns", databaseName, tableInfo.getTableName(), columns.size());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to discover tables from database: {}", databaseName, e);
                // 继续处理其他数据库
            }
        }

        log.info("Total discovered {} tables from {} databases in datasource: {}",
                allTables.size(), databaseNames.size(), dataSource.getName());
        return allTables;
    }

    /**
     * 构建指定数据库的 JDBC URL
     */
    private String buildJdbcUrlForDatabase(DataSource dataSource, String databaseName) {
        return String.format("jdbc:%s://%s:%d/%s",
                dataSource.getType(),
                dataSource.getHost(),
                dataSource.getPort(),
                databaseName);
    }

    /**
     * 发现表的字段信息
     */
    private List<ColumnInfo> discoverColumns(Connection conn, String databaseName, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();

        // 获取主键信息
        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet pkRs = metaData.getPrimaryKeys(databaseName, null, tableName)) {
            while (pkRs.next()) {
                primaryKeys.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        // 获取字段信息
        try (ResultSet rs = metaData.getColumns(databaseName, null, tableName, "%")) {
            while (rs.next()) {
                ColumnInfo columnInfo = new ColumnInfo();
                String columnName = rs.getString("COLUMN_NAME");
                columnInfo.setColumnName(columnName);
                columnInfo.setDataType(rs.getString("TYPE_NAME"));
                columnInfo.setColumnComment(rs.getString("REMARKS"));
                columnInfo.setIsNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                columnInfo.setDefaultValue(rs.getString("COLUMN_DEF"));
                columnInfo.setColumnSize(rs.getInt("COLUMN_SIZE"));
                columnInfo.setIsPrimaryKey(primaryKeys.contains(columnName));

                columns.add(columnInfo);
            }
        }

        return columns;
    }

    /**
     * 导入表结构到语义模型（带数据库名）
     */
    @Transactional
    public ImportResult importTablesWithDatabase(Long datasourceId, List<TableImportRequest> tables) throws Exception {
        try {
            if (tables == null || tables.isEmpty()) {
                return ImportResult.error("没有要导入的表");
            }

            // 收集所有需要的数据库名
            List<String> databaseNames = tables.stream()
                    .map(TableImportRequest::getDatabaseName)
                    .distinct()
                    .toList();

            // 批量获取或创建 database 记录
            java.util.Map<String, com.tecdo.mac.sql2bot.domain.Database> databaseMap =
                    databaseService.batchGetOrCreate(datasourceId, databaseNames);

            // 构建 (databaseName, tableName) -> TableImportRequest 的映射
            java.util.Map<String, TableImportRequest> tableRequestMap = new java.util.HashMap<>();
            for (TableImportRequest table : tables) {
                String key = table.getDatabaseName() + ":" + table.getTableName();
                tableRequestMap.put(key, table);
            }

            // 从指定的数据库发现表结构
            List<TableInfo> allTables = discoverTablesFromDatabases(datasourceId, databaseNames);

            // 过滤需要导入的表
            List<TableInfo> tablesToImport = allTables.stream()
                    .filter(t -> {
                        String key = t.getDatabaseName() + ":" + t.getTableName();
                        return tableRequestMap.containsKey(key);
                    })
                    .toList();

            // 获取已存在的表名集合
            List<Model> existingModels = modelService.listByDatasourceId(datasourceId);
            Set<String> existingTableNames = existingModels.stream()
                    .map(Model::getTableName)
                    .collect(java.util.stream.Collectors.toSet());

            // 收集需要创建的 Model
            List<Model> modelsToCreate = new ArrayList<>();
            List<TableInfo> tableInfosToProcess = new ArrayList<>();

            for (TableInfo tableInfo : tablesToImport) {
                // 跳过已存在的表
                if (existingTableNames.contains(tableInfo.getTableName())) {
                    log.info("Table {} already exists, skipping import", tableInfo.getTableName());
                    continue;
                }

                // 从 Map 中获取 database
                com.tecdo.mac.sql2bot.domain.Database database = databaseMap.get(tableInfo.getDatabaseName());
                if (database == null) {
                    log.warn("Database not found for table: {}, skipping", tableInfo.getTableName());
                    continue;
                }

                // 创建 Model 对象
                Model model = new Model();
                model.setDatasourceId(datasourceId);
                model.setDatabaseId(database.getId());
                model.setTableName(tableInfo.getTableName());
                model.setDisplayName(tableInfo.getTableName());
                model.setDescription(tableInfo.getTableComment());
                model.setIsVisible(true);

                // 查找主键
                String primaryKey = tableInfo.getColumns().stream()
                        .filter(ColumnInfo::getIsPrimaryKey)
                        .map(ColumnInfo::getColumnName)
                        .findFirst()
                        .orElse(null);
                model.setPrimaryKey(primaryKey);

                modelsToCreate.add(model);
                tableInfosToProcess.add(tableInfo);
            }

            // 批量插入 Model
            if (!modelsToCreate.isEmpty()) {
                modelService.batchCreate(modelsToCreate);
                log.info("Batch created {} models", modelsToCreate.size());
            }

            int tableCount = modelsToCreate.size();
            int columnCount = 0;

            // 批量创建字段定义
            List<ColumnDefinition> allColumnDefinitions = new ArrayList<>();
            for (int i = 0; i < modelsToCreate.size(); i++) {
                Model model = modelsToCreate.get(i);
                TableInfo tableInfo = tableInfosToProcess.get(i);

                for (ColumnInfo columnInfo : tableInfo.getColumns()) {
                    ColumnDefinition columnDef = new ColumnDefinition();
                    columnDef.setModelId(model.getId());
                    columnDef.setColumnName(columnInfo.getColumnName());
                    columnDef.setDisplayName(columnInfo.getColumnName());
                    columnDef.setDescription(columnInfo.getColumnComment());
                    columnDef.setDataType(columnInfo.getDataType());
                    columnDef.setIsNullable(columnInfo.getIsNullable());
                    columnDef.setDefaultValue(columnInfo.getDefaultValue());

                    // 自动判断字段类型（维度或度量）
                    columnDef.setColumnType(inferColumnType(columnInfo));

                    allColumnDefinitions.add(columnDef);
                }
            }

            // 批量插入字段定义
            if (!allColumnDefinitions.isEmpty()) {
                columnDefinitionService.batchCreate(allColumnDefinitions);
                columnCount = allColumnDefinitions.size();
                log.info("Batch created {} column definitions", columnCount);
            }

            return ImportResult.success(tableCount, columnCount);
        } catch (Exception e) {
            log.error("Failed to import tables with database", e);
            return ImportResult.error(e.getMessage());
        }
    }

    /**
     * 导入表结构到语义模型
     */
    @Transactional
    public ImportResult importTables(Long datasourceId, List<String> tableNames) throws Exception {
        try {
            // 发现所有表
            List<TableInfo> allTables = discoverTables(datasourceId);

            // 过滤需要导入的表
            List<TableInfo> tablesToImport = allTables;
            if (tableNames != null && !tableNames.isEmpty()) {
                tablesToImport = allTables.stream()
                        .filter(t -> tableNames.contains(t.getTableName()))
                        .toList();
            }

            // 获取已存在的表名集合
            List<Model> existingModels = modelService.listByDatasourceId(datasourceId);
            Set<String> existingTableNames = existingModels.stream()
                    .map(Model::getTableName)
                    .collect(java.util.stream.Collectors.toSet());

            // 收集所有需要的数据库名
            List<String> databaseNames = tablesToImport.stream()
                    .map(TableInfo::getDatabaseName)
                    .distinct()
                    .toList();

            // 批量获取或创建 database 记录
            java.util.Map<String, com.tecdo.mac.sql2bot.domain.Database> databaseMap =
                    databaseService.batchGetOrCreate(datasourceId, databaseNames);

            // 收集需要创建的 Model
            List<Model> modelsToCreate = new ArrayList<>();
            // 用于存储 TableInfo 和对应的 Model，后续创建字段时使用
            List<TableInfo> tableInfosToProcess = new ArrayList<>();

            for (TableInfo tableInfo : tablesToImport) {
                // 跳过已存在的表
                if (existingTableNames.contains(tableInfo.getTableName())) {
                    log.info("Table {} already exists, skipping import", tableInfo.getTableName());
                    continue;
                }

                // 从 Map 中获取 database
                com.tecdo.mac.sql2bot.domain.Database database = databaseMap.get(tableInfo.getDatabaseName());
                if (database == null) {
                    log.warn("Database not found for table: {}, skipping", tableInfo.getTableName());
                    continue;
                }

                // 创建 Model 对象
                Model model = new Model();
                model.setDatasourceId(datasourceId);
                model.setDatabaseId(database.getId());
                model.setTableName(tableInfo.getTableName());
                model.setDisplayName(tableInfo.getTableName());
                model.setDescription(tableInfo.getTableComment());
                model.setIsVisible(true);

                // 查找主键
                String primaryKey = tableInfo.getColumns().stream()
                        .filter(ColumnInfo::getIsPrimaryKey)
                        .map(ColumnInfo::getColumnName)
                        .findFirst()
                        .orElse(null);
                model.setPrimaryKey(primaryKey);

                modelsToCreate.add(model);
                tableInfosToProcess.add(tableInfo);
            }

            // 批量插入 Model
            if (!modelsToCreate.isEmpty()) {
                modelService.batchCreate(modelsToCreate);
                log.info("Batch created {} models", modelsToCreate.size());
            }

            int tableCount = modelsToCreate.size();
            int columnCount = 0;

            // 批量创建字段定义
            List<ColumnDefinition> allColumnDefinitions = new ArrayList<>();
            for (int i = 0; i < modelsToCreate.size(); i++) {
                Model model = modelsToCreate.get(i);
                TableInfo tableInfo = tableInfosToProcess.get(i);

                for (ColumnInfo columnInfo : tableInfo.getColumns()) {
                    ColumnDefinition columnDef = new ColumnDefinition();
                    columnDef.setModelId(model.getId());
                    columnDef.setColumnName(columnInfo.getColumnName());
                    columnDef.setDisplayName(columnInfo.getColumnName());
                    columnDef.setDescription(columnInfo.getColumnComment());
                    columnDef.setDataType(columnInfo.getDataType());
                    columnDef.setIsNullable(columnInfo.getIsNullable());
                    columnDef.setDefaultValue(columnInfo.getDefaultValue());

                    // 自动判断字段类型（维度或度量）
                    columnDef.setColumnType(inferColumnType(columnInfo));

                    allColumnDefinitions.add(columnDef);
                }
            }

            // 批量插入字段定义
            if (!allColumnDefinitions.isEmpty()) {
                columnDefinitionService.batchCreate(allColumnDefinitions);
                columnCount = allColumnDefinitions.size();
                log.info("Batch created {} column definitions", columnCount);
            }

            return ImportResult.success(tableCount, columnCount);
        } catch (Exception e) {
            log.error("Failed to import tables", e);
            return ImportResult.error(e.getMessage());
        }
    }

    /**
     * 推断字段类型（维度或度量）
     */
    private String inferColumnType(ColumnInfo columnInfo) {
        String dataType = columnInfo.getDataType().toUpperCase();

        // 数值类型通常是度量
        if (dataType.contains("INT") || dataType.contains("DECIMAL") ||
            dataType.contains("FLOAT") || dataType.contains("DOUBLE") ||
            dataType.contains("NUMERIC")) {
            // 但如果是 ID 字段，则是维度
            if (columnInfo.getColumnName().toLowerCase().endsWith("_id") ||
                columnInfo.getColumnName().equalsIgnoreCase("id")) {
                return "dimension";
            }
            return "measure";
        }

        // 其他类型默认是维度
        return "dimension";
    }

    /**
     * 构建 JDBC URL
     */
    private String buildJdbcUrl(DataSource dataSource) {
        return String.format("jdbc:%s://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai",
                dataSource.getType(), dataSource.getHost(), dataSource.getPort(), dataSource.getDatabaseName());
    }
}
