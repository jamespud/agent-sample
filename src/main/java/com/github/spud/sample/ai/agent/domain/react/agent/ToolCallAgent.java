package com.github.spud.sample.ai.agent.domain.react.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.spud.sample.ai.agent.domain.state.AgentState;
import com.github.spud.sample.ai.agent.domain.state.ToolChoice;
import com.github.spud.sample.ai.agent.domain.tools.ToolRegistry;
import com.github.spud.sample.ai.agent.infrastructure.util.JsonUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Getter
@SuperBuilder
public class ToolCallAgent extends ReActAgent {

  private static final String TERMINATE_TOOL_NAME = "terminate";
  private static final String NO_TOOL_CALLS_CORRECTION_PROMPT =
    """
      【重要】你上一轮的输出没有包含工具调用。请注意：
      1. 不要直接输出最终答案文本
      2. 必须通过调用 terminate 工具来结束任务
      3. terminate 工具的参数格式：{"answer": "你的最终答案"}
      4. 只需要一次 terminate 调用即可完成任务
      
      请重新思考并使用工具调用（tool calls）来完成任务。""";

  protected final ToolRegistry toolRegistry;

  @Builder.Default
  private ToolChoice toolChoice = ToolChoice.AUTO;

  @Builder.Default
  public List<ToolCall> pendingToolCalls = new ArrayList<>();

  // Available tool callbacks for this agent (can be local + MCP)
  // Subclasses like McpAgent can provide combined callbacks
  @Builder.Default
  protected List<ToolCallback> availableCallbacks = new ArrayList<>();

  @Override
  protected Mono<Boolean> think() {
    return Mono.fromCallable(() -> {
        // Add next step prompt if provided
        if (StringUtils.hasText(this.nextStepPrompt)) {
          this.messages.add(new UserMessage(this.nextStepPrompt));
        }

        // Build prompt messages
        List<Message> promptMessages = new ArrayList<>();
        if (StringUtils.hasText(this.systemPrompt)) {
          promptMessages.add(new SystemMessage(this.systemPrompt));
        }
        if (this.messages != null) {
          promptMessages.addAll(this.messages);
        }

        Prompt prompt = new Prompt(promptMessages);

        // Call LLM with tools (unless toolChoice is NONE)
        ChatResponse chatResponse;
        try {
          log.debug("Calling chat client in think() with {} messages", promptMessages.size());
          ChatClientRequestSpec requestSpec = this.chatClient.prompt(prompt);
          if (this.toolChoice != ToolChoice.NONE) {
            // Use injected callbacks if available, otherwise fallback to toolRegistry
            List<ToolCallback> callbacks = !availableCallbacks.isEmpty() 
              ? availableCallbacks 
              : new ArrayList<>(toolRegistry.getAllCallbacks());
            requestSpec.toolCallbacks(callbacks);
          }
          chatResponse = requestSpec.call().chatResponse();
        } catch (Exception e) {
          log.error("Error calling chat client during think(): {}", e.getMessage(), e);
          throw e;
        }

        if (chatResponse == null) {
          log.warn("No chat response received during think()");
          return false;
        }

        // Get assistant message with structured tool_calls
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        String content = assistantMessage.getText() != null ? assistantMessage.getText() : "";

        if (this.toolChoice.equals(ToolChoice.NONE)) {
          if (StringUtils.hasText(content)) {
            this.messages.add(assistantMessage);
            return true;
          }
          return false;
        }

        // Append assistant message to history
        this.messages.add(assistantMessage);

        // Extract tool calls from assistant message
        List<ToolCall> toolCalls = assistantMessage.getToolCalls();
        this.pendingToolCalls = !toolCalls.isEmpty() ? new ArrayList<>(toolCalls) : new ArrayList<>();

        log.info("Think phase complete: content length={}, tool_calls={}",
          content.length(), this.pendingToolCalls.size());

        if (this.toolChoice.equals(ToolChoice.REQUIRED) && this.pendingToolCalls.isEmpty()) {
          return true;
        }

        if (this.toolChoice.equals(ToolChoice.AUTO) && this.pendingToolCalls.isEmpty()) {
          return StringUtils.hasText(content);
        }

        // Decide whether to ACT
        if (!this.pendingToolCalls.isEmpty()) {
          log.debug("Tool calls found: {}",
            this.pendingToolCalls.stream()
              .map(ToolCall::name)
              .collect(Collectors.joining(", ")));
          return true; // Enter ACT phase
        }

        // No tool calls: inject correction prompt
        log.warn("No tool calls in response, injecting correction prompt");
        this.messages.add(new SystemMessage(NO_TOOL_CALLS_CORRECTION_PROMPT));
        return false; // Stay in THINK (will retry next step)
      })
      .onErrorStop();
  }

