# Agent Sample - Spring AI Agent Framework

基于 Spring Boot 3.5 + Spring AI 1.0.3 的生产可用 AI Agent 框架，支持：

- **多模型**：OpenAI (及兼容 API) + Ollama
- **MCP**：支持 SSE 和 STDIO 传输，多服务器配置
- **RAG**：pgvector 向量检索 + Redis 缓存
- **状态机**：Spring Statemachine 驱动的 think/act 循环
- **可观测性**：完整的步骤追踪与轨迹记录

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

### 4. 测试 API

```bash
# 执行 Agent
curl -X POST http://localhost:8080/agent/run \
  -H "Content-Type: application/json" \
  -d '{"request": "What time is it?"}'

# 查看执行轨迹
curl http://localhost:8080/agent/{traceId}

# RAG 数据摄取
curl -X POST http://localhost:8080/agent/rag/ingest/text \
  -H "Content-Type: application/json" \
  -d '{"content": "Spring AI is a framework for building AI applications.", "metadata": {"source": "docs"}}'
```

## 架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         AgentController                         │
│                    (REST API: /agent/*)                         │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                          AgentKernel                            │
│               (核心编排器: think/act 循环)                        │
└─────────────────────────────────────────────────────────────────┘
         │              │              │              │
         ▼              ▼              ▼              ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│ StateMachine│ │ ToolRegistry│ │    RAG      │ │    MCP      │
│   Driver    │ │  + Executor │ │  Retrieve   │ │ Synchronizer│
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
         │              │              │              │
         ▼              ▼              ▼              ▼
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
