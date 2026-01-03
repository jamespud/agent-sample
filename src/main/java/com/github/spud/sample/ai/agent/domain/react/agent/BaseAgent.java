package com.github.spud.sample.ai.agent.domain.react.agent;

import com.github.spud.sample.ai.agent.domain.state.AgentState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Getter
@SuperBuilder
public abstract class BaseAgent {

  protected String name;

  protected String description;

  protected String systemPrompt;

  protected String nextStepPrompt;

  protected ChatClient chatClient;

  protected List<AbstractMessage> messages;

  @Builder.Default
  protected AgentState state = AgentState.IDLE;

  @Builder.Default
  protected Integer maxSteps = 5;

  @Builder.Default
  protected Integer currentStep = 0;

  @Builder.Default
  protected Integer duplicateThreshold = 3;

  protected String finalAnswer;

  public <T> Mono<T> stateContext(AgentState newState, Supplier<Mono<T>> work) {

    if (newState == null) {
      return Mono.error(new IllegalArgumentException("Invalid state: " + null));
    }
    AgentState previousState = this.state;
    this.state = newState;
    Mono<T> inner;
    try {
      inner = work.get();
    } catch (Exception e) {
      this.state = previousState;
      return Mono.error(e);
    }
    return inner
      .onErrorResume(e -> {
        this.state = AgentState.ERROR;
        return Mono.error(e);
      })
      .doFinally(signal -> this.state = previousState);
  }

  public Mono<String> run(String request) {
    if (this.state != AgentState.IDLE) {
      throw new IllegalStateException("Cannot run agent from state: " + this.state);
    }
    if (StringUtils.hasText(request)) {
      UserMessage userMessage = new UserMessage(request);
      this.messages.add(userMessage);
    }
    List<String> results = new ArrayList<>();

    return Mono.defer(() -> {
      this.state = AgentState.ACTING;

      return Flux.range(1, this.maxSteps)
        .concatMap(i -> {
          // Check FINISHED state at the start of each step
          if (this.state == AgentState.FINISHED) {
            log.info("Agent already finished, skipping step {}", i);
            return Mono.empty();
          }
          
          this.currentStep = i;
          log.info("Executing step {}/{}", this.currentStep, this.maxSteps);
          return this.step()
            .doOnNext(stepResult -> {
              if (isStuck()) {
                handleStuckState();
              }
              results.add("Step " + this.currentStep + ": " + stepResult);
            })
            .onErrorResume(e -> {
              results.add("Step " + this.currentStep + ": Error - " + e.getMessage());
              return Mono.empty();
            });
        })
        .then(Mono.fromCallable(() -> {
          if (this.currentStep >= this.maxSteps) {
            results.add("Terminated: Reached max steps (" + this.maxSteps + ")");
          }
          // Priority: return finalAnswer if set (terminate tool called)
          if (StringUtils.hasText(this.finalAnswer)) {
            return this.finalAnswer;
          }
          return results.isEmpty() ? "No steps executed" : String.join("\n", results);
        }))
        .doFinally(signal -> {
          cleanup();
          this.currentStep = 0;
          this.state = AgentState.IDLE;
          this.finalAnswer = null; // Reset finalAnswer for next run
        });
    });
  }

  protected abstract Mono<String> step();

  protected void cleanup() {
    log.info("Cleaning up resources for agent '{}'...", this.name);
    // Override in subclasses to clean up specific resources
  }

  protected void handleStuckState() {
    String stuckPrompt = "Observed duplicate responses. Consider new strategies and avoid repeating ineffective paths already attempted.";
    this.nextStepPrompt = stuckPrompt + "\n" + this.nextStepPrompt;
    log.warn("Agent detected stuck state. Added prompt: {}", stuckPrompt);
  }

  protected Boolean isStuck() {
    if (this.messages.size() < 2) {
      return false;
    }
    String lastResponse = this.messages.get(this.messages.size() - 1).getText();
    if (!StringUtils.hasText(lastResponse)) {
      return false;
    }
    int duplicateCount = 0;
    // Exclude the last message from counting (iterate up to size-2)
    for (int i = messages.size() - 2; i >= 0; i--) {
      Message message = messages.get(i);
      if (message.getMessageType().equals(MessageType.ASSISTANT) && lastResponse.equals(
        message.getText())) {
        duplicateCount++;
      }
    }
    return duplicateCount >= this.duplicateThreshold;
  }

}
