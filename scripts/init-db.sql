-- Enable the PGVector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Spring AI's PgVectorStore auto-creates the table on startup,
-- but we pre-create it here for explicitness and to add indexes.

CREATE TABLE IF NOT EXISTS vector_store (
    id        VARCHAR(255) PRIMARY KEY,
    content   TEXT,
    metadata  JSONB DEFAULT '{}'::jsonb,
    embedding VECTOR(768)                 -- 768 = nomic-embed-text dimension
);

-- HNSW index for fast approximate nearest-neighbour search
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);