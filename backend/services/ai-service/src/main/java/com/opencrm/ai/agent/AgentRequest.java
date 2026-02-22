package com.opencrm.ai.agent;

import java.util.List;
import java.util.Map;

public interface AgentRequest {
    String getSystemPrompt();
    List<String> getMessages();
    Map<String, Object> getContext();
}
