package com.opencrm.ai.agent;

import java.util.Map;

public interface AgentResponse {
    boolean isSuccess();
    String getOutput();
    Map<String, Object> getMetadata();
}
