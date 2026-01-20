-- 添加数据库索引以提高查询性能
-- 为 react_agent 表添加索引
CREATE INDEX IF NOT EXISTS idx_react_agent_status ON react_agent(status);

-- 为 react_agent_session 表添加索引
CREATE INDEX IF NOT EXISTS idx_react_agent_session_agent_id ON react_agent_session(agent_id);
CREATE INDEX IF NOT EXISTS idx_react_agent_session_created_at ON react_agent_session(created_at);
CREATE INDEX IF NOT EXISTS idx_react_agent_session_status ON react_agent_session(status);

-- 为 react_agent_message 表添加索引
CREATE INDEX IF NOT EXISTS idx_react_agent_message_conversation_id ON react_agent_message(conversation_id);
CREATE INDEX IF NOT EXISTS idx_react_agent_message_message_type ON react_agent_message(message_type);
CREATE INDEX IF NOT EXISTS idx_react_agent_message_created_at ON react_agent_message(created_at);

-- 为 react_agent_session_mcp_server 表添加索引
CREATE INDEX IF NOT EXISTS idx_react_agent_session_mcp_server_conversation_id ON react_agent_session_mcp_server(conversation_id);
CREATE INDEX IF NOT EXISTS idx_react_agent_session_mcp_server_server_id ON react_agent_session_mcp_server(server_id);
