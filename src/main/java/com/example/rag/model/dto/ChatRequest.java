package com.example.rag.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 2000) String question,
        String documentId,            // optional: scope retrieval to a single document
        Integer topK                  // optional: override default top-k
) {
    public ChatRequest {
        if (topK != null && (topK < 1 || topK > 20)) {
            throw new IllegalArgumentException("topK must be between 1 and 20");
        }
    }
}