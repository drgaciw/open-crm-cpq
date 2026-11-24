package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.AssetReadClient;
import com.opencrm.quote.client.InstalledAssetView;
import com.opencrm.quote.client.RenewalPolicyClient;
import com.opencrm.quote.client.RenewalPolicyView;
import com.opencrm.quote.client.SubscriptionScheduleClient;
import com.opencrm.quote.client.SubscriptionScheduleView;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.ramp.RampEscalatorExpander;
import com.opencrm.quote.ramp.RampPricingProperties;
import com.opencrm.quote.ramp.RampScheduleAttacher;
import com.opencrm.quote.ramp.RampScheduleValidator;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class AssetRenewalDraftsTest {

    private static final UUID ACCOUNT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID POLICY = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID CANONICAL_LINE = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final LocalDate TERM_END = LocalDate.of(2029, 12, 31);

    private SubscriptionScheduleClient schedules;
    private QuoteRepository repository;
    private QuoteLineItemRepository lines;
    private JdbcTemplate jdbc;
    private AssetReadClient assets;
    private RenewalPolicyClient policies;
    private QuoteContinuationService continuations;
    private Quote saved;

    @BeforeEach
    void setup() {
        schedules = mock(SubscriptionScheduleClient.class);
        repository = mock(QuoteRepository.class);
        lines = mock(QuoteLineItemRepository.class);
        jdbc = mock(JdbcTemplate.class);
        assets = mock(AssetReadClient.class);
        policies = mock(RenewalPolicyClient.class);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        RampPricingProperties properties = new RampPricingProperties();
        properties.setEnabled(true);
        QuoteNumberGenerator numbers = mock(QuoteNumberGenerator.class);
        when(numbers.generate()).thenReturn("Q-NEXT");
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        continuations = new QuoteContinuationService(schedules, repository, lines, numbers, Mappers.getMapper(QuoteMapper.class),
                properties, new RampScheduleAttacher(properties, new RampScheduleValidator(), new RampEscalatorExpander()),
                json, jdbc, new CurrentActor("service:quote-service", false), mock(org.springframework.web.client.RestClient.class));
        continuations.setAssetRenewalClients(assets, policies);
        when(schedules.byNumber("SUB-R3")).thenReturn(schedule());
        when(repository.save(any())).thenAnswer(invocation -> {
            saved = invocation.getArgument(0);
            saved.setId(1L);
            saved.setUuid("synthetic-quote-next");
            return saved;
        });
        when(lines.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assets.installedBase("synthetic-customer", TERM_END)).thenReturn(installed());
        when(policies.fetchRungs(eq(ACCOUNT), isNull(), isNull(), eq(TERM_END))).thenReturn(List.of(policy("UPLIFT_PCT", "10")));
    }

    @Test
    void choosingFromActiveAssetsBuildsThoseLinesAndTheExistingRenewalStaysOneLine() {
        QuoteDto draft = continuations.author(assetRequest());
        assertThat(draft.getDealType()).isEqualTo(DealType.RENEWAL);
        assertThat(draft.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.RENEWAL);
        assertThat(draft.getLineItems()).extracting(item -> item.getSourceSubscriptionLineUuid())
                .containsExactly("source-line", "source-line-2");
        assertThat(saved.getContinuationRequestJson()).contains("FROM_ACTIVE_ASSETS");

        QuoteContinuationRequest single = QuoteContinuationRequest.builder()
                .operation(QuoteContinuationRequest.Operation.RENEWAL)
                .targetSubscriptionNumber("SUB-R3")
                .sourceSubscriptionLineUuid("source-line")
                .observedRevision(7L)
                .customerExternalId("synthetic-customer")
                .currency("USD")
                .effectiveDate(LocalDate.of(2030, 1, 1))
                .quantity(20)
                .build();
        QuoteDto unchanged = continuations.author(single);
        assertThat(unchanged.getLineItems()).hasSize(1);
        assertThat(unchanged.getLineItems().getFirst().getQuantity()).isEqualTo(20);
        verify(assets).installedBase("synthetic-customer", TERM_END);
    }

    @Test
    void supersededAndTerminatedAssetsAreExcluded() {
        QuoteDto draft = continuations.author(assetRequest());
        assertThat(saved.getLineItems()).extracting(this::assetId)
                .containsExactly("asset-active", "asset-amended");
        assertThat(draft.getLineItems()).extracting(item -> item.getQuantity()).containsExactly(7, 4);
        assertThat(saved.getContinuationRequestJson()).doesNotContain("asset-super").doesNotContain("asset-term")
                .doesNotContain("asset-other");
    }

    @Test
    void quantityComesFromTheCurrentAssetNotTheScheduleExit() {
        QuoteDto draft = continuations.author(assetRequest());
        assertThat(draft.getLineItems().getFirst().getQuantity()).isEqualTo(7);
        assertThat(draft.getLineItems().get(1).getQuantity()).isEqualTo(4);
        assertThat(draft.getLineItems()).allSatisfy(item -> assertThat(item.getQuantity()).isNotEqualTo(50));
    }

    @Test
    void canonicalAssetRenewsThroughVerifiedAcceptedRateSource() throws Exception {
        when(schedules.byNumber("SUB-R3")).thenReturn(linkedSchedule(true));
        when(assets.installedBase("synthetic-customer", TERM_END))
                .thenReturn(List.of(asset("asset-canonical", CANONICAL_LINE.toString(), "7", "ACTIVE")));
        when(policies.fetchRungs(eq(ACCOUNT), isNull(), eq(CANONICAL_LINE), eq(TERM_END)))
                .thenReturn(List.of(policy("UPLIFT_PCT", "10")));
        var request = assetRequest();
        request.setContractLineId(CANONICAL_LINE.toString());

        QuoteDto draft = continuations.author(request);

        assertThat(draft.getLineItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceSubscriptionLineUuid()).isEqualTo("source-line");
            assertThat(item.getQuantity()).isEqualTo(7);
            assertThat(item.getUnitPrice()).isEqualByComparingTo("55.00");
        });
        assertThat(saved.getContinuationRequestJson()).contains("asset-canonical");
    }

    @Test
    void verifiedAccountPricesRenewalWhenCallerOmitsPolicyAccount() throws Exception {
        when(schedules.byNumber("SUB-R3")).thenReturn(linkedSchedule(true));
        when(assets.installedBase("synthetic-customer", TERM_END))
                .thenReturn(List.of(asset("asset-canonical", CANONICAL_LINE.toString(), "7", "ACTIVE")));
        when(policies.fetchRungs(ACCOUNT, null, CANONICAL_LINE, TERM_END))
                .thenReturn(List.of(policy("UPLIFT_PCT", "10")));
        var request = assetRequest();
        request.setRenewalPolicyAccountId(null);

        assertThat(continuations.author(request).getLineItems()).singleElement()
                .satisfies(line -> assertThat(line.getUnitPrice()).isEqualByComparingTo("55.00"));
        assertThat(saved.getContinuationRequestJson()).contains(ACCOUNT.toString());
    }

    @Test
    void verifiedAccountRejectsAnUnrelatedPolicyAccountBeforeReadingAssets() throws Exception {
        when(schedules.byNumber("SUB-R3")).thenReturn(linkedSchedule(true));
        var request = assetRequest();
        request.setRenewalPolicyAccountId(UUID.randomUUID());

        assertThatThrownBy(() -> continuations.author(request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("verified customer account");
        verifyNoInteractions(assets, policies);
        verify(repository, never()).save(any());
    }

    @Test
    void verifiedTargetRejectsAnUnlinkedAgreementBeforeReadingAssets() throws Exception {
        when(schedules.byNumber("SUB-R3")).thenReturn(linkedSchedule(true));
        var request = assetRequest();
        request.setRenewalPolicyAgreementId(UUID.randomUUID());

        assertThatThrownBy(() -> continuations.author(request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("verified renewal-policy agreement");
        verifyNoInteractions(assets, policies);
        verify(repository, never()).save(any());
    }

    @Test
    void acceptedLineAliasUsesTheVerifiedCanonicalLinePolicy() throws Exception {
        when(schedules.byNumber("SUB-R3")).thenReturn(linkedSchedule(true));
        when(assets.installedBase("synthetic-customer", TERM_END))
                .thenReturn(List.of(asset("asset-accepted-key", "source-line", "7", "ACTIVE")));
        when(policies.fetchRungs(ACCOUNT, null, CANONICAL_LINE, TERM_END)).thenReturn(List.of(
                new RenewalPolicyView(POLICY, "LINE", CANONICAL_LINE, 1, "SAME", "CONTRACTED_NET", null, null, null)));

        assertThat(continuations.author(assetRequest()).getLineItems()).singleElement()
                .satisfies(line -> assertThat(line.getUnitPrice()).isEqualByComparingTo("50.00"));
    }

    @Test
    void acceptedLineAliasCannotBypassTheVerifiedProductCheck() throws Exception {
        when(schedules.byNumber("SUB-R3")).thenReturn(linkedSchedule(true));
        when(assets.installedBase("synthetic-customer", TERM_END)).thenReturn(List.of(
                new InstalledAssetView("asset-accepted-key", "synthetic-customer", "source-line", 42L,
                        "other-product", BigDecimal.valueOf(7), LocalDate.of(2027, 1, 1), TERM_END, "ACTIVE")));

        assertThatThrownBy(() -> continuations.author(assetRequest())).isInstanceOf(BusinessException.class)
                .hasMessageContaining("product does not match the canonical contract line");
        verifyNoInteractions(policies);
        verify(repository, never()).save(any());
    }

    @Test
    void inconsistentCanonicalLinkageCannotUseAnExistingLegacyAssetKey() throws Exception {
        when(schedules.byNumber("SUB-R3")).thenReturn(linkedSchedule(false));

        assertThatThrownBy(() -> continuations.author(assetRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("canonical contract line");
        verifyNoInteractions(assets, policies);
        verify(repository, never()).save(any());
    }

    @Test
    void staleAssetRenewalPinIsRejectedBeforeReadingAssetsOrWriting() {
        var request = assetRequest();
        request.setObservedRevision(6L);

        assertThatThrownBy(() -> continuations.preview(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("observed revision");
        assertThatThrownBy(() -> continuations.author(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("observed revision");
        verifyNoInteractions(assets, policies);
        verify(repository, never()).save(any());
    }

    private SubscriptionScheduleView linkedSchedule(boolean verified) throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        com.fasterxml.jackson.databind.node.ObjectNode body = json.valueToTree(schedule());
        body.put("subscriptionId", 42);
        body.put("canonicalLinkageVerified", verified);
        if (verified) {
            body.put("canonicalCustomerAccountId", ACCOUNT.toString());
            ((com.fasterxml.jackson.databind.node.ObjectNode) body.path("lines").get(0))
                    .put("canonicalContractLineId", CANONICAL_LINE.toString());
        }
        return json.treeToValue(body, SubscriptionScheduleView.class);
    }

    @Test
    void pricingIsDelegatedToTheRenewalPolicyClient() {
        QuoteDto draft = continuations.author(assetRequest());
        verify(policies).fetchRungs(ACCOUNT, null, null, TERM_END);
        assertThat(draft.getLineItems().getFirst().getUnitPrice()).isEqualByComparingTo("55.00");
        assertThat(draft.getLineItems().get(1).getUnitPrice()).isEqualByComparingTo("88.00");

        when(policies.fetchRungs(eq(ACCOUNT), isNull(), isNull(), eq(TERM_END))).thenReturn(List.of(policy("SAME", null)));
        QuoteDto same = continuations.author(assetRequest());
        assertThat(same.getLineItems().getFirst().getUnitPrice()).isEqualByComparingTo("50.00");

        QuoteContinuationRequest callerUplift = assetRequest();
        callerUplift.setUpliftPercent(new BigDecimal("99"));
        assertThatThrownBy(() -> continuations.author(callerUplift))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("renewal policy");
    }

    @Test
    void auditStoresTheSourceModeAndAssetIds() {
        continuations.author(assetRequest());
        ArgumentCaptor<String> inputs = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> outputs = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), eq(1L), eq("Q-NEXT"), eq(AssetRenewalDrafts.DECISION_KIND),
                eq("service:quote-service"), eq(POLICY.toString()), eq("RENEWAL"), eq(AssetRenewalDrafts.SOURCE_MODE),
                eq("SUB-R3"), isNull(), inputs.capture(), outputs.capture());
        assertThat(inputs.getValue()).contains("\"sourceMode\":\"FROM_ACTIVE_ASSETS\"")
                .contains("asset-active").contains("asset-amended");
        assertThat(outputs.getValue()).contains("\"sourceMode\":\"FROM_ACTIVE_ASSETS\"")
                .contains("asset-active").contains("asset-amended");
    }

    @Test
    void previewDoesNotOpenAQuoteAndValidateFollowsTheSameAssets() {
        QuoteContinuationPolicy.Plan preview = continuations.preview(assetRequest());
        assertThat(preview.periods()).extracting(QuoteContinuationPolicy.Period::quantity).containsExactly(7, 4);
        verify(repository, never()).save(any());
        verifyNoInteractions(jdbc);

        QuoteDto draft = continuations.author(assetRequest());
        assertThat(continuations.validate(saved, saved.getLineItems()).periods())
                .extracting(QuoteContinuationPolicy.Period::unitPrice)
                .containsExactly(draft.getLineItems().getFirst().getUnitPrice(), draft.getLineItems().get(1).getUnitPrice());

        when(assets.installedBase("synthetic-customer", TERM_END)).thenReturn(List.of(installed().get(4)));
        assertThatThrownBy(() -> continuations.validate(saved, saved.getLineItems()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Active assets changed");
    }

    @Test
    void theModeIsRejectedOnANonRenewal() {
        QuoteContinuationRequest expansion = assetRequest();
        expansion.setOperation(QuoteContinuationRequest.Operation.EXPANSION);
        assertThatThrownBy(() -> continuations.author(expansion))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("renewal source mode");
        verifyNoInteractions(assets);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"maxIncreasePct\":5", "\"minIncreasePct\":15", "\"minUnitAmount\":60",
            "\"notAboveList\":true", "\"maxDecreasePct\":5", "\"priceAsOf\":\"ACTION_DATE\""
    })
    void controlledRenewalWithoutAuthoritativePricingFactsRefusesPreviewAndAuthoring(String control) throws Exception {
        var controlled = decodedPolicy("ACCOUNT", ACCOUNT, "CONTRACTED_NET", control);
        when(policies.fetchRungs(eq(ACCOUNT), isNull(), isNull(), eq(TERM_END))).thenReturn(List.of(controlled));

        assertThatThrownBy(() -> continuations.preview(assetRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("UNRESOLVED_BASIS"));
        assertThatThrownBy(() -> continuations.author(assetRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("UNRESOLVED_BASIS"));
        verifyNoInteractions(repository, lines, jdbc);
    }

    @Test
    void lessSpecificCapCannotBeSilentlyDiscardedWhenTheLineSuppliesTheMode() throws Exception {
        when(schedules.byNumber("SUB-R3")).thenReturn(linkedSchedule(true));
        when(assets.installedBase("synthetic-customer", TERM_END))
                .thenReturn(List.of(asset("asset-canonical", CANONICAL_LINE.toString(), "7", "ACTIVE")));
        var linePolicy = decodedPolicy("LINE", CANONICAL_LINE, "CONTRACTED_NET", "\"status\":\"ACTIVE\"");
        var accountCap = decodedPolicy("ACCOUNT", ACCOUNT, "CONTRACTED_NET", "\"maxIncreasePct\":5");
        when(policies.fetchRungs(eq(ACCOUNT), isNull(), eq(CANONICAL_LINE), eq(TERM_END)))
                .thenReturn(List.of(linePolicy, accountCap));
        var request = assetRequest();
        request.setContractLineId(CANONICAL_LINE.toString());

        assertThatThrownBy(() -> continuations.author(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("UNRESOLVED_BASIS"));
        verifyNoInteractions(repository, lines, jdbc);
    }

    @Test
    void listBasedUpliftWithoutPinnedListFactsDoesNotBecomeAnExitRateUplift() throws Exception {
        var listBasis = decodedPolicy("ACCOUNT", ACCOUNT, "CURRENT_LIST", "\"status\":\"ACTIVE\"");
        when(policies.fetchRungs(eq(ACCOUNT), isNull(), isNull(), eq(TERM_END))).thenReturn(List.of(listBasis));

        assertThatThrownBy(() -> continuations.preview(assetRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("UNRESOLVED_BASIS"));
        verifyNoInteractions(repository, lines, jdbc);
    }

    @Test
    void inactiveControlsAndAdditiveMetadataPreserveTheExistingExitRateUplift() throws Exception {
        var ordinary = decodedPolicy("ACCOUNT", ACCOUNT, "CONTRACTED_NET", """
                "status":"ACTIVE","notAboveList":false,"notBelowExitNet":false,
                "priceAsOf":"EFFECTIVE_DATE","retentionMarker":"REVIEW_LATER"
                """);
        when(policies.fetchRungs(eq(ACCOUNT), isNull(), isNull(), eq(TERM_END))).thenReturn(List.of(ordinary));

        var preview = continuations.preview(assetRequest());

        assertThat(preview.periods()).hasSize(2);
        assertThat(preview.periods().getFirst().unitPrice()).isEqualByComparingTo("55.00");
        verifyNoInteractions(repository, lines, jdbc);
    }

    private static RenewalPolicyView decodedPolicy(String scopeKind, UUID scopeRef, String basis, String control)
            throws Exception {
        return new ObjectMapper().readValue("""
                {"id":"%s","scopeKind":"%s","scopeRef":"%s","policyVersion":1,"mode":"UPLIFT_PCT",
                 "basis":"%s","upliftPercentPoints":10,%s}
                """.formatted(POLICY, scopeKind, scopeRef, basis, control), RenewalPolicyView.class);
    }

    private QuoteContinuationRequest assetRequest() {
        return QuoteContinuationRequest.builder()
                .operation(QuoteContinuationRequest.Operation.RENEWAL)
                .renewalSourceMode(QuoteContinuationRequest.RenewalSourceMode.FROM_ACTIVE_ASSETS)
                .targetSubscriptionNumber("SUB-R3")
                .observedRevision(7L)
                .customerExternalId("synthetic-customer")
                .currency("USD")
                .renewalPolicyAccountId(ACCOUNT)
                .build();
    }

    private String assetId(QuoteLineItem line) {
        int start = line.getMetadata().indexOf("asset-");
        int end = line.getMetadata().indexOf('"', start);
        return line.getMetadata().substring(start, end);
    }

    private static RenewalPolicyView policy(String mode, String percent) {
        return new RenewalPolicyView(POLICY, "ACCOUNT", ACCOUNT, 1, mode, "CONTRACTED_NET",
                percent == null ? null : new BigDecimal(percent), null, null);
    }

    private static List<InstalledAssetView> installed() {
        return List.of(
                asset("asset-super", "source-line", "9", "SUPERSEDED"),
                asset("asset-term", "source-line-2", "8", "TERMINATED"),
                asset("asset-other", "other-contract", "3", "ACTIVE"),
                asset("asset-amended", "source-line-2", "4", "AMENDED"),
                asset("asset-active", "source-line", "7", "ACTIVE"));
    }

    private static InstalledAssetView asset(String id, String contractLine, String quantity, String status) {
        return new InstalledAssetView(id, "synthetic-customer", contractLine, 42L, "synthetic-product",
                new BigDecimal(quantity), LocalDate.of(2027, 1, 1), TERM_END, status);
    }

    private static SubscriptionScheduleView schedule() {
        return SubscriptionScheduleView.builder()
                .subscriptionNumber("SUB-R3")
                .customerId("synthetic-customer")
                .currencyCode("USD")
                .status("ACTIVE")
                .startDate(LocalDate.of(2027, 1, 1))
                .endDate(TERM_END)
                .commercialRevision(7L)
                .lines(List.of(line("source-line", "synthetic-product", 40, 5, 2000, 250),
                        line("source-line-2", "synthetic-product-2", 60, 10, 3000, 500)))
                .build();
    }

    private static SubscriptionScheduleView.Line line(String id, String product, int firstUnit, int unitStep,
                                                      int firstMonthly, int monthlyStep) {
        SubscriptionScheduleView.Line source = SubscriptionScheduleView.Line.builder()
                .acceptedLineUuid(id)
                .productExternalId(product)
                .productName(product)
                .productType("SAAS_SEAT_NAMED")
                .lineItemType("SAAS_SEAT_NAMED")
                .billingFrequency("MONTHLY")
                .billingAnchorDate(LocalDate.of(2027, 1, 1))
                .provenance("ACCEPTED_PRICED")
                .periods(new ArrayList<>())
                .build();
        for (int i = 0; i < 3; i++) {
            source.getPeriods().add(SubscriptionScheduleView.Period.builder()
                    .sequence(i + 1)
                    .startMonth(i * 12 + 1)
                    .months(12)
                    .quantity(50)
                    .startDate(LocalDate.of(2027 + i, 1, 1))
                    .endDate(LocalDate.of(2027 + i, 12, 31))
                    .netUnitPrice(BigDecimal.valueOf(firstUnit + (long) i * unitStep))
                    .netMonthly(BigDecimal.valueOf(firstMonthly + (long) i * monthlyStep))
                    .periodAmount(BigDecimal.valueOf((firstMonthly + (long) i * monthlyStep) * 12L))
                    .build());
        }
        return source;
    }
}
