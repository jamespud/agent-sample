package com.github.spud.sample.ai.agent.domain.react.session;

import com.github.spud.sample.ai.agent.domain.react.agent.BaseAgent;
import com.github.spud.sample.ai.agent.domain.react.message.AgentMessage;
import com.github.spud.sample.ai.agent.domain.react.message.AgentMessageMapper;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentConfig;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentMessage;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentSession;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentConfigRepository;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentMessageRepository;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentSessionRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  /**
   * Create a new agent configuration
   */
  @Transactional
  public String createAgent(CreateAgentRequest req) {
    String agentId = UUID.randomUUID().toString();

    ReActAgentConfig agent = new ReActAgentConfig();
    agent.setAgentId(agentId);
    agent.setAgentType(req.getAgentType());
    agent.setModelProvider(
      req.getModelProvider() != null ? req.getModelProvider() : defaults.getModelProvider());
    agent.setSystemPrompt(defaults.getSystemPrompt());
    agent.setNextStepPrompt(defaults.getNextStepPrompt());
    agent.setMaxSteps(req.getMaxSteps() != null ? req.getMaxSteps() : defaults.getMaxSteps());
    agent.setDuplicateThreshold(defaults.getDuplicateThreshold());
    agent.setToolChoice(
      req.getToolChoice() != null ? req.getToolChoice() : defaults.getToolChoiceDefault());
    agent.setStatus("ACTIVE");

    agentConfigRepository.save(agent);

    log.info("Created agent: agentId={}, agentType={}", agentId, req.getAgentType());
    return agentId;
  }

  /**
   * Create a new session
   */
  @Transactional
  public String createSession(CreateSessionRequest req) {
    String conversationId = UUID.randomUUID().toString();

    // Compatibility mode: if agentId not provided, create agent first
    final String agentId;
    if (req.getAgentId() == null) {
      CreateAgentRequest agentReq = new CreateAgentRequest();
      agentReq.setAgentType(req.getAgentType());
      agentReq.setModelProvider(req.getModelProvider());
      agentReq.setMaxSteps(req.getMaxSteps());
      agentReq.setToolChoice(req.getToolChoice());
      agentId = createAgent(agentReq);
    } else {
      agentId = req.getAgentId();
    }

    // Load agent configuration
    ReActAgentConfig agent = agentConfigRepository.findByAgentId(agentId)
      .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

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
      .status(ReActSessionStatus.ACTIVE)
      .version(0)
      .build()
      .toEntity();

    List<String> enabledMcpServers = null;
    if (agent.getAgentType() == ReActAgentType.MCP && req.getEnabledMcpServers() != null) {
      enabledMcpServers = req.getEnabledMcpServers();
    }

    sessionRepository.create(record, enabledMcpServers);

    log.info("Created session: conversationId={}, agentId={}, agentType={}", conversationId,
      agentId, agent.getAgentType());
    return conversationId;
  }

  /**
   * Send a message to an existing session
   */
  @Transactional
  public SendMessageResponse sendMessage(String conversationId, String content) {
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

    // Run agent (blocking)
    String answer = agent.run(content).block();

    // Calculate appended messages by comparing sizes
    List<AbstractMessage> appendedSpringMessages = historyMessages.subList(beforeSize, historyMessages.size());
    
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

  @Data
  public static class CreateAgentRequest {

    private ReActAgentType agentType;
    private String modelProvider;
    private Integer maxSteps;
    private String toolChoice;
  }

  @Data
  public static class CreateSessionRequest {

    private String agentId;
    private ReActAgentType agentType;
    private String modelProvider;
    private Integer maxSteps;
    private String toolChoice;
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
