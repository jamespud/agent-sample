package com.github.spud.sample.ai.agent.kernel;

import com.github.spud.sample.ai.agent.state.AgentState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Agent 一次会话的上下文，贯穿整个执行周期
 */
@Data
@Builder
public class AgentContext {

  /**
   * 会话唯一 ID
   */
  @Builder.Default
  private String conversationId = UUID.randomUUID().toString();

  /**
   * 追踪 ID（用于可观测性）
   */
  @Builder.Default
  private String traceId = UUID.randomUUID().toString();

  /**
   * 用户原始请求
   */
  private String userRequest;

  /**
   * 当前步数
   */
  @Builder.Default
  private int stepCounter = 0;

  /**
   * 最大步数
   */
  @Builder.Default
  private int maxSteps = 15;

  /**
   * 当前状态
   */
  @Builder.Default
  private AgentState currentState = AgentState.IDLE;

  /**
   * 最后一次 assistant 消息内容（用于重复/空检测）
   */
  private String lastAssistantContent;

  /**
   * 最后一次工具调用签名（用于重复检测）
   */
  private String lastToolCallSignature;

  /**
   * 连续空响应计数
   */
  @Builder.Default
  private int emptyResponseCount = 0;

  /**
   * 连续重复响应计数
   */
  @Builder.Default
  private int duplicateResponseCount = 0;

  /**
   * 空响应阈值
   */
  @Builder.Default
  private int emptyThreshold = 2;

  /**
   * 重复响应阈值
   */
  @Builder.Default
  private int duplicateThreshold = 3;

  /**
   * 选择的模型提供商
   */
  @Builder.Default
  private String modelProvider = "openai";

  /**
   * 是否启用 RAG
   */
  @Builder.Default
  private boolean ragEnabled = true;

  /**
   * RAG 无数据标志（用于降级处理）
   */
  @Builder.Default
  private boolean ragNoData = false;

  /**
   * 增强后的查询（用于 RAG 检索）
   */
  private String enhancedQuery;

  /**
   * 问题增强结果摘要
   */
  private String enhancementSummary;

  /**
   * 启用的 MCP server ID 列表
   */
  @Builder.Default
  private List<String> enabledMcpServers = new ArrayList<>();

  /**
   * 最终回答
   */
  private String finalAnswer;

  /**
   * 终止原因
   */
  private TerminationReason terminationReason;

  /**
   * 步骤记录（完整轨迹）
   */
  @Builder.Default
  private List<StepRecord> stepRecords = new ArrayList<>();

  /**
   * 扩展元数据
   */
  @Builder.Default
  private Map<String, Object> metadata = new HashMap<>();

  /**
   * 开始时间
   */
  @Builder.Default
  private Instant startTime = Instant.now();

  /**
   * 结束时间
   */
  private Instant endTime;

  /**
   * 增加步数
   */
  public int incrementStep() {
    return ++stepCounter;
  }

  /**
   * 添加步骤记录
   */
  public void addStepRecord(StepRecord record) {
    stepRecords.add(record);
  }

  /**
   * 终止原因枚举
   */
  public enum TerminationReason {
    COMPLETED,           // 正常完成
    MAX_STEPS,           // 达到最大步数
    EMPTY_RESPONSE,      // 连续空响应
    DUPLICATE_RESPONSE,  // 连续重复响应
    TOOL_TERMINATE,      // terminate 工具触发
    ERROR                // 执行错误
  }
}
