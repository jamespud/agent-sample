package com.github.spud.sample.ai.agent.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * 问题增强器 对用户问题进行释义、补充上下文、结构化重写，用于后续 RAG 与 LLM 回答
 */
@Slf4j
@Component
public class QuestionEnhancer {

  private static final String ENHANCE_SYSTEM_PROMPT = """
    你是一个问题分析助手。请分析用户的问题，输出一个 JSON 对象，包含以下字段：
    
    1. "rephrased": 对原始问题的释义和改写，使其更清晰、更完整
    2. "keywords": 从问题中提取的关键词数组，用于检索
    3. "constraints": 问题中的约束条件对象，可能包含：
       - "time": 时间相关约束
       - "location": 地点相关约束
       - "entity": 涉及的实体（人物、组织、产品等）
       - "domain": 问题所属领域
    
    注意：
    - 只输出 JSON，不要有其他文字
    - 如果某个字段没有信息，可以省略或留空
    - keywords 应包含 3-5 个最相关的检索词
    
    示例输出：
    {
      "rephrased": "当前全球经济形势不佳的原因是什么？",
      "keywords": ["经济形势", "大环境", "经济下行", "原因分析"],
      "constraints": {
        "time": "当前",
        "domain": "经济"
      }
    }
    """;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * 增强用户问题
   */
  public EnhancementResult enhance(ChatClient chatClient, String userRequest) {
    log.debug("Enhancing question: {}", userRequest);

    try {
      Prompt prompt = new Prompt(List.of(
        new SystemMessage(ENHANCE_SYSTEM_PROMPT),
        new UserMessage(userRequest)
      ));

      ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
      String content = response.getResult().getOutput().getText();

      log.debug("Enhancement response: {}", content);

      return parseEnhancementResult(content, userRequest);

    } catch (Exception e) {
      log.warn("Question enhancement failed, using original: {}", e.getMessage());
      // 降级：返回原始问题
      return EnhancementResult.builder()
        .rephrased(userRequest)
        .keywords(List.of())
        .constraints(new HashMap<>())
        .original(userRequest)
        .build();
    }
  }

  private EnhancementResult parseEnhancementResult(String jsonContent, String original) {
    try {
      // 清理可能的 markdown 代码块标记
      String cleaned = jsonContent.trim();
      if (cleaned.startsWith("```json")) {
        cleaned = cleaned.substring(7);
      } else if (cleaned.startsWith("```")) {
        cleaned = cleaned.substring(3);
      }
      if (cleaned.endsWith("```")) {
        cleaned = cleaned.substring(0, cleaned.length() - 3);
      }
      cleaned = cleaned.trim();

      JsonNode node = objectMapper.readTree(cleaned);

      String rephrased = node.has("rephrased") ? node.get("rephrased").asText() : original;

      List<String> keywords = new ArrayList<>();
      if (node.has("keywords") && node.get("keywords").isArray()) {
        for (JsonNode kw : node.get("keywords")) {
          keywords.add(kw.asText());
        }
      }

      Map<String, String> constraints = new HashMap<>();
      if (node.has("constraints") && node.get("constraints").isObject()) {
        JsonNode constraintsNode = node.get("constraints");
        constraintsNode.fieldNames().forEachRemaining(field -> {
          JsonNode value = constraintsNode.get(field);
          if (value != null && !value.isNull()) {
            constraints.put(field, value.asText());
          }
        });
      }

      return EnhancementResult.builder()
        .rephrased(rephrased)
        .keywords(keywords)
        .constraints(constraints)
        .original(original)
        .build();

    } catch (Exception e) {
      log.warn("Failed to parse enhancement JSON: {}", e.getMessage());
      return EnhancementResult.builder()
        .rephrased(original)
        .keywords(List.of())
        .constraints(new HashMap<>())
        .original(original)
        .build();
    }
  }

  /**
   * 生成增强摘要消息（用于注入到对话历史）
   */
  public String toSummaryMessage(EnhancementResult result) {
    StringBuilder sb = new StringBuilder();
    sb.append("【问题分析】\n");
    sb.append("- 原始问题: ").append(result.getOriginal()).append("\n");
    sb.append("- 改写问题: ").append(result.getRephrased()).append("\n");

    if (!result.getKeywords().isEmpty()) {
      sb.append("- 关键词: ").append(String.join(", ", result.getKeywords())).append("\n");
    }

    if (!result.getConstraints().isEmpty()) {
      sb.append("- 约束条件: ");
      result.getConstraints().forEach((k, v) -> sb.append(k).append("=").append(v).append(" "));
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * 增强结果
   */
  @Data
  @Builder
  public static class EnhancementResult {

    private String original;
    private String rephrased;
    private List<String> keywords;
    private Map<String, String> constraints;

    /**
     * 获取用于 RAG 检索的 query（优先使用改写后的问题）
     */
    public String getSearchQuery() {
      if (rephrased != null && !rephrased.isBlank()) {
        return rephrased;
      }
      return original;
    }
  }
}
