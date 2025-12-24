package com.github.spud.sample.ai.agent.domain.state;

import com.github.spud.sample.ai.agent.domain.kernel.AgentContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 状态机驱动器 - Kernel 与 StateMachine 的适配层
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StateMachineDriver {

  private final StateMachineFactory<AgentState, AgentEvent> stateMachineFactory;

  /**
   * 为新的会话创建状态机实例
   */
  public StateMachine<AgentState, AgentEvent> create(String machineId) {
    StateMachine<AgentState, AgentEvent> sm = stateMachineFactory.getStateMachine(machineId);
    sm.startReactively().block();
    return sm;
  }

  /**
   * 获取当前状态
   */
  public AgentState getCurrentState(StateMachine<AgentState, AgentEvent> sm) {
    return sm.getState().getId();
  }

  /**
   * 发送事件并等待状态转换完成
   */
  public boolean sendEvent(StateMachine<AgentState, AgentEvent> sm, AgentEvent event) {
    log.debug("Sending event {} to state machine, current state: {}", event, getCurrentState(sm));

    StateMachineEventResult<AgentState, AgentEvent> result = sm
      .sendEvent(Mono.just(org.springframework.messaging.support.MessageBuilder
        .withPayload(event)
        .build()))
      .blockFirst();

    boolean accepted = result != null &&
      result.getResultType() == StateMachineEventResult.ResultType.ACCEPTED;

    if (accepted) {
      log.debug("Event {} accepted, new state: {}", event, getCurrentState(sm));
    } else {
      log.warn("Event {} rejected in state {}", event, getCurrentState(sm));
    }

    return accepted;
  }

  /**
   * 发送事件并带上下文
   */
  public boolean sendEvent(StateMachine<AgentState, AgentEvent> sm, AgentEvent event,
    AgentContext ctx) {
    // 可以在这里将 ctx 放入 extended state
    sm.getExtendedState().getVariables().put("agentContext", ctx);
    return sendEvent(sm, event);
  }

  /**
   * 停止状态机
   */
  public void stop(StateMachine<AgentState, AgentEvent> sm) {
    sm.stopReactively().block();
  }

  /**
   * 判断是否处于终态
   */
  public boolean isInFinalState(StateMachine<AgentState, AgentEvent> sm) {
    return AgentState.isFinal(getCurrentState(sm));
  }
}
