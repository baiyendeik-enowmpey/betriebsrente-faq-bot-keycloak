
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT NOW(),
    username VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    details TEXT,
    file_name VARCHAR(255),
    kategorie VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent TEXT,
    ocr_used BOOLEAN DEFAULT false,
    chunk_count INT,
    char_count INT
);
CREATE TABLE chat_history (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    username VARCHAR(100),
    frage TEXT NOT NULL,
    antwort TEXT NOT NULL,
    kategorie VARCHAR(100),
    quellen TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp DESC);
