package com.opencrm.pricing.metricshistory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class MetricsSnapshotsMigrationVersionTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)(?:_\\d+)?__metrics_snapshots\\.sql$");

    @Test
    void metricsSnapshotsMigrationDoesNotCollideWithV10OrV11() throws IOException {
        assertThat(new ClassPathResource("db/migration/V10__metrics_snapshots.sql").exists())
                .as("V10__metrics_snapshots.sql must not exist (collides with price_book_versioning_and_cost_books)")
                .isFalse();

        Resource[] matches = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__metrics_snapshots.sql");
        assertThat(matches)
                .as("Expected exactly one metrics_snapshots Flyway migration on classpath")
                .hasSize(1);

        String filename = matches[0].getFilename();
        assertThat(filename).isNotNull();
        Matcher matcher = VERSION_PATTERN.matcher(filename);
        assertThat(matcher.matches())
                .as("Migration filename %s should match Flyway V<n>__metrics_snapshots.sql", filename)
                .isTrue();

        int majorVersion = Integer.parseInt(matcher.group(1));
        assertThat(majorVersion)
                .as("metrics_snapshots migration version must be >= 12 to avoid V10/V11 collision")
                .isGreaterThanOrEqualTo(12);
    }
}
