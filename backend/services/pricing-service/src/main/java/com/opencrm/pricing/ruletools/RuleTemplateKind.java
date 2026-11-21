package com.opencrm.pricing.ruletools;

import com.opencrm.common.exception.BusinessException;

import java.util.Locale;

/**
 * Template kinds harvested from price-dools {@code RuleTemplateService}.
 */
public enum RuleTemplateKind {
    VOLUME_DISCOUNT("volume-discount"),
    COMMITMENT("commitment"),
    RISK_ADJUSTMENT("risk-adjustment"),
    CONTRACT_LENGTH("contract-length");

    private final String path;

    RuleTemplateKind(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    public static RuleTemplateKind fromPath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Template kind is required", "UNKNOWN_TEMPLATE_KIND");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (RuleTemplateKind kind : values()) {
            if (kind.path.equals(normalized)) {
                return kind;
            }
        }
        throw new BusinessException("Unknown template kind: " + raw, "UNKNOWN_TEMPLATE_KIND");
    }
}
