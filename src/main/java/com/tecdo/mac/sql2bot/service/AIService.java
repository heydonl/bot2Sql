package com.tecdo.mac.sql2bot.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * AI 服务（直接调用 Claude API）
 */
@Slf4j
@Service
public class AIService {

    @Value("${claude.api.base-url}")
    private String baseUrl;

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.api.model}")
    private String model;

    @Value("${claude.api.max-tokens}")
    private Integer maxTokens;

    @Value("${claude.api.temperature}")
    private Double temperature;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new Gson();

    /**
     * 调用 Claude API 生成 SQL
     */
    public String generateSQL(String systemPrompt, String userPrompt) {
        try {
            log.debug("Calling Claude API at: {}", baseUrl);
            log.debug("Using model: {}", model);

            // 构建请求体
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("max_tokens", maxTokens);
            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("system", systemPrompt);

            JsonArray messages = new JsonArray();
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", userPrompt);
            messages.add(userMessage);
            requestBody.add("messages", messages);

            String apiUrl = baseUrl + "/v1/messages";
            log.debug("API URL: {}", apiUrl);

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("Claude API error: {} - {}", response.code(), errorBody);
                    throw new RuntimeException("Claude API call failed: " + response.code() + " - " + errorBody);
                }

                String responseBody = response.body().string();
                log.debug("Claude API response: {}", responseBody);

                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                JsonArray content = jsonResponse.getAsJsonArray("content");
                if (content != null && content.size() > 0) {
                    return content.get(0).getAsJsonObject().get("text").getAsString();
                }

                throw new RuntimeException("No content in Claude API response");
            }
        } catch (IOException e) {
            log.error("Failed to call Claude API", e);
            throw new RuntimeException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 提取 SQL 语句（从 Claude 的响应中）
     */
    public String extractSQL(String response) {
        // 尝试提取 SQL 代码块
        if (response.contains("```sql")) {
            int start = response.indexOf("```sql") + 6;
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }

        // 尝试提取普通代码块
        if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            // 跳过可能的语言标识符（如 "sql\n"）
            while (start < response.length() && response.charAt(start) != '\n') {
                start++;
            }
            if (start < response.length()) {
                start++; // 跳过换行符
            }
            int end = response.indexOf("```", start);
            if (end > start) {
                return response.substring(start, end).trim();
            }
        }

        // 如果没有代码块，尝试查找 SELECT 语句
        if (response.toUpperCase().contains("SELECT")) {
            int start = response.toUpperCase().indexOf("SELECT");
            // 查找语句结束（分号或换行）
            int end = response.indexOf(";", start);
            if (end == -1) {
                // 如果没有分号，查找两个连续换行
                end = response.indexOf("\n\n", start);
            }
            if (end > start) {
                return response.substring(start, end + 1).trim();
            }
            // 如果都没找到，返回从 SELECT 开始到结尾
            return response.substring(start).trim();
        }

        // 如果都没有，返回整个响应
        return response.trim();
    }
}
