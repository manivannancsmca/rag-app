# RAG Application — PostgreSQL + PgVector + Ollama + Spring AI

![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen?logo=springboot)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-blue?logo=postgresql)
![pgvector](https://img.shields.io/badge/pgvector-enabled-orange)
![Ollama](https://img.shields.io/badge/Ollama-llama3.1-black)
![License](https://img.shields.io/badge/license-MIT-green)

A local-first **Retrieval-Augmented Generation (RAG)** application that ingests
PDF documents, embeds them with `nomic-embed-text`, stores vectors in
PostgreSQL via `pgvector`, and answers questions using `llama3.1` through Ollama.

---

## 📋 Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Step-by-Step Running Instructions](#2-step-by-step-running-instructions)
3. [Example `curl` Requests](#3-example-curl-requests)
4. [Logging & Observability](#4-logging--observability)
5. [Key Design Decisions](#5-key-design-decisions)
6. [Verification Checklist](#6-verification-checklist)
7. [References](#7-references)

---

## 1. Prerequisites

| Requirement        | Version / Notes                                      |
|--------------------|------------------------------------------------------|
| Docker & Compose   | Latest stable                                        |
| Java               | 25 (or 21+)                                          |
| Maven              | 3.9+ (wrapper `./mvnw` included)                     |
| Disk space         | ~10 GB for models                                    |
|                    | • Llama 3.1 ≈ 4.7 GB                                 |
|                    | • nomic-embed-text ≈ 274 MB                          |

---

## 2. Step-by-Step Running Instructions

### Step 1 — Start Infrastructure

```bash
# From the project root
docker compose up -d

# Verify both containers are healthy
docker compose ps
```

### Step 2 — Pull Ollama Models

```bash
# Pull the chat model
docker exec -it rag-ollama ollama pull llama3.1

# Pull the embedding model
docker exec -it rag-ollama ollama pull nomic-embed-text

# Verify models are available
curl http://localhost:11434/api/tags | python3 -m json.tool
```

### Step 3 — Verify PostgreSQL

```bash
docker exec -it rag-postgres psql -U postgres -d ragdb -c "\dx"
# Should show: vector extension
```

### Step 4 — Build the Application

```bash
./mvnw clean package -DskipTests
```

### Step 5 — Run the Application

```bash
# Option A: Maven
./mvnw spring-boot:run

# Option B: JAR
java -XX:+UseZGC -Xms512m -Xmx2g \
     -jar target/rag-app-0.0.1-SNAPSHOT.jar
```

Wait for the Spring Boot banner and the log line:

```text
Started RagApplication in X.XX seconds
```

### Step 6 — Verify Health

```bash
curl http://localhost:8080/actuator/health | python3 -m json.tool
```

---

## 3. Example `curl` Requests

### 3.1 Upload a PDF Document

```bash
curl -X POST http://localhost:8080/api/documents/upload \
     -F "file=@/path/to/your-500-page-document.pdf" \
     | python3 -m json.tool
```

**Response:**

```json
{
    "documentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "fileName": "your-500-page-document.pdf",
    "fileSizeBytes": 52428800,
    "totalPages": 0,
    "status": "PROCESSING",
    "message": "Document accepted. Poll /api/documents/a1b2c3d4.../status for progress."
}
```

### 3.2 Poll Ingestion Status

```bash
curl http://localhost:8080/api/documents/a1b2c3d4-e5f6-7890-abcd-ef1234567890/status \
     | python3 -m json.tool
```

**Response (while processing):**

```json
{
    "documentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "fileName": "your-500-page-document.pdf",
    "status": "EMBEDDING",
    "percentComplete": 72,
    "totalPages": 500,
    "chunksCreated": 340,
    "errorMessage": null,
    "startedAt": "2026-09-08T12:00:00Z",
    "completedAt": null
}
```

**Response (completed):**

```json
{
    "documentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "fileName": "your-500-page-document.pdf",
    "status": "COMPLETED",
    "percentComplete": 100,
    "totalPages": 500,
    "chunksCreated": 612,
    "errorMessage": null,
    "startedAt": "2026-09-08T12:00:00Z",
    "completedAt": "2026-09-08T12:04:32Z"
}
```

### 3.3 Ask a Question (RAG)

```bash
curl -X POST http://localhost:8080/api/chat \
     -H "Content-Type: application/json" \
     -d '{
           "question": "What is the main thesis of Chapter 3?"
         }' \
     | python3 -m json.tool
```

**Response:**

```json
{
    "answer": "Based on the document, Chapter 3 argues that...",
    "sources": [
        {
            "documentName": "your-500-page-document.pdf",
            "pageNumber": 87,
            "similarity": 0.0,
            "snippet": "In Chapter 3, we explore the fundamental..."
        },
        {
            "documentName": "your-500-page-document.pdf",
            "pageNumber": 89,
            "similarity": 0.0,
            "snippet": "The evidence presented in the previous sections..."
        }
    ],
    "model": "llama3.1",
    "timestamp": "2026-09-08T12:10:00Z"
}
```

### 3.4 Ask a Question Scoped to a Specific Document

```bash
curl -X POST http://localhost:8080/api/chat \
     -H "Content-Type: application/json" \
     -d '{
           "question": "Summarize the executive summary.",
           "documentId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
           "topK": 3
         }' \
     | python3 -m json.tool
```

---

## 4. Logging & Observability

### Already Configured

- All service classes use **SLF4J** with structured messages.
- **Document IDs** serve as correlation keys throughout the pipeline.
- **DEBUG**-level logging for batch progress; **INFO** for milestones; **ERROR** for failures.

### Production Enhancements to Consider

| Area                 | Tool / Approach                                                       |
|----------------------|-----------------------------------------------------------------------|
| Distributed tracing  | Micrometer Tracing + Zipkin or OpenTelemetry                          |
| Metrics              | Micrometer + Prometheus (auto-configured via Actuator)                |
| Structured logging   | Logback with JSON encoder + ELK/Loki                                  |
| Alerting             | Prometheus Alertmanager on ingestion failure rate                     |
| Rate limiting        | Bucket4j or Resilience4j on `/api/chat`                               |
| Async job queue      | Replace `@Async` + `ConcurrentHashMap` with Spring Batch or a message queue |

---

## 5. Key Design Decisions

| Decision                                | Rationale                                                                 |
|-----------------------------------------|---------------------------------------------------------------------------|
| PDFBox directly (not Tika)              | Lighter, PDF-only scope, explicit control over page extraction            |
| `TokenTextSplitter` (800 tokens, overlap) | Token-aware chunking respects LLM context window better than character-based splitting |
| `@Async` ingestion                      | A 500-page PDF takes 2–5 min to embed; blocking HTTP would time out       |
| `ConcurrentHashMap` for status          | Simple, no extra dependency; replace with Redis/DB for multi-instance     |
| HNSW index on PGVector                  | ~10× faster than IVFFlat for datasets under 10M vectors; good default     |
| `nomic-embed-text` (768d)               | Best quality/speed ratio for local Ollama embeddings                      |
| `llama3.1` (8B default)                 | Good balance of quality and speed; switch to 70B for production if GPU allows |
| `similarityThreshold = 0.40`            | Conservative enough to filter noise; tune up (0.6+) to be stricter       |
| `filterExpression` for per-doc scoping  | Allows users to focus retrieval on a specific uploaded document           |

---

## 6. Verification Checklist

To verify against actual **Spring AI 2.0.0 / Spring Boot 4.1.1**:

- [ ] Check if `TokenTextSplitter` constructor signature has changed.
- [ ] Confirm `SearchRequest.builder()` API (may be `SearchRequest.defaults()` + setters).
- [ ] Verify PGVector auto-config property path (`spring.ai.vectorstore.pgvector.*`).
- [ ] Check if `ChatClient.Builder` is still the injectable bean name.

> **Note:** The patterns — `VectorStore.add()`, `VectorStore.similaritySearch()`,
> `ChatClient.prompt().user().call().content()` — are foundational to Spring AI
> and will remain stable.

---

## 7. References

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [Ollama Model Library](https://ollama.com/library)
- [Apache PDFBox](https://pdfbox.apache.org/)
- [HNSW Paper](https://arxiv.org/abs/1603.09320)

---

## 📝 License

MIT © Your Name


---

# PostgreSQL + PgVector Setup

![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-blue?logo=postgresql)
![pgvector](https://img.shields.io/badge/pgvector-vector--extension-orange)
![License](https://img.shields.io/badge/license-MIT-green)

A step-by-step guide to set up the PostgreSQL `vector` extension, create
the `vector_store` table, add an HNSW index for similarity search, and
verify the setup.

---

## 📋 Table of Contents

1. [Create the Extension](#1-create-the-extension)
2. [Create the Table](#2-create-the-table)
3. [Create the Index](#3-create-the-index)
4. [Verify the Setup](#4-verify-the-setup)

---

## 1. Create the Extension

Connect to your PostgreSQL database and run:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

> **Note:** Requires PostgreSQL 11+ and the `pgvector` extension installed
> on the host system.

---

## 2. Create the Table

Create the `vector_store` table:

```sql
CREATE TABLE IF NOT EXISTS vector_store (
    id        VARCHAR(255) PRIMARY KEY,
    content   TEXT,
    metadata  JSONB DEFAULT '{}'::jsonb,
    embedding VECTOR(768)
);
```

### Table Schema

| Column      | Type           | Description                     |
|-------------|----------------|---------------------------------|
| `id`        | `VARCHAR(255)` | Unique identifier               |
| `content`   | `TEXT`         | Text content                    |
| `metadata`  | `JSONB`        | Additional metadata             |
| `embedding` | `VECTOR(768)`  | 768-dimensional vector embedding |

---

## 3. Create the Index

Add an HNSW index for fast approximate nearest-neighbor search:

```sql
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
ON vector_store
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

| Parameter         | Value | Purpose                                 |
|-------------------|-------|-----------------------------------------|
| `m`               | 16    | Max connections per layer               |
| `ef_construction` | 64    | Build-time search breadth               |
| `vector_cosine_ops` | —   | Cosine distance operator class          |

> **Tip:** Use `vector_l2_ops` for Euclidean distance or
> `vector_ip_ops` for inner product.

---

## 4. Verify the Setup

Confirm the extension, table, and index exist:

```sql
-- Check extension
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';

-- Check table
\d vector_store

-- Check index
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'vector_store';
```

Expected output: the `vector` extension is listed, the `vector_store`
table has four columns, and the HNSW index is present.

---

## 🚀 Quick Test Query

Insert and search a sample vector:

```sql
INSERT INTO vector_store (id, content, metadata, embedding)
VALUES (
    'doc-1',
    'PostgreSQL with pgvector is powerful.',
    '{"source": "readme"}'::jsonb,
    array_fill(0.1, ARRAY[768])::vector
);

SELECT id, content
FROM vector_store
ORDER BY embedding <=> array_fill(0.1, ARRAY[768])::vector
LIMIT 5;
```

---

## 📚 References

- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [HNSW Paper](https://arxiv.org/abs/1603.09320)

---

## 📝 License

MIT © Your Name
