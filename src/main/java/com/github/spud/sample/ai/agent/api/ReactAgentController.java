package com.github.spud.sample.ai.agent.api;

import com.github.spud.sample.ai.agent.react.session.ReactAgentType;
import com.github.spud.sample.ai.agent.react.session.ReactSessionService;
import com.github.spud.sample.ai.agent.react.session.ReactSessionService.CreateSessionRequest;
import com.github.spud.sample.ai.agent.react.session.ReactSessionService.SendMessageResponse;
import com.github.spud.sample.ai.agent.react.session.ReactSessionService.SessionNotFoundException;
import com.github.spud.sample.ai.agent.react.session.ReactSessionService.VersionConflictException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * REST API for React Agent sessions
 */
@Slf4j
@RestController
@RequestMapping("/agent/react")
@RequiredArgsConstructor
public class ReactAgentController {

  private final ReactSessionService sessionService;

  /**
   * Create a new React Agent session
   */
  @PostMapping("/sessions")
  public Mono<ResponseEntity<CreateSessionResponse>> createSession(
    @RequestBody CreateSessionRequestDto request
  ) {
    return Mono.fromCallable(() -> {
        log.info("Creating session: agentType={}", request.getAgentType());

        CreateSessionRequest serviceRequest = new CreateSessionRequest();
        serviceRequest.setAgentType(request.getAgentType());
        serviceRequest.setModelProvider(request.getModelProvider());
        serviceRequest.setMaxSteps(request.getMaxSteps());
        serviceRequest.setToolChoice(request.getToolChoice());
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

  /**
   * Send a message to an existing session
   */
  @PostMapping("/sessions/{conversationId}/messages")
  public Mono<ResponseEntity<SendMessageResponse>> sendMessage(
    @PathVariable String conversationId,
    @RequestBody SendMessageRequestDto request
  ) {
    return Mono.fromCallable(() -> {
        log.info("Sending message to conversationId={}, content length={}",
          conversationId, request.getContent() != null ? request.getContent().length() : 0);

        SendMessageResponse response = sessionService.sendMessage(
          conversationId,
          request.getContent()
        );

        return ResponseEntity.ok(response);
      })
      .subscribeOn(Schedulers.boundedElastic())
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
  public static class CreateSessionRequestDto {

    private ReactAgentType agentType;
    private String modelProvider;
    private Integer maxSteps;
    private String toolChoice;
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