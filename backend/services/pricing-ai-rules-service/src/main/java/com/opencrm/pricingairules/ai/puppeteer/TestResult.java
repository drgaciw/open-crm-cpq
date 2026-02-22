package com.opencrm.pricingairules.ai.puppeteer;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Result of a rule test execution
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestResult {
    private String testCaseId;
    private String testCaseName;
    private boolean successful;
    private Map<String, Object> actualOutput;
    private Map<String, Object> expectedOutput;
    private Long executionTime;
    private String screenshotBase64;
    private String errorMessage;

    // Additional fields for PuppeteerMCPClient compatibility
    private boolean success;
    private Map<String, Object> testCase;
    private Map<String, Object> result;
    private String screenshotPath;

    /**
     * Sets success status and synchronizes with successful field
     */
    public void setSuccess(boolean success) {
        this.success = success;
        this.successful = success;
    }

    /**
     * Sets result and synchronizes with actualOutput field
     */
    public void setResult(Map<String, Object> result) {
        this.result = result;
        this.actualOutput = result;
    }
}
