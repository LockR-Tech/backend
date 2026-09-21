-- Trợ lý RAG (luồng 5): kho tri thức + hội thoại. Container pgvector riêng, user của container là
-- superuser nên tạo được extension.
CREATE EXTENSION IF NOT EXISTS vector;

-- Tài liệu admin nạp. allowed_roles: 'ALL' hoặc tập vai trò (CUSTOMER, LOCKER_TECHNICIAN, DRONE_TECHNICIAN, ADMIN).
-- status: PENDING → INDEXING → READY | FAILED.
CREATE TABLE kb_documents
(
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(255)  NOT NULL,
    file_name     VARCHAR(255),
    mime_type     VARCHAR(100)  NOT NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    allowed_roles TEXT[]        NOT NULL DEFAULT ARRAY ['ALL'],
    checksum      VARCHAR(64)   NOT NULL,
    content       BYTEA         NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    chunk_count   INT           NOT NULL DEFAULT 0,
    error         VARCHAR(1000),
    created_by    BIGINT,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    indexed_at    TIMESTAMP
);

CREATE UNIQUE INDEX uq_kb_documents_checksum ON kb_documents (checksum);
CREATE INDEX idx_kb_documents_status ON kb_documents (status, id);

-- Đoạn đã nhúng. 1024 = số chiều của model embedding (voyage-4 / voyage-3.5 mặc định).
CREATE TABLE kb_chunks
(
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT       NOT NULL REFERENCES kb_documents (id) ON DELETE CASCADE,
    ordinal     INT          NOT NULL,
    heading     VARCHAR(500),
    content     TEXT         NOT NULL,
    embedding   vector(1024) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kb_chunks_document ON kb_chunks (document_id, ordinal);
CREATE INDEX idx_kb_chunks_embedding ON kb_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE assistant_conversations
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assistant_conversations_user ON assistant_conversations (user_id, updated_at DESC);

-- role: USER / ASSISTANT. refused: trả lời "tài liệu chưa đề cập" (không gọi LLM) hoặc model từ chối.
CREATE TABLE assistant_messages
(
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES assistant_conversations (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    citations       JSONB,
    refused         BOOLEAN     NOT NULL DEFAULT FALSE,
    top_score       DOUBLE PRECISION,
    model           VARCHAR(100),
    input_tokens    INT,
    output_tokens   INT,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assistant_messages_conversation ON assistant_messages (conversation_id, id);
CREATE INDEX idx_assistant_messages_created ON assistant_messages (role, created_at);

-- Bộ câu hỏi đánh giá: câu phải trúng tài liệu mong đợi, hoặc phải bị từ chối.
CREATE TABLE eval_cases
(
    id                      BIGSERIAL PRIMARY KEY,
    question                TEXT         NOT NULL,
    roles                   VARCHAR(255) NOT NULL DEFAULT 'CUSTOMER',
    expected_document_title VARCHAR(255),
    must_refuse             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Quy tắc admin cấu hình được (ADR-0005), cùng cấu trúc các service khác.
CREATE TABLE system_settings
(
    setting_key        VARCHAR(150)  PRIMARY KEY,
    setting_value      VARCHAR(4000) NOT NULL,
    updated_by_user_id BIGINT,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE system_setting_audits
(
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(150)  NOT NULL,
    old_value     VARCHAR(4000),
    new_value     VARCHAR(4000),
    actor_user_id BIGINT,
    changed_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_system_setting_audits_key ON system_setting_audits (setting_key, changed_at DESC);
