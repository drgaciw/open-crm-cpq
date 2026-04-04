#!/bin/bash
# Check if ChatClient is used in any other agents
grep -rn "org.springframework.ai.chat.ChatClient" backend/services/ai-service/src/main/java/com/opencrm/ai/
