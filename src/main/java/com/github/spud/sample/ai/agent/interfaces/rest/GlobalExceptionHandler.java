package com.github.spud.sample.ai.agent.interfaces.rest;

import com.github.spud.sample.ai.agent.domain.session.ReActSessionService.SessionNotFoundException;
import com.github.spud.sample.ai.agent.domain.session.ReActSessionService.VersionConflictException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @Data
  @Builder
  public static class ErrorResponse {
    private String code;
    private String message;
    private OffsetDateTime timestamp;
    private Map<String, Object> details;
  }

  @ExceptionHandler(SessionNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleSessionNotFound(SessionNotFoundException e) {
    ErrorResponse error = ErrorResponse.builder()
        .code("SESSION_NOT_FOUND")
        .message(e.getMessage())
        .timestamp(OffsetDateTime.now())
        .build();
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  @ExceptionHandler(VersionConflictException.class)
  public ResponseEntity<ErrorResponse> handleVersionConflict(VersionConflictException e) {
    ErrorResponse error = ErrorResponse.builder()
        .code("VERSION_CONFLICT")
        .message(e.getMessage())
        .timestamp(OffsetDateTime.now())
        .build();
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<ErrorResponse> handleWebExchangeBind(WebExchangeBindException e) {
    Map<String, String> fieldErrors = new HashMap<>();
    for (FieldError error : e.getBindingResult().getFieldErrors()) {
      fieldErrors.put(error.getField(), error.getDefaultMessage());
    }

    ErrorResponse error = ErrorResponse.builder()
        .code("VALIDATION_ERROR")
        .message("Request validation failed")
        .timestamp(OffsetDateTime.now())
        .details(Map.of("fieldErrors", fieldErrors))
        .build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
    Map<String, String> fieldErrors = new HashMap<>();
    for (FieldError error : e.getBindingResult().getFieldErrors()) {
      fieldErrors.put(error.getField(), error.getDefaultMessage());
    }

    ErrorResponse error = ErrorResponse.builder()
        .code("VALIDATION_ERROR")
        .message("Request validation failed")
        .timestamp(OffsetDateTime.now())
        .details(Map.of("fieldErrors", fieldErrors))
        .build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
    ErrorResponse error = ErrorResponse.builder()
        .code("INVALID_ARGUMENT")
        .message(e.getMessage())
        .timestamp(OffsetDateTime.now())
        .build();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
    log.error("Unhandled exception", e);
    ErrorResponse error = ErrorResponse.builder()
        .code("INTERNAL_ERROR")
        .message("An unexpected error occurred")
        .timestamp(OffsetDateTime.now())
        .details(createDetailsMap(e))
        .build();
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }

  private Map<String, Object> createDetailsMap(Exception e) {
    Map<String, Object> details = new HashMap<>();
    details.put("exception", e.getClass().getSimpleName());
    details.put("message", e.getMessage());
    if (e.getCause() != null) {
      details.put("cause", e.getCause().getMessage());
    }
    return details;
  }
}