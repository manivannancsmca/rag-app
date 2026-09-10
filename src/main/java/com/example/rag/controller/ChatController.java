package com.example.rag.controller;

import com.example.rag.model.dto.ChatRequest;
import com.example.rag.model.dto.ChatResponse;
import com.example.rag.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "RAG-powered question answering")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @Operation(
            summary = "Ask a question",
            description = "Sends a question through the RAG pipeline: retrieves the most "
                    + "relevant document chunks from the vector store, provides them as context "
                    + "to the Llama model via Ollama, and returns a grounded answer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer generated",
                    content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Query processing failure",
                    content = @Content)
    })
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Question with optional document filter and top-k override",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ChatRequest.class)))
            @Valid @RequestBody ChatRequest request) {

        log.info("Chat request: question='{}', docFilter={}",
                request.question(), request.documentId());

        ChatResponse response = ragService.query(
                request.question(),
                request.documentId(),
                request.topK()
        );

        return ResponseEntity.ok(response);
    }
}