  @Override
  protected Mono<String> act() {
    return Mono.fromCallable(() -> {
      if (this.pendingToolCalls == null || this.pendingToolCalls.isEmpty()) {
        log.warn("ACT phase called with no pending tool calls");
        return StringUtils.hasText(this.messages.get(this.messages.size() - 1).getText())
          ? this.messages.get(this.messages.size() - 1).getText() : "No tools to execute";
      }

      // Terminate priority: if terminate is present, execute only terminate
      List<ToolCall> toolCallsToExecute = filterForTerminate(this.pendingToolCalls);

      log.info("Executing {} tool call(s)", toolCallsToExecute.size());

      // Execute tool calls directly using ToolCallback
      List<String> resultSummaries = new ArrayList<>();
      for (ToolCall toolCall : toolCallsToExecute) {
        try {
          log.info("🔧 Executing tool: {} (id: {})", toolCall.name(), toolCall.id());

          // Execute tool and get result
          String toolResult = executeToolCall(toolCall);

          // Create and append tool response message (aligned with Python)
          // ToolResponseMessage expects List<ToolResponse>
          ToolResponseMessage.ToolResponse toolResponse =
            new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), toolResult);
          ToolResponseMessage toolResponseMessage = new ToolResponseMessage(
            Collections.singletonList(toolResponse));
          this.messages.add(toolResponseMessage);

          resultSummaries.add(String.format("Tool '%s': %s",
            toolCall.name(),
            StringUtils.truncate(toolResult, 100)));

          // Handle terminate tool
          if (TERMINATE_TOOL_NAME.equals(toolCall.name())) {
            handleTerminate(toolCall.arguments());
            log.info("Terminate tool called, finalAnswer set");
            break; // Stop executing further tools
          }
        } catch (Exception e) {
          log.error("Error executing tool {}: {}", toolCall.name(), e.getMessage(), e);
          // Add error as tool response
          ToolResponseMessage.ToolResponse errorResponse =
            new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(),
              "Error: " + e.getMessage());
          ToolResponseMessage errorMessage = new ToolResponseMessage(
            Collections.singletonList(errorResponse));
          this.messages.add(errorMessage);
          resultSummaries.add(String.format("Tool '%s': Error - %s",
            toolCall.name(), e.getMessage()));
        }
      }

      // Clear pending tool calls
      this.pendingToolCalls.clear();

      return String.join("\n", resultSummaries);
    });
  }

  /**
   * Filter tool calls: if terminate is present, return only terminate
   */
  private List<ToolCall> filterForTerminate(List<ToolCall> toolCalls) {
    for (ToolCall toolCall : toolCalls) {
      if (TERMINATE_TOOL_NAME.equals(toolCall.name())) {
        log.info("Terminate tool detected, filtering out other tools");
        return Collections.singletonList(toolCall);
      }
    }
    return toolCalls;
  }

  /**
   * Execute a single tool call using ToolCallback
   */
  private String executeToolCall(ToolCall toolCall) {
    if (!StringUtils.hasText(toolCall.name())) {
      throw new IllegalArgumentException("Tool call name is empty");
    }

    // Find callback from available callbacks first, then fallback to toolRegistry
    ToolCallback callback = null;
    
    if (!availableCallbacks.isEmpty()) {
      callback = availableCallbacks.stream()
        .filter(cb -> cb.getToolDefinition().name().equals(toolCall.name()))
        .findFirst()
        .orElse(null);
    }
    
    if (callback == null) {
      callback = toolRegistry.getCallback(toolCall.name())
        .orElseThrow(() -> new IllegalArgumentException(
          "Unknown tool: " + toolCall.name()));
    }

    // Execute using callback
    return callback.call(toolCall.arguments());
  }

  /**
   * Handle terminate tool: parse answer and set finalAnswer
   */
  private void handleTerminate(String arguments) {
    try {
      JsonNode jsonNode = JsonUtils.readTree(arguments);
      String answer = jsonNode.has("answer")
        ? jsonNode.get("answer").asText()
        : "Task completed";

      this.finalAnswer = answer;
      this.state = AgentState.FINISHED;

      log.info("Terminate handled: finalAnswer={}", StringUtils.truncate(answer, 100));
    } catch (Exception e) {
      log.error("Error parsing terminate arguments: {}", e.getMessage(), e);
      this.finalAnswer = "Task completed (error parsing terminate arguments)";
      this.state = AgentState.FINISHED;
    }
  }

  @Override
  protected void cleanup() {
    log.info("🧹 Cleaning up resources for agent {}...", this.name);
    // TODO: implement any necessary cleanup logic here
  }
}
