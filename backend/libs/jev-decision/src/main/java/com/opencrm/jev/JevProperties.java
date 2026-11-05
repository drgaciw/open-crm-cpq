package com.opencrm.jev;

import com.opencrm.jev.transport.JevWireFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Evaluator configuration (ADR-015). Everything that identifies how a decision was produced is explicit
 * here and recorded on each decision row. Defaults are inert: disabled, no-op transport, no key.
 */
@ConfigurationProperties(prefix = "opencrm.jev")
public class JevProperties {

    /** Global kill switch. False means no request is ever made, whatever the use-case flags say. */
    private boolean enabled = false;

    /** {@code noop} or {@code http}. */
    private String transport = "noop";

    /** Bearer credential for the upstream or facade. Blank forces the no-op transport. */
    private String apiKey = "";

    /** Pinned evaluator model. Must not end in {@code latest}. Ignored by the FACADE wire format. */
    private String model = "";

    /** Full endpoint URL. HTTPS required except for localhost. */
    private String baseUrl = "";

    private JevWireFormat wireFormat = JevWireFormat.NATIVE;

    /** Hard ceiling on requests per service instance per UTC day. */
    private int dailyCallBudget = 500;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(10);

    private int maxRetries = 2;

    /** Applies to request and response bodies. */
    private int maxBodyBytes = 65_536;

    /** Advisory evaluations in flight at once; further submissions are dropped. */
    private int maxConcurrent = 4;

    private UseCase leadScore = new UseCase("lead-score-v1", 0.6);
    private UseCase discountTierShadow = new UseCase("tier-v1-thresholds", 0.0);
    private UseCase quoteTierSuggestion = new UseCase("quote-tier-v1", 0.6);
    private DuplicateHints duplicateHints = new DuplicateHints();
    private UseCase omsExceptionTriage = new UseCase("oms-exception-triage-v1", 0.9);

    public static class UseCase {
        private boolean enabled = false;
        private String rubricVersion;
        private double minConfidence;

        public UseCase() {
            this("unversioned", 0.0);
        }

        public UseCase(String rubricVersion, double minConfidence) {
            this.rubricVersion = rubricVersion;
            this.minConfidence = minConfidence;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRubricVersion() {
            return rubricVersion;
        }

        public void setRubricVersion(String rubricVersion) {
            this.rubricVersion = rubricVersion;
        }

        public double getMinConfidence() {
            return minConfidence;
        }

        public void setMinConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
        }
    }

    public static class DuplicateHints extends UseCase {

        public enum PiiMode {
            /** Similarity features only; no names leave the service. */
            FEATURES_ONLY,
            /** Organization names are sent. Requires the ADR-015 data-processing sign-off. */
            NAMES
        }

        private PiiMode piiMode = PiiMode.FEATURES_ONLY;

        public DuplicateHints() {
            super("duplicate-pair-v1", 0.0);
        }

        public PiiMode getPiiMode() {
            return piiMode;
        }

        public void setPiiMode(PiiMode piiMode) {
            this.piiMode = piiMode;
        }
    }

    public static final String LEAD_SCORE = "lead-score";
    public static final String DISCOUNT_TIER_SHADOW = "discount-tier-shadow";
    public static final String QUOTE_TIER_SUGGESTION = "quote-tier-suggestion";
    public static final String DUPLICATE_HINTS = "duplicate-hints";
    public static final String OMS_EXCEPTION_TRIAGE = "oms-exception-triage";

    /** @return the use case settings, or null for a name ADR-015 does not authorise */
    public UseCase useCase(String name) {
        return Map.<String, UseCase>of(
                LEAD_SCORE, leadScore,
                DISCOUNT_TIER_SHADOW, discountTierShadow,
                QUOTE_TIER_SUGGESTION, quoteTierSuggestion,
                DUPLICATE_HINTS, duplicateHints,
                OMS_EXCEPTION_TRIAGE, omsExceptionTriage).get(name);
    }

    public UseCase getOmsExceptionTriage() { return omsExceptionTriage; }

    public void setOmsExceptionTriage(UseCase settings) { this.omsExceptionTriage = settings; }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public JevWireFormat getWireFormat() {
        return wireFormat;
    }

    public void setWireFormat(JevWireFormat wireFormat) {
        this.wireFormat = wireFormat;
    }

    public int getDailyCallBudget() {
        return dailyCallBudget;
    }

    public void setDailyCallBudget(int dailyCallBudget) {
        this.dailyCallBudget = dailyCallBudget;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public UseCase getLeadScore() {
        return leadScore;
    }

    public void setLeadScore(UseCase leadScore) {
        this.leadScore = leadScore;
    }

    public UseCase getDiscountTierShadow() {
        return discountTierShadow;
    }

    public void setDiscountTierShadow(UseCase discountTierShadow) {
        this.discountTierShadow = discountTierShadow;
    }

    public UseCase getQuoteTierSuggestion() {
        return quoteTierSuggestion;
    }

    public void setQuoteTierSuggestion(UseCase quoteTierSuggestion) {
        this.quoteTierSuggestion = quoteTierSuggestion;
    }

    public DuplicateHints getDuplicateHints() {
        return duplicateHints;
    }

    public void setDuplicateHints(DuplicateHints duplicateHints) {
        this.duplicateHints = duplicateHints;
    }
}
