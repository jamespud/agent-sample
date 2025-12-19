package com.github.spud.sample.ai.agent.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 模型路由配置 基于配置选择使用 OpenAI 或 Ollama
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ModelRoutingConfig {

  @Value("${app.model.provider:openai}")
  private String modelProvider;

  /**
   * 主 ChatClient - 基于配置的 provider 选择
   */
  @Bean
  @Primary
  public ChatClient chatClient(
    @Qualifier("openAiChatModel") ChatModel openAiChatModel,
    @Qualifier("ollamaChatModel") ChatModel ollamaChatModel) {

    ChatModel selectedModel = selectChatModel(openAiChatModel, ollamaChatModel);
    log.info("Using {} as primary chat model", modelProvider);

    return ChatClient.builder(selectedModel).build();
  }

  /**
   * 根据 provider 选择 ChatModel
   */
  public ChatModel selectChatModel(ChatModel openAiChatModel, ChatModel ollamaChatModel) {
    switch (modelProvider.toLowerCase()) {
      case "ollama":
        return ollamaChatModel;
      default:
        return openAiChatModel;
    }
  }

  /**
   * 动态获取指定 provider 的 ChatClient
   */
  public ChatClient getChatClient(String provider, ChatModel openAiChatModel,
    ChatModel ollamaChatModel) {
    ChatModel model;
    switch (provider.toLowerCase()) {
      case "ollama":
        model = ollamaChatModel;
        break;
      default:
        model = openAiChatModel;
        break;
    }
    return ChatClient.builder(model).build();
  }

  @Primary
  @Bean("agentEmbeddingModel")
  public EmbeddingModel getEmbeddingModel(
    @Qualifier("openAiEmbeddingModel") EmbeddingModel openAiEmbeddingModel,
    @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbeddingModel) {
    if (modelProvider.equalsIgnoreCase("ollama")) {
      return ollamaEmbeddingModel;
    }
    return openAiEmbeddingModel;
  }
}
