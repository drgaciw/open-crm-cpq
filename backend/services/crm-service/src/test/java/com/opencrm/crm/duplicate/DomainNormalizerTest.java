package com.opencrm.crm.duplicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DRG-517 read-time normalization")
class DomainNormalizerTest {

    @Test
    @DisplayName("AC1: stripsSchemeWwwPathQueryPortAndTrailingDot")
    void stripsSchemeWwwPathQueryPortAndTrailingDot() {
        assertThat(DomainNormalizer.normalizeDomain("https://www.Acme.com/about")).isEqualTo("acme.com");
        assertThat(DomainNormalizer.normalizeDomain("http://acme.com/path?q=1")).isEqualTo("acme.com");
        assertThat(DomainNormalizer.normalizeDomain("HTTPS://WWW.ACME.COM:443/about#frag")).isEqualTo("acme.com");
        assertThat(DomainNormalizer.normalizeDomain("www.acme.com.")).isEqualTo("acme.com");
        assertThat(DomainNormalizer.normalizeDomain("http://user:pass@www.acme.com/path")).isEqualTo("acme.com");
        assertThat(DomainNormalizer.normalizeDomain("  acme.com  ")).isEqualTo("acme.com");
        assertThat(DomainNormalizer.normalizeDomain(null)).isNull();
        assertThat(DomainNormalizer.normalizeDomain("   ")).isNull();
    }

    @Test
    @DisplayName("AC3: normalizesEmailByTrimAndLowercaseWithoutPlusStripping")
    void normalizesEmailByTrimAndLowercaseWithoutPlusStripping() {
        assertThat(DomainNormalizer.normalizeEmail("  Ann@Acme.com ")).isEqualTo("ann@acme.com");
        assertThat(DomainNormalizer.normalizeEmail("ann+news@acme.com")).isEqualTo("ann+news@acme.com");
        assertThat(DomainNormalizer.normalizeEmail(null)).isNull();
        assertThat(DomainNormalizer.normalizeEmail("   ")).isNull();
    }

    @Test
    @DisplayName("AC4: collapsesNameWhitespace")
    void collapsesNameWhitespace() {
        assertThat(DomainNormalizer.normalizeName("  Ann   Marie ", " Smith ")).isEqualTo("ann marie smith");
        assertThat(DomainNormalizer.normalizeName("Ann", "Smith")).isEqualTo("ann smith");
        assertThat(DomainNormalizer.normalizeName("  ", "Smith")).isNull();
        assertThat(DomainNormalizer.normalizeName("Ann", null)).isNull();
    }
}
