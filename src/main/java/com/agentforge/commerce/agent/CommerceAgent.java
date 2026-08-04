package com.agentforge.commerce.agent;

import com.agentforge.commerce.model.AgentMessage;
import com.agentforge.commerce.model.DailyAnalysisContext;

/**
 * Contract for all specialist agents in the commerce platform.
 * Each agent has a single area of expertise and communicates via AgentMessage.
 */
public interface CommerceAgent {
    String getName();
    AgentMessage analyse(DailyAnalysisContext context);
}
