package com.github.spud.sample.ai.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Slf4j
@EnableJpaRepositories
@SpringBootApplication
public class AgentSampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentSampleApplication.class, args);
  }

  @Bean
  public ApplicationRunner startupValidator(
      @Value("${spring.ai.openai.api-key:}") String openaiApiKey,
      @Value("${app.model.provider:openai}") String modelProvider) {
    return args -> {
      if (modelProvider.equals("openai") && (openaiApiKey.isEmpty() || openaiApiKey.equals("sk-placeholder"))) {
        log.warn("Using default OpenAI API key. Please set OPENAI_API_KEY environment variable.");
      }
    };
  }
}
