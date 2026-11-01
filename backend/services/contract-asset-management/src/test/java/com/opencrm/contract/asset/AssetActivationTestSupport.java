package com.opencrm.contract.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.domain.AppliedQuoteAcceptance;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.repository.AppliedQuoteAcceptanceRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.service.AcceptedQuoteSubscriptionService;
import com.opencrm.contract.service.AcceptedRampValidator;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Wires the accepted-quote path to a real asset activation collaborator over in-memory fakes. */
final class AssetActivationTestSupport {

    final AcceptedQuoteSubscriptionService subscriptions;
    final AssetActivation activation;
    final Map<String, Asset> assets = new LinkedHashMap<>();
    final List<Publication> publications = new ArrayList<>();
    Subscription subscription;
    List<SubscriptionRatePeriod> periods = List.of();

    private AssetActivationTestSupport(EntitlementLookup entitlements) {
        SubscriptionRepository subscriptionRepository = Mockito.mock(SubscriptionRepository.class);
        SubscriptionRatePeriodRepository rates = Mockito.mock(SubscriptionRatePeriodRepository.class);
        AppliedQuoteAcceptanceRepository ledger = Mockito.mock(AppliedQuoteAcceptanceRepository.class);
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        AssetRepository assetRepository = Mockito.mock(AssetRepository.class);
        OutboxEventPublisher publisher = Mockito.mock(OutboxEventPublisher.class);

        when(subscriptionRepository.save(any())).thenAnswer(call -> {
            Subscription saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(5L);
            }
            subscription = saved;
            return saved;
        });
        when(rates.saveAll(any())).thenAnswer(call -> {
            List<SubscriptionRatePeriod> saved = new ArrayList<>();
            ((Iterable<?>) call.getArgument(0)).forEach(period -> saved.add((SubscriptionRatePeriod) period));
            periods = saved;
            return saved;
        });
        AtomicReference<AppliedQuoteAcceptance> prior = new AtomicReference<>();
        when(ledger.findByAcceptedQuoteId(any())).thenAnswer(call -> Optional.ofNullable(prior.get()));
        when(ledger.save(any())).thenAnswer(call -> {
            prior.set(call.getArgument(0));
            return prior.get();
        });
        when(assetRepository.save(any(Asset.class))).thenAnswer(call -> {
            Asset asset = call.getArgument(0);
            if (asset.getId() == null) {
                asset.setId((long) assets.size() + 1);
            }
            if (asset.getUuid() == null) {
                asset.setUuid("asset-" + asset.getContractLineId());
            }
            assets.put(key(asset.getSubscriptionId(), asset.getContractLineId()), asset);
            return asset;
        });
        when(assetRepository.findBySubscriptionIdAndContractLineIdAndDeletedFalse(any(), any()))
                .thenAnswer(call -> Optional.ofNullable(assets.get(key(call.getArgument(0), call.getArgument(1)))));

        Mockito.doAnswer(call -> {
            publications.add(new Publication(call.getArgument(2), call.getArgument(3)));
            return null;
        }).when(publisher).publish(any(), any(), any(), any(), any());

        activation = new AssetActivation(assetRepository, entitlements, publisher, new AssetAuditOutbox(publisher));
        subscriptions = new AcceptedQuoteSubscriptionService(subscriptionRepository, rates, ledger,
                new AcceptedRampValidator(), MAPPER, jdbc);
        subscriptions.setAssetActivation(activation);
    }

    static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    static AssetActivationTestSupport open(EntitlementLookup entitlements) {
        return new AssetActivationTestSupport(entitlements);
    }

    static QuoteAcceptedEvent fixture() throws IOException {
        try (InputStream input = AssetActivationTestSupport.class.getResourceAsStream("/accepted-v1/ramp-new.json")) {
            return MAPPER.readValue(input, QuoteAcceptedEvent.class);
        }
    }

    List<AssetCreatedEvent> createdEvents() {
        return publications.stream()
                .filter(publication -> publication.contract == EventContractRegistry.ASSET_CREATED_V1)
                .map(publication -> (AssetCreatedEvent) publication.payload)
                .toList();
    }

    long auditCount() {
        return publications.stream()
                .filter(publication -> publication.contract == EventContractRegistry.ASSET_AUDIT_RECORDED_V1)
                .count();
    }

    /** A second recurring line with the published totals doubled so the accepted facts still agree. */
    static QuoteAcceptedEvent twoLines(QuoteAcceptedEvent event) {
        QuoteAcceptedEvent.LineItemSnapshot first = event.getLineItems().getFirst();
        QuoteAcceptedEvent.LineItemSnapshot second = QuoteAcceptedEvent.LineItemSnapshot.builder()
                .lineUuid("11111111-1111-4111-8111-000000000202")
                .productExternalId("synthetic-product-202")
                .productName("Named seat extra")
                .productType(first.getProductType())
                .lineItemType(first.getLineItemType())
                .quantity(first.getQuantity())
                .unitOfMeasure(first.getUnitOfMeasure())
                .unitPrice(first.getUnitPrice())
                .netPrice(first.getNetPrice())
                .extendedAmount(first.getExtendedAmount())
                .billingFrequency(first.getBillingFrequency())
                .sortOrder(2)
                .ramp(first.getRamp())
                .build();
        event.setLineItems(List.of(first, second));
        event.getPricing().setArr(new BigDecimal("48000"));
        event.getPricing().setExitArr(new BigDecimal("60000"));
        event.getPricing().setTcv(new BigDecimal("162000"));
        event.getPricing().setAcv(new BigDecimal("54000"));
        event.getPricing().setTotalAmount(new BigDecimal("4000"));
        event.setTotalAmount(new BigDecimal("4000"));
        return event;
    }

    private static String key(Long subscriptionId, String contractLineId) {
        return subscriptionId + ":" + contractLineId;
    }

    record Publication(EventContractRegistry.EventContract contract, Object payload) {
    }
}
