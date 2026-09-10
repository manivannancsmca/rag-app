package com.example.rag.model.dto;

public record UploadResponse(
        String documentId,
        String fileName,
        long fileSizeBytes,
        int totalPages,
        String status,
        String message
) {}