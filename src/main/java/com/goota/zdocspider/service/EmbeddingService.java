package com.goota.zdocspider.service;

public interface EmbeddingService {

    /**
     * Generate float vector embedding for the given text.
     *
     * @param text input text
     * @return float array embedding
     */
    float[] generateEmbedding(String text);

    /**
     * Get the configured embedding dimension (e.g. 1536).
     *
     * @return vector dimension
     */
    int getDimension();
}
