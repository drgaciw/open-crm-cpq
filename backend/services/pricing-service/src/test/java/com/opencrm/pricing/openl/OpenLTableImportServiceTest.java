package com.opencrm.pricing.openl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenLTableImportServiceTest {

    private OpenLTableImportService importService;

    @BeforeEach
    void setUp() {
        importService = new OpenLTableImportService();
    }

    @Test
    void acceptsValidOpenLTableDescriptor() {
        OpenLTableValidationResult result = importService.validateImport(
                OpenLTableImportRequest.builder()
                        .tableName("volume-discount-na")
                        .content("openl-table:discountRate=0.10")
                        .contentType("text/plain")
                        .build());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getStatus()).isEqualTo(OpenLTableValidationStatus.READY);
        assertThat(result.getIssues()).isEmpty();
    }

    @Test
    void rejectsMissingTableName() {
        OpenLTableValidationResult result = importService.validateImport(
                OpenLTableImportRequest.builder()
                        .content("openl-table:discountRate=0.10")
                        .build());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getStatus()).isEqualTo(OpenLTableValidationStatus.REJECTED);
        assertThat(result.getIssues()).anyMatch(issue -> issue.contains("tableName"));
    }

    @Test
    void rejectsUnknownContentFormat() {
        OpenLTableValidationResult result = importService.validateImport(
                OpenLTableImportRequest.builder()
                        .tableName("bad-table")
                        .content("not-an-openl-table")
                        .build());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getStatus()).isEqualTo(OpenLTableValidationStatus.REJECTED);
        assertThat(result.getIssues()).anyMatch(issue -> issue.contains("openl-table"));
    }
}
