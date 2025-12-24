package com.github.spud.sample.ai.agent.kernel.protocol;

import com.github.spud.sample.ai.agent.util.JsonUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ReAct JSON 协议解析器
 * <p>
 * 负责从模型文本输出中提取并解析 JSON 对象，支持： - 去除 markdown 代码块包裹（```json ... ```） - 从混合文本中提取第一个完整 JSON 对象 -
 * 验证结构合法性
 */
@Slf4j
@Component
public class ReactJsonParser {

  // 匹配代码块包裹的 JSON（贪婪匹配到第一个闭合 ```）
  private static final Pattern CODE_BLOCK_PATTERN =
    Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

  // 匹配第一个完整的 JSON 对象（简化版：匹配 { ... }，处理嵌套）
  private static final Pattern JSON_OBJECT_PATTERN =
    Pattern.compile("\\{[^{}]*(?:\\{[^{}]*}[^{}]*)*}");

  /**
   * 解析模型输出文本为 ReactJsonStep
   *
   * @param modelText 模型原始输出
   * @return 解析后的步骤对象
   * @throws ReactJsonParseException 解析失败
   */
  public ReactJsonStep parse(String modelText) throws ReactJsonParseException {
    if (modelText == null || modelText.isBlank()) {
      throw new ReactJsonParseException("Model output is empty or null", modelText);
    }

    String cleaned = modelText.trim();
    log.debug("Parsing model text (length={}): {}", cleaned.length(),
      cleaned.length() > 200 ? cleaned.substring(0, 200) + "..." : cleaned);

    // 步骤1：尝试去除代码块包裹
    cleaned = removeCodeBlockWrapper(cleaned);

    // 步骤2：尝试提取第一个完整 JSON 对象
    String jsonText = extractFirstJsonObject(cleaned);
    if (jsonText == null) {
      throw new ReactJsonParseException(
        "No valid JSON object found in model output", modelText);
    }

    // 步骤3：反序列化为 ReactJsonStep
    try {
      ReactJsonStep step = JsonUtils.fromJson(jsonText, ReactJsonStep.class);

      // 步骤4：验证结构
      step.validate();

      log.debug("Successfully parsed ReactJsonStep: type={}, thought length={}",
        step.getAction() != null ? step.getAction().getType() : "null",
        step.getThought() != null ? step.getThought().length() : 0);

      return step;
    } catch (IllegalArgumentException e) {
      throw new ReactJsonParseException(
        "JSON structure validation failed: " + e.getMessage(), jsonText, e);
    } catch (Exception e) {
      throw new ReactJsonParseException(
        "JSON deserialization failed: " + e.getMessage(), jsonText, e);
    }
  }

  /**
   * 去除 markdown 代码块包裹
   */
  private String removeCodeBlockWrapper(String text) {
    Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
    if (matcher.find()) {
      String content = matcher.group(1).trim();
      log.debug("Removed code block wrapper, extracted content length: {}", content.length());
      return content;
    }
    return text;
  }

  /**
   * 从文本中提取第一个完整的 JSON 对象 简化实现：依赖正则表达式匹配 { ... } 复杂嵌套场景可能需要增强解析器
   */
  private String extractFirstJsonObject(String text) {
    // 先尝试直接解析整个文本（可能就是纯 JSON）
    if (text.startsWith("{") && text.endsWith("}")) {
      try {
        // 快速验证是否为合法 JSON
        JsonUtils.readTree(text);
        return text;
      } catch (Exception e) {
        log.debug("Text looks like JSON but parse failed, will try extraction: {}",
          e.getMessage());
      }
    }

    // 使用正则表达式提取
    Matcher matcher = JSON_OBJECT_PATTERN.matcher(text);
    if (matcher.find()) {
      String candidate = matcher.group();
      try {
        // 验证提取出的片段是否为合法 JSON
        JsonUtils.readTree(candidate);
        log.debug("Extracted JSON object from mixed text, length: {}", candidate.length());
        return candidate;
      } catch (Exception e) {
        log.debug("Extracted candidate is not valid JSON: {}", e.getMessage());
      }
    }

    return null;
  }
}
