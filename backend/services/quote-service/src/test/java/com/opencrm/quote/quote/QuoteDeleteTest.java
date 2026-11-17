package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.config.GlobalExceptionHandler;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.ramp.RampScheduleGuard;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller, service, advice and JWT filter; only persistence and outgoing ports are doubles. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = QuoteDeleteTest.App.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
class QuoteDeleteTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, QuoteController.class, GlobalExceptionHandler.class})
    static class App {
        @Bean QuoteContinuationService continuation(Ports p) { return p.continuation; }
        @Bean Ports ports() { return new Ports(); }
        @Bean QuoteService quoteService(Ports p) {
            return new QuoteService(p.quotes, p.mapper, p.numbers, p.events, p.lines, p.pricing,
                    p.products, new ObjectMapper(), p.outbox, p.actor, p.ramps);
        }
    }

    static class Ports {
        final QuoteContinuationService continuation = mock(QuoteContinuationService.class);
        final QuoteRepository quotes = mock(QuoteRepository.class);
        final QuoteMapper mapper = mock(QuoteMapper.class);
        final QuoteNumberGenerator numbers = mock(QuoteNumberGenerator.class);
        final QuoteEventPublisher events = mock(QuoteEventPublisher.class);
        final QuoteLineItemRepository lines = mock(QuoteLineItemRepository.class);
        final PricingServiceClient pricing = mock(PricingServiceClient.class);
        final ProductServiceClient products = mock(ProductServiceClient.class);
        final OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
        final CurrentActor actor = mock(CurrentActor.class);
        final RampScheduleGuard ramps = mock(RampScheduleGuard.class);
        Object[] all() { return new Object[]{continuation, quotes, mapper, numbers, events, lines, pricing, products, outbox, actor, ramps}; }
        void noOtherEffects() {
            verifyNoInteractions(continuation, mapper, numbers, events, lines, pricing, products, outbox, actor, ramps);
            verifyNoMoreInteractions(quotes);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired Ports ports;
    MockMvc mvc;

    @BeforeEach void setUp() {
        reset(ports.all());
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    private Quote stored(QuoteStatus status) {
        Quote quote = Quote.builder().id(7L).quoteNumber("Q-7").status(status)
                .approvalState(ApprovalState.PENDING).tenantId("stored-tenant").build();
        when(ports.quotes.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote));
        return quote;
    }

    @Test void pendingRefusalIs409AndLeavesEveryQuoteFieldAndOutgoingPortUntouched() throws Exception {
        Quote quote = stored(QuoteStatus.PENDING_APPROVAL);
        var before = new ObjectMapper().valueToTree(quote);
        mvc.perform(delete("/api/v1/quotes/7").header("Authorization", TestTokens.user("seller", "CPQ.User").bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("QUOTE_APPROVAL_PENDING"))
                .andExpect(jsonPath("$.message").value("Cannot delete a quote while approval is pending"));
        com.fasterxml.jackson.databind.JsonNode after = new ObjectMapper().valueToTree(quote);
        assertThat(after).isEqualTo(before);
        assertThat(quote.isDeleted()).isFalse();
        verify(ports.quotes).findByIdAndDeletedFalse(7L);
        ports.noOtherEffects();
    }

    @Test void acceptedRetainsExisting400AndCodeWithoutMutation() throws Exception {
        Quote quote = stored(QuoteStatus.ACCEPTED);
        mvc.perform(delete("/api/v1/quotes/7").header("Authorization", TestTokens.user("seller", "CPQ.User").bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("QUOTE_ACCEPTED_NO_DELETE"));
        assertThat(quote.isDeleted()).isFalse();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        verify(ports.quotes).findByIdAndDeletedFalse(7L);
        ports.noOtherEffects();
    }

    @ParameterizedTest
    @EnumSource(value = QuoteStatus.class, names = {"PENDING_APPROVAL", "ACCEPTED"}, mode = EnumSource.Mode.EXCLUDE)
    void eligibleStatusesRetain204AndSoftDeleteOnly(QuoteStatus status) throws Exception {
        Quote quote = stored(status);
        mvc.perform(delete("/api/v1/quotes/7").header("Authorization", TestTokens.user("seller", "CPQ.User").bearer()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(quote.isDeleted()).isTrue();
        assertThat(quote.getStatus()).isEqualTo(status);
        assertThat(quote.getTenantId()).isEqualTo("stored-tenant");
        verify(ports.quotes).findByIdAndDeletedFalse(7L);
        verify(ports.quotes).save(quote);
        ports.noOtherEffects();
    }

    @Test void absentOrAlreadyDeletedQuoteRetains404WithoutMutation() throws Exception {
        when(ports.quotes.findByIdAndDeletedFalse(7L)).thenReturn(Optional.empty());
        mvc.perform(delete("/api/v1/quotes/7").header("Authorization", TestTokens.user("seller", "CPQ.User").bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        verify(ports.quotes).findByIdAndDeletedFalse(7L);
        ports.noOtherEffects();
    }

    @Test void anonymousAndInvalidTokenCannotReadOrMutateQuote() throws Exception {
        mvc.perform(delete("/api/v1/quotes/7")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/quotes/7").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(ports.all());
    }
}
