package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.ColumnDefinition;
import com.tecdo.mac.sql2bot.domain.DataSource;
import com.tecdo.mac.sql2bot.domain.Model;
import com.tecdo.mac.sql2bot.dto.ColumnInfo;
import com.tecdo.mac.sql2bot.dto.ImportResult;
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

    /**
     * 发现数据源中的所有表结构
     */
    public List<TableInfo> discoverTables(Long datasourceId) throws Exception {
        DataSource dataSource = dataSourceService.getById(datasourceId);
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource not found: " + datasourceId);
        }

        String jdbcUrl = buildJdbcUrl(dataSource);
        List<TableInfo> tables = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, dataSource.getUsername(), dataSource.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();

            // 获取所有表
            try (ResultSet rs = metaData.getTables(dataSource.getDatabaseName(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    TableInfo tableInfo = new TableInfo();
                    tableInfo.setTableName(rs.getString("TABLE_NAME"));
                    tableInfo.setTableComment(rs.getString("REMARKS"));
                    tableInfo.setTableType(rs.getString("TABLE_TYPE"));

                    // 获取表的字段信息
                    List<ColumnInfo> columns = discoverColumns(conn, dataSource.getDatabaseName(), tableInfo.getTableName());
                    tableInfo.setColumns(columns);

                    tables.add(tableInfo);
                    log.info("Discovered table: {} with {} columns", tableInfo.getTableName(), columns.size());
                }
            }
        }

        log.info("Total discovered {} tables from datasource: {}", tables.size(), dataSource.getName());
        return tables;
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

            int tableCount = 0;
            int columnCount = 0;

            // 导入每个表
            for (TableInfo tableInfo : tablesToImport) {
                // 创建 Model
                Model model = new Model();
                model.setDatasourceId(datasourceId);
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

                modelService.create(model);
                tableCount++;

                // 创建字段定义
                List<ColumnDefinition> columnDefinitions = new ArrayList<>();
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

                    columnDefinitions.add(columnDef);
                }

                columnDefinitionService.batchCreate(columnDefinitions);
                columnCount += columnDefinitions.size();

                log.info("Imported table: {} with {} columns", tableInfo.getTableName(), columnDefinitions.size());
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
