package com.example.rag.model.dto;

import java.time.Instant;
import java.util.List;

public record ChatResponse(
        String answer,
        List<SourceReference> sources,
        String model,
        Instant timestamp
) {}