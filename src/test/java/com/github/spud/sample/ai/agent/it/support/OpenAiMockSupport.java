package com.github.spud.sample.ai.agent.it.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * OpenAI API MockWebServer for deterministic LLM responses
 * Allows scripted sequences of responses for each test scenario
 */
@Slf4j
public class OpenAiMockSupport {

  private static final MockWebServer MOCK_SERVER;
  private static final ObjectMapper JSON = new ObjectMapper();

  // Queue of responses (thread-safe via synchronized access)
  private static final List<MockResponse> RESPONSE_QUEUE = new ArrayList<>();

  static {
    MOCK_SERVER = new MockWebServer();
    MOCK_SERVER.setDispatcher(new OpenAiDispatcher());

    try {
      MOCK_SERVER.start();
      log.info("MockWebServer started at {}", MOCK_SERVER.url("/"));
    } catch (IOException e) {
      throw new RuntimeException("Failed to start MockWebServer", e);
    }
  }

  /**
   * Enqueue a scripted response for next chat completion call
   */
  public static synchronized void enqueueResponse(MockResponse response) {
    RESPONSE_QUEUE.add(response);
    log.debug("Enqueued mock response (total queued: {})", RESPONSE_QUEUE.size());
  }

  /**
   * Clear response queue (call before each test to ensure isolation)
   */
  public static synchronized void clearQueue() {
    RESPONSE_QUEUE.clear();
    log.debug("Cleared response queue");
  }

  /**
   * Get MockWebServer instance for advanced assertions
   */
  public static MockWebServer getServer() {
    return MOCK_SERVER;
  }

  /**
   * Builder for OpenAI chat completion responses
   */
  @Getter
  public static class ResponseBuilder {

    private String content = "";
    private List<Map<String, Object>> toolCalls = new ArrayList<>();
    private String model = "gpt-4o-test";
    private String finishReason = "stop";

    public ResponseBuilder withContent(String content) {
      this.content = content;
      return this;
    }

    public ResponseBuilder withToolCall(String id, String name, String arguments) {
      Map<String, Object> toolCall = new HashMap<>();
      toolCall.put("id", id);
      toolCall.put("type", "function");
      Map<String, Object> function = new HashMap<>();
      function.put("name", name);
      function.put("arguments", arguments);
      toolCall.put("function", function);
      this.toolCalls.add(toolCall);
      this.finishReason = "tool_calls";
      return this;
    }

    public ResponseBuilder withFinishReason(String reason) {
      this.finishReason = reason;
      return this;
    }

    public MockResponse build() {
      try {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        if (!toolCalls.isEmpty()) {
          message.put("tool_calls", toolCalls);
        }

        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", finishReason);

        Map<String, Object> response = new HashMap<>();
        response.put("id", "chatcmpl-test-" + System.nanoTime());
        response.put("object", "chat.completion");
        response.put("created", System.currentTimeMillis() / 1000);
        response.put("model", model);
        response.put("choices", List.of(choice));

        Map<String, Object> usage = new HashMap<>();
        usage.put("prompt_tokens", 10);
        usage.put("completion_tokens", 20);
        usage.put("total_tokens", 30);
        response.put("usage", usage);

        String json = JSON.writeValueAsString(response);
        return new MockResponse()
          .setResponseCode(200)
          .setBody(json)
          .addHeader("Content-Type", "application/json");
      } catch (Exception e) {
        throw new RuntimeException("Failed to build mock response", e);
      }
    }
  }

  /**
   * Dispatcher that validates requests and returns queued responses
   */
  private static class OpenAiDispatcher extends Dispatcher {
    
    private int requestCount = 0;

    @NotNull
    @Override
    public MockResponse dispatch(@NotNull RecordedRequest request) {
      requestCount++;
      String path = request.getPath();
      String method = request.getMethod();

      log.info("📥 Request #{}: {} {}", requestCount, method, path);
      
      // Read and log request body
      try {
        String body = request.getBody().readUtf8();
        if (body.contains("\"stream\"")) {
          log.warn("⚠️  Request contains 'stream' parameter!");
        }
        if (requestCount <= 3) {
          log.info("Request body snippet: {}", body.substring(0, Math.min(300, body.length())));
        }
      } catch (Exception e) {
        log.debug("Could not read request body: {}", e.getMessage());
      }

      // Validate request path (Spring AI appends /v1/chat/completions to base-url)
      // But some clients may send just /chat/completions, so we allow both
      if (path == null || !(path.contains("/chat/completions"))) {
        log.error("Invalid request path: {}. Expected to contain /chat/completions", path);
        return new MockResponse()
          .setResponseCode(404)
          .setBody("{\"error\": {\"message\": \"Invalid path: " + path + "\"}}");
      }

      // Validate method
      if (!"POST".equals(method)) {
        log.error("Invalid request method: {}. Expected POST", method);
        return new MockResponse()
          .setResponseCode(405)
          .setBody("{\"error\": {\"message\": \"Method not allowed\"}}");
      }

      // Return queued response or error
      synchronized (OpenAiMockSupport.class) {
        if (RESPONSE_QUEUE.isEmpty()) {
          log.error("No mock responses queued for this request!");
          return new MockResponse()
            .setResponseCode(500)
            .setBody("{\"error\": {\"message\": \"No mock response configured\"}}");
        }

        MockResponse response = RESPONSE_QUEUE.remove(0);
        log.debug("Returning mock response (remaining: {})", RESPONSE_QUEUE.size());
        return response;
      }
    }
  }
}
