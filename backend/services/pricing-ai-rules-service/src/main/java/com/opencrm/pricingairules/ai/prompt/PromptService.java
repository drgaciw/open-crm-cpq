package com.opencrm.pricingairules.ai.prompt;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@ConfigurationProperties(prefix = "ai.prompts")
@Data
@Slf4j
public class PromptService {

    private Map<String, PromptCategory> categories = new HashMap<>();

    public String getSystemPrompt(String category) {
        PromptCategory promptCategory = categories.get(category);
        if (promptCategory == null || promptCategory.getSystem() == null) {
            log.debug("Missing system prompt for category {}", category);
            return "You are a helpful AI assistant.";
        }
        return promptCategory.getSystem();
    }

    public String renderPrompt(String category, String name, Map<String, Object> variables) {
        PromptCategory promptCategory = categories.get(category);
        if (promptCategory == null) {
            log.debug("Missing prompt category {}", category);
            return variables.getOrDefault("userMessage", "").toString();
        }

        String template = promptCategory.getPrompts().get(name);
        if (template == null) {
            log.debug("Missing prompt {}.{}", category, name);
            return variables.getOrDefault("userMessage", "").toString();
        }

        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            rendered = rendered.replace("{" + entry.getKey() + "}", value);
            rendered = rendered.replace("{{" + entry.getKey() + "}}", value);
        }
        return rendered;
    }

    @Data
    public static class PromptCategory {
        private String system;
        private Map<String, String> prompts = new HashMap<>();
    }
}
