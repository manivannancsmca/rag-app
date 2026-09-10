package com.example.rag.service;

import com.example.rag.exception.QueryProcessingException;
import com.example.rag.model.dto.ChatResponse;
import com.example.rag.model.dto.SourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final int defaultTopK;
    private final double similarityThreshold;

    public RagService(
            VectorStore vectorStore,
            ChatClient chatClient,
            @Value("${rag.retrieval.top-k:5}") int defaultTopK,
            @Value("${rag.retrieval.similarity-threshold:0.40}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.defaultTopK = defaultTopK;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * Full RAG pipeline: retrieve → augment → generate.
     */
    public ChatResponse query(String question, String documentId, Integer topK) {
        int effectiveTopK = (topK != null) ? topK : defaultTopK;

        log.info("RAG query: question='{}', docFilter={}, topK={}",
                truncate(question, 80), documentId, effectiveTopK);

        try {
            // ── Step 1: Retrieve relevant chunks from PGVector ──
            SearchRequest.Builder searchBuilder = SearchRequest.builder()
                    .query(question)
                    .topK(effectiveTopK)
                    .similarityThreshold(similarityThreshold);

            // Optional: scope to a single document
            if (documentId != null && !documentId.isBlank()) {
                searchBuilder.filterExpression(
                        "document_id == '%s'".formatted(documentId));
            }

            List<Document> relevantChunks = vectorStore.similaritySearch(
                    searchBuilder.build());

            log.info("Retrieved {} relevant chunks", relevantChunks.size());

            // ── Step 2: Build context string from retrieved docs ─
            String context = buildContext(relevantChunks);

            // ── Step 3: Build source references ─────────────────
            List<SourceReference> sources = relevantChunks.stream()
                    .map(this::toSourceReference)
                    .toList();

            // ── Step 4: Construct prompt and call LLM ───────────
            String userPrompt = buildUserPrompt(question, context);

            String answer = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            log.info("RAG answer generated ({} chars)", answer != null ? answer.length() : 0);

            return new ChatResponse(
                    answer,
                    sources,
                    "llama3.1",
                    Instant.now()
            );

        } catch (QueryProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new QueryProcessingException(
                    "Failed to process query: " + e.getMessage(), e);
        }
    }

    // ── Private helpers ──────────────────────────────────────

    private String buildContext(List<Document> chunks) {
        if (chunks.isEmpty()) {
            return "[No relevant document sections found for this question.]";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> meta = chunk.getMetadata();

            sb.append("--- Source Section %d ---".formatted(i + 1));
            sb.append("\nDocument: %s".formatted(
                    meta.getOrDefault("source", "unknown")));
            sb.append("\nPage: %s".formatted(
                    meta.getOrDefault("page_number", "?")));
            sb.append("\n\n");
            sb.append(chunk.getText());
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private String buildUserPrompt(String question, String context) {
        return """
                Use the following context to answer the question.

                ==================== CONTEXT ====================
                %s
                ==================================================

                Question: %s

                Answer the question based on the context above.
                If the context doesn't contain the answer, say:
                "I could not find relevant information in the uploaded documents."
                """.formatted(context, question);
    }

    private SourceReference toSourceReference(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String snippet = doc.getText();
        if (snippet.length() > 300) {
            snippet = snippet.substring(0, 300) + "...";
        }
        return new SourceReference(
                (String) meta.getOrDefault("source", "unknown"),
                (int) meta.getOrDefault("page_number", 0),
                0.0,  // similarity score not exposed by default VectorStore API
                snippet
        );
    }

    private String truncate(String s, int maxLen) {
        return (s != null && s.length() > maxLen)
                ? s.substring(0, maxLen) + "..."
                : s;
    }
}