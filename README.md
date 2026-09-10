# PostgreSQL + PgVector Setup

This guide sets up the PostgreSQL `vector` extension, creates the `vector_store` table, adds an HNSW index for similarity search, and verifies the setup.

---

## 1. Create the Extension

Connect to your PostgreSQL database and run:

```sql
CREATE EXTENSION IF NOT EXISTS vector;


2. Create the Table
Create the vector_store table:

CREATE TABLE IF NOT EXISTS vector_store (
    id        VARCHAR(255) PRIMARY KEY,
    content   TEXT,
    metadata  JSONB DEFAULT '{}'::jsonb,
    embedding VECTOR(768)
);

The table contains:

Column	Type	Description
id	VARCHAR(255)	Unique identifier
content	TEXT	Text content
metadata	JSONB	Additional metadata
embedding	VECTOR(768)	768-dimensional vector embedding

3. Create the Index
Create an HNSW index for fast cosine-similarity searches:

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

This index improves the performance of vector similarity searches.

4. Verify Everything
Check Installed Extensions
Run:

\dx
