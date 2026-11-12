package com.opencrm.document.dto;

import java.util.List;

/** A bounded predicate tree. Fields are literal keys, never executable expressions. */
public record ClauseCondition(Operator operator, String field, Object value, List<ClauseCondition> children) {
    public enum Operator { EQUALS, IN, GT, GTE, LT, LTE, EXISTS, AND, OR }
}
