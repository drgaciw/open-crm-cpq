package com.opencrm.quote.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencrm.quote.QuoteServiceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;

@DisplayName("outbox tests ignore security's management-port boot app")
class ExcludeSecurityManagementPortAppFilterTest {

    private final ExcludeSecurityManagementPortAppFilter filter = new ExcludeSecurityManagementPortAppFilter();

    @Test
    @DisplayName("matches only AbstractManagementPortTest.App")
    void matchesOnlyTheManagementPortApp() throws Exception {
        assertThat(filter.match(reader(ExcludeSecurityManagementPortAppFilter.EXCLUDED_CLASS), null))
                .isTrue();
        assertThat(filter.match(reader("com.opencrm.security.web.AbstractManagementPortTest$Api"), null))
                .isFalse();
        assertThat(filter.match(reader(QuoteServiceApplication.class.getName()), null)).isFalse();
    }

    @Test
    @DisplayName("equal instances so the Spring test context cache can key this filter")
    void instancesAreEqual() {
        ExcludeSecurityManagementPortAppFilter other = new ExcludeSecurityManagementPortAppFilter();
        assertThat(filter).isEqualTo(other);
        assertThat(filter.hashCode()).isEqualTo(other.hashCode());
    }

    private static MetadataReader reader(String className) {
        ClassMetadata metadata = mock(ClassMetadata.class);
        when(metadata.getClassName()).thenReturn(className);
        MetadataReader reader = mock(MetadataReader.class);
        when(reader.getClassMetadata()).thenReturn(metadata);
        return reader;
    }
}
