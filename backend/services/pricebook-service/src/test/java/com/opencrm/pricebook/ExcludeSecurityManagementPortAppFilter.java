package com.opencrm.pricebook;

import java.io.IOException;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * Keeps {@code com.opencrm.security.web.AbstractManagementPortTest$App} out of pricebook-service's component scan.
 *
 * <p>That nested {@code @SpringBootConfiguration} lives in security's {@code target/test-classes}. A reactor
 * {@code mvn test} can put that directory on the classpath even though the filtered security test-jar (only
 * {@code com/opencrm/security/test/**}) excludes it. {@code PriceBookServiceApplication} scans
 * {@code com.opencrm.security}, and Boot merges every {@code @EnableAutoConfiguration} exclusion it finds.
 * {@code App} excludes DataSource, Hibernate JPA, Data JPA repositories, Kafka, and the JDBC transaction manager,
 * so {@link PriceBookOutboxWiringTest} starts without an {@code entityManagerFactory} (DRG-675).
 *
 * <p>{@code App} is package-private, so this filter matches the binary name. It is registered on the outbox wiring
 * test via {@code @TypeExcludeFilters}. Same pattern as quote-service's outbox integration tests.
 */
final class ExcludeSecurityManagementPortAppFilter extends TypeExcludeFilter {

    static final String EXCLUDED_CLASS = "com.opencrm.security.web.AbstractManagementPortTest$App";

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
            throws IOException {
        return EXCLUDED_CLASS.equals(metadataReader.getClassMetadata().getClassName());
    }

    @Override
    public boolean equals(Object other) {
        return other != null && getClass() == other.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
