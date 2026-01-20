package com.github.spud.sample.ai.agent.domain.session;

import com.github.spud.sample.ai.agent.domain.mcp.McpClientManager;
import com.github.spud.sample.ai.agent.domain.agent.McpAgent;
import com.github.spud.sample.ai.agent.domain.agent.ReActAgent;
import com.github.spud.sample.ai.agent.domain.agent.ToolCallAgent;
import com.github.spud.sample.ai.agent.domain.state.ToolChoice;
import com.github.spud.sample.ai.agent.domain.tools.ToolRegistry;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentSession;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentSessionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * Default implementation of ReactAgentFactory Creates new agent instances (not singletons) for each
 * request
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultReActAgentFactory implements ReActAgentFactory {

  private final ChatClient chatClient;
  private final ToolRegistry toolRegistry;
  private final McpClientManager mcpClientManager;
  private final ReActAgentSessionRepository sessionRepository;

  @Override
  public ReActAgent create(ReActAgentSession session, List<AbstractMessage> historyMessages) {
    log.debug("Creating new {} agent instance for conversationId={}",
      session.getAgentType(), session.getConversationId());

    ToolChoice toolChoice = ToolChoice.valueOf(session.getToolChoice());

    // Use enabled tools snapshot (immutable copy taken at session creation)
    List<ToolCallback> callbacks = new java.util.ArrayList<>();
    if (session.getEnabledToolsSnapshot() != null) {
      session.getEnabledToolsSnapshot().forEach(name -> toolRegistry.getCallback(name).ifPresent(callbacks::add));
    }
    switch (session.getAgentType()) {
      case TOOLCALL:
        // ToolAgent only uses local tools from ToolRegistry

        ToolCallAgent toolAgent = ToolCallAgent.builder()
          .name("react-tool_call-" + session.getConversationId())
          .description("React tool-calling agent")
          .systemPrompt(session.getSystemPrompt())
          .nextStepPrompt(session.getNextStepPrompt())
          .chatClient(chatClient)
          .toolChoice(toolChoice)
          .messages(historyMessages)
          .maxSteps(session.getMaxSteps())
          .duplicateThreshold(session.getDuplicateThreshold())
          .availableCallbacks(callbacks)
          .build();

        return toolAgent;

      case MCP:
        List<String> enabledMcpServers = sessionRepository.listEnabledMcpServers(
          session.getConversationId()
        );

        McpAgent mcpAgent = McpAgent.builder()
          .name("react-mcp-" + session.getConversationId())
          .description("React MCP agent")
          .systemPrompt(session.getSystemPrompt())
          .nextStepPrompt(session.getNextStepPrompt())
          .chatClient(chatClient)
          .messages(historyMessages)
          .maxSteps(session.getMaxSteps())
          .duplicateThreshold(session.getDuplicateThreshold())
          .mcpClientManager(mcpClientManager)
          .availableCallbacks(callbacks)
          .build();

        // Initialize MCP with enabled servers (builds and injects MCP callbacks)
        mcpAgent.initializeMcp(enabledMcpServers, 5);

        return mcpAgent;

      default:
        throw new IllegalArgumentException("Unknown agent type: " + session.getAgentType());
    }
  }
}
