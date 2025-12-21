package com.github.spud.sample.ai.agent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.sample.ai.agent.tools.ToolRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RAG 检索工具 作为 Agent 工具注册，供 LLM 调用进行知识检索
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class RagRetrieveTool {

  private final VectorStore vectorStore;
  private final ToolRegistry toolRegistry;
  private final RagProperties ragProperties;
  private final RetrievalCache retrievalCache;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @PostConstruct
  public void register() {
    log.info("Registering RAG retrieve tool");

    ToolDefinition definition = DefaultToolDefinition.builder()
      .name("retrieve_knowledge")
      .description(
        "Search the knowledge base for relevant information. Use this when you need to find specific information or context to answer a question.")
      .inputSchema("""
        {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "The search query to find relevant documents"
                },
                "topK": {
                    "type": "integer",
                    "description": "Number of documents to return (default: 5)"
                }
            },
            "required": ["query"]
        }
        """)
      .build();

    ToolCallback callback = new ToolCallback() {
      @Override
      public ToolDefinition getToolDefinition() {
        return definition;
      }

      @Override
      public String call(String toolInput) {
        return doRetrieve(toolInput);
      }
    };

    toolRegistry.register("retrieve_knowledge", definition, callback);
  }

  private String doRetrieve(String toolInput) {
    try {
      var node = objectMapper.readTree(toolInput);
      String query = node.path("query").asText();
      int topK = node.has("topK") ? node.path("topK").asInt() : ragProperties.getTopK();

      log.debug("RAG retrieve: query='{}', topK={}", query, topK);

      // 检查缓存
      var cached = retrievalCache.get(query, topK, null);
      if (cached.isPresent()) {
        return formatCachedResults(cached.get());
      }

      // 执行检索
      SearchRequest request = SearchRequest.builder()
        .query(query)
        .topK(topK)
        .build();

      log.debug("RAG search request: {}", request);
      List<Document> results = vectorStore.similaritySearch(request);

      // 缓存结果
      retrievalCache.put(query, topK, null, results);

      // 格式化返回
      return formatResults(results);

    } catch (Exception e) {
      log.error("RAG retrieve failed: {}", e.getMessage(), e);
      return "Error retrieving knowledge: " + e.getMessage();
    }
  }

  private String formatResults(List<Document> documents) {
    if (documents.isEmpty()) {
      return "No relevant documents found.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Found ").append(documents.size()).append(" relevant documents:\n\n");

    for (int i = 0; i < documents.size(); i++) {
      Document doc = documents.get(i);
      sb.append("--- Document ").append(i + 1).append(" ---\n");
      if (doc.getScore() != null) {
        sb.append("Score: ").append(String.format("%.4f", doc.getScore())).append("\n");
      }
      if (doc.getMetadata().containsKey("source")) {
        sb.append("Source: ").append(doc.getMetadata().get("source")).append("\n");
      }
      sb.append("Content:\n").append(doc.getText()).append("\n\n");
    }

    return sb.toString();
  }

  private String formatCachedResults(List<RetrievalCache.CachedDocument> documents) {
    if (documents.isEmpty()) {
      return "No relevant documents found.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Found ").append(documents.size()).append(" relevant documents (cached):\n\n");

    for (int i = 0; i < documents.size(); i++) {
      RetrievalCache.CachedDocument doc = documents.get(i);
      sb.append("--- Document ").append(i + 1).append(" ---\n");
      sb.append("Score: ").append(String.format("%.4f", doc.score())).append("\n");
      if (doc.metadata() != null && doc.metadata().containsKey("source")) {
        sb.append("Source: ").append(doc.metadata().get("source")).append("\n");
      }
      sb.append("Content:\n").append(doc.content()).append("\n\n");
    }

    return sb.toString();
  }
}
