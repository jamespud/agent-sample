-- Add missing columns to chat_memory table
ALTER TABLE chat_memory
    ADD COLUMN IF NOT EXISTS media JSONB,
    ADD COLUMN IF NOT EXISTS tool_calls JSONB;
