package com.tecdo.mac.sql2bot.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库初始化工具
 */
@Slf4j
public class DatabaseInitializer {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "123456";
    private static final String DATABASE_NAME = "sql2bot";

    public static void main(String[] args) {
        try {
            log.info("开始初始化数据库...");

            // 1. 创建数据库
            createDatabase();

            // 2. 执行 schema.sql
            executeSchema();

            log.info("数据库初始化完成！");
        } catch (Exception e) {
            log.error("数据库初始化失败", e);
            System.exit(1);
        }
    }

    /**
     * 创建数据库
     */
    private static void createDatabase() throws Exception {
        log.info("创建数据库: {}", DATABASE_NAME);

        try (Connection conn = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
             Statement stmt = conn.createStatement()) {

            String sql = String.format(
                "CREATE DATABASE IF NOT EXISTS %s DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
                DATABASE_NAME
            );
            stmt.execute(sql);
            log.info("数据库创建成功");
        }
    }

    /**
     * 执行 schema.sql
     */
    private static void executeSchema() throws Exception {
        log.info("执行 schema.sql...");

        String jdbcUrl = String.format(
            "jdbc:mysql://localhost:3306/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
            DATABASE_NAME
        );

        try (Connection conn = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
             Statement stmt = conn.createStatement()) {

            // 读取 schema.sql 文件
            List<String> sqlStatements = readSqlFile("/schema.sql");

            // 执行每条 SQL 语句
            for (String sql : sqlStatements) {
                if (!sql.trim().isEmpty()) {
                    log.debug("执行 SQL: {}", sql.substring(0, Math.min(50, sql.length())) + "...");
                    stmt.execute(sql);
                }
            }

            log.info("Schema 执行成功，共执行 {} 条语句", sqlStatements.size());
        }
    }

    /**
     * 读取 SQL 文件并分割成多条语句
     */
    private static List<String> readSqlFile(String filePath) throws Exception {
        List<String> statements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();

        try (InputStream is = DatabaseInitializer.class.getResourceAsStream(filePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // 跳过注释和空行
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }

                currentStatement.append(line).append(" ");

                // 如果遇到分号，表示一条语句结束
                if (line.endsWith(";")) {
                    String sql = currentStatement.toString().trim();
                    if (!sql.isEmpty()) {
                        statements.add(sql);
                    }
                    currentStatement = new StringBuilder();
                }
            }

            // 处理最后一条语句（如果没有分号结尾）
            if (currentStatement.length() > 0) {
                String sql = currentStatement.toString().trim();
                if (!sql.isEmpty()) {
                    statements.add(sql);
                }
            }
        }

        return statements;
    }
}
