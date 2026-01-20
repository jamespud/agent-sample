package com.github.spud.sample.ai.agent.domain.session;

import com.github.spud.sample.ai.agent.domain.agent.BaseAgent;
import com.github.spud.sample.ai.agent.domain.message.AgentMessage;
import com.github.spud.sample.ai.agent.domain.message.AgentMessageMapper;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentConfig;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentMessage;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentSession;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentConfigRepository;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentMessageRepository;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentSessionRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * Business service for React Agent sessions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReActSessionService {

  private final ReActAgentConfigRepository agentConfigRepository;
  private final ReActAgentSessionRepository sessionRepository;
  private final ReActAgentMessageRepository messageRepository;
  private final ReActAgentFactory agentFactory;
  private final ReActAgentDefaultsProperties defaults;
  private final AgentMessageMapper messageMapper;

  public ReActAgentConfig mergeDefaults(CreateAgentRequest request) {
    ReActAgentConfig config = new ReActAgentConfig();
    config.setSystemPrompt(
      StringUtils.hasText(request.getSystemPrompt()) ? request.getSystemPrompt()
        : defaults.getSystemPrompt());
    config.setNextStepPrompt(
      StringUtils.hasText(request.getNextStepPrompt()) ? request.getNextStepPrompt()
        : defaults.getNextStepPrompt());
    config.setMaxSteps(
      request.getMaxSteps() != null ? request.getMaxSteps() : defaults.getMaxSteps());
    config.setDuplicateThreshold(
      request.getDuplicateThreshold() != null ? request.getDuplicateThreshold()
        : defaults.getDuplicateThreshold());
    config.setToolChoice(
      request.getToolChoice() != null ? request.getToolChoice() : defaults.getToolChoiceDefault());
    config.setModelProvider(StringUtils.hasText(request.getModelProvider()) ? request.getModelProvider()
      : defaults.getModelProvider());
    return config;
  }

  @Transactional
  public String defaultAgentId() {
    // 查找是否有默认代理配置，如果没有则创建一个默认代理
    return agentConfigRepository.findAll().stream()
        .filter(config -> "ACTIVE".equals(config.getStatus()))
        .findFirst()
        .map(ReActAgentConfig::getAgentId)
        .orElseGet(() -> {
          log.warn("No active agent configuration found. Creating a default agent.");
          CreateAgentRequest defaultRequest = CreateAgentRequest.builder()
              .name("Default Agent")
              .description("Default agent configuration")
              .agentType(ReActAgentType.TOOLCALL)
              .build();
          return createAgent(defaultRequest);
        });
  }

  /**
   * Create a new agent configuration
   */
  @Transactional
  public String createAgent(CreateAgentRequest req) {
    String agentId = UUID.randomUUID().toString();

    ReActAgentConfig config = mergeDefaults(req);
    config.setAgentId(agentId);
    config.setName(req.getName());
    config.setDescription(req.getDescription());
    config.setAgentType(req.getAgentType());

    agentConfigRepository.save(config);

    log.info("Created agent: agentId={}, agentType={}", agentId, req.getAgentType());
    return agentId;
  }

  /**
   * Create a new session
   */
  @Transactional
  public String createSession(CreateSessionRequest req) {
    String conversationId = UUID.randomUUID().toString();

    final String agentId;
    if (!StringUtils.hasText(req.getAgentId())) {
      agentId = defaultAgentId();
    } else {
      agentId = req.getAgentId();
    }

    // Load agent configuration
    ReActAgentConfig agent = agentConfigRepository.findByAgentId(agentId)
      .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

    // Parse enabled tools from agent config (JSON array of tool names)
    java.util.List<String> enabledToolNames = null;
    if (StringUtils.hasText(agent.getEnabledTools())) {
      try {
        var node = com.github.spud.sample.ai.agent.infrastructure.util.JsonUtils.readTree(agent.getEnabledTools());
        if (node.isArray()) {
          enabledToolNames = new java.util.ArrayList<>();
          for (com.fasterxml.jackson.databind.JsonNode n : node) {
            enabledToolNames.add(n.asText());
          }
        }
      } catch (Exception e) {
        log.warn("Invalid enabled_tools JSON for agent {}: {}", agentId, e.getMessage());
      }
    }

    // Create session with agent configuration snapshot
    ReActAgentSession record = ReActAgentSessionRecord.builder()
      .conversationId(conversationId)
      .agentId(agentId)
      .agentType(agent.getAgentType())
      .modelProvider(agent.getModelProvider())
      .systemPrompt(agent.getSystemPrompt())
      .nextStepPrompt(agent.getNextStepPrompt())
      .maxSteps(agent.getMaxSteps())
      .duplicateThreshold(agent.getDuplicateThreshold())
      .toolChoice(agent.getToolChoice())
      .enabledToolNames(enabledToolNames)
      .status(ReActSessionStatus.ACTIVE)
      .version(0)
      .build()
      .toEntity();

    log.debug("Session enabled tools: {}", enabledToolNames);

    List<String> enabledMcpServers = null;
    if (agent.getAgentType() == ReActAgentType.MCP && req.getEnabledMcpServers() != null) {
      enabledMcpServers = req.getEnabledMcpServers();
    }

    sessionRepository.create(record, enabledMcpServers);

    log.info("Created session: conversationId={}, agentId={}, agentType={}", conversationId,
      agentId, agent.getAgentType());
    return conversationId;
  }

  public List<String> listSessions() {
    return sessionRepository.listAllConversationIds();
  }

  /**
   * Send a message to an existing session
   */
  @Transactional
  public java.util.concurrent.CompletableFuture<SendMessageResponse> sendMessage(String conversationId, String content) {
    // Load session
    ReActAgentSession session = sessionRepository.findByConversationId(conversationId)
      .orElseThrow(() -> new SessionNotFoundException("Session not found: " + conversationId));

    // Serialize concurrent requests for same conversation (optimistic locking)
    Integer versionBumped = sessionRepository.tryBumpVersion(conversationId, session.getVersion());
    if (versionBumped != 1) {
      throw new VersionConflictException(
        "Concurrent modification detected for conversationId: " + conversationId);
    }

    // Load history entities and convert to Spring AI messages
    List<ReActAgentMessage> historyEntities = messageRepository.listMessages(conversationId);
    List<AgentMessage> historyDomains = messageMapper.toDomainList(historyEntities);
    List<AbstractMessage> historyMessages = messageMapper.toSpringMessages(historyDomains);

    int beforeSize = historyMessages.size();

    // Create agent instance (new instance, not singleton)
    BaseAgent agent = agentFactory.create(session, historyMessages);

    // Run agent (non-blocking)
    return agent.run(content)
      .map(answer -> {
        // Calculate appended messages by comparing sizes
        List<AbstractMessage> appendedSpringMessages = historyMessages.subList(beforeSize,
          historyMessages.size());

        // Convert appended Spring messages back to domain, then to entities for persistence
        List<AgentMessage> appendedDomains = appendedSpringMessages.stream()
          .map(messageMapper::fromSpringMessage)
          .collect(Collectors.toList());

        List<ReActAgentMessage> appendedEntities = appendedDomains.stream()
          .map(domain -> messageMapper.toEntity(domain, session))
          .collect(Collectors.toList());

        messageRepository.appendMessages(conversationId, appendedEntities);

        // Determine if finished (check for terminate or finalAnswer presence)
        boolean finished = answer != null && !answer.isEmpty();

        log.info("Processed message: conversationId={}, appendedMessages={}, finished={}",
          conversationId, appendedEntities.size(), finished);

        return SendMessageResponse.builder()
          .conversationId(conversationId)
          .answer(answer)
          .finished(finished)
          .appendedMessages(toMessageDtos(appendedDomains))
          .build();
      })
      .onErrorResume(ex -> {
        // Ensure we persist any messages the agent appended before the failure
        List<AbstractMessage> appendedSpringMessages = historyMessages.subList(beforeSize, historyMessages.size());
        List<AgentMessage> appendedDomains = appendedSpringMessages.stream()
          .map(messageMapper::fromSpringMessage)
          .collect(Collectors.toList());

        List<ReActAgentMessage> appendedEntities = appendedDomains.stream()
          .map(domain -> messageMapper.toEntity(domain, session))
          .collect(Collectors.toList());

        if (!appendedEntities.isEmpty()) {
          messageRepository.appendMessages(conversationId, appendedEntities);
        }

        log.error("Agent run failed for conversationId={}: {}", conversationId, ex.getMessage(), ex);

        return Mono.just(SendMessageResponse.builder()
          .conversationId(conversationId)
          .answer(ex.getMessage())
          .finished(true)
          .appendedMessages(toMessageDtos(appendedDomains))
          .build());
      })
      .toFuture();
  }

  private List<MessageDto> toMessageDtos(List<AgentMessage> domains) {
    return domains.stream()
      .map(d -> MessageDto.builder()
        .messageType(d.getMessageType())
        .content(d.getContent())
        .toolCallId(d.getToolCallId())
        .toolName(d.getToolName())
        .toolArguments(d.getToolArguments())
        .build())
      .collect(Collectors.toList());
  }

  @Builder
  @Data
  public static class CreateAgentRequest {

    private String name;
    private String description;
    private Integer duplicateThreshold;
    private ReActAgentType agentType;
    private String modelProvider;
    private String systemPrompt;
    private String nextStepPrompt;
    private Integer maxSteps;
    private String toolChoice;
  }

  @Data
  public static class CreateSessionRequest {

    private String agentId;
    private List<String> enabledMcpServers;
  }

  @Data
  @lombok.Builder
  public static class SendMessageResponse {

    private String conversationId;
    private String answer;
    private boolean finished;
    private List<MessageDto> appendedMessages;
  }

  @Data
  @lombok.Builder
  public static class MessageDto {

    private MessageType messageType;
    private String content;
    private String toolCallId;
    private String toolName;
    private String toolArguments;
  }

  public static class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String message) {
      super(message);
    }
  }

  public static class VersionConflictException extends RuntimeException {

    public VersionConflictException(String message) {
      super(message);
    }
  }

}
