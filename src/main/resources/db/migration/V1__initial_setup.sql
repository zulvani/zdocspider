CREATE TABLE IF NOT EXISTS docs (
    id VARCHAR(36) PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    doc_content TEXT,
    file_path TEXT NOT NULL,
    checksum VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
