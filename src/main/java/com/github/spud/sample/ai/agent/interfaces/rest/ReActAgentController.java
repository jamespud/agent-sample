package com.github.spud.sample.ai.agent.interfaces.rest;

import com.github.spud.sample.ai.agent.domain.react.session.ReActAgentType;
import com.github.spud.sample.ai.agent.domain.react.session.ReActSessionService;
import com.github.spud.sample.ai.agent.domain.react.session.ReActSessionService.CreateSessionRequest;
import com.github.spud.sample.ai.agent.domain.react.session.ReActSessionService.SendMessageResponse;
import com.github.spud.sample.ai.agent.domain.react.session.ReActSessionService.SessionNotFoundException;
import com.github.spud.sample.ai.agent.domain.react.session.ReActSessionService.VersionConflictException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
   * 发起一个新的 ReAct 会话
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

  @GetMapping("/sessions/list")
  public Mono<ResponseEntity<List<String>>> listSessions() {
    return Mono.fromCallable(() -> {
      log.info("Listing all ReAct sessions");
      return ResponseEntity.ok(List.of());
    });
  }

  /**
   * 发送消息到 ReAct 会话
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

    private ReActAgentType agentType;
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