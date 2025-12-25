package com.github.spud.sample.ai.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaRepositories
@SpringBootApplication
public class AgentSampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentSampleApplication.class, args);
  }

}
