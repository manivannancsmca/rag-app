package com.example.rag.service;

import com.example.rag.exception.DocumentProcessingException;
import com.example.rag.model.dto.IngestionProgress;
import com.example.rag.model.dto.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final PdfParsingService pdfParsingService;
    private final TokenTextSplitter textSplitter;
    private final VectorStore vectorStore;
    private final Executor ingestionExecutor;
    private final int batchSize;

    /** In-memory tracking of ingestion progress. Production: use Redis or DB. */
    
    private final ConcurrentHashMap<String, IngestionProgress> progressMap =
            new ConcurrentHashMap<>();

    public IngestionService(
            PdfParsingService pdfParsingService,
            TokenTextSplitter textSplitter,
            VectorStore vectorStore,
            @Qualifier("ingestionExecutor") Executor ingestionExecutor,
            @Value("${rag.ingestion.batch-size:50}") int batchSize) {
        this.pdfParsingService = pdfParsingService;
        this.textSplitter = textSplitter;
        this.vectorStore = vectorStore;
        this.ingestionExecutor = ingestionExecutor;
        this.batchSize = batchSize;
    }

    // ── Public API ───────────────────────────────────────────

    /**
     * Accepts a PDF upload, immediately returns, and kicks off async processing.
     */
    
    public UploadResponse upload(MultipartFile file) {
        String documentId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "unknown.pdf";

        progressMap.put(documentId, new IngestionProgress(
                documentId, fileName, "RECEIVED", 0, 0, 0, null, Instant.now(), null));

        log.info("Received upload: docId={}, file={}, size={} bytes",
                documentId, fileName, file.getSize());

        // Kick off async processing (byte array copy to avoid stream being closed)
        ingestAsync(documentId, file);

        return new UploadResponse(
                documentId,
                fileName,
                file.getSize(),
                0,          // totalPages — not known yet
                "PROCESSING",
                "Document accepted. Poll /api/documents/" + documentId + "/status for progress."
        );
    }

    /**
     * Returns current ingestion progress for a document.
     */
    
    public IngestionProgress getProgress(String documentId) {
        IngestionProgress progress = progressMap.get(documentId);
        if (progress == null) {
            throw new DocumentProcessingException(
                    "No ingestion record found for documentId: " + documentId);
        }
        return progress;
    }

    // ── Async ingestion pipeline ─────────────────────────────

    @Async("ingestionExecutor")
    public void ingestAsync(String documentId, MultipartFile file) {
        Instant startedAt = Instant.now();
        String fileName = file.getOriginalFilename();

        try {
            // ── Step 1: Parse PDF into per-page documents ─────
            updateProgress(documentId, "PARSING", 10, 0, 0, null);
            List<Document> pages = pdfParsingService.parseToPages(file, documentId);
            updateProgress(documentId, "PARSING", 25, pages.size(), 0, null);
            log.info("[{}] Parsed {} pages", documentId, pages.size());

            // ── Step 2: Chunk pages into token-sized pieces ───
            updateProgress(documentId, "CHUNKING", 35, pages.size(), 0, null);
            List<Document> chunks = textSplitter.apply(pages);
            log.info("[{}] Produced {} chunks", documentId, chunks.size());

            // Enrich chunk metadata with sequential index
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).getMetadata().put("chunk_index", i);
                chunks.get(i).getMetadata().put("document_id", documentId);
            }
            updateProgress(documentId, "CHUNKING", 50, pages.size(), chunks.size(), null);

            // ── Step 3: Generate embeddings + store ───────────
            //   VectorStore.add() calls the EmbeddingModel for each document
            //   and persists the result in PGVector.
            //   We batch to manage memory and provide progress feedback.
            updateProgress(documentId, "EMBEDDING", 55, pages.size(), 0, null);
            int totalStored = 0;
            for (int i = 0; i < chunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, chunks.size());
                List<Document> batch = chunks.subList(i, end);
                vectorStore.add(batch);
                totalStored += batch.size();

                int percent = 55 + (int) ((totalStored / (double) chunks.size()) * 40);
                updateProgress(documentId, "EMBEDDING", percent,
                        pages.size(), totalStored, null);
                log.debug("[{}] Stored batch {}-{}/{}", documentId, i, end, chunks.size());
            }

            // ── Done ──────────────────────────────────────────
            progressMap.put(documentId, new IngestionProgress(
                    documentId, fileName, "COMPLETED", 100,
                    pages.size(), totalStored, null, startedAt, Instant.now()));
            log.info("[{}] Ingestion COMPLETED — {} pages → {} chunks stored",
                    documentId, pages.size(), totalStored);

        } catch (Exception e) {
            log.error("[{}] Ingestion FAILED: {}", documentId, e.getMessage(), e);
            progressMap.put(documentId, new IngestionProgress(
                    documentId, fileName, "FAILED", -1,
                    0, 0, e.getMessage(), startedAt, Instant.now()));
        }
    }

    // ── Internal helpers ─────────────────────────────────────

    private void updateProgress(String documentId, String status, int percent,
                                int pages, int chunks, String error) {
        IngestionProgress current = progressMap.get(documentId);
        progressMap.put(documentId, new IngestionProgress(
                documentId,
                current != null ? current.fileName() : "unknown",
                status, percent, pages, chunks, error,
                current != null ? current.startedAt() : Instant.now(),
                null
        ));
    }
    
}
