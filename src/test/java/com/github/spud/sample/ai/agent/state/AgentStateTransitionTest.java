package com.github.spud.sample.ai.agent.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.spud.sample.ai.agent.domain.state.AgentEvent;
import com.github.spud.sample.ai.agent.domain.state.AgentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;

/**
 * 状态机转换测试
 */
class AgentStateTransitionTest {

  private StateMachine<AgentState, AgentEvent> stateMachine;

  @BeforeEach
  void setUp() throws Exception {
    StateMachineBuilder.Builder<AgentState, AgentEvent> builder = StateMachineBuilder.builder();

    builder.configureStates()
      .withStates()
      .initial(AgentState.IDLE)
      .states(java.util.EnumSet.allOf(AgentState.class));

    builder.configureTransitions()
      // IDLE -> THINKING
      .withExternal()
      .source(AgentState.IDLE).target(AgentState.THINKING)
      .event(AgentEvent.START)
      .and()
      // THINKING -> ACTING (有工具调用)
      .withExternal()
      .source(AgentState.THINKING).target(AgentState.ACTING)
      .event(AgentEvent.THINK_DONE_WITH_TOOLS)
      .and()
      // THINKING -> FINISHED (无工具调用)
      .withExternal()
      .source(AgentState.THINKING).target(AgentState.FINISHED)
      .event(AgentEvent.THINK_DONE_NO_TOOLS)
      .and()
      // ACTING -> THINKING (继续循环)
      .withExternal()
      .source(AgentState.ACTING).target(AgentState.THINKING)
      .event(AgentEvent.ACT_DONE)
      .and()
      // ACTING -> FINISHED (工具返回终止)
      .withExternal()
      .source(AgentState.ACTING).target(AgentState.FINISHED)
      .event(AgentEvent.TOOL_TERMINATE)
      .and()
      // 终止转换
      .withExternal()
      .source(AgentState.THINKING).target(AgentState.FINISHED)
      .event(AgentEvent.STOP_MAX_STEPS)
      .and()
      .withExternal()
      .source(AgentState.THINKING).target(AgentState.FINISHED)
      .event(AgentEvent.STOP_EMPTY)
      .and()
      .withExternal()
      .source(AgentState.THINKING).target(AgentState.FINISHED)
      .event(AgentEvent.STOP_DUPLICATE)
      .and()
      // 错误转换
      .withExternal()
      .source(AgentState.THINKING).target(AgentState.ERROR)
      .event(AgentEvent.FAIL)
      .and()
      .withExternal()
      .source(AgentState.ACTING).target(AgentState.ERROR)
      .event(AgentEvent.FAIL);

    stateMachine = builder.build();
    stateMachine.start();
  }

  @Test
  void initialStateShouldBeIdle() {
    assertEquals(AgentState.IDLE, stateMachine.getState().getId());
  }

  @Test
  void shouldTransitionFromIdleToThinking() {
    stateMachine.sendEvent(AgentEvent.START);
    assertEquals(AgentState.THINKING, stateMachine.getState().getId());
  }

  @Test
  void shouldTransitionFromThinkingToActing() {
    stateMachine.sendEvent(AgentEvent.START);
    stateMachine.sendEvent(AgentEvent.THINK_DONE_WITH_TOOLS);
    assertEquals(AgentState.ACTING, stateMachine.getState().getId());
  }

  @Test
  void shouldTransitionFromThinkingToFinishedWithoutTools() {
    stateMachine.sendEvent(AgentEvent.START);
    stateMachine.sendEvent(AgentEvent.THINK_DONE_NO_TOOLS);
    assertEquals(AgentState.FINISHED, stateMachine.getState().getId());
  }

  @Test
  void shouldCycleThinkingAndActing() {
    stateMachine.sendEvent(AgentEvent.START);
    assertEquals(AgentState.THINKING, stateMachine.getState().getId());

    stateMachine.sendEvent(AgentEvent.THINK_DONE_WITH_TOOLS);
    assertEquals(AgentState.ACTING, stateMachine.getState().getId());

    stateMachine.sendEvent(AgentEvent.ACT_DONE);
    assertEquals(AgentState.THINKING, stateMachine.getState().getId());

    stateMachine.sendEvent(AgentEvent.THINK_DONE_WITH_TOOLS);
    assertEquals(AgentState.ACTING, stateMachine.getState().getId());
  }

  @Test
  void shouldTransitionToFinishedOnToolTerminate() {
    stateMachine.sendEvent(AgentEvent.START);
    stateMachine.sendEvent(AgentEvent.THINK_DONE_WITH_TOOLS);
    stateMachine.sendEvent(AgentEvent.TOOL_TERMINATE);
    assertEquals(AgentState.FINISHED, stateMachine.getState().getId());
  }

  @Test
  void shouldTransitionToFinishedOnMaxSteps() {
    stateMachine.sendEvent(AgentEvent.START);
    stateMachine.sendEvent(AgentEvent.STOP_MAX_STEPS);
    assertEquals(AgentState.FINISHED, stateMachine.getState().getId());
  }

  @Test
  void shouldTransitionToErrorOnFailure() {
    stateMachine.sendEvent(AgentEvent.START);
    stateMachine.sendEvent(AgentEvent.FAIL);
    assertEquals(AgentState.ERROR, stateMachine.getState().getId());
  }
}
