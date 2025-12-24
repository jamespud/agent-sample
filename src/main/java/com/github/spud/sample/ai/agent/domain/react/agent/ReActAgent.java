package com.github.spud.sample.ai.agent.domain.react.agent;

import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Mono;

@SuperBuilder
public abstract class ReActAgent extends BaseAgent {

  protected abstract Mono<Boolean> think();

  protected abstract Mono<String> act();

  @Override
  protected Mono<String> step() {
    return this.think()
      .defaultIfEmpty(false)
      .flatMap(shouldAct -> {
        if (!shouldAct) {
          return Mono.just("Thinking complete - no action needed");
        }
        return this.act();
      });
  }
}
