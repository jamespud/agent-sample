package com.github.spud.sample.ai.agent.react.session;

import com.github.spud.sample.ai.agent.mcp.McpClientManager;
import com.github.spud.sample.ai.agent.mcp.McpToolSynchronizer;
import com.github.spud.sample.ai.agent.react.agent.McpAgent;
import com.github.spud.sample.ai.agent.react.agent.ReActAgent;
import com.github.spud.sample.ai.agent.react.agent.ToolCallAgent;
import com.github.spud.sample.ai.agent.react.session.repo.ReActAgentSessionRepository;
import com.github.spud.sample.ai.agent.state.ToolChoice;
import com.github.spud.sample.ai.agent.tools.ToolRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
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
  private final McpToolSynchronizer mcpToolSynchronizer;
  private final ReActAgentSessionRepository sessionRepository;

  @Override
  public ReActAgent create(ReActAgentSessionRecord session, List<Message> historyMessages) {
    log.debug("Creating new {} agent instance for conversationId={}",
      session.getAgentType(), session.getConversationId());

    ToolChoice toolChoice = ToolChoice.valueOf(session.getToolChoice());

    switch (session.getAgentType()) {
      case TOOLCALL:
        return ToolCallAgent.builder()
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
          .mcpToolSynchronizer(mcpToolSynchronizer)
          .build();

        // Initialize MCP with enabled servers
        mcpAgent.initializeMcp(enabledMcpServers, 5);

        return mcpAgent;

      default:
        throw new IllegalArgumentException("Unknown agent type: " + session.getAgentType());
    }
  }
}
