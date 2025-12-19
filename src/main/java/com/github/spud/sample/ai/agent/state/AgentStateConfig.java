package com.github.spud.sample.ai.agent.state;

import java.util.EnumSet;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

/**
 * Agent 状态机配置
 * <pre>
 * 状态流转:
 *   IDLE --(START)--> THINKING
 *   THINKING --(THINK_DONE_WITH_TOOLS)--> ACTING
 *   THINKING --(THINK_DONE_NO_TOOLS)--> FINISHED
 *   THINKING --(STOP_*)--> FINISHED
 *   THINKING --(FAIL)--> ERROR
 *   ACTING --(ACT_DONE)--> THINKING
 *   ACTING --(TOOL_TERMINATE)--> FINISHED
 *   ACTING --(STOP_*)--> FINISHED
 *   ACTING --(FAIL)--> ERROR
 * </pre>
 */
@Configuration
@EnableStateMachineFactory
public class AgentStateConfig extends EnumStateMachineConfigurerAdapter<AgentState, AgentEvent> {

  @Override
  public void configure(StateMachineConfigurationConfigurer<AgentState, AgentEvent> config)
    throws Exception {
    config
      .withConfiguration()
      .autoStartup(false);
  }

  @Override
  public void configure(StateMachineStateConfigurer<AgentState, AgentEvent> states)
    throws Exception {
    states
      .withStates()
      .initial(AgentState.IDLE)
      .states(EnumSet.allOf(AgentState.class))
      .end(AgentState.FINISHED)
      .end(AgentState.ERROR);
  }

  @Override
  public void configure(StateMachineTransitionConfigurer<AgentState, AgentEvent> transitions)
    throws Exception {
    transitions
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

      // THINKING -> FINISHED (无工具调用，直接完成)
      .withExternal()
      .source(AgentState.THINKING).target(AgentState.FINISHED)
      .event(AgentEvent.THINK_DONE_NO_TOOLS)
      .and()

      // ACTING -> THINKING (继续循环)
      .withExternal()
      .source(AgentState.ACTING).target(AgentState.THINKING)
      .event(AgentEvent.ACT_DONE)
      .and()

      // ACTING -> FINISHED (terminate 工具)
      .withExternal()
      .source(AgentState.ACTING).target(AgentState.FINISHED)
      .event(AgentEvent.TOOL_TERMINATE)
      .and()

      // 终止条件: THINKING -> FINISHED
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

      // 终止条件: ACTING -> FINISHED
      .withExternal()
      .source(AgentState.ACTING).target(AgentState.FINISHED)
      .event(AgentEvent.STOP_MAX_STEPS)
      .and()
      .withExternal()
      .source(AgentState.ACTING).target(AgentState.FINISHED)
      .event(AgentEvent.STOP_EMPTY)
      .and()
      .withExternal()
      .source(AgentState.ACTING).target(AgentState.FINISHED)
      .event(AgentEvent.STOP_DUPLICATE)
      .and()

      // 错误处理
      .withExternal()
      .source(AgentState.THINKING).target(AgentState.ERROR)
      .event(AgentEvent.FAIL)
      .and()
      .withExternal()
      .source(AgentState.ACTING).target(AgentState.ERROR)
      .event(AgentEvent.FAIL);
  }
}
