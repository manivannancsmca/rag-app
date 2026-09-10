package com.example.rag.service;

import com.example.rag.exception.DocumentProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfParsingService {

    private static final Logger log = LoggerFactory.getLogger(PdfParsingService.class);

    /**
     * Extracts one {@link Document} per page from a PDF file.
     * Each document carries metadata: source (filename), page_number.
     */
    public List<Document> parseToPages(MultipartFile file, String documentId) {
        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "unknown.pdf";

        log.info("Starting PDF parse: file={}, size={} bytes", fileName, file.getSize());

        try (PDDocument pdDocument = Loader.loadPDF(file.getBytes())) {
            int totalPages = pdDocument.getNumberOfPages();
            log.info("PDF loaded: {} pages", totalPages);

            PDFTextStripper stripper = new PDFTextStripper();
            List<Document> pages = new ArrayList<>(totalPages);

            for (int pageNum = 1; pageNum <= totalPages; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String text = stripper.getText(pdDocument);

                if (text != null && !text.isBlank()) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("document_id", documentId);
                    metadata.put("source", fileName);
                    metadata.put("page_number", pageNum);

                    pages.add(new Document(text.strip(), metadata));
                } else {
                    log.debug("Page {} is blank — skipped", pageNum);
                }
            }

            log.info("Parsed {} non-blank pages from {}", pages.size(), fileName);
            return pages;

        } catch (IOException e) {
            throw new DocumentProcessingException(
                    "Failed to parse PDF '%s': %s".formatted(fileName, e.getMessage()), e);
        }
    }
}