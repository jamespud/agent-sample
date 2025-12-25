package com.github.spud.sample.ai.agent.domain.react.session;

import com.github.spud.sample.ai.agent.domain.react.agent.BaseAgent;
import com.github.spud.sample.ai.agent.domain.react.message.AgentMessage;
import com.github.spud.sample.ai.agent.domain.react.message.AgentMessageMapper;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentMessage;
import com.github.spud.sample.ai.agent.infrastructure.persistence.entity.ReActAgentSession;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentMessageRepository;
import com.github.spud.sample.ai.agent.infrastructure.persistence.repository.ReActAgentSessionRepository;
import java.time.Instant;
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

  private final ReActAgentSessionRepository sessionRepository;
  private final ReActAgentMessageRepository messageRepository;
  private final ReActAgentFactory agentFactory;
  private final ReActAgentDefaultsProperties defaults;
  private final AgentMessageMapper messageMapper;

  /**
   * Create a new session
   */
  @Transactional
  public String createSession(CreateSessionRequest req) {
    String conversationId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    ReActAgentSession record = ReActAgentSessionRecord.builder()
      .conversationId(conversationId)
      .agentType(req.getAgentType())
      .modelProvider(
        req.getModelProvider() != null ? req.getModelProvider() : defaults.getModelProvider())
      .systemPrompt(defaults.getSystemPrompt())
      .nextStepPrompt(defaults.getNextStepPrompt())
      .maxSteps(req.getMaxSteps() != null ? req.getMaxSteps() : defaults.getMaxSteps())
      .duplicateThreshold(defaults.getDuplicateThreshold())
      .toolChoice(
        req.getToolChoice() != null ? req.getToolChoice() : defaults.getToolChoiceDefault())
      .status(ReActSessionStatus.ACTIVE)
      .version(0)
      .build()
      .toEntity();

    List<String> enabledMcpServers = null;
    if (req.getAgentType() == ReActAgentType.MCP && req.getEnabledMcpServers() != null) {
      enabledMcpServers = req.getEnabledMcpServers();
    }

    sessionRepository.create(record, enabledMcpServers);

    log.info("Created session: conversationId={}, agentType={}", conversationId,
      req.getAgentType());
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
  public static class CreateSessionRequest {

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
