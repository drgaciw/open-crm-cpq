package com.opencrm.contract.documents;

import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.ActorType;
import com.opencrm.contract.domain.documents.AmendmentDocumentEvent;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplyTargetType;
import com.opencrm.contract.domain.documents.BusinessCategory;
import com.opencrm.contract.domain.documents.ChargeType;
import com.opencrm.contract.domain.documents.ContractHeader;
import com.opencrm.contract.domain.documents.ContractStatus;
import com.opencrm.contract.domain.documents.PolicySlot;
import com.opencrm.contract.domain.documents.PriceAsOf;
import com.opencrm.contract.domain.documents.QuantitySemantics;
import com.opencrm.contract.domain.documents.RevisionCloseStatus;
import com.opencrm.contract.domain.documents.SignaturePolicy;
import com.opencrm.contract.domain.documents.TermModel;
import com.opencrm.contract.repository.documents.AmendmentDocumentEventRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRepository;
import com.opencrm.contract.repository.documents.ContractHeaderRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-S3-1 through AC-S3-11 and the two OQ6 tests. PostgreSQL is required; the class is tagged
 * integration so the Docker-free unit stage does not start a container.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false",
        "opencrm.security.mode=dev"})
class AmendmentDocumentWorkflowIntegrationTest {

    private static final String HASH = "ab".repeat(32);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = POSTGRES.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=contract";
        });
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AmendmentDocumentService documents;
    @Autowired AmendmentRevisionService revisions;
    @Autowired ContractHeaderRepository contracts;
    @Autowired AmendmentDocumentRepository documentRows;
    @Autowired AmendmentDocumentEventRepository events;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext web;

    private MockMvc mvc;
    private final AtomicInteger keys = new AtomicInteger();

    @BeforeEach
    void http() {
        mvc = MockMvcBuilders.webAppContextSetup(web).build();
    }

    @Test
    void acS3_1_sameLifecycleChangeCreatesOneDocumentAndReplays() throws Exception {
        Fixture fixture = fixture(SignaturePolicy.NOT_REQUIRED);
        String body = createJson(fixture, fixture.lifecycleChangeId);
        mvc.perform(post("/api/v1/contracts/{id}/amendments", fixture.contract.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers(fixture, "create-1"))
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mvc.perform(post("/api/v1/contracts/{id}/amendments", fixture.contract.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers(fixture, "create-1"))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.data.id").exists());
        assertThat(documentRows.findByLifecycleChangeId(fixture.lifecycleChangeId)).isPresent();
        assertThat(documentRows.findAll().stream()
                .filter(row -> fixture.lifecycleChangeId.equals(row.getLifecycleChangeId()))
                .count()).isEqualTo(1);

        CreateAmendment command = create(fixture).withLifecycleChangeId(UUID.randomUUID());
        AmendmentView created = documents.create(fixture.contract.getId(), command, context(fixture, "create-natural"));
        AmendmentView again = documents.create(fixture.contract.getId(), command, context(fixture, "create-natural-2"));
        assertThat(again.id()).isEqualTo(created.id());

        assertThatThrownBy(() -> documents.create(
                fixture.contract.getId(), command.withCurrency("EUR"), context(fixture, "create-natural-3")))
                .isInstanceOf(AmendmentCommandException.class)
                .extracting(ex -> ((AmendmentCommandException) ex).getErrorCode())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void acS3_2_pricedSubmitStoresDigestAndAFailingRuleLeavesStateAndNamesTheRule() {
        Fixture fixture = fixture(SignaturePolicy.CLICK_THROUGH);
        AmendmentView draft = documents.create(fixture.contract.getId(), create(fixture), context(fixture, "s2-create"));
        AmendmentCommandException unpriced = catchCommand(() -> documents.submit(draft.id(), draft.version(), context(fixture, "s2-submit")));
        assertThat(unpriced.getStatus().value()).isEqualTo(422);
        assertThat(unpriced.getErrorCode()).isEqualTo("VALIDATION_REJECTED");
        assertThat(unpriced.getRuleId()).isEqualTo("V9");
        AmendmentView unchanged = documents.get(draft.id());
        assertThat(unchanged.status()).isEqualTo(AmendmentDocumentStatus.DRAFT);
        assertThat(unchanged.version()).isEqualTo(draft.version());
        assertThat(reasonCodes(draft.id())).contains("V9");

        AmendmentView priced = revisions.installPricing(draft.id(), 1, pricing(fixture, draft), context(fixture, "s2-price"));
        AmendmentView submitted = documents.submit(priced.id(), priced.version(), context(fixture, "s2-submit-ok"));
        assertThat(submitted.status()).isEqualTo(AmendmentDocumentStatus.IN_REVIEW);
        assertThat(submitted.contentDigest()).matches("^[0-9a-f]{64}$");
        assertThat(submitted.revisions().get(0).contentDigest()).isEqualTo(submitted.contentDigest());
    }

    @Test
    void acS3_3_clickThroughAcceptanceExecutesWithGaplessNumber() {
        AmendmentView executed = executeClickThrough(fixture(SignaturePolicy.CLICK_THROUGH), "s3");
        assertThat(executed.status()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
        assertThat(executed.amendmentSeq()).isEqualTo(1);
        assertThat(executed.amendmentNumber()).endsWith("-A001");
        assertThat(executed.revisions()).anyMatch(revision -> revision.statusAtClose() == RevisionCloseStatus.EXECUTED
                && executed.contentDigest().equals(revision.contentDigest()));
    }

    @Test
    void acS3_4_wetAndEsignDoNotExecute() throws Exception {
        for (SignaturePolicy policy : List.of(SignaturePolicy.WET, SignaturePolicy.E_SIGN)) {
            Fixture fixture = fixture(policy);
            AmendmentView approved = approve(fixture, "s4-" + policy);
            AmendmentCommandException refused = catchCommand(() ->
                    documents.accept(approved.id(), approved.version(), context(fixture, "s4-accept-" + policy)));
            assertThat(refused.getStatus().value()).isEqualTo(422);
            assertThat(refused.getErrorCode()).isEqualTo("SIGNATURE_NOT_SUPPORTED_IN_SLICE");
            assertThat(documents.get(approved.id()).status()).isEqualTo(AmendmentDocumentStatus.APPROVED);
            mvc.perform(post("/api/v1/amendments/{id}:send-for-signature", approved.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .headers(headers(fixture, "s4-sign-" + policy))
                            .content("{\"version\":" + approved.version() + "}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("SIGNATURE_NOT_SUPPORTED_IN_SLICE"));
            assertThat(documents.get(approved.id()).status()).isEqualTo(AmendmentDocumentStatus.APPROVED);
        }
    }

    @Test
    void acS3_5_executedDocumentRejectsMutationAndWritesAnEvent() {
        Fixture fixture = fixture(SignaturePolicy.CLICK_THROUGH);
        AmendmentView executed = executeClickThrough(fixture, "s5");
        String digest = executed.contentDigest();
        AmendmentCommandException refused = catchCommand(() -> revisions.open(
                executed.id(), supersede(fixture, executed, 2), context(fixture, "s5-revise")));
        assertThat(refused.getStatus().value()).isEqualTo(409);
        assertThat(refused.getErrorCode()).isEqualTo("IMMUTABLE_EXECUTED");
        AmendmentView after = documents.get(executed.id());
        assertThat(after.contentDigest()).isEqualTo(digest);
        assertThat(after.revisions()).hasSize(1);
        assertThat(eventTypes(executed.id())).contains("AMENDMENT_REVISION_REJECTED");
    }

    @Test
    void acS3_6_concurrentExecutesOnOneContractAreGapless() throws Exception {
        Fixture first = fixture(SignaturePolicy.CLICK_THROUGH);
        Fixture second = new Fixture(first.contract, first.tenant, UUID.randomUUID(), UUID.randomUUID(),
                first.subscriptionId, SignaturePolicy.CLICK_THROUGH);
        AmendmentView left = approve(first, "s6-a");
        AmendmentView right = approve(second, "s6-b");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<AmendmentView> one = pool.submit(() -> {
                start.await();
                return documents.accept(left.id(), left.version(), context(first, "s6-exec-a"));
            });
            Future<AmendmentView> two = pool.submit(() -> {
                start.await();
                return documents.accept(right.id(), right.version(), context(second, "s6-exec-b"));
            });
            start.countDown();
            List<Integer> sequences = List.of(one.get(30, TimeUnit.SECONDS).amendmentSeq(),
                    two.get(30, TimeUnit.SECONDS).amendmentSeq());
            assertThat(sequences).containsExactlyInAnyOrder(1, 2);
        } finally {
            pool.shutdownNow();
        }
        Integer last = jdbc.queryForObject(
                "SELECT last_executed_seq FROM contracts WHERE id = ?", Integer.class, first.contract.getId());
        assertThat(last).isEqualTo(2);
    }

    @Test
    void acS3_7_withdrawnDraftConsumesNoNumber() {
        Fixture fixture = fixture(SignaturePolicy.NOT_REQUIRED);
        AmendmentView draft = documents.create(fixture.contract.getId(), create(fixture), context(fixture, "s7-create"));
        AmendmentView withdrawn = documents.withdraw(draft.id(), draft.version(), context(fixture, "s7-withdraw"));
        assertThat(withdrawn.status()).isEqualTo(AmendmentDocumentStatus.WITHDRAWN);
        assertThat(withdrawn.amendmentSeq()).isNull();
        assertThat(withdrawn.amendmentNumber()).isNull();
        Integer last = jdbc.queryForObject(
                "SELECT last_executed_seq FROM contracts WHERE id = ?", Integer.class, fixture.contract.getId());
        assertThat(last).isZero();
    }

    @Test
    void acS3_8_backdatingWithoutReasonFailsV6AndDoesNotChangeState() {
        Fixture fixture = fixture(SignaturePolicy.NOT_REQUIRED);
        CreateAmendment backdated = create(fixture).withDates(
                LocalDate.parse("2026-06-02"), LocalDate.parse("2026-06-01"), null);
        AmendmentCommandException onCreate = catchCommand(() ->
                documents.create(fixture.contract.getId(), backdated, context(fixture, "s8-create")));
        assertThat(onCreate.getStatus().value()).isEqualTo(422);
        assertThat(onCreate.getRuleId()).isEqualTo("V6");
        assertThat(documentRows.findByLifecycleChangeId(fixture.lifecycleChangeId)).isEmpty();

        AmendmentView draft = documents.create(fixture.contract.getId(), create(fixture), context(fixture, "s8-ok"));
        AmendmentCommandException onRevise = catchCommand(() -> revisions.open(
                draft.id(), supersede(fixture, draft, 2).withDates(
                        LocalDate.parse("2026-06-02"), LocalDate.parse("2026-06-01"), null),
                context(fixture, "s8-revise")));
        assertThat(onRevise.getRuleId()).isEqualTo("V6");
        assertThat(documents.get(draft.id()).status()).isEqualTo(AmendmentDocumentStatus.DRAFT);
        assertThat(documents.get(draft.id()).version()).isEqualTo(draft.version());
        assertThat(reasonCodes(draft.id())).contains("V6");
    }

    @Test
    void acS3_9_andOq6SupersedeBeforeExecutionClearsPinsAndVoidsApproval() {
        supersedeFrom(AmendmentDocumentStatus.DRAFT, SignaturePolicy.CLICK_THROUGH, "s9-draft");
        supersedeFrom(AmendmentDocumentStatus.IN_REVIEW, SignaturePolicy.CLICK_THROUGH, "s9-review");
        supersedeFrom(AmendmentDocumentStatus.APPROVED, SignaturePolicy.CLICK_THROUGH, "s9-approved");
    }

    @Test
    void acS3_10_andOq6NoRevisionAfterExecutedAndTerminalsStayClosed() {
        Fixture executed = fixture(SignaturePolicy.CLICK_THROUGH);
        AmendmentView done = executeClickThrough(executed, "s10");
        assertImmutable(executed, done, "s10-again");
        for (String status : List.of("APPLIED", "PARTIALLY_APPLIED", "APPLY_BLOCKED")) {
            Fixture fixture = fixture(SignaturePolicy.CLICK_THROUGH);
            AmendmentView row = executeClickThrough(fixture, "s10-" + status);
            jdbc.update("UPDATE amendment_documents SET status = ? WHERE id = ?", status, row.id());
            assertImmutable(fixture, documents.get(row.id()), "s10-status-" + status);
        }
        Fixture rejected = fixture(SignaturePolicy.CLICK_THROUGH);
        AmendmentView review = submitPriced(rejected, "s10-reject");
        AmendmentView closed = documents.rejectReview(review.id(), review.version(), context(rejected, "s10-do-reject"));
        assertThat(closed.status()).isEqualTo(AmendmentDocumentStatus.REJECTED);
        assertIllegal(rejected, closed, "s10-reject-revise");

        Fixture withdrawn = fixture(SignaturePolicy.NOT_REQUIRED);
        AmendmentView draft = documents.create(withdrawn.contract.getId(), create(withdrawn), context(withdrawn, "s10-w"));
        AmendmentView gone = documents.withdraw(draft.id(), draft.version(), context(withdrawn, "s10-wd"));
        assertIllegal(withdrawn, gone, "s10-wd-revise");

        jdbc.update("UPDATE amendment_documents SET status = 'VOID' WHERE id = ?", gone.id());
        assertIllegal(withdrawn, documents.get(gone.id()), "s10-void");
    }

    @Test
    void acS3_11_revisionAndExecutionOfTheSameVersionHaveOneWinner() throws Exception {
        Fixture fixture = fixture(SignaturePolicy.CLICK_THROUGH);
        AmendmentView approved = approve(fixture, "s11");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> execute = pool.submit(() -> {
                start.await();
                try {
                    return documents.accept(approved.id(), approved.version(), context(fixture, "s11-exec"));
                } catch (AmendmentCommandException failure) {
                    return failure;
                }
            });
            Future<Object> revise = pool.submit(() -> {
                start.await();
                try {
                    return revisions.open(approved.id(), supersede(fixture, approved, 2), context(fixture, "s11-revise"));
                } catch (AmendmentCommandException failure) {
                    return failure;
                }
            });
            start.countDown();
            Object left = execute.get(30, TimeUnit.SECONDS);
            Object right = revise.get(30, TimeUnit.SECONDS);
            long wins = List.of(left, right).stream().filter(AmendmentView.class::isInstance).count();
            long conflicts = List.of(left, right).stream().filter(AmendmentCommandException.class::isInstance)
                    .filter(value -> ((AmendmentCommandException) value).getStatus().value() == 409
                            && "VERSION_CONFLICT".equals(((AmendmentCommandException) value).getErrorCode()))
                    .count();
            assertThat(wins).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            AmendmentView after = documents.get(approved.id());
            if (after.status() == AmendmentDocumentStatus.EXECUTED) {
                assertThat(after.contentDigest()).isEqualTo(after.revisions().stream()
                        .filter(revision -> revision.statusAtClose() == RevisionCloseStatus.EXECUTED)
                        .findFirst().orElseThrow().contentDigest());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void crossTenantReadIsNotFound() {
        Fixture fixture = fixture(SignaturePolicy.NOT_REQUIRED);
        AmendmentView draft = documents.create(fixture.contract.getId(), create(fixture), context(fixture, "tenant-create"));
        CommandContext other = new CommandContext(UUID.randomUUID(), "s5-caller", "tenant-read",
                "seller-1", ActorType.USER, "seller", "corr-tenant");
        assertThatThrownBy(() -> documents.getForTenant(draft.id(), other))
                .isInstanceOf(com.opencrm.common.exception.ResourceNotFoundException.class);
        assertThat(documents.getForTenant(draft.id(), context(fixture, "tenant-ok")).id()).isEqualTo(draft.id());
    }

    @Test
    void oq6_secondExecutedRevisionIsRejected() {
        Fixture fixture = fixture(SignaturePolicy.CLICK_THROUGH);
        AmendmentView executed = executeClickThrough(fixture, "oq6-once");
        AmendmentCommandException second = catchCommand(() ->
                documents.execute(executed.id(), executed.version(), context(fixture, "oq6-twice")));
        assertThat(second.getErrorCode()).isEqualTo("IMMUTABLE_EXECUTED");
        assertThat(documents.get(executed.id()).revisions().stream()
                .filter(revision -> revision.statusAtClose() == RevisionCloseStatus.EXECUTED)
                .count()).isEqualTo(1);
    }

    private void supersedeFrom(AmendmentDocumentStatus status, SignaturePolicy policy, String seed) {
        Fixture fixture = fixture(policy);
        AmendmentView current = switch (status) {
            case DRAFT -> documents.create(fixture.contract.getId(), create(fixture), context(fixture, seed + "-c"));
            case IN_REVIEW -> submitPriced(fixture, seed);
            case APPROVED -> approve(fixture, seed);
            default -> throw new IllegalStateException(status.name());
        };
        String previousDigest = current.revisions().get(0).contentDigest();
        AmendmentView opened = revisions.open(current.id(), supersede(fixture, current, 2), context(fixture, seed + "-r"));
        assertThat(opened.provisionalNumber()).isEqualTo(current.provisionalNumber());
        assertThat(opened.status()).isEqualTo(AmendmentDocumentStatus.DRAFT);
        assertThat(opened.contentDigest()).isNull();
        assertThat(opened.approvalRef()).isNull();
        assertThat(opened.signatureEnvelopeRef()).isNull();
        assertThat(opened.tcvDelta()).isNull();
        assertThat(opened.lines()).allSatisfy(line -> {
            assertThat(line.productExternalId()).isNull();
            assertThat(line.pricebookVersion()).isNull();
            assertThat(line.configurationHash()).isNull();
            assertThat(line.policyDecisionId()).isNull();
        });
        RevisionView frozen = opened.revisions().stream()
                .filter(revision -> revision.revisionNo() == 1).findFirst().orElseThrow();
        assertThat(frozen.statusAtClose()).isEqualTo(RevisionCloseStatus.SUPERSEDED);
        assertThat(frozen.contentDigest()).matches("^[0-9a-f]{64}$");
        if (previousDigest != null) {
            assertThat(frozen.contentDigest()).isEqualTo(previousDigest);
        }
        assertThat(eventTypes(opened.id())).contains("AMENDMENT_SUPERSEDED");
        AmendmentCommandException blocked = catchCommand(() ->
                documents.submit(opened.id(), opened.version(), context(fixture, seed + "-resubmit")));
        assertThat(blocked.getRuleId()).isIn("V8", "V9");
        assertThat(documents.get(opened.id()).status()).isEqualTo(AmendmentDocumentStatus.DRAFT);
    }

    private void assertImmutable(Fixture fixture, AmendmentView current, String seed) {
        String digest = current.contentDigest();
        AmendmentCommandException refused = catchCommand(() ->
                revisions.open(current.id(), supersede(fixture, current, 2), context(fixture, seed)));
        assertThat(refused.getStatus().value()).isEqualTo(409);
        assertThat(refused.getErrorCode()).isEqualTo("IMMUTABLE_EXECUTED");
        assertThat(documents.get(current.id()).contentDigest()).isEqualTo(digest);
        assertThat(eventTypes(current.id())).contains("AMENDMENT_REVISION_REJECTED");
    }

    private void assertIllegal(Fixture fixture, AmendmentView current, String seed) {
        AmendmentCommandException refused = catchCommand(() ->
                revisions.open(current.id(), supersede(fixture, current, current.revisions().size() + 1), context(fixture, seed)));
        assertThat(refused.getStatus().value()).isEqualTo(409);
        assertThat(refused.getErrorCode()).isEqualTo("ILLEGAL_TRANSITION");
    }

    private AmendmentView executeClickThrough(Fixture fixture, String seed) {
        AmendmentView approved = approve(fixture, seed);
        return documents.accept(approved.id(), approved.version(), context(fixture, seed + "-accept"));
    }

    private AmendmentView approve(Fixture fixture, String seed) {
        AmendmentView submitted = submitPriced(fixture, seed);
        return documents.recordApproval(submitted.id(), submitted.version(), "approval-" + seed, context(fixture, seed + "-approve"));
    }

    private AmendmentView submitPriced(Fixture fixture, String seed) {
        AmendmentView draft = documents.create(fixture.contract.getId(), create(fixture), context(fixture, seed + "-create"));
        AmendmentView priced = revisions.installPricing(draft.id(), 1, pricing(fixture, draft), context(fixture, seed + "-price"));
        return documents.submit(priced.id(), priced.version(), context(fixture, seed + "-submit"));
    }

    private AmendmentCommandException catchCommand(Runnable call) {
        try {
            call.run();
        } catch (AmendmentCommandException exception) {
            return exception;
        }
        throw new AssertionError("expected AmendmentCommandException");
    }

    private List<String> eventTypes(UUID documentId) {
        return events.findAll().stream()
                .filter(event -> documentId.equals(event.getDocumentId()))
                .map(AmendmentDocumentEvent::getEventType)
                .toList();
    }

    private List<String> reasonCodes(UUID documentId) {
        return events.findAll().stream()
                .filter(event -> documentId.equals(event.getDocumentId()))
                .map(AmendmentDocumentEvent::getReasonCode)
                .toList();
    }

    private Fixture fixture(SignaturePolicy policy) {
        UUID tenant = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        ContractHeader contract = new TransactionTemplate(transactions).execute(status -> contracts.saveAndFlush(ContractHeader.builder()
                .tenantId(tenant)
                .contractNumber("C-" + subscriptionId.toString().substring(0, 8))
                .customerAccountId(UUID.randomUUID())
                .termModel(TermModel.TERMED)
                .startDate(LocalDate.parse("2026-01-01"))
                .endDate(LocalDate.parse("2026-12-31"))
                .status(ContractStatus.ACTIVE)
                .lastExecutedSeq(0)
                .createdBy("s3-test")
                .build()));
        jdbc.update("""
                INSERT INTO subscriptions(uuid, subscription_number, customer_id, quote_id, plan_name,
                    start_date, end_date, status, currency_code, contract_id, commercial_revision)
                VALUES (?, ?, 'customer', 'quote', 'Plan', '2026-01-01', '2026-12-31', 'ACTIVE', 'USD', ?, 0)
                """, subscriptionId.toString(), "SUB-" + subscriptionId.toString().substring(0, 8), contract.getId());
        return new Fixture(contract, tenant, UUID.randomUUID(), UUID.randomUUID(), subscriptionId, policy);
    }

    private CreateAmendment create(Fixture fixture) {
        return new CreateAmendment(
                fixture.lifecycleChangeId, 1, fixture.targetQuoteId, "caller-snap", fixture.policy,
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-01"), null, "USD",
                List.of(line(fixture)));
    }

    private OpenRevision supersede(Fixture fixture, AmendmentView current, int lifecycleRevision) {
        return new OpenRevision(current.version(), fixture.lifecycleChangeId, lifecycleRevision,
                fixture.targetQuoteId, "caller-snap-" + lifecycleRevision,
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-01"), null,
                List.of(line(fixture)));
    }

    private InstallPricing pricing(Fixture fixture, AmendmentView current) {
        return new InstallPricing(current.version(), fixture.lifecycleChangeId, 1, fixture.targetQuoteId,
                "caller-snap", HASH, "policy", "v1", "decision-1", "trace-1", "USD",
                new BigDecimal("10.0000"), new BigDecimal("10.0000"), BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(new PricedLine(fixture.changeLineRef, "sku-1", "pb-1", HASH,
                        "policy", "v1", "decision-1", "trace-1",
                        new BigDecimal("10.0000"), new BigDecimal("10.0000"))));
    }

    private LineCommand line(Fixture fixture) {
        return new LineCommand(fixture.changeLineRef, ActionCode.ADD, BusinessCategory.UPSELL,
                ApplyTargetType.SUBSCRIPTION, fixture.subscriptionId, null, QuantitySemantics.DELTA,
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("2"),
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-12-31"),
                ChargeType.RECURRING, PriceAsOf.EFFECTIVE_DATE, PolicySlot.AMENDMENT, 1, 0L);
    }

    private CommandContext context(Fixture fixture, String seed) {
        return new CommandContext(fixture.tenant, "s5-caller", seed + "-" + keys.incrementAndGet(),
                "seller-1", ActorType.USER, "seller", "corr-" + seed);
    }

    private org.springframework.http.HttpHeaders headers(Fixture fixture, String key) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Idempotency-Key", key);
        headers.add("X-Tenant-Id", fixture.tenant.toString());
        headers.add("X-Caller-Id", "s5-caller");
        headers.add("X-Actor-Id", "seller-1");
        headers.add("X-Actor-Role", "seller");
        headers.add("X-Correlation-Id", "corr-" + key);
        return headers;
    }

    private String createJson(Fixture fixture, UUID lifecycleChangeId) {
        return """
                {"lifecycleChangeId":"%s","lifecycleChangeRevision":1,"targetQuoteId":"%s",
                "inputsSnapshotRef":"caller-snap","signaturePolicy":"%s","actionDate":"2026-06-01",
                "contractEffectiveDate":"2026-06-01","backdatingReasonCode":null,"currency":"USD",
                "lines":[{"changeLineRef":"%s","action":"ADD","category":"UPSELL","targetType":"SUBSCRIPTION",
                "targetSubscriptionId":"%s","sourceAcceptedLineUuid":null,"semantics":"DELTA",
                "startQty":1,"deltaQty":1,"endQty":2,"effectiveFrom":"2026-06-01","effectiveTo":"2026-12-31",
                "chargeType":"RECURRING","priceAsOf":"EFFECTIVE_DATE","policySlot":"AMENDMENT",
                "applicationUnitNo":1,"observedCommercialRevision":0}]}
                """.formatted(lifecycleChangeId, fixture.targetQuoteId, fixture.policy,
                fixture.changeLineRef, fixture.subscriptionId);
    }

    private record Fixture(ContractHeader contract, UUID tenant, UUID lifecycleChangeId, UUID targetQuoteId,
                           UUID subscriptionId, SignaturePolicy policy, UUID changeLineRef) {
        Fixture(ContractHeader contract, UUID tenant, UUID lifecycleChangeId, UUID targetQuoteId, UUID subscriptionId) {
            this(contract, tenant, lifecycleChangeId, targetQuoteId, subscriptionId, SignaturePolicy.NOT_REQUIRED, UUID.randomUUID());
        }

        Fixture(ContractHeader contract, UUID tenant, UUID lifecycleChangeId, UUID targetQuoteId,
                UUID subscriptionId, SignaturePolicy policy) {
            this(contract, tenant, lifecycleChangeId, targetQuoteId, subscriptionId, policy, UUID.randomUUID());
        }
    }
}
