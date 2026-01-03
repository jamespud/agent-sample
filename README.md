# Agent Sample - Spring AI ReAct Agent Framework

基于 Spring Boot 3.5 + Spring AI 1.0.3 的生产可用 AI Agent 框架，支持：

- **多模型**：OpenAI (及兼容 API) + Ollama
- **MCP**：支持 SSE 和 STDIO 传输，多服务器配置
- **RAG**：pgvector 向量检索 + Redis 缓存
- **ReAct Agent**：Session-based 对话流，自动 Tool Callback 机制
- **可观测性**：完整的消息记录与会话追踪

## 快速开始

### 1. 启动基础设施

```bash
# 启动 PostgreSQL + Redis
docker compose up -d

# 如需本地 Ollama
docker compose --profile ollama up -d
docker exec -it agent-ollama ollama pull llama3
```

### 2. 配置环境变量

```bash
export OPENAI_API_KEY=sk-your-api-key
# 可选：使用 OpenAI 兼容端点
# export OPENAI_BASE_URL=https://your-compatible-api.com

# 可选：切换到 Ollama
# export MODEL_PROVIDER=ollama
```

### 3. 运行应用

```bash
./mvnw spring-boot:run
```

### 4. 测试 ReAct Agent API

```bash
# 创建新会话
curl -X POST http://localhost:8080/agent/react/session/new \
  -H "Content-Type: application/json" \
  -d '{"model": "gpt-4o", "temperature": 0.7}'


# 返回 {"conversationId": "uuid-xxx"}

# 发送消息到会话
curl -X POST http://localhost:8080/agent/react/session/{conversationId}/message \
  -H "Content-Type: application/json" \
  -d '{"question": "What time is it?"}'

# 返回示例
# {
#   "conversationId": "uuid-xxx",
#   "answer": "The current time is 14:23:45 UTC",
#   "finished": true,
#   "steps": 2,
#   "toolCalls": ["get_current_time"]
# }
```

### 5. 测试 RAG 数据注入

```bash
# 注入文本知识
curl -X POST http://localhost:8080/rag/ingest/text \
  -H "Content-Type: application/json" \
  -d '{
    "content": "The company was founded in 2024 by Alice and Bob.",
    "metadata": {"source": "company_intro"}
  }'

# 注入文件（仅限 RAG 启用时）
curl -X POST http://localhost:8080/rag/ingest/file \
  -F "file=@document.pdf" \
  -F "metadata={\"category\":\"legal\"}"
```

## 架构

```
┌──────────────────────────────────────────────────────────────┐
│              ReActAgentController (REST Layer)               │
│          /agent/react/session/new  ←→  /session/{id}/message│
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│             ReActSessionService (Session Manager)            │
│  • Manages Conversation Context (ReactAgentSession)          │
│  • Handles Message Persistence (ReactAgentMessage)           │
│  • Coordinates Agent Execution                               │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│              ReActAgent Hierarchy (Core Logic)               │
│  • BaseAgent: State management & step execution              │
│  • ToolCallAgent: Spring AI Tools (local functions)          │
│  • McpAgent: MCP Servers (external tools)                    │
└────────────────────────┬─────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Local Tools  │ │  pgvector    │ │ MCP Servers  │
│ (Callbacks)  │ │  + RAG       │ │ (SSE/STDIO)  │
└──────────────┘ └──────────────┘ └──────────────┘
```

## ReAct 执行流程

1. **创建会话**: POST /agent/react/session/new → 返回 `conversationId`
2. **发送消息**: POST /agent/react/session/{id}/message
   - Agent 进入 ReAct 循环 (Reason → Act → Observe)
   - 自动调用 Spring AI Tools / MCP Tools
   - 达到 maxSteps 或 terminate 工具调用时终止
3. **结果**: 返回 finalAnswer (最终答案)

## 终止策略

Agent 使用以下规则确保始终返回有效答案：

1. **terminate 工具调用**: 如果 LLM 调用 `terminate(answer="xxx")`，直接返回 answer 作为 finalAnswer
2. **直接文本回复**: 如果 LLM 未调用工具而直接输出内容，该内容作为 finalAnswer
3. **maxSteps 达到**: 如果达到最大步数限制，返回 "达到最大步数，最后推理: {lastContent}"
4. **空回复/重复**: 如果 LLM 连续返回空内容或重复，强制终止并返回降级答案

## 配置
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│   Spring    │ │ Local Tools │ │  pgvector   │ │ MCP Servers │
│ Statemachine│ │ + MCP Tools │ │ + Redis     │ │ (SSE/STDIO) │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
```

## 状态机

```
IDLE ──START──▶ THINKING ──THINK_DONE_WITH_TOOLS──▶ ACTING
                    │                                   │
                    │                              ACT_DONE
                    │                                   │
                    ◀───────────────────────────────────┘
                    │
          THINK_DONE_NO_TOOLS / STOP_* / TOOL_TERMINATE
                    │
                    ▼
               FINISHED

              FAIL ──▶ ERROR
```

## 配置

### 模型配置

```yaml
app:
  model:
    provider: openai  # openai | ollama

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: gpt-4o
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: llama3
```

### MCP 配置

```yaml
app:
  mcp:
    servers:
      - id: filesystem
        enabled: true
        transport: stdio
        stdio:
          command: npx
          args: ["-y", "@anthropic/mcp-server-filesystem", "/tmp"]
      - id: web
        enabled: true
        transport: sse
        sse:
          url: http://localhost:8000/sse
    namespace:
      enabled: true
      separator: "__"
```

### Agent 配置

```yaml
app:
  agent:
    max-steps: 15
    duplicate-threshold: 3
    empty-threshold: 2
```

### RAG 配置

```yaml
app:
  rag:
    enabled: true
    top-k: 5
    chunk-size: 1000
    chunk-overlap: 200
    cache:
      embedding:
        ttl: 86400
      retrieval:
        ttl: 3600
```

## 内置工具

| 工具名 | 描述 |
|--------|------|
| `terminate` | 终止 Agent 并返回最终答案 |
| `get_current_time` | 获取当前时间 |
| `echo` | 回显消息（测试用） |
| `retrieve_knowledge` | RAG 知识检索 |

## 开发

### 添加本地工具

```java
@Component
@RequiredArgsConstructor
public class MyToolsConfig {
    private final ToolRegistry toolRegistry;

    @PostConstruct
    public void register() {
        ToolDefinition def = DefaultToolDefinition.builder()
                .name("my_tool")
                .description("My custom tool")
                .inputSchema("{...}")
                .build();

        toolRegistry.register("my_tool", def, input -> {
            // 实现工具逻辑
            return "result";
        });
    }
}
```

### 运行测试

```bash
./mvnw test
```

## License

MIT
