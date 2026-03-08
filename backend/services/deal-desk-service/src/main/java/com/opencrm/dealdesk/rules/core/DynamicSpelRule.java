package com.opencrm.dealdesk.rules.core;

import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rule;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

public class DynamicSpelRule implements Rule {

    private final String ruleKey;
    private final String name;
    private final String description;
    private final int priority;
    private final Expression condition;
    private final Expression action;
    private final List<String> firedRuleKeys;
    private final List<String> firedRuleNames;

    public DynamicSpelRule(
            String ruleKey,
            String name,
            String description,
            int priority,
            Expression condition,
            Expression action,
            List<String> firedRuleKeys,
            List<String> firedRuleNames
    ) {
        this.ruleKey = ruleKey;
        this.name = StringUtils.hasText(name) ? name : ruleKey;
        this.description = description;
        this.priority = priority;
        this.condition = condition;
        this.action = action;
        this.firedRuleKeys = firedRuleKeys;
        this.firedRuleNames = firedRuleNames;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean evaluate(Facts facts) {
        if (condition == null) {
            return true;
        }
        StandardEvaluationContext context = createContext(facts);
        return Boolean.TRUE.equals(condition.getValue(context, Boolean.class));
    }

    @Override
    public void execute(Facts facts) {
        StandardEvaluationContext context = createContext(facts);
        if (action != null) {
            action.getValue(context);
        }
        firedRuleKeys.add(ruleKey);
        firedRuleNames.add(name);
    }

    @Override
    public int compareTo(Rule other) {
        if (priority != other.getPriority()) {
            return Integer.compare(priority, other.getPriority());
        }
        return getName().compareTo(other.getName());
    }

    private StandardEvaluationContext createContext(Facts facts) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (Map.Entry<String, Object> entry : facts.asMap().entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }
        context.setRootObject(facts.get("deal"));
        return context;
    }
}
