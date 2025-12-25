package com.github.spud.sample.ai.agent.domain.react.session;

import com.github.spud.sample.ai.agent.domain.mcp.McpClientManager;
import com.github.spud.sample.ai.agent.domain.react.agent.McpAgent;
import com.github.spud.sample.ai.agent.domain.react.agent.ReActAgent;
import com.github.spud.sample.ai.agent.domain.react.agent.ToolCallAgent;
import com.github.spud.sample.ai.agent.domain.state.ToolChoice;
import com.github.spud.sample.ai.agent.domain.tools.ToolRegistry;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentSession;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentSessionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AbstractMessage;
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

    switch (session.getAgentType()) {
      case TOOLCALL:
        // ToolAgent only uses local tools from ToolRegistry
        ToolCallAgent toolAgent = ToolCallAgent.builder()
          .name("react-toolcall-" + session.getConversationId())
          .description("React tool-calling agent")
          .systemPrompt(session.getSystemPrompt())
          .nextStepPrompt(session.getNextStepPrompt())
          .chatClient(chatClient)
          .toolChoice(toolChoice)
          .messages(historyMessages)
          .maxSteps(session.getMaxSteps())
          .duplicateThreshold(session.getDuplicateThreshold())
          .toolRegistry(toolRegistry)
          .build();
        
        // Explicitly set availableCallbacks to empty, will fall back to toolRegistry
        // This ensures ToolAgent boundary is enforced
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
          .toolRegistry(toolRegistry)
          .mcpClientManager(mcpClientManager)
          .build();

        // Initialize MCP with enabled servers (builds and injects MCP callbacks)
        mcpAgent.initializeMcp(enabledMcpServers, 5);

        return mcpAgent;

      default:
        throw new IllegalArgumentException("Unknown agent type: " + session.getAgentType());
    }
  }
}
