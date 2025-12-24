package com.github.spud.sample.ai.agent.domain.react.session;

import com.github.spud.sample.ai.agent.domain.react.agent.BaseAgent;
import com.github.spud.sample.ai.agent.domain.react.session.repo.ReActAgentMessageRepository;
import com.github.spud.sample.ai.agent.domain.react.session.repo.ReActAgentSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
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
  private final ReActMessageMapper messageMapper;
  private final ReActAgentDefaultsProperties defaults;

  /**
   * Create a new session
   */
  @Transactional
  public String createSession(CreateSessionRequest req) {
    String conversationId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    ReActAgentSessionRecord record = ReActAgentSessionRecord.builder()
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
      .createdAt(now)
      .updatedAt(now)
      .build();

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
    ReActAgentSessionRecord session = sessionRepository.findByConversationId(conversationId)
      .orElseThrow(() -> new SessionNotFoundException("Session not found: " + conversationId));

    // Serialize concurrent requests for same conversation (optimistic locking)
    boolean versionBumped = sessionRepository.tryBumpVersion(conversationId, session.getVersion());
    if (!versionBumped) {
      throw new VersionConflictException(
        "Concurrent modification detected for conversationId: " + conversationId);
    }

    // Load history messages
    List<ReActAgentMessageRecord> historyRecords = messageRepository.listMessages(conversationId);
    List<Message> historyMessages = messageMapper.toMessages(historyRecords);

    int beforeSize = historyMessages.size();

    // Create agent instance (new instance, not singleton)
    BaseAgent agent = agentFactory.create(session, historyMessages);

    // Run agent (blocking)
    String answer = agent.run(content).block();

    // Calculate appended messages
    List<Message> appendedMessages = historyMessages.subList(beforeSize, historyMessages.size());

    // Convert to records and persist
    List<ReActAgentMessageRecord> appendedRecords = messageMapper.toRecords(
      conversationId,
      appendedMessages
    );

    messageRepository.appendMessages(conversationId, appendedRecords);

    // Determine if finished (check for terminate or finalAnswer presence)
    boolean finished = answer != null && !answer.isEmpty();

    log.info("Processed message: conversationId={}, appendedMessages={}, finished={}",
      conversationId, appendedRecords.size(), finished);

    return SendMessageResponse.builder()
      .conversationId(conversationId)
      .answer(answer)
      .finished(finished)
      .appendedMessages(toMessageDtos(appendedRecords))
      .build();
  }

  private List<MessageDto> toMessageDtos(List<ReActAgentMessageRecord> records) {
    return records.stream()
      .map(r -> MessageDto.builder()
        .messageType(r.getMessageType())
        .content(r.getContent())
        .toolCallId(r.getToolCallId())
        .toolName(r.getToolName())
        .toolArguments(r.getToolArguments())
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

    private ReActMessageType messageType;
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
