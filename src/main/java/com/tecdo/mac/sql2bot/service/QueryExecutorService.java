package com.tecdo.mac.sql2bot.service;

import com.tecdo.mac.sql2bot.domain.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 查询执行服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryExecutorService {

    private final DataSourceService dataSourceService;

    /**
     * 执行 SQL 查询
     */
    public List<Map<String, Object>> executeQuery(Long datasourceId, String sql) throws SQLException {
        DataSource dataSource = dataSourceService.getById(datasourceId);
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource not found: " + datasourceId);
        }

        // 验证 SQL 安全性
        validateSQL(sql);

        String jdbcUrl = buildJdbcUrl(dataSource);
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, dataSource.getUsername(), dataSource.getPassword());
             Statement stmt = conn.createStatement()) {

            log.info("Executing SQL: {}", sql);
            long startTime = System.currentTimeMillis();

            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    results.add(row);
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Query executed successfully, returned {} rows in {} ms", results.size(), executionTime);
        }

        return results;
    }

    /**
     * 验证 SQL 安全性
     */
    private void validateSQL(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL cannot be empty");
        }

        String upperSQL = sql.toUpperCase().trim();

        // 只允许 SELECT 查询
        if (!upperSQL.startsWith("SELECT")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed");
        }

        // 禁止的关键字（使用词边界匹配，避免误判字段名）
        String[] forbiddenKeywords = {
            "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER",
            "TRUNCATE", "GRANT", "REVOKE", "EXEC", "EXECUTE"
        };

        for (String keyword : forbiddenKeywords) {
            // 使用正则表达式匹配完整的关键词，避免匹配到字段名中的部分字符
            // \b 表示词边界，确保只匹配完整的关键词
            String pattern = "\\b" + keyword + "\\b";
            if (upperSQL.matches(".*" + pattern + ".*")) {
                throw new IllegalArgumentException("SQL contains forbidden keyword: " + keyword);
            }
        }

        // 检查是否包含多条语句
        if (sql.contains(";") && !sql.trim().endsWith(";")) {
            throw new IllegalArgumentException("Multiple SQL statements are not allowed");
        }
    }

    /**
     * 构建 JDBC URL
     */
    private String buildJdbcUrl(DataSource dataSource) {
        return String.format("jdbc:%s://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai",
                dataSource.getType(), dataSource.getHost(), dataSource.getPort(), dataSource.getDatabaseName());
    }
}
