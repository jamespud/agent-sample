-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Vector store table for RAG documents
CREATE TABLE IF NOT EXISTS vector_store
(
    id         UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    content    TEXT NOT NULL,
    metadata   JSONB,
    embedding  vector(1536),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create HNSW index for fast similarity search
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store
        USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Chat memory table
CREATE TABLE IF NOT EXISTS chat_memory
(
    id              UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    conversation_id VARCHAR(255) NOT NULL,
    message_type    VARCHAR(50)  NOT NULL,
    content         TEXT,
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS chat_memory_conversation_idx ON chat_memory (conversation_id);

-- Agent trace table for observability
CREATE TABLE IF NOT EXISTS agent_trace
(
    id             UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    trace_id       VARCHAR(255) NOT NULL,
    step_number    INT          NOT NULL,
    state          VARCHAR(50)  NOT NULL,
    event          VARCHAR(50),
    prompt_summary TEXT,
    tool_calls     JSONB,
    tool_results   JSONB,
    retrieval_docs JSONB,
    duration_ms    BIGINT,
    error          TEXT,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS agent_trace_trace_id_idx ON agent_trace (trace_id);
