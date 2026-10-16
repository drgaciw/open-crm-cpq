package com.opencrm.quote.event;

import java.io.IOException;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * Keeps {@code com.opencrm.security.web.AbstractManagementPortTest$App} out of quote-service's component scan.
 *
 * <p>That nested {@code @SpringBootConfiguration} lives in security's {@code target/test-classes}. {@code mvn test}
 * puts that directory on the classpath because the filtered security test-jar (only {@code com/opencrm/security/test/**})
 * is built at package time, so {@code mvn verify} never sees the class. {@code QuoteServiceApplication} scans
 * {@code com.opencrm.security}, and Boot merges every {@code @EnableAutoConfiguration} exclusion it finds. {@code App}
 * excludes DataSource, Hibernate JPA, Data JPA repositories, Kafka, and the JDBC transaction manager, so the outbox
 * tests start without an {@code entityManagerFactory}.
 *
 * <p>{@code App} is package-private, so this filter matches the binary name. It is registered only on the two outbox
 * integration tests via {@code @TypeExcludeFilters}.
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
