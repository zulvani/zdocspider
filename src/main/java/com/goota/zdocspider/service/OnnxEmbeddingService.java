package com.goota.zdocspider.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link EmbeddingService} utilizing Spring AI's ONNX Transformer
 * embedding model (sentence-transformers/all-MiniLM-L6-v2 by default).
 */
@Slf4j
@Service
@Primary
public class OnnxEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final int configuredDimension;

    public OnnxEmbeddingService(
            EmbeddingModel embeddingModel,
            @Value("${embedding.dimension:384}") int configuredDimension) {
        this.embeddingModel = embeddingModel;
        this.configuredDimension = configuredDimension;
        log.info("Initialized OnnxEmbeddingService with model: {}, dimension: {}",
                embeddingModel.getClass().getSimpleName(), getDimension());
    }

    @Override
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[getDimension()];
        }

        String trimmedText = text.trim();
        if (trimmedText.length() > 8000) {
            trimmedText = trimmedText.substring(0, 8000);
        }

        try {
            float[] embedding = embeddingModel.embed(trimmedText);
            if (embedding != null && embedding.length > 0) {
                return embedding;
            }
        } catch (Exception e) {
            log.error("Failed to generate ONNX transformer embedding: {}", e.getMessage(), e);
            throw new RuntimeException("ONNX transformer embedding generation failed: " + e.getMessage(), e);
        }

        return new float[getDimension()];
    }

    @Override
    public int getDimension() {
        if (embeddingModel != null) {
            try {
                int dim = embeddingModel.dimensions();
                if (dim > 0) {
                    return dim;
                }
            } catch (Exception ignored) {
            }
        }
        return configuredDimension;
    }
}
