package com.example.rag.controller;

import com.example.rag.model.dto.IngestionProgress;
import com.example.rag.model.dto.UploadResponse;
import com.example.rag.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "PDF upload and ingestion management")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private final IngestionService ingestionService;

    public DocumentController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Operation(
            summary = "Upload a PDF document",
            description = "Accepts a PDF file (up to 200 MB, ~500 pages). "
                    + "Processing runs asynchronously. Use the returned documentId "
                    + "to poll ingestion status.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Document accepted for processing",
                    content = @Content(schema = @Schema(implementation = UploadResponse.class))),
            @ApiResponse(responseCode = "413", description = "File too large",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "Unprocessable — invalid PDF",
                    content = @Content)
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadPdf(
            @Parameter(description = "PDF file to ingest", required = true)
            @RequestParam("file") MultipartFile file) {

        log.info("Received file upload: name={}, size={} bytes, type={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        UploadResponse response = ingestionService.upload(file);
        return ResponseEntity.accepted().body(response);
    }

    @Operation(
            summary = "Get ingestion status",
            description = "Poll the ingestion pipeline for a previously uploaded document. "
                    + "Status transitions: RECEIVED → PARSING → CHUNKING → EMBEDDING → COMPLETED / FAILED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress returned",
                    content = @Content(schema = @Schema(implementation = IngestionProgress.class))),
            @ApiResponse(responseCode = "404", description = "Document ID not found",
                    content = @Content)
    })
    @GetMapping("/{documentId}/status")
    public ResponseEntity<IngestionProgress> getStatus(
            @Parameter(description = "UUID of the uploaded document", required = true,
                    example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @PathVariable String documentId) {

        IngestionProgress progress = ingestionService.getProgress(documentId);
        return ResponseEntity.ok(progress);
    }
    
}
