package com.github.spud.sample.ai.agent.domain.react.session;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Default configuration properties for React Agent sessions
 */
@Component
@Getter
public class ReActAgentDefaultsProperties {

  @Value("${app.agent.system-prompt:You are an intelligent AI agent.}")
  private String systemPrompt;

  @Value("${app.agent.next-step-prompt:What is the next step to take?}")
  private String nextStepPrompt;

  @Value("${app.agent.max-steps:15}")
  private Integer maxSteps;

  @Value("${app.agent.duplicate-threshold:3}")
  private Integer duplicateThreshold;

  @Value("${app.model.provider:openai}")
  private String modelProvider;

  @Value("${app.agent.tool-choice-default:AUTO}")
  private String toolChoiceDefault;
}
