package com.tecdo.mac.sql2bot.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Embedding 服务
 * 使用 OpenAI API 生成文本的向量表示
 */
@Slf4j
@Service
public class EmbeddingService {

    @Value("${embedding.api.base-url}")
    private String baseUrl;

    @Value("${embedding.api.key}")
    private String apiKey;

    @Value("${embedding.dimension:1024}")
    private int dimension;

    private final OkHttpClient httpClient;
    private final Gson gson;

    public EmbeddingService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    /**
     * 生成文本的 embedding 向量
     */
    public float[] generateEmbedding(String text) {
        try {
            log.debug("Generating embedding for text: {}", text.substring(0, Math.min(50, text.length())));

            // 构建请求
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("input", text);
            requestBody.addProperty("model", "text-embedding-ada-002");

            String url = baseUrl + "/v1/embeddings";
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(
                            requestBody.toString(),
                            MediaType.parse("application/json")
                    ))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            // 发送请求
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Embedding API request failed: {}", response.code());
                    throw new RuntimeException("Embedding API request failed: " + response.code());
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                // 提取 embedding 向量
                JsonArray data = jsonResponse.getAsJsonArray("data");
                if (data.size() == 0) {
                    throw new RuntimeException("No embedding data returned");
                }

                JsonArray embeddingArray = data.get(0).getAsJsonObject()
                        .getAsJsonArray("embedding");

                float[] embedding = new float[embeddingArray.size()];
                for (int i = 0; i < embeddingArray.size(); i++) {
                    embedding[i] = embeddingArray.get(i).getAsFloat();
                }

                log.debug("Generated embedding with dimension: {}", embedding.length);
                return embedding;
            }

        } catch (IOException e) {
            log.error("Failed to generate embedding", e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * 批量生成 embedding
     */
    public List<float[]> generateEmbeddings(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        return embeddings;
    }

    /**
     * 计算两个向量的余弦相似度
     */
    public double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
