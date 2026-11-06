package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.quote.client.PricingExecuteView;
import com.opencrm.quote.client.PricingLineResultView;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tenant is taken from the verified user's Entra {@code tid} on create and clone only.
 * A request body cannot set it, and later lifecycle writes leave it unchanged.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Quote tenantId from the caller tid")
class QuoteServiceTenantTest {

    static final String OWNER_WORDING = "tenantId is the Entra `tid` claim: a per-deployment value "
            + "(single issuer per deployment, ADR-016), chosen by the owner on 2026-10-23 as a deliberately "
            + "weak interim. It is NOT a real per-tenant id. DRG-631 publishes this same value on QuoteAccepted "
            + "and inherits this limit.";

    private static final String STORED = "tenant-a";
    private static final String CALLER = "tenant-b";

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private QuoteNumberGenerator quoteNumberGenerator;
    @Mock
    private QuoteEventPublisher eventPublisher;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepository;
    @Mock
    private PricingServiceClient pricingServiceClient;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    /** Same unknown-property policy as Spring Boot's HTTP mapper: extra JSON fields are dropped. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final QuoteMapper quoteMapper = Mappers.getMapper(QuoteMapper.class);
    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(
                quoteRepository,
                quoteMapper,
                quoteNumberGenerator,
                eventPublisher,
                quoteLineItemRepository,
                pricingServiceClient,
                productServiceClient,
                objectMapper,
                outboxEventPublisher,
                new CurrentActor("service:quote-service", false),
                RampScheduleTestSupport.guard(quoteLineItemRepository, outboxEventPublisher));
    }

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("AC-T1: V1_13 adds a nullable tenant_id with a manual rollback comment and no backfill")
    void migrationIsNullableWithManualRollbackAndNoBackfill() throws Exception {
        String sql = Files.readString(serviceRoot().resolve(
                "src/main/resources/db/migration/V1_13__quote_tenant_id.sql"));

        assertThat(sql).contains("SET search_path TO quote;");
        assertThat(sql).contains("ALTER TABLE quotes ADD COLUMN tenant_id VARCHAR(128);");
        assertThat(sql).contains(
                "Rollback is manual. Flyway Community does not run undo scripts. Apply in the quote schema:");
        assertThat(sql).contains("ALTER TABLE quotes DROP COLUMN IF EXISTS tenant_id;");
        assertThat(sql).contains("Do not drop the column while that code is deployed.");
        assertThat(sql).contains("no backfill");
        assertThat(sql).doesNotContain("NOT NULL");
        assertThat(sql).doesNotContain("DEFAULT");
        assertThat(sql).doesNotContain("UPDATE ");
    }

    @Test
    @DisplayName("AC-T2: create stores the user token tid")
    void createStoresTidFromUserToken() throws Exception {
        signInUser("seller-1", CALLER);
        when(quoteNumberGenerator.generate()).thenReturn("Q-100");
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        quoteService.create(QuoteCreateRequest.builder().name("Acme").customerExternalId("cust-1").build());

        Quote saved = savedQuote();
        assertThat(tenantOf(saved)).isEqualTo(CALLER);
    }

    @Test
    @DisplayName("AC-T3: clone stores the caller tid, not the source quote tenant")
    void cloneStoresCallerTidNotSourceTenant() throws Exception {
        Quote source = sourceQuote(STORED);
        signInUser("seller-2", CALLER);
        stubClone(source);

        quoteService.clone(source.getId());

        assertThat(savedQuote().getId()).isNotEqualTo(source.getId());
        assertThat(tenantOf(savedQuote())).isEqualTo(CALLER);
        assertThat(tenantOf(source)).isEqualTo(STORED);
    }

    @Test
    @DisplayName("AC-T3: clone with no caller tid stores NULL even when the source has a tenant")
    void cloneWithoutCallerTidStoresNull() throws Exception {
        Quote source = sourceQuote(STORED);
        stubClone(source);

        quoteService.clone(source.getId());

        assertThat(tenantOf(savedQuote())).isNull();
        assertThat(tenantOf(source)).isEqualTo(STORED);
    }

    @Test
    @DisplayName("AC-T4: a tenantId in the create body is ignored")
    void createIgnoresTenantIdInTheBody() throws Exception {
        signInUser("seller-1", CALLER);
        when(quoteNumberGenerator.generate()).thenReturn("Q-100");
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        QuoteCreateRequest request = objectMapper.readValue(
                "{\"name\":\"Acme\",\"customerExternalId\":\"cust-1\",\"tenantId\":\"evil\"}",
                QuoteCreateRequest.class);

        quoteService.create(request);

        assertThat(tenantOf(savedQuote())).isEqualTo(CALLER);
    }

    @Test
    @DisplayName("AC-T4: a tenantId in the update body is ignored")
    void updateIgnoresTenantIdInTheBody() throws Exception {
        signInUser("seller-1", "other-tid");
        Quote quote = storedQuote(QuoteStatus.DRAFT);
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(quote.getId()))
                .thenReturn(List.of());
        QuoteUpdateRequest request = objectMapper.readValue(
                "{\"name\":\"Renamed\",\"tenantId\":\"evil\"}", QuoteUpdateRequest.class);

        quoteService.update(quote.getId(), request);

        assertThat(quote.getName()).isEqualTo("Renamed");
        assertThat(tenantOf(quote)).isEqualTo(STORED);
    }

    @Test
    @DisplayName("AC-T5: update and lifecycle transitions leave the stored tenant unchanged")
    void lifecycleWritesLeaveTenantUnchanged() throws Exception {
        signInUser("reviewer-oid", "other-tid", "ROLE_DEAL_DESK");

        Quote updated = storedQuote(QuoteStatus.DRAFT);
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(updated.getId()))
                .thenReturn(List.of());
        quoteService.update(updated.getId(), QuoteUpdateRequest.builder().name("Edited").build());
        assertThat(tenantOf(updated)).isEqualTo(STORED);

        Quote submitted = storedQuote(QuoteStatus.DRAFT);
        submitted.getLineItems().add(line("10"));
        when(productServiceClient.validateConfiguration(List.of(10L))).thenReturn(ConfigurationValidationResult.ok());
        quoteService.submitForReview(submitted.getId());
        assertThat(submitted.getStatus()).isEqualTo(QuoteStatus.IN_REVIEW);
        assertThat(tenantOf(submitted)).isEqualTo(STORED);

        Quote approved = storedQuote(QuoteStatus.IN_REVIEW);
        quoteService.approve(approved.getId());
        assertThat(approved.getStatus()).isEqualTo(QuoteStatus.APPROVED);
        assertThat(tenantOf(approved)).isEqualTo(STORED);

        Quote rejected = storedQuote(QuoteStatus.IN_REVIEW);
        quoteService.reject(rejected.getId(), "price");
        assertThat(rejected.getStatus()).isEqualTo(QuoteStatus.REJECTED);
        assertThat(tenantOf(rejected)).isEqualTo(STORED);

        Quote sent = storedQuote(QuoteStatus.APPROVED);
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(sent.getId()))
                .thenReturn(List.of());
        quoteService.send(sent.getId());
        assertThat(sent.getStatus()).isEqualTo(QuoteStatus.SENT);
        assertThat(tenantOf(sent)).isEqualTo(STORED);

        Quote accepted = storedQuote(QuoteStatus.SENT);
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(accepted.getId()))
                .thenReturn(List.of());
        quoteService.accept(accepted.getId());
        assertThat(accepted.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(tenantOf(accepted)).isEqualTo(STORED);

        Quote expired = storedQuote(QuoteStatus.SENT);
        quoteService.expire(expired.getId());
        assertThat(expired.getStatus()).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(tenantOf(expired)).isEqualTo(STORED);

        Quote cancelled = storedQuote(QuoteStatus.DRAFT);
        quoteService.cancel(cancelled.getId());
        assertThat(cancelled.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
        assertThat(tenantOf(cancelled)).isEqualTo(STORED);

        Quote calculated = storedQuote(QuoteStatus.DRAFT);
        QuoteLineItem priced = line("PROD-001");
        priced.setLineItemType("SUBSCRIPTION");
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(calculated.getId()))
                .thenReturn(List.of(priced));
        when(quoteLineItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pricingServiceClient.executePricing(anyMap())).thenReturn(ApiResponse.success(
                PricingExecuteView.builder()
                        .success(true)
                        .lineResults(List.of(PricingLineResultView.builder()
                                .productExternalId("PROD-001")
                                .lineItemType("SUBSCRIPTION")
                                .listPrice(new BigDecimal("10.00"))
                                .unitPrice(new BigDecimal("10.00"))
                                .discountAmount(BigDecimal.ZERO)
                                .finalPrice(new BigDecimal("10.00"))
                                .build()))
                        .build(),
                "ok"));
        quoteService.calculate(calculated.getId());
        assertThat(tenantOf(calculated)).isEqualTo(STORED);
    }

    @Test
    @DisplayName("AC-T6: service, anonymous, dev, blank, and missing tid store NULL")
    void missingUserTidStoresNull() throws Exception {
        when(quoteNumberGenerator.generate()).thenReturn("Q-100");
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        QuoteCreateRequest request = QuoteCreateRequest.builder().name("Acme").customerExternalId("cust-1").build();

        signInService("quote-client", "should-not-stick");
        quoteService.create(request);
        assertThat(tenantOf(savedQuote())).isNull();

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        quoteService.create(request);
        assertThat(tenantOf(savedQuote())).isNull();

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("dev-user", null, AuthorityUtils.NO_AUTHORITIES));
        quoteService.create(request);
        assertThat(tenantOf(savedQuote())).isNull();

        SecurityContextHolder.clearContext();
        quoteService.create(request);
        assertThat(tenantOf(savedQuote())).isNull();

        signInUser("seller-1", "   ");
        quoteService.create(request);
        assertThat(tenantOf(savedQuote())).isNull();

        signInUser("seller-1", null);
        quoteService.create(request);
        assertThat(tenantOf(savedQuote())).isNull();
    }

    @Test
    @DisplayName("mapper ignores tenantId and the DTO and requests have no tenant field")
    void mapperNeverReadsOrWritesTenant() throws Exception {
        String mapper = Files.readString(serviceRoot().resolve(
                "src/main/java/com/opencrm/quote/quote/QuoteMapper.java"));
        assertThat(count(mapper, "@Mapping(target = \"tenantId\", ignore = true)")).isEqualTo(2);

        String quoteSource = Files.readString(serviceRoot().resolve(
                "src/main/java/com/opencrm/quote/quote/Quote.java"));
        assertThat(quoteSource).contains(OWNER_WORDING);
        assertThat(quoteSource).contains("@Column(name = \"tenant_id\", length = 128, updatable = false)");

        String resolver = Files.readString(serviceRoot().resolve(
                "src/main/java/com/opencrm/quote/quote/QuoteTenantResolver.java"));
        assertThat(resolver).contains("SecurityUtils.getCurrentUser()");
        assertThat(resolver).doesNotContain("@Component");
        assertThat(resolver).doesNotContain("@Service");
        assertThat(resolver).doesNotContain("@Bean");

        assertThat(fieldNames(QuoteCreateRequest.class)).doesNotContain("tenantId");
        assertThat(fieldNames(QuoteUpdateRequest.class)).doesNotContain("tenantId");
        assertThat(fieldNames(QuoteDto.class)).doesNotContain("tenantId");

        QuoteCreateRequest create = objectMapper.readValue(
                "{\"name\":\"Acme\",\"customerExternalId\":\"cust-1\",\"tenantId\":\"evil\"}",
                QuoteCreateRequest.class);
        Quote mapped = quoteMapper.toEntity(create);
        assertThat(tenantOf(mapped)).isNull();

        Quote existing = Quote.builder().name("Original").customerExternalId("cust-1").build();
        setTenant(existing, STORED);
        quoteMapper.updateEntity(objectMapper.readValue(
                "{\"name\":\"Renamed\",\"tenantId\":\"evil\"}", QuoteUpdateRequest.class), existing);
        assertThat(existing.getName()).isEqualTo("Renamed");
        assertThat(tenantOf(existing)).isEqualTo(STORED);

        setTenant(existing, STORED);
        String json = objectMapper.writeValueAsString(quoteMapper.toDto(existing));
        assertThat(json).doesNotContain("tenantId");
        assertThat(json).doesNotContain(STORED);
    }

    private Quote savedQuote() {
        var captor = org.mockito.ArgumentCaptor.forClass(Quote.class);
        verify(quoteRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private Quote sourceQuote(String tenant) throws Exception {
        Quote source = Quote.builder()
                .id(7L)
                .uuid("source-uuid")
                .quoteNumber("Q-SOURCE")
                .name("Source")
                .customerExternalId("cust-1")
                .status(QuoteStatus.ACCEPTED)
                .dealType(DealType.NEW)
                .currency("USD")
                .durationInMonths(12)
                .build();
        setTenant(source, tenant);
        return source;
    }

    private void stubClone(Quote source) {
        when(quoteRepository.findByIdAndDeletedFalse(source.getId())).thenReturn(Optional.of(source));
        when(quoteNumberGenerator.generate()).thenReturn("Q-COPY");
        AtomicLong ids = new AtomicLong(100);
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> {
            Quote quote = invocation.getArgument(0);
            if (quote.getId() == null) {
                quote.setId(ids.getAndIncrement());
            }
            return quote;
        });
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(source.getId()))
                .thenReturn(List.of());
    }

    private final AtomicLong quoteIds = new AtomicLong(1);

    private Quote storedQuote(QuoteStatus status) throws Exception {
        long id = quoteIds.getAndIncrement();
        Quote quote = Quote.builder()
                .id(id)
                .uuid("uuid-" + id)
                .quoteNumber("Q-" + id)
                .name("Original")
                .customerExternalId("cust-1")
                .status(status)
                .dealType(DealType.NEW)
                .currency("USD")
                .durationInMonths(12)
                .totalAmount(new BigDecimal("10.00"))
                .createdBy("original-author")
                .approvalState(ApprovalState.NONE)
                .build();
        if (quote.getLineItems() == null) {
            quote.setLineItems(new java.util.ArrayList<>());
        }
        setTenant(quote, STORED);
        when(quoteRepository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return quote;
    }

    private static QuoteLineItem line(String productExternalId) {
        return QuoteLineItem.builder()
                .productExternalId(productExternalId)
                .productName("Seat")
                .quantity(1)
                .listPrice(new BigDecimal("10.00"))
                .build();
    }

    private static void signInUser(String oid, String tid, String... authorities) {
        Jwt.Builder jwt = baseJwt(oid)
                .claim("oid", oid)
                .claim("scp", "access_as_user")
                .claim("azp", "quote-ui");
        if (tid != null) {
            jwt.claim("tid", tid);
        }
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt.build(), AuthorityUtils.createAuthorityList(authorities)));
    }

    private static void signInService(String clientId, String tid) {
        Jwt jwt = baseJwt("sp-" + clientId)
                .claim("azp", clientId)
                .claim("idtyp", "app")
                .claim("tid", tid)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_INTERNAL_INVOKE")));
    }

    private static Jwt.Builder baseJwt(String subject) {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .subject(subject);
    }

    private static String tenantOf(Quote quote) {
        return quote.getTenantId();
    }

    private static void setTenant(Quote quote, String tenant) {
        quote.setTenantId(tenant);
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }

    private static int count(String text, String token) {
        int found = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(token, from);
            if (at < 0) {
                return found;
            }
            found++;
            from = at + token.length();
        }
    }

    private static Path serviceRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("src/main/java/com/opencrm/quote/quote/Quote.java"))) {
            return cwd;
        }
        Path nested = cwd.resolve("services/quote-service");
        if (Files.exists(nested.resolve("src/main/java/com/opencrm/quote/quote/Quote.java"))) {
            return nested;
        }
        throw new AssertionError("quote-service root not found from " + cwd);
    }
}
