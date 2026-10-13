package com.goota.zdocspider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
public class ExternalApiEmbeddingService implements EmbeddingService {

    private final String provider;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final int dimension;
    private final boolean fallbackEnabled;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExternalApiEmbeddingService(
            @Value("${embedding.provider:openai}") String provider,
            @Value("${embedding.api-url:https://api.openai.com/v1/embeddings}") String apiUrl,
            @Value("${embedding.api-key:}") String apiKey,
            @Value("${embedding.model:text-embedding-3-small}") String model,
            @Value("${embedding.dimension:1536}") int dimension,
            @Value("${embedding.fallback-enabled:true}") boolean fallbackEnabled) {
        this.provider = provider.trim().toLowerCase();
        this.apiUrl = apiUrl.trim();
        this.apiKey = apiKey.trim();
        this.model = model.trim();
        this.dimension = dimension;
        this.fallbackEnabled = fallbackEnabled;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[dimension];
        }

        // Trim leading/trailing whitespace and limit text length to avoid token limit errors
        String trimmedText = text.trim();
        if (trimmedText.length() > 8000) {
            trimmedText = trimmedText.substring(0, 8000);
        }

        if ("mock".equals(provider)) {
            return generateDeterministicVector(trimmedText);
        }

        try {
            if ("openai".equals(provider)) {
                if (apiKey.isEmpty()) {
                    log.debug("No OpenAI API key provided. Using fallback deterministic embedding.");
                    return generateDeterministicVector(trimmedText);
                }
                return fetchOpenAiEmbedding(trimmedText);
            } else if ("ollama".equals(provider)) {
                return fetchOllamaEmbedding(trimmedText);
            } else {
                log.warn("Unknown embedding provider '{}'. Falling back to deterministic embedding.", provider);
                return generateDeterministicVector(trimmedText);
            }
        } catch (Exception e) {
            log.warn("Failed to generate embedding via {} ({}): {}", provider, apiUrl, e.getMessage());
            if (fallbackEnabled) {
                log.info("Generating fallback deterministic embedding for text of length {}", trimmedText.length());
                return generateDeterministicVector(trimmedText);
            }
            throw new RuntimeException("Embedding generation failed: " + e.getMessage(), e);
        }
    }

    private float[] fetchOpenAiEmbedding(String text) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("input", text);
        body.put("model", model);

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API returned status " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            JsonNode embeddingNode = data.get(0).path("embedding");
            if (embeddingNode.isArray()) {
                float[] vector = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vector[i] = (float) embeddingNode.get(i).asDouble();
                }
                return vector;
            }
        }
        throw new RuntimeException("Unexpected response format from OpenAI embedding API: " + response.body());
    }

    private float[] fetchOllamaEmbedding(String text) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("prompt", text);
        body.put("model", model);

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama API returned status " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode embeddingNode = root.path("embedding");
        if (embeddingNode.isArray()) {
            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            return vector;
        }
        throw new RuntimeException("Unexpected response format from Ollama embedding API: " + response.body());
    }

    /**
     * Generates a deterministic normalized unit vector based on the SHA-256 hash of the input text.
     * Ensures identical text produces identical vectors, while keeping vectors normalized for cosine / L2 distance.
     */
    private float[] generateDeterministicVector(String text) {
        float[] vector = new float[dimension];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (int i = 0; i < 8 && i < hash.length; i++) {
                seed = (seed << 8) | (hash[i] & 0xFF);
            }
            Random random = new Random(seed);
            double sumSquares = 0.0;
            for (int i = 0; i < dimension; i++) {
                float val = (float) (random.nextGaussian());
                vector[i] = val;
                sumSquares += (double) val * val;
            }
            // Normalize to unit vector
            float norm = (float) Math.sqrt(sumSquares);
            if (norm > 0) {
                for (int i = 0; i < dimension; i++) {
                    vector[i] /= norm;
                }
            }
        } catch (Exception e) {
            log.error("Error creating deterministic vector", e);
        }
        return vector;
    }
}
