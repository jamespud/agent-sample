package com.github.spud.sample.ai.agent.domain.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * RAG 数据摄取服务 负责文档加载、切分、向量化并存入 pgvector
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class RagIngestService {

  private final VectorStore vectorStore;
  private final RagProperties ragProperties;

  /**
   * 摄取单个文件
   */
  public int ingestFile(Path filePath, Map<String, Object> metadata) {
    log.info("Ingesting file: {}", filePath);

    Resource resource = new FileSystemResource(filePath);
    DocumentReader reader = new TextReader(resource);
    List<Document> documents = reader.get();

    // 添加元数据
    for (Document doc : documents) {
      doc.getMetadata().put("source", filePath.toString());
      doc.getMetadata().put("filename", filePath.getFileName().toString());
      if (metadata != null) {
        doc.getMetadata().putAll(metadata);
      }
    }

    return ingestDocuments(documents);
  }

  /**
   * 摄取目录下所有文本文件
   */
  public int ingestDirectory(Path directory, Map<String, Object> metadata) throws IOException {
    log.info("Ingesting directory: {}", directory);

    List<Document> allDocuments = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(directory)) {
      paths.filter(Files::isRegularFile)
        .filter(p -> isTextFile(p.toString()))
        .forEach(filePath -> {
          try {
            Resource resource = new FileSystemResource(filePath);
            DocumentReader reader = new TextReader(resource);
            List<Document> docs = reader.get();

            for (Document doc : docs) {
              doc.getMetadata().put("source", filePath.toString());
              doc.getMetadata().put("filename", filePath.getFileName().toString());
              if (metadata != null) {
                doc.getMetadata().putAll(metadata);
              }
            }

            allDocuments.addAll(docs);
          } catch (Exception e) {
            log.warn("Failed to read file: {} - {}", filePath, e.getMessage());
          }
        });
    }

    return ingestDocuments(allDocuments);
  }

  /**
   * 摄取文档列表
   */
  public int ingestDocuments(List<Document> documents) {
    if (documents.isEmpty()) {
      log.warn("No documents to ingest");
      return 0;
    }

    log.info("Processing {} documents for ingestion", documents.size());

    // 文档切分
    TokenTextSplitter splitter = new TokenTextSplitter(
      ragProperties.getChunkSize(),
      ragProperties.getChunkOverlap(),
      5,
      10000,
      true
    );

    List<Document> chunks = splitter.apply(documents);
    log.info("Split into {} chunks", chunks.size());

    // 存入向量库
    vectorStore.add(chunks);

    log.info("Successfully ingested {} chunks into vector store", chunks.size());
    return chunks.size();
  }

  /**
   * 直接摄取文本内容
   */
  public int ingestText(String content, Map<String, Object> metadata) {
    Document doc = new Document(content, metadata != null ? metadata : Map.of());
    return ingestDocuments(List.of(doc));
  }

  private boolean isTextFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".txt") ||
      lower.endsWith(".md") ||
      lower.endsWith(".java") ||
      lower.endsWith(".py") ||
      lower.endsWith(".js") ||
      lower.endsWith(".ts") ||
      lower.endsWith(".json") ||
      lower.endsWith(".xml") ||
      lower.endsWith(".yaml") ||
      lower.endsWith(".yml") ||
      lower.endsWith(".html") ||
      lower.endsWith(".css");
  }
}
