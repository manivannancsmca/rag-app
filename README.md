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
