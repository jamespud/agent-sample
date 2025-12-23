-- React Agent Session tables for conversation-based agent execution

-- Main session configuration table
CREATE TABLE IF NOT EXISTS react_agent_session
(
    conversation_id     VARCHAR(255) PRIMARY KEY,
    agent_type          VARCHAR(20) NOT NULL CHECK (agent_type IN ('TOOLCALL', 'MCP')),
    model_provider      VARCHAR(50) NOT NULL,
    system_prompt       TEXT        NOT NULL,
    next_step_prompt    TEXT,
    max_steps           INT         NOT NULL,
    duplicate_threshold INT         NOT NULL,
    tool_choice         VARCHAR(20) NOT NULL CHECK (tool_choice IN ('AUTO', 'REQUIRED', 'NONE')),
    status              VARCHAR(20) NOT NULL     DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FINISHED', 'ERROR')),
    version             INT         NOT NULL     DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS react_agent_session_updated_at_idx ON react_agent_session (updated_at);

-- MCP server association table (for MCP agent type)
CREATE TABLE IF NOT EXISTS react_agent_session_mcp_server
(
    conversation_id VARCHAR(255) NOT NULL,
    server_id       VARCHAR(255) NOT NULL,
    PRIMARY KEY (conversation_id, server_id),
    FOREIGN KEY (conversation_id) REFERENCES react_agent_session (conversation_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS react_agent_session_mcp_server_conv_idx ON react_agent_session_mcp_server (conversation_id);

-- Conversation message history table
CREATE TABLE IF NOT EXISTS react_agent_message
(
    id              UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    conversation_id VARCHAR(255) NOT NULL,
    seq             BIGSERIAL    NOT NULL,
    message_type    VARCHAR(20)  NOT NULL CHECK (message_type IN ('SYSTEM', 'USER', 'ASSISTANT', 'TOOL')),
    content         TEXT,
    tool_call_id    VARCHAR(255),
    tool_name       VARCHAR(255),
    tool_arguments  TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (conversation_id) REFERENCES react_agent_session (conversation_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS react_agent_message_conv_seq_idx ON react_agent_message (conversation_id, seq);
