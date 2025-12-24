package com.github.spud.sample.ai.agent.domain.tools;

import com.github.spud.sample.ai.agent.domain.kernel.StepRecord;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

/**
 * 工具执行服务 统一捕获异常并返回结构化结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutionService {

  private final ToolRegistry toolRegistry;

  /**
   * 执行工具调用
   */
  public ToolExecutionResult execute(String toolName, String arguments) {
    long startTime = System.currentTimeMillis();

    try {
      ToolCallback callback = toolRegistry.getCallback(toolName)
        .orElseThrow(() -> new ToolNotFoundException("Tool not found: " + toolName));

      log.debug("Executing tool: {} with args: {}", toolName, arguments);
      String result = callback.call(arguments);

      long duration = System.currentTimeMillis() - startTime;
      log.debug("Tool {} completed in {}ms", toolName, duration);

      return ToolExecutionResult.builder()
        .toolName(toolName)
        .arguments(arguments)
        .result(result)
        .success(true)
        .durationMs(duration)
        .timestamp(Instant.now())
        .build();

    } catch (ToolNotFoundException e) {
      log.error("Tool not found: {}", toolName);
      return ToolExecutionResult.builder()
        .toolName(toolName)
        .arguments(arguments)
        .success(false)
        .error(e.getMessage())
        .durationMs(System.currentTimeMillis() - startTime)
        .timestamp(Instant.now())
        .build();

    } catch (Exception e) {
      log.error("Tool execution failed: {} - {}", toolName, e.getMessage(), e);
      return ToolExecutionResult.builder()
        .toolName(toolName)
        .arguments(arguments)
        .success(false)
        .error(e.getMessage())
        .durationMs(System.currentTimeMillis() - startTime)
        .timestamp(Instant.now())
        .build();
    }
  }

  /**
   * 将执行结果转换为 StepRecord 格式
   */
  public StepRecord.ToolResultRecord toResultRecord(String toolCallId, ToolExecutionResult result) {
    return StepRecord.ToolResultRecord.builder()
      .toolCallId(toolCallId)
      .toolName(result.getToolName())
      .result(result.getResult())
      .success(result.isSuccess())
      .error(result.getError())
      .build();
  }

  @Data
  @Builder
  public static class ToolExecutionResult {

    private String toolName;
    private String arguments;
    private String result;
    private boolean success;
    private String error;
    private long durationMs;
    private Instant timestamp;
  }

  public static class ToolNotFoundException extends RuntimeException {

    public ToolNotFoundException(String message) {
      super(message);
    }
  }
}
