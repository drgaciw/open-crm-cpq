package com.opencrm.contract.service;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.dto.SubscriptionDto;
import com.opencrm.contract.repository.AppliedQuoteAcceptanceRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-662: subscription.tenant_id from QuoteAccepted provenance (top-level tenantId only).
 * Interim Entra {@code tid} (per-deployment); not a verified customer tenant.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DRG-662 subscription tenant provenance")
class SubscriptionTenantProvenanceTest {

    static final String INTERIM_LIMIT = "tenantId is the Entra `tid` claim: a per-deployment value "
            + "(single issuer per deployment, ADR-016 / DRG-632), deliberately weak interim. "
            + "It is NOT a real per-tenant id.";

    static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock SubscriptionRepository subscriptions;
    @Mock SubscriptionRatePeriodRepository rates;
    @Mock AppliedQuoteAcceptanceRepository ledger;
    @Mock JdbcTemplate jdbc;

    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    AcceptedQuoteSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new AcceptedQuoteSubscriptionService(subscriptions, rates, ledger, new AcceptedRampValidator(),
                AcceptedRampValidatorTest.MAPPER, jdbc);
        service.bindTenantMeters(meters);
        when(subscriptions.save(any())).thenAnswer(call -> {
            Subscription subscription = call.getArgument(0);
            if (subscription.getId() == null) {
                subscription.setId(5L);
            }
            return subscription;
        });
    }

    @Test
    @DisplayName("AC2/AC6: tenant_id mapped, updatable=false, no public setter, interim limit stated")
    void tenantFieldIsImmutableAfterCreation() throws Exception {
        Field field = Subscription.class.getDeclaredField("tenantId");
        jakarta.persistence.Column column = field.getAnnotation(jakarta.persistence.Column.class);
        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("tenant_id");
        assertThat(column.updatable()).isFalse();
        assertThat(field.getType()).isEqualTo(UUID.class);

        assertThat(Arrays.stream(Subscription.class.getMethods()).map(Method::getName))
                .doesNotContain("setTenantId");

        String source = Files.readString(
                Path.of("src/main/java/com/opencrm/contract/domain/Subscription.java"),
                StandardCharsets.UTF_8);
        assertThat(source).contains(INTERIM_LIMIT);
    }

    @Test
    @DisplayName("AC8: SubscriptionDto has no tenantId (FE held; never from a request body)")
    void dtoExposesNoTenantField() {
        assertThat(Arrays.stream(SubscriptionDto.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet())).doesNotContain("tenantId");
    }

    @Test
    @DisplayName("stores GUID provenance tenant on create")
    void storesGuidTenantFromProvenance() throws Exception {
        QuoteAcceptedEvent event = legacyNew();

        service.apply(event, TENANT.toString());

        ArgumentCaptor<Subscription> captured = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(captured.capture());
        assertThat(captured.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(meters.counter(SubscriptionTenant.INVALID_METRIC).count()).isZero();
    }

    @Test
    @DisplayName("AC3: absent tid stores NULL, increments counter, does not reject")
    void absentTenantStoresNullAndCounts() throws Exception {
        QuoteAcceptedEvent event = legacyNew();

        assertThatCode(() -> service.apply(event, null)).doesNotThrowAnyException();

        ArgumentCaptor<Subscription> captured = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(captured.capture());
        assertThat(captured.getValue().getTenantId()).isNull();
        assertThat(meters.counter(SubscriptionTenant.INVALID_METRIC).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC3: non-GUID tid stores NULL, increments counter, does not reject")
    void nonGuidTenantStoresNullAndCounts() throws Exception {
        QuoteAcceptedEvent event = legacyNew();

        assertThatCode(() -> service.apply(event, "not-a-guid")).doesNotThrowAnyException();

        ArgumentCaptor<Subscription> captured = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(captured.capture());
        assertThat(captured.getValue().getTenantId()).isNull();
        assertThat(meters.counter(SubscriptionTenant.INVALID_METRIC).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC8: blank provenance is treated as absent (not body/nested)")
    void blankProvenanceIsAbsent() throws Exception {
        QuoteAcceptedEvent event = legacyNew();

        service.apply(event, "  ");

        ArgumentCaptor<Subscription> captured = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(captured.capture());
        assertThat(captured.getValue().getTenantId()).isNull();
        assertThat(meters.counter(SubscriptionTenant.INVALID_METRIC).count()).isEqualTo(1);
    }

    private static QuoteAcceptedEvent legacyNew() throws Exception {
        QuoteAcceptedEvent event = AcceptedRampValidatorTest.fixture("ramp-new");
        event.getLineItems().getFirst().setRamp(null);
        event.getLineItems().getFirst().setProductType(null);
        return event;
    }
}
