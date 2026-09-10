package com.example.rag.model.dto;

public record SourceReference(
        String documentName,
        int pageNumber,
        double similarity,
        String snippet
) {}