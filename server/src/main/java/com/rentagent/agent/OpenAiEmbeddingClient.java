package com.rentagent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 规范的 Embedding 模型客户端（自研实现，对接 bge-m3 等词嵌入模型）：
 * POST {baseUrl}/embeddings，请求体 {"model": "...", "input": [...]}
 */
@Slf4j
public class OpenAiEmbeddingClient implements EmbeddingModelClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiEmbeddingClient(String baseUrl, String apiKey, String model, Duration timeout) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "bge-m3" : model.trim();
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public boolean available() {
        return !baseUrl.isBlank() && !apiKey.isBlank();
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        List<float[]> list = embedBatch(List.of(text));
        return list.isEmpty() ? new float[0] : list.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty() || !available()) {
            return Collections.emptyList();
        }
        String endpoint;
        if (baseUrl.endsWith("/embeddings")) {
            endpoint = baseUrl;
        } else if (baseUrl.endsWith("/v1")) {
            endpoint = baseUrl + "/embeddings";
        } else {
            endpoint = baseUrl + "/v1/embeddings";
        }

        try {
            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model", model);
            reqBody.put("input", texts);
            String json = mapper.writeValueAsString(reqBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.warn("Embedding API 调用失败 HTTP {}: {}", resp.statusCode(), resp.body());
                throw new LlmHttpException(resp.statusCode(), "Embedding API 调用失败: " + resp.body());
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                log.warn("Embedding API 返回格式异常，无 data 数组: {}", resp.body());
                return Collections.emptyList();
            }

            List<float[]> result = new ArrayList<>(dataNode.size());
            for (JsonNode item : dataNode) {
                JsonNode embNode = item.get("embedding");
                if (embNode != null && embNode.isArray()) {
                    float[] vec = new float[embNode.size()];
                    for (int i = 0; i < embNode.size(); i++) {
                        vec[i] = (float) embNode.get(i).asDouble();
                    }
                    result.add(vec);
                }
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Embedding API 调用被中断: {}", e.getMessage());
            throw new RuntimeException("Embedding interrupted", e);
        } catch (IOException e) {
            log.warn("Embedding API 网络调用失败: {}", e.getMessage());
            throw new RuntimeException("Embedding I/O error: " + e.getMessage(), e);
        }
    }
}

