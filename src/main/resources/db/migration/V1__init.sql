CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE fachwissen (id BIGSERIAL PRIMARY KEY, titel VARCHAR(255) NOT NULL, kategorie VARCHAR(100) NOT NULL, quelle VARCHAR(255), original_text TEXT NOT NULL, version INT DEFAULT 1, erstellt_von VARCHAR(100), created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW());
CREATE TABLE fachwissen_chunks (id BIGSERIAL PRIMARY KEY, fachwissen_id BIGINT REFERENCES fachwissen(id) ON DELETE CASCADE, chunk_index INT NOT NULL, chunk_text TEXT NOT NULL, chunk_metadata JSONB, embedding vector(768), token_count INT, created_at TIMESTAMP DEFAULT NOW());
CREATE INDEX idx_chunks_fach ON fachwissen_chunks(fachwissen_id);
CREATE INDEX ON fachwissen_chunks USING hnsw (embedding vector_cosine_ops);
