package com.github.spud.sample.ai.agent.interfaces.rest;

import com.github.spud.sample.ai.agent.domain.session.ReActAgentType;
import com.github.spud.sample.ai.agent.domain.session.ReActSessionService;
import com.github.spud.sample.ai.agent.domain.session.ReActSessionService.CreateAgentRequest;
import com.github.spud.sample.ai.agent.domain.session.ReActSessionService.CreateSessionRequest;
import com.github.spud.sample.ai.agent.domain.session.ReActSessionService.SendMessageResponse;
import com.github.spud.sample.ai.agent.domain.session.ReActSessionService.SessionNotFoundException;
import com.github.spud.sample.ai.agent.domain.session.ReActSessionService.VersionConflictException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * ReAct Agent Api
 */
@Slf4j
@RestController
@RequestMapping("/agent/react")
@RequiredArgsConstructor
public class ReActAgentController {

  private final ReActSessionService sessionService;

  /**
   * 创建一个新的 ReAct Agent
   */
  @PostMapping("/agent/new")
  public Mono<ResponseEntity<CreateAgentResponse>> createAgent(
    @Validated @RequestBody CreateAgentRequestDto request
  ) {
    return Mono.fromCallable(() -> {
        log.info("Creating agent: agentType={}", request.getAgentType());

        CreateAgentRequest agentRequest = CreateAgentRequest.builder()
          .name(request.getName())
          .description(request.getDescription())
          .duplicateThreshold(request.getDuplicateThreshold())
          .modelProvider(request.getModelProvider())
          .agentType(request.getAgentType())
          .systemPrompt(request.getSystemPrompt())
          .nextStepPrompt(request.getNextStepPrompt())
          .maxSteps(request.getMaxSteps())
          .toolChoice(request.getToolChoice())
          .build();

        String agentId = sessionService.createAgent(agentRequest);

        return ResponseEntity.ok(new CreateAgentResponse(agentId));
      })
      .subscribeOn(Schedulers.boundedElastic())
      .onErrorResume(e -> {
        log.error("Failed to create agent", e);
        return Mono.just(ResponseEntity.badRequest().build());
      });
  }

  /**
   * 发起一个新的 ReAct 会话
   */
  @PostMapping("/session/new")
  public Mono<ResponseEntity<CreateSessionResponse>> createSession(
    @Validated @RequestBody CreateSessionRequestDto request
  ) {
    return Mono.fromCallable(() -> {
        log.info("Creating session: agentId={}", request.getAgentId());

        CreateSessionRequest serviceRequest = new CreateSessionRequest();
        serviceRequest.setAgentId(request.getAgentId());
        serviceRequest.setEnabledMcpServers(request.getEnabledMcpServers());

        String conversationId = sessionService.createSession(serviceRequest);

        return ResponseEntity.ok(new CreateSessionResponse(conversationId));
      })
      .subscribeOn(Schedulers.boundedElastic())
      .onErrorResume(e -> {
        log.error("Failed to create session", e);
        return Mono.just(ResponseEntity.badRequest().build());
      });
  }

  @GetMapping("/session/list")
  public Mono<ResponseEntity<List<String>>> listSessions() {
    List<String> sessions = sessionService.listSessions();
    return Mono.just(ResponseEntity.ok(sessions));
  }

  /**
   * 发送消息到 ReAct 会话
   */
  @PostMapping("/session/{conversationId}/messages")
  public Mono<ResponseEntity<SendMessageResponse>> sendMessage(
    @PathVariable String conversationId,
    @Validated @RequestBody SendMessageRequestDto request
  ) {
    log.info("Sending message to conversationId={}, content length={}",
      conversationId, request.getContent() != null ? request.getContent().length() : 0);
    
    // Call service directly (no subscribeOn) - this runs on HTTP thread where @Transactional works
    return sessionService.sendMessage(conversationId, request.getContent())
      .map(ResponseEntity::ok)
      .onErrorResume(SessionNotFoundException.class, e -> {
        log.warn("Session not found: {}", conversationId);
        return Mono.just(ResponseEntity.notFound().build());
      })
      .onErrorResume(VersionConflictException.class, e -> {
        log.warn("Version conflict for conversationId: {}", conversationId);
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
      })
      .onErrorResume(e -> {
        log.error("Failed to send message to conversationId: {}", conversationId, e);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
      });
  }

  // ===== DTOs =====

  @Data
  public static class CreateAgentRequestDto {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Description is required")
    private String description;
    private Integer duplicateThreshold;

    @NotNull
    private ReActAgentType agentType;
    private String modelProvider;
    private String systemPrompt;
    private String nextStepPrompt;
    private Integer maxSteps;
    private String toolChoice;
  }

  @Data
  @AllArgsConstructor
  public static class CreateAgentResponse {

    private String agentId;
  }

  @Data
  public static class CreateSessionRequestDto {

    private String agentId;
    private List<String> enabledMcpServers;
  }

  @Data
  @AllArgsConstructor
  public static class CreateSessionResponse {

    private String conversationId;
  }

  @Data
  public static class SendMessageRequestDto {

    private String content;
  }
}