package com.example.rag.model.dto;

import java.time.Instant;

public record IngestionProgress(
        String documentId,
        String fileName,
        String status,       // RECEIVED | PARSING | CHUNKING | EMBEDDING | COMPLETED | FAILED
        int percentComplete,
        int totalPages,
        int chunksCreated,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {}