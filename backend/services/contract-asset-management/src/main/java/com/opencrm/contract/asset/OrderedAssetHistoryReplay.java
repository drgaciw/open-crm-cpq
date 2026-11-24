package com.opencrm.contract.asset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry.EventContract;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.AssetAmendedEvent;
import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.common.event.dto.AssetTerminatedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Internal capture and reconstruction of an approved CAM asset history manifest. */
@Service
public class OrderedAssetHistoryReplay {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OrderedAssetHistoryReplay(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * The operator supplies the independently verified, complete ordered list of lifecycle event IDs
     * for this activation. CAM preserves the retained source after matching that manifest.
     */
    @Transactional
    public void capture(UUID activationId, List<UUID> expectedEvents) {
        Objects.requireNonNull(activationId, "activation id is required");
        List<UUID> manifest = List.copyOf(expectedEvents);
        require(!manifest.isEmpty() && new HashSet<>(manifest).size() == manifest.size(),
                "The manifest must contain distinct lifecycle event IDs");

        Activation activation = jdbc.query("""
                select asset_id, created_event_id, subscription_id, contract_line_id, tenant_id,
                       customer_account_id, external_customer_id, product_ref, quantity, effective_start, effective_end
                  from asset_activation_receipts
                 where activation_id = ? and status = 'APPLIED'
                   for no key update
                """, (row, index) -> new Activation(
                row.getString("asset_id"), UUID.fromString(row.getString("created_event_id")),
                row.getLong("subscription_id"), row.getObject("contract_line_id", UUID.class),
                row.getObject("tenant_id", UUID.class), row.getObject("customer_account_id", UUID.class),
                row.getString("external_customer_id"), row.getString("product_ref"), row.getBigDecimal("quantity"),
                row.getObject("effective_start", LocalDate.class), row.getObject("effective_end", LocalDate.class)),
                activationId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("An applied activation receipt is required"));

        List<UUID> existing = jdbc.query("""
                select event_id from asset_history_replay_events
                 where activation_id = ? order by source_outbox_id
                """, (row, index) -> row.getObject("event_id", UUID.class), activationId);
        if (!existing.isEmpty()) {
            require(existing.equals(manifest), "The activation already has a different replay manifest");
            return;
        }

        List<OutboxRow> source = jdbc.query("""
                select id, uuid, coalesce(topic, event_type) as lifecycle_topic, event_version, status, payload
                  from outbox_events
                 where aggregate_type = ? and aggregate_id = ? and not is_deleted
                   and coalesce(topic, event_type) in (?, ?, ?, ?)
                 order by id for share
                """, (row, index) -> new OutboxRow(row.getLong("id"), UUID.fromString(row.getString("uuid")),
                row.getString("lifecycle_topic"), row.getInt("event_version"), row.getString("status"),
                row.getString("payload")),
                AssetEventPublisher.AGGREGATE_TYPE, activation.assetId(), KafkaTopics.ASSET_CREATED,
                KafkaTopics.ASSET_AMENDED, KafkaTopics.ASSET_TERMINATED, KafkaTopics.ASSET_SUPERSEDED);
        require(manifest.getFirst().equals(activation.createdEvent())
                && source.stream().map(OutboxRow::eventId).toList().equals(manifest)
                && source.stream().allMatch(row -> "PUBLISHED".equals(row.status())),
                "The manifest must match the retained published CAM lifecycle events");

        List<ReplayEvent> captured = source.stream().map(row -> verify(row, activation)).toList();
        requireValidEffectiveOrder(captured);
        jdbc.batchUpdate("""
                insert into asset_history_replay_events
                    (event_id, activation_id, source_outbox_id, topic, event_version, effective_date, payload)
                values (?, ?, ?, ?, ?, ?, ?)
                """, captured.stream().map(event -> new Object[]{
                    event.eventId(), activationId, event.sourceOrder(), event.topic(), event.version(),
                    event.effectiveDate(), event.payload()
                }).toList());
    }

    /** Reconstructs the verified effective facts as of the requested day, using producer position on ties. */
    @Transactional(readOnly = true)
    public List<ReplayEvent> historyThrough(UUID activationId, LocalDate asOf) {
        Objects.requireNonNull(activationId, "activation id is required");
        Objects.requireNonNull(asOf, "effective date is required");
        return List.copyOf(jdbc.query("""
                select event_id, source_outbox_id, topic, event_version, effective_date, payload
                  from asset_history_replay_events
                 where activation_id = ? and effective_date <= ?
                 order by effective_date, source_outbox_id
                """, (row, index) -> new ReplayEvent(row.getObject("event_id", UUID.class),
                row.getLong("source_outbox_id"), row.getString("topic"), row.getInt("event_version"),
                row.getObject("effective_date", LocalDate.class), row.getString("payload")), activationId, asOf));
    }

    private ReplayEvent verify(OutboxRow source, Activation activation) {
        EventContract contract = switch (source.topic()) {
            case KafkaTopics.ASSET_CREATED -> EventContractRegistry.ASSET_CREATED_V1;
            case KafkaTopics.ASSET_AMENDED -> EventContractRegistry.ASSET_AMENDED_V1;
            case KafkaTopics.ASSET_TERMINATED -> EventContractRegistry.ASSET_TERMINATED_V1;
            case KafkaTopics.ASSET_SUPERSEDED -> EventContractRegistry.ASSET_SUPERSEDED_V1;
            default -> throw new IllegalArgumentException("Unsupported asset lifecycle topic");
        };
        require(source.version() == contract.eventVersion(), "Unsupported asset lifecycle event version");
        require(source.eventId().equals(activation.createdEvent()) == KafkaTopics.ASSET_CREATED.equals(source.topic()),
                "One creation event must anchor the replay manifest");
        Object payload;
        try {
            payload = mapper.readValue(source.payload(), contract.payloadClass());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid registered asset lifecycle event", exception);
        }
        LocalDate effective = switch (payload) {
            case AssetCreatedEvent created -> {
                requireAsset(activation, created.assetId(), created.accountId());
                require(Objects.equals(activation.subscriptionId(), created.subscriptionId())
                                && activation.lineId().toString().equals(created.contractLineId())
                                && activation.productRef().equals(created.productRef())
                                && created.quantity() != null && activation.quantity().compareTo(created.quantity()) == 0
                                && activation.start().equals(created.effectiveStart())
                                && activation.end().equals(created.effectiveEnd()),
                        "The created event differs from the applied activation receipt");
                yield created.effectiveStart();
            }
            case AssetAmendedEvent amended -> {
                requireAsset(activation, amended.assetId(), amended.accountId());
                requireLine(activation, amended.subscriptionId(), amended.contractLineId());
                yield amended.effectiveDate() == null ? amended.effectiveStart() : amended.effectiveDate();
            }
            case AssetTerminatedEvent terminated -> {
                requireAsset(activation, terminated.assetId(), terminated.accountId());
                requireLine(activation, terminated.subscriptionId(), terminated.contractLineId());
                yield terminated.effectiveDate();
            }
            case AssetSupersededEvent superseded -> {
                requireAsset(activation, superseded.assetId(), superseded.accountId());
                require(Objects.equals(activation.assetId(), superseded.predecessorAssetId())
                                && verifiedEarlierSuccessor(activation, source.id(), superseded.successorAssetId(),
                                        superseded.effectiveDate()),
                        "The replacement needs earlier published creation evidence for a successor effective on that date");
                yield superseded.effectiveDate();
            }
            case null, default -> throw new IllegalArgumentException("Invalid registered asset lifecycle payload");
        };
        require(effective != null && !effective.isBefore(activation.start()),
                "The event effective date must follow asset activation");
        return new ReplayEvent(source.eventId(), source.id(), source.topic(), source.version(), effective, source.payload());
    }

    private static void requireAsset(Activation activation, String assetId, String accountId) {
        require(activation.assetId().equals(assetId) && activation.externalAccount().equals(accountId),
                "The event does not match the asset and account on the receipt");
    }

    private void requireLine(Activation activation, Long subscriptionId, String lineId) {
        require(Objects.equals(activation.subscriptionId(), subscriptionId),
                "The event does not match the subscription on the receipt");
        if (activation.lineId().toString().equals(lineId)) {
            return;
        }
        Boolean canonical = jdbc.queryForObject("""
                select exists(
                    select 1 from contract_lines l join contracts c on c.id = l.contract_id
                    where l.id = ? and l.tenant_id = ? and c.tenant_id = ? and c.customer_account_id = ?
                )
                """, Boolean.class, UUID.fromString(Objects.requireNonNull(lineId, "contract line id is required")),
                activation.tenantId(), activation.tenantId(), activation.customerAccountId());
        require(Boolean.TRUE.equals(canonical), "The event does not identify a canonical line for the receipt account");
    }

    private boolean verifiedEarlierSuccessor(Activation activation, long predecessorEventOrder, String successorId,
                                             LocalDate effectiveDate) {
        if (effectiveDate == null || successorId == null || activation.assetId().equals(successorId)) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(
                    select 1 from asset_activation_receipts r
                     where r.status = 'APPLIED' and r.asset_id = ? and r.tenant_id = ?
                       and r.customer_account_id = ? and r.external_customer_id = ?
                       and ? between r.effective_start and r.effective_end
                       and (
                           exists(select 1 from outbox_events o
                                   where o.uuid = r.created_event_id and o.aggregate_id = r.asset_id
                                     and o.aggregate_type = ? and not o.is_deleted and o.status = 'PUBLISHED'
                                     and coalesce(o.topic, o.event_type) = ? and o.id < ?)
                           or exists(select 1 from asset_history_replay_events j
                                     where j.activation_id = r.activation_id and j.topic = ? and j.source_outbox_id < ?)
                       )
                       and not exists(select 1 from asset_history_replay_events j
                                       where j.activation_id = r.activation_id and j.topic in (?, ?)
                                         and j.effective_date <= ?)
                       and not exists(select 1 from outbox_events o
                                       where o.aggregate_id = r.asset_id and o.aggregate_type = ?
                                         and not o.is_deleted and o.status = 'PUBLISHED'
                                         and coalesce(o.topic, o.event_type) in (?, ?)
                                         and coalesce((o.payload::jsonb ->> 'effectiveDate')::date <= ?, true))
                )
                """, Boolean.class, successorId, activation.tenantId(), activation.customerAccountId(),
                activation.externalAccount(), effectiveDate, AssetEventPublisher.AGGREGATE_TYPE,
                KafkaTopics.ASSET_CREATED, predecessorEventOrder, KafkaTopics.ASSET_CREATED, predecessorEventOrder,
                KafkaTopics.ASSET_TERMINATED, KafkaTopics.ASSET_SUPERSEDED, effectiveDate,
                AssetEventPublisher.AGGREGATE_TYPE, KafkaTopics.ASSET_TERMINATED, KafkaTopics.ASSET_SUPERSEDED,
                effectiveDate));
    }

    private static void requireValidEffectiveOrder(List<ReplayEvent> events) {
        List<ReplayEvent> effectiveOrder = events.stream()
                .sorted(Comparator.comparing(ReplayEvent::effectiveDate).thenComparingLong(ReplayEvent::sourceOrder))
                .toList();
        boolean closed = false;
        for (ReplayEvent event : effectiveOrder) {
            switch (event.topic()) {
                case KafkaTopics.ASSET_TERMINATED, KafkaTopics.ASSET_SUPERSEDED -> closed = true;
                case KafkaTopics.ASSET_AMENDED ->
                        require(!closed, "An amendment effective after closure requires explicit reconciliation");
                default -> { }
            }
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    public record ReplayEvent(UUID eventId, long sourceOrder, String topic, int version, LocalDate effectiveDate,
                              String payload) {
    }

    private record OutboxRow(long id, UUID eventId, String topic, int version, String status, String payload) {
    }

    private record Activation(String assetId, UUID createdEvent, long subscriptionId, UUID lineId, UUID tenantId,
                              UUID customerAccountId, String externalAccount, String productRef, BigDecimal quantity,
                              LocalDate start, LocalDate end) {
    }
}
