package com.github.spud.sample.ai.agent.kernel.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.spud.sample.ai.agent.domain.kernel.protocol.ReactJsonParseException;
import com.github.spud.sample.ai.agent.domain.kernel.protocol.ReactJsonParser;
import com.github.spud.sample.ai.agent.domain.kernel.protocol.ReactJsonStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ReactJsonParser 单元测试
 */
class ReactJsonParserTest {

  private ReactJsonParser parser;

  @BeforeEach
  void setUp() {
    parser = new ReactJsonParser();
  }

  @Test
  void shouldParsePureJson() throws Exception {
    String json = """
      {
        "thought": "I need to search for information",
        "action": {
          "type": "tool",
          "name": "search",
          "args": {"query": "test"}
        }
      }
      """;

    ReactJsonStep step = parser.parse(json);

    assertThat(step.getThought()).isEqualTo("I need to search for information");
    assertThat(step.getAction().getType()).isEqualTo("tool");
    assertThat(step.getAction().getName()).isEqualTo("search");
    assertThat(step.getAction().getArgs().get("query").asText()).isEqualTo("test");
  }

  @Test
  void shouldParseJsonWithCodeBlock() throws Exception {
    String text = """
      ```json
      {
        "thought": "Final answer",
        "action": {
          "type": "final",
          "answer": "The answer is 42"
        }
      }
      ```
      """;

    ReactJsonStep step = parser.parse(text);

    assertThat(step.getThought()).isEqualTo("Final answer");
    assertThat(step.getAction().getType()).isEqualTo("final");
    assertThat(step.getAction().getAnswer()).isEqualTo("The answer is 42");
  }

  @Test
  void shouldParseJsonFromMixedText() throws Exception {
    String text = """
      Here is my response:
      {
        "thought": "No action needed",
        "action": {"type": "none"}
      }
      End of response
      """;

    ReactJsonStep step = parser.parse(text);

    assertThat(step.getThought()).isEqualTo("No action needed");
    assertThat(step.getAction().getType()).isEqualTo("none");
  }

  @Test
  void shouldThrowExceptionOnEmptyText() {
    assertThatThrownBy(() -> parser.parse(""))
      .isInstanceOf(ReactJsonParseException.class)
      .hasMessageContaining("empty or null");
  }

  @Test
  void shouldThrowExceptionOnInvalidJson() {
    String invalid = "This is not JSON at all";

    assertThatThrownBy(() -> parser.parse(invalid))
      .isInstanceOf(ReactJsonParseException.class)
      .hasMessageContaining("No valid JSON object found");
  }

  @Test
  void shouldThrowExceptionOnMissingAction() {
    String json = """
      {
        "thought": "Missing action field"
      }
      """;

    assertThatThrownBy(() -> parser.parse(json))
      .isInstanceOf(ReactJsonParseException.class)
      .hasMessageContaining("Action is required");
  }

  @Test
  void shouldThrowExceptionOnInvalidActionType() {
    String json = """
      {
        "thought": "Invalid action",
        "action": {
          "type": "invalid_type"
        }
      }
      """;

    assertThatThrownBy(() -> parser.parse(json))
      .isInstanceOf(ReactJsonParseException.class)
      .hasMessageContaining("Unknown action type");
  }

  @Test
  void shouldThrowExceptionOnToolWithoutName() {
    String json = """
      {
        "thought": "Tool without name",
        "action": {
          "type": "tool",
          "args": {}
        }
      }
      """;

    assertThatThrownBy(() -> parser.parse(json))
      .isInstanceOf(ReactJsonParseException.class)
      .hasMessageContaining("Tool name is required");
  }

  @Test
  void shouldThrowExceptionOnFinalWithoutAnswer() {
    String json = """
      {
        "thought": "Final without answer",
        "action": {
          "type": "final"
        }
      }
      """;

    assertThatThrownBy(() -> parser.parse(json))
      .isInstanceOf(ReactJsonParseException.class)
      .hasMessageContaining("Answer is required");
  }

  @Test
  void shouldParseToolActionWithComplexArgs() throws Exception {
    String json = """
      {
        "thought": "Search with filters",
        "action": {
          "type": "tool",
          "name": "advanced_search",
          "args": {
            "query": "machine learning",
            "filters": {
              "year": 2023,
              "tags": ["AI", "ML"]
            }
          }
        }
      }
      """;

    ReactJsonStep step = parser.parse(json);

    assertThat(step.getAction().getType()).isEqualTo("tool");
    assertThat(step.getAction().getName()).isEqualTo("advanced_search");
    assertThat(step.getAction().getArgs().get("query").asText()).isEqualTo("machine learning");
    assertThat(step.getAction().getArgs().get("filters").get("year").asInt()).isEqualTo(2023);
  }
}
