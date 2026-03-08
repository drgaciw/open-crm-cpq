package com.opencrm.dealdesk.rules.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "rules")
@Validated
@Getter
@Setter
public class RuleEngineProperties {

    private boolean enabled = true;

    @Valid
    private final Validation validation = new Validation();

    @Valid
    private final Defaults defaults = new Defaults();

    @Getter
    @Setter
    public static class Validation {

        private boolean spelSyntaxValidationEnabled = true;

        @Min(128)
        @Max(20000)
        private int maxExpressionLength = 4000;

        private boolean allowTypeReferences = false;

        private boolean allowBeanReferences = false;

        private boolean requireConditionExpression = true;

        private boolean requireActionExpression = false;
    }

    @Getter
    @Setter
    public static class Defaults {

        @NotBlank
        private String category = "general";

        @Min(0)
        @Max(10000)
        private int priority = 100;
    }
}
