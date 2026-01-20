-- Add enabled_tools column to react_agent to store JSON array of enabled tool names
ALTER TABLE react_agent
  ADD COLUMN IF NOT EXISTS enabled_tools jsonb;

-- Optionally initialize to null or an empty array for existing rows
UPDATE react_agent SET enabled_tools = NULL WHERE enabled_tools IS NULL;