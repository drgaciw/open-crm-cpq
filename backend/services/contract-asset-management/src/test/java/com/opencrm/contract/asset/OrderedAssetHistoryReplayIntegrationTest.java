package com.opencrm.contract.asset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.AssetAmendedEvent;
import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.common.event.dto.AssetTerminatedEvent;
import com.opencrm.contract.ContractAssetManagementApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class OrderedAssetHistoryReplayIntegrationTest {
    private static final LocalDate START = LocalDate.of(2030, 1, 1);
    private static final LocalDate END = LocalDate.of(2030, 12, 31);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=contract");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired EffectiveAssetActivation activation;
    @Autowired AssetRepository assets;
    @Autowired AssetEventPublisher publisher;
    @Autowired PlatformTransactionManager transactions;
    @Autowired OrderedAssetHistoryReplay replay;
    @Autowired ObjectMapper mapper;

    @Test
    void reconstructionSurvivesOutboxRetentionAndOrdersEffectiveDatesThenProducerPositions() throws Exception {
        Origin origin = origin(UUID.randomUUID(), UUID.randomUUID());
        UUID future = amend(origin, START.plusMonths(2), "5", origin.customer());
        UUID first = amend(origin, START.plusMonths(1), "6", origin.customer());
        UUID second = amend(origin, START.plusMonths(1), "7", origin.customer());
        UUID terminated = new TransactionTemplate(transactions).execute(status -> publisher.publishTerminated(
                origin.asset(), new AssetTerminatedEvent(origin.asset().getUuid(), origin.customer(),
                origin.asset().getContractLineId(), origin.asset().getSubscriptionId(), "product-replay", "TERMINATED",
                START.plusMonths(3), UUID.randomUUID().toString(), "1"), null));
        List<UUID> manifest = List.of(origin.created(), future, first, second, terminated);
        acknowledge(origin);

        capture(origin, manifest);
        assertThat(eventIds(origin, START.minusDays(1))).isEmpty();
        assertThat(eventIds(origin, START.plusMonths(1))).containsExactly(origin.created(), first, second);
        assertThat(eventIds(origin, END)).containsExactly(origin.created(), first, second, future, terminated);

        List<OrderedAssetHistoryReplay.ReplayEvent> original = replay.historyThrough(origin.activation(), END);
        jdbc.update("delete from outbox_events where aggregate_id = ?", origin.asset().getUuid());
        capture(origin, manifest);
        List<OrderedAssetHistoryReplay.ReplayEvent> retained = replay.historyThrough(origin.activation(), END);
        assertThat(retained).containsExactlyElementsOf(original);
        assertThat(journalSize(origin)).isEqualTo(5);
        assertReconstructedHistory(origin, retained);
        assertThat(jdbc.queryForObject("select count(*) from asset_history where asset_id = ?",
                Long.class, origin.asset().getUuid())).isEqualTo(1);
    }

    @Test
    void unacknowledgedAndIncompleteOrReorderedManifestsLeaveNoJournal() {
        Origin origin = origin(UUID.randomUUID(), UUID.randomUUID());
        UUID amended = amend(origin, START.plusDays(1), "4", origin.customer());
        assertThatThrownBy(() -> capture(origin, List.of(origin.created(), amended)))
                .isInstanceOf(IllegalArgumentException.class);
        acknowledge(origin);

        assertThatThrownBy(() -> capture(origin, List.of(origin.created())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> capture(origin, List.of(amended, origin.created())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(journalSize(origin)).isZero();
    }

    @Test
    void aDifferentManifestCannotReplaceCapturedEvidence() {
        Origin origin = origin(UUID.randomUUID(), UUID.randomUUID());
        acknowledge(origin);
        capture(origin, List.of(origin.created()));
        UUID amended = amend(origin, START.plusDays(1), "4", origin.customer());
        acknowledge(origin);

        assertThatThrownBy(() -> capture(origin, List.of(origin.created(), amended)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(eventIds(origin, END)).containsExactly(origin.created());
    }

    @Test
    void foreignAccountPayloadCannotBeCapturedEvenWithAMatchingManifest() {
        Origin origin = origin(UUID.randomUUID(), UUID.randomUUID());
        UUID amended = amend(origin, START.plusDays(1), "4", "unrelated-external-account");
        acknowledge(origin);

        assertThatThrownBy(() -> capture(origin, List.of(origin.created(), amended)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(journalSize(origin)).isZero();
    }

    @Test
    void replacementFactEntersHistoryOnItsEffectiveDate() {
        UUID tenant = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        Origin predecessor = origin(tenant, account);
        Origin successor = origin(tenant, account);
        LocalDate day = START.plusMonths(1);
        UUID superseded = new TransactionTemplate(transactions).execute(status -> publisher.publishSuperseded(
                predecessor.asset(), new AssetSupersededEvent(predecessor.asset().getUuid(), predecessor.customer(),
                successor.asset().getUuid(), predecessor.asset().getUuid(), "SUPERSEDED", day,
                UUID.randomUUID().toString(), "1"), null));
        acknowledge(predecessor);
        List<UUID> manifest = List.of(predecessor.created(), superseded);
        assertThatThrownBy(() -> capture(predecessor, manifest)).isInstanceOf(IllegalArgumentException.class);
        assertThat(journalSize(predecessor)).isZero();
        acknowledge(successor);

        capture(predecessor, manifest);
        assertThat(eventIds(predecessor, day.minusDays(1))).containsExactly(predecessor.created());
        assertThat(eventIds(predecessor, day)).containsExactly(predecessor.created(), superseded);
    }

    @ParameterizedTest
    @CsvSource({"2030-03-01, 2030-12-31", "2030-01-01, 2030-01-20"})
    void successorWithoutEffectiveCoverageCannotBeCaptured(LocalDate successorStart, LocalDate successorEnd) {
        UUID tenant = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        Origin predecessor = origin(tenant, account);
        Origin successor = origin(tenant, account, successorStart, successorEnd);
        UUID superseded = supersede(predecessor, successor, START.plusMonths(1));
        acknowledge(predecessor);
        acknowledge(successor);

        assertThatThrownBy(() -> capture(predecessor, List.of(predecessor.created(), superseded)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(journalSize(predecessor)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void knownTerminalSuccessorCannotBeCaptured(boolean retainedInJournal) {
        UUID tenant = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        Origin predecessor = origin(tenant, account);
        Origin successor = origin(tenant, account);
        UUID successorTerminated = terminate(successor, START.plusDays(1));
        UUID superseded = supersede(predecessor, successor, START.plusMonths(1));
        acknowledge(predecessor);
        acknowledge(successor);
        if (retainedInJournal) {
            capture(successor, List.of(successor.created(), successorTerminated));
            jdbc.update("delete from outbox_events where aggregate_id = ?", successor.asset().getUuid());
        }

        assertThatThrownBy(() -> capture(predecessor, List.of(predecessor.created(), superseded)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(journalSize(predecessor)).isZero();
    }

    @Test
    void sameDayAmendmentPublishedAfterTerminationCannotReopenAsset() {
        Origin origin = origin(UUID.randomUUID(), UUID.randomUUID());
        LocalDate day = START.plusMonths(1);
        UUID terminated = terminate(origin, day);
        UUID amended = amend(origin, day, "6", origin.customer());
        acknowledge(origin);

        assertThatThrownBy(() -> capture(origin, List.of(origin.created(), terminated, amended)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(journalSize(origin)).isZero();
    }

    @Test
    void backdatedTerminationDoesNotSilentlyCancelPreviouslyPublishedFutureAmendment() {
        Origin origin = origin(UUID.randomUUID(), UUID.randomUUID());
        UUID amended = amend(origin, START.plusMonths(2), "6", origin.customer());
        UUID terminated = terminate(origin, START.plusMonths(1));
        acknowledge(origin);

        assertThatThrownBy(() -> capture(origin, List.of(origin.created(), amended, terminated)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(journalSize(origin)).isZero();
    }

    @Test
    void amendmentAfterSupersessionCannotReopenPredecessor() {
        UUID tenant = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        Origin predecessor = origin(tenant, account);
        Origin successor = origin(tenant, account);
        UUID superseded = supersede(predecessor, successor, START.plusMonths(1));
        UUID amended = amend(predecessor, START.plusMonths(2), "6", predecessor.customer());
        acknowledge(predecessor);
        acknowledge(successor);

        assertThatThrownBy(() -> capture(predecessor, List.of(predecessor.created(), superseded, amended)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(journalSize(predecessor)).isZero();
    }

    @Test
    void sameDayAmendmentPublishedBeforeTerminationRemainsValid() {
        Origin origin = origin(UUID.randomUUID(), UUID.randomUUID());
        LocalDate day = START.plusMonths(1);
        UUID amended = amend(origin, day, "6", origin.customer());
        UUID terminated = terminate(origin, day);
        acknowledge(origin);

        capture(origin, List.of(origin.created(), amended, terminated));
        assertThat(eventIds(origin, day)).containsExactly(origin.created(), amended, terminated);
    }

    private UUID terminate(Origin origin, LocalDate day) {
        return new TransactionTemplate(transactions).execute(status -> publisher.publishTerminated(
                origin.asset(), new AssetTerminatedEvent(origin.asset().getUuid(), origin.customer(),
                origin.asset().getContractLineId(), origin.asset().getSubscriptionId(), "product-replay", "TERMINATED",
                day, UUID.randomUUID().toString(), "1"), null));
    }

    private UUID supersede(Origin predecessor, Origin successor, LocalDate day) {
        return new TransactionTemplate(transactions).execute(status -> publisher.publishSuperseded(
                predecessor.asset(), new AssetSupersededEvent(predecessor.asset().getUuid(), predecessor.customer(),
                successor.asset().getUuid(), predecessor.asset().getUuid(), "SUPERSEDED", day,
                UUID.randomUUID().toString(), "1"), null));
    }

    private void assertReconstructedHistory(Origin origin, List<OrderedAssetHistoryReplay.ReplayEvent> events)
            throws JsonProcessingException {
        AssetHistoryProjection shadow = new AssetHistoryProjection(new InMemoryAssetHistoryStore());
        for (OrderedAssetHistoryReplay.ReplayEvent event : events) {
            String eventId = event.eventId().toString();
            switch (event.topic()) {
                case KafkaTopics.ASSET_CREATED -> shadow.apply(
                        mapper.readValue(event.payload(), AssetCreatedEvent.class), eventId);
                case KafkaTopics.ASSET_AMENDED -> shadow.apply(
                        mapper.readValue(event.payload(), AssetAmendedEvent.class), eventId);
                case KafkaTopics.ASSET_TERMINATED -> shadow.apply(
                        mapper.readValue(event.payload(), AssetTerminatedEvent.class), eventId);
                default -> throw new AssertionError("Unexpected topic " + event.topic());
            }
        }
        AssetReadTestCaller.signIn();
        try {
            assertThat(shadow.installedBase(origin.customer(), START.plusMonths(1)))
                    .singleElement().satisfies(asset -> assertThat(asset.getQuantity()).isEqualByComparingTo("7"));
            assertThat(shadow.installedBase(origin.customer(), START.plusMonths(2)))
                    .singleElement().satisfies(asset -> assertThat(asset.getQuantity()).isEqualByComparingTo("5"));
            assertThat(shadow.installedBase(origin.customer(), START.plusMonths(3))).isEmpty();
        } finally {
            AssetReadTestCaller.clear();
        }
    }

    private void capture(Origin origin, List<UUID> manifest) {
        replay.capture(origin.activation(), manifest);
    }

    private List<UUID> eventIds(Origin origin, LocalDate asOf) {
        return replay.historyThrough(origin.activation(), asOf).stream()
                .map(OrderedAssetHistoryReplay.ReplayEvent::eventId).toList();
    }

    private UUID amend(Origin origin, LocalDate day, String quantity, String customer) {
        return new TransactionTemplate(transactions).execute(status -> publisher.publishAmended(origin.asset(),
                new AssetAmendedEvent(origin.asset().getUuid(), customer, origin.asset().getContractLineId(),
                origin.asset().getSubscriptionId(), null, "product-replay", new BigDecimal(quantity), "AMENDED",
                START, END, day, UUID.randomUUID().toString(), "1"), null));
    }

    private void acknowledge(Origin origin) {
        jdbc.update("update outbox_events set status = 'PUBLISHED', published = true, published_at = now() "
                + "where aggregate_id = ?", origin.asset().getUuid());
    }

    private long journalSize(Origin origin) {
        return jdbc.queryForObject("select count(*) from asset_history_replay_events where activation_id = ?",
                Long.class, origin.activation());
    }

    private Origin origin(UUID tenant, UUID account) {
        return origin(tenant, account, START, END);
    }

    private Origin origin(UUID tenant, UUID account, LocalDate start, LocalDate end) {
        UUID contract = UUID.randomUUID();
        UUID line = UUID.randomUUID();
        String quote = UUID.randomUUID().toString();
        String customer = "customer-" + account;
        jdbc.update("""
                insert into contracts (id, tenant_id, contract_number, customer_account_id, term_model, start_date,
                                       end_date, status, created_by)
                values (?, ?, ?, ?, 'TERMED', ?, ?, 'ACTIVE', 'test')
                """, contract, tenant, contract.toString(), account, start, end);
        jdbc.update("""
                insert into contract_lines (id, tenant_id, contract_id, line_no, accepted_line_uuid, quote_id,
                                            product_external_id, quantity, status, effective_from, effective_to,
                                            charge_type, created_by)
                values (?, ?, ?, 1, ?, ?, 'product-replay', 3, 'ACTIVE', ?, ?, 'RECURRING', 'test')
                """, line, tenant, contract, UUID.randomUUID().toString(), quote, start, end);
        Long subscription = jdbc.queryForObject("""
                insert into subscriptions (uuid, subscription_number, customer_id, quote_id, status, plan_name,
                                           start_date, end_date, tenant_id, customer_account_id, contract_id, contract_line_id)
                values (?, ?, ?, ?, 'ACTIVE', 'Plan', ?, ?, ?, ?, ?, ?) returning id
                """, Long.class, UUID.randomUUID().toString(), "SUB-" + line, customer, quote,
                start, end, tenant, account, contract, line);
        UUID activationId = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> activation.stage(subscription, line,
                activationId, start));
        assertThat(activation.applyNext(start)).isTrue();
        Asset asset = assets.findBySubscriptionIdAndContractLineIdAndDeletedFalse(subscription, line.toString()).orElseThrow();
        String created = jdbc.queryForObject("select uuid from outbox_events where aggregate_id = ? and topic = ?",
                String.class, asset.getUuid(), KafkaTopics.ASSET_CREATED);
        return new Origin(activationId, asset, customer, UUID.fromString(created));
    }

    private record Origin(UUID activation, Asset asset, String customer, UUID created) {
    }
}
