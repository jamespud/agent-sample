-- Add @Version column and ARCHIVED status to react_agent table
-- Supports optimistic locking and agent lifecycle management

-- Add version column for optimistic locking
ALTER TABLE react_agent
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Update existing rows to have version 0
UPDATE react_agent SET version = 0 WHERE version IS NULL;

-- Add NOT NULL constraint
ALTER TABLE react_agent
    ALTER COLUMN version SET NOT NULL;

-- Update CHECK constraint to include ARCHIVED status
-- PostgreSQL constraint names vary; safely handle with PL/pgSQL block
DO $$
DECLARE
    v_constraint_name TEXT;
BEGIN
    -- Find any CHECK constraint on status column for react_agent table
    SELECT constraint_name INTO v_constraint_name
    FROM information_schema.table_constraints
    WHERE table_name = 'react_agent'
      AND constraint_type = 'CHECK'
      AND constraint_name LIKE '%status%';
    
    -- Drop if found
    IF v_constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE react_agent DROP CONSTRAINT ' || quote_ident(v_constraint_name);
    END IF;
END $$;

-- Add new CHECK constraint with ARCHIVED status
ALTER TABLE react_agent
    ADD CONSTRAINT react_agent_status_check CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED'));
