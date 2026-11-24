package com.opencrm.quote.lineitem;

import com.opencrm.quote.config.GlobalExceptionHandler;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.ramp.RampEscalatorExpander;
import com.opencrm.quote.ramp.RampPricingProperties;
import com.opencrm.quote.ramp.RampScheduleAttacher;
import com.opencrm.quote.ramp.RampScheduleGuard;
import com.opencrm.quote.ramp.RampScheduleValidator;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C16/C17 for {@code POST /api/v1/quotes/{quoteId}/line-items/bulk-remove}.
 * The caller's tenant is the {@code tid} {@link TestTokens} mints.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = BulkLineItemRemovalSecurityTest.App.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@Tag("unit")
class BulkLineItemRemovalSecurityTest {

    /** Matches {@code TestTokens.Builder#mint}. */
    static final String CALLER_TENANT = "00000000-0000-4000-8000-00000000c0de";
    static final String OTHER_TENANT = "11111111-1111-4111-8111-111111111111";
    private static final long QUOTE_ID = 9L;
    private static final long LINE_ID = 5L;
    private static final String PATH = "/api/v1/quotes/" + QUOTE_ID + "/line-items/bulk-remove";
    private static final String BODY = "{\"lineItemIds\":[" + LINE_ID + "],\"expectedQuoteVersion\":4}";

    private static final QuoteRepository QUOTES = mock(QuoteRepository.class);
    private static final QuoteLineItemRepository LINES = mock(QuoteLineItemRepository.class);
    private static final EntityManager ENTITY_MANAGER = mock(EntityManager.class);
    private static final RampScheduleGuard GUARD = mock(RampScheduleGuard.class);

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ComponentScan(basePackages = "com.opencrm.quote.security")
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, GlobalExceptionHandler.class})
    static class App {
        @Bean
        QuoteLineItemController quoteLineItemController() {
            // Not a Spring bean: @PersistenceContext would demand an EntityManagerFactory.
            RampScheduleAttacher attacher = new RampScheduleAttacher(
                    new RampPricingProperties(), new RampScheduleValidator(), new RampEscalatorExpander());
            QuoteLineItemService service = new QuoteLineItemService(LINES, QUOTES, attacher, GUARD);
            ReflectionTestUtils.setField(service, "entityManager", ENTITY_MANAGER);
            return new QuoteLineItemController(service);
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;
    private QuoteLineItem line;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        reset(QUOTES, LINES, ENTITY_MANAGER, GUARD);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousBulkRemoveIs401() throws Exception {
        stage(CALLER_TENANT);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        assertThat(line.isDeleted()).isFalse();
        verify(QUOTES, never()).save(any());
    }

    @ParameterizedTest(name = "{0} bulk-remove is 403")
    @ValueSource(strings = {"Admin", "InstalledBase.Read", "NotARealRole"})
    void refusedRoleBulkRemoveIs403(String role) throws Exception {
        stage(CALLER_TENANT);
        asUser(role);
        assertThat(line.isDeleted()).isFalse();
        verify(QUOTES, never()).save(any());
    }

    @Test
    void installedBaseReadOnlyBulkRemoveIs403() throws Exception {
        refusedRoleBulkRemoveIs403("InstalledBase.Read");
    }

    @Test
    void rolelessTokenBulkRemoveIs403() throws Exception {
        stage(CALLER_TENANT);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice").bearer()))
                .andExpect(status().isForbidden());
        assertThat(line.isDeleted()).isFalse();
        verify(QUOTES, never()).save(any());
    }

    @Test
    void internalInvokeBulkRemoveIs403() throws Exception {
        stage(CALLER_TENANT);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service", "Internal.Invoke").bearer()))
                .andExpect(status().isForbidden());
        assertThat(line.isDeleted()).isFalse();
        verify(QUOTES, never()).save(any());
    }

    @ParameterizedTest(name = "{0} may bulk-remove lines on their tenant")
    @ValueSource(strings = {"CPQ.User", "DealDesk", "SalesVP", "CFO"})
    void allowedRoleBulkRemoveDeletesTheLines(String role) throws Exception {
        stage(CALLER_TENANT);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("editor", role).bearer()))
                .andExpect(status().isNoContent());
        assertThat(line.isDeleted()).isTrue();
    }

    @Test
    void otherTenantBulkRemoveDoesNotDeleteLines() throws Exception {
        stage(OTHER_TENANT);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("editor", "CPQ.User").bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        assertThat(line.isDeleted()).isFalse();
        verify(QUOTES, never()).save(any());
        verify(LINES, never()).save(any());
    }

    private void asUser(String role) throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", role).bearer()))
                .andExpect(status().isForbidden());
    }

    private void stage(String quoteTenant) {
        Quote quote = Quote.builder()
                .id(QUOTE_ID)
                .tenantId(quoteTenant)
                .version(4L)
                .status(QuoteStatus.DRAFT)
                .durationInMonths(12)
                .currency("USD")
                .customerExternalId("customer")
                .build();
        line = QuoteLineItem.builder()
                .id(LINE_ID)
                .quote(quote)
                .productExternalId("sku")
                .quantity(1)
                .listPrice(BigDecimal.TEN)
                .extendedAmount(BigDecimal.TEN)
                .build();
        when(QUOTES.findByIdAndDeletedFalse(QUOTE_ID)).thenReturn(Optional.of(quote));
        when(QUOTES.findByIdAndTenantIdAndDeletedFalse(eq(QUOTE_ID), any())).thenAnswer(invocation -> {
            String tenant = invocation.getArgument(1);
            return quoteTenant.equals(tenant) ? Optional.of(quote) : Optional.empty();
        });
        when(LINES.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(QUOTE_ID)).thenReturn(List.of(line));
        when(QUOTES.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
