-- Add metadata column to react_agent_message table for storing additional message information

ALTER TABLE react_agent_message
    ADD COLUMN IF NOT EXISTS metadata JSONB;

-- Add comment for documentation
COMMENT ON COLUMN react_agent_message.metadata IS 'Additional metadata for messages, stored as JSONB';

-- Update sequence to fix naming (PostgreSQL creates sequence with BIGSERIAL)
-- The sequence name is automatically created as react_agent_message_seq_seq by PostgreSQL
-- We need to ensure it exists and is properly associated
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_sequences WHERE schemaname = 'public' AND sequencename = 'react_agent_message_seq_seq') THEN
        -- If the sequence doesn't exist with expected name, check the actual sequence
        -- BIGSERIAL creates a sequence named {table}_{column}_seq
        EXECUTE 'ALTER SEQUENCE IF EXISTS react_agent_message_seq RENAME TO react_agent_message_seq_seq';
    END IF;
END $$;
