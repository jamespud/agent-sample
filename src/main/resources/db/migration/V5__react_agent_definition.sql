-- React Agent definition table for agent lifecycle management
-- Separates agent configuration from session (conversation thread)

-- Agent configuration table
CREATE TABLE IF NOT EXISTS react_agent
(
    agent_id            VARCHAR(36) PRIMARY KEY,
    agent_type          VARCHAR(20) NOT NULL CHECK (agent_type IN ('TOOLCALL', 'MCP')),
    model_provider      VARCHAR(50) NOT NULL,
    system_prompt       TEXT        NOT NULL,
    next_step_prompt    TEXT,
    max_steps           INT         NOT NULL,
    duplicate_threshold INT         NOT NULL,
    tool_choice         VARCHAR(20) NOT NULL CHECK (tool_choice IN ('AUTO', 'REQUIRED', 'NONE')),
    status              VARCHAR(20) NOT NULL     DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS react_agent_updated_at_idx ON react_agent (updated_at);
CREATE INDEX IF NOT EXISTS react_agent_type_provider_idx ON react_agent (agent_type, model_provider);

-- Add agent_id column to react_agent_session
ALTER TABLE react_agent_session
    ADD COLUMN IF NOT EXISTS agent_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS react_agent_session_agent_id_idx ON react_agent_session (agent_id);

-- Backfill: create agent for each existing session
INSERT INTO react_agent (agent_id, agent_type, model_provider, system_prompt, next_step_prompt, 
                         max_steps, duplicate_threshold, tool_choice, status, created_at, updated_at)
SELECT 
    gen_random_uuid()::text,
    agent_type,
    model_provider,
    system_prompt,
    next_step_prompt,
    max_steps,
    duplicate_threshold,
    tool_choice,
    'ACTIVE',
    created_at,
    updated_at
FROM react_agent_session
WHERE agent_id IS NULL
ON CONFLICT DO NOTHING;

-- Update sessions to link to their newly created agents
-- Match by configuration fields (since we just created agents from sessions)
UPDATE react_agent_session s
SET agent_id = a.agent_id
FROM react_agent a
WHERE s.agent_id IS NULL
  AND s.agent_type = a.agent_type
  AND s.model_provider = a.model_provider
  AND s.system_prompt = a.system_prompt
  AND COALESCE(s.next_step_prompt, '') = COALESCE(a.next_step_prompt, '')
  AND s.max_steps = a.max_steps
  AND s.duplicate_threshold = a.duplicate_threshold
  AND s.tool_choice = a.tool_choice;

-- Add foreign key constraint
ALTER TABLE react_agent_session
    ADD CONSTRAINT fk_react_agent_session_agent
        FOREIGN KEY (agent_id) REFERENCES react_agent (agent_id);
