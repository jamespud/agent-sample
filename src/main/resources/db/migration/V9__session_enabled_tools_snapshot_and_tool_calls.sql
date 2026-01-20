-- PR1: Session enabled_tools_snapshot 落库 + Message tool_calls 持久化
-- 目标：让会话工具集与多工具调用具有持久化快照，支持可重放

-- 1. react_agent_session 增加 enabled_tools_snapshot（会话创建时的工具集快照）
ALTER TABLE react_agent_session
    ADD COLUMN IF NOT EXISTS enabled_tools_snapshot jsonb;

COMMENT ON COLUMN react_agent_session.enabled_tools_snapshot IS 'JSON array of tool names enabled at session creation time; immutable snapshot to prevent configuration drift';

-- 2. react_agent_message 增加 tool_calls（AssistantMessage 的多个工具调用）
ALTER TABLE react_agent_message
    ADD COLUMN IF NOT EXISTS tool_calls jsonb;

COMMENT ON COLUMN react_agent_message.tool_calls IS 'JSON array of tool calls from AssistantMessage (each call has name, arguments, id); for replay and audit';

-- 索引优化：提升 (conversation_id, message_type) 查询性能（用于查找 assistant 消息）
CREATE INDEX IF NOT EXISTS idx_react_agent_message_conv_type 
    ON react_agent_message(conversation_id, message_type);
