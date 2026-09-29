package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.test.TestCallers;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code POST /api/v1/quotes/{id}/approve|reject} passes or fails a review.
 *
 * <p>ADR-016 step 4: a quote waiting on a tiered approval is decided through that approval in approval-routing, which
 * checks the approver's role; deciding it here would bypass that check and leave the approval open.
 *
 * <p>ADR-016 owner decision 1: passing a review releases the quote to be sent, so it needs the Deal Desk, Sales VP or CFO
 * role, and the quote's creator cannot pass their own review.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QuoteService approve/reject: reviews only, by a reviewer, never a routed approval (ADR-016)")
class QuoteServiceReviewDecisionTest {

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private QuoteMapper quoteMapper;
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

    private QuoteService quoteService(boolean devMode) {
        return new QuoteService(quoteRepository, quoteMapper, quoteNumberGenerator, eventPublisher,
                quoteLineItemRepository, pricingServiceClient, productServiceClient, new ObjectMapper(),
                outboxEventPublisher, new CurrentActor("service:quote-service", devMode));
    }

    private QuoteService quoteService() {
        return quoteService(false);
    }

    @AfterEach
    void signOut() {
        TestCallers.clear();
    }

    private Quote quote(QuoteStatus status, String createdBy) {
        Quote quote = Quote.builder().quoteNumber("Q-7").status(status).build();
        quote.setId(7L);
        quote.setCreatedBy(createdBy);
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote));
        return quote;
    }

    private Quote quote(QuoteStatus status) {
        return quote(status, null);
    }

    private void expectSaveAndDto(Quote quote, QuoteStatus status) {
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenReturn(QuoteDto.builder().id(7L).status(status).build());
    }

    private static void assertRefused(HttpStatus status, String code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, refused -> {
            assertThat(refused.getStatus()).isEqualTo(status);
            assertThat(refused.getErrorCode()).isEqualTo(code);
        });
    }

    @Test
    @DisplayName("a PENDING_APPROVAL quote cannot be approved or rejected here: 409 APPROVAL_PENDING")
    void routedQuoteIsRefused() {
        TestCallers.signInUser("reviewer-1", "DealDesk");
        Quote quote = quote(QuoteStatus.PENDING_APPROVAL);

        for (Runnable decision : new Runnable[]{() -> quoteService().approve(7L), () -> quoteService().reject(7L, "no")}) {
            assertRefused(HttpStatus.CONFLICT, "APPROVAL_PENDING", decision);
        }
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("an IN_REVIEW quote still passes or fails its review here, for a reviewer")
    void reviewIsDecidedHere() {
        TestCallers.signInUser("reviewer-1", "DealDesk");
        Quote quote = quote(QuoteStatus.IN_REVIEW);
        expectSaveAndDto(quote, QuoteStatus.APPROVED);

        quoteService().approve(7L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
        assertThat(quote.getApprovalState()).isEqualTo(ApprovalState.APPROVED);
    }

    @Test
    @DisplayName("other states are still an invalid transition")
    void otherStatesAreInvalid() {
        TestCallers.signInUser("reviewer-1", "DealDesk");
        quote(QuoteStatus.DRAFT);

        assertThatThrownBy(() -> quoteService().reject(7L, null)).isInstanceOfSatisfying(BusinessException.class,
                refused -> assertThat(refused.getErrorCode()).isEqualTo("INVALID_STATUS_TRANSITION"));
    }

    @ParameterizedTest(name = "{0} may pass and fail a review")
    @ValueSource(strings = {"DealDesk", "SalesVP", "CFO"})
    @DisplayName("Deal Desk, Sales VP and CFO are the reviewers")
    void reviewerRolesMayDecide(String role) {
        TestCallers.signInUser("reviewer-1", role);
        Quote quote = quote(QuoteStatus.IN_REVIEW, TestTokens.oidOf("author-1"));
        expectSaveAndDto(quote, QuoteStatus.APPROVED);

        quoteService().approve(7L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
    }

    @ParameterizedTest(name = "roles [{0}] are refused: 403 REVIEWER_ROLE_REQUIRED")
    @ValueSource(strings = {"CPQ.User", "Admin", "CPQ.User,Admin"})
    @DisplayName("CPQ.User and Admin review nothing")
    void otherRolesAreRefused(String roles) {
        TestCallers.signInUser("author-1", roles.split(","));
        Quote quote = quote(QuoteStatus.IN_REVIEW, TestTokens.oidOf("someone-else"));

        for (Runnable decision : new Runnable[]{() -> quoteService().approve(7L), () -> quoteService().reject(7L, "no")}) {
            assertRefused(HttpStatus.FORBIDDEN, "REVIEWER_ROLE_REQUIRED", decision);
        }
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.IN_REVIEW);
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("a person with no app role at all is refused")
    void noRoleIsRefused() {
        TestCallers.signInUser("author-1");
        quote(QuoteStatus.IN_REVIEW);

        assertRefused(HttpStatus.FORBIDDEN, "REVIEWER_ROLE_REQUIRED", () -> quoteService().approve(7L));
    }

    @Test
    @DisplayName("authorization comes before the state check, so a non-reviewer learns nothing about the quote's state")
    void authorizationPrecedesState() {
        TestCallers.signInUser("author-1", "CPQ.User");
        quote(QuoteStatus.DRAFT);

        assertRefused(HttpStatus.FORBIDDEN, "REVIEWER_ROLE_REQUIRED", () -> quoteService().approve(7L));
    }

    @Test
    @DisplayName("a service token is refused even with a reviewer role: reviews are decided by people")
    void serviceIsRefused() {
        TestCallers.signInService("agent-client", "DealDesk");
        quote(QuoteStatus.IN_REVIEW);

        assertRefused(HttpStatus.FORBIDDEN, "REVIEWER_MUST_BE_A_USER", () -> quoteService().approve(7L));
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("no caller is 401 outside dev mode")
    void noCallerIsRefused() {
        quote(QuoteStatus.IN_REVIEW);

        for (Runnable decision : new Runnable[]{() -> quoteService().approve(7L), () -> quoteService().reject(7L, "no")}) {
            assertRefused(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", decision);
        }
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("dev mode lets a review through without a verified user")
    void devModeAllowsNoCaller() {
        Quote quote = quote(QuoteStatus.IN_REVIEW);
        expectSaveAndDto(quote, QuoteStatus.APPROVED);

        quoteService(true).approve(7L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
    }

    @Test
    @DisplayName("the creator cannot pass their own review, even with a reviewer role: 403 SELF_REVIEW")
    void creatorCannotPassOwnReview() {
        String author = TestCallers.signInUser("author-1", "DealDesk");
        Quote quote = quote(QuoteStatus.IN_REVIEW, author);

        assertRefused(HttpStatus.FORBIDDEN, "SELF_REVIEW", () -> quoteService().approve(7L));

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.IN_REVIEW);
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("the creator may fail their own quote: that only holds it back")
    void creatorMayFailOwnQuote() {
        String author = TestCallers.signInUser("author-1", "DealDesk");
        Quote quote = quote(QuoteStatus.IN_REVIEW, author);
        expectSaveAndDto(quote, QuoteStatus.REJECTED);

        quoteService().reject(7L, "withdrawn");

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.REJECTED);
    }

    @Test
    @DisplayName("another reviewer may pass the author's quote")
    void anotherReviewerMayPass() {
        TestCallers.signInUser("reviewer-1", "SalesVP");
        Quote quote = quote(QuoteStatus.IN_REVIEW, TestTokens.oidOf("author-1"));
        expectSaveAndDto(quote, QuoteStatus.APPROVED);

        quoteService().approve(7L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
    }

    @Test
    @DisplayName("a quote with no recorded creator (made before ADR-016 step 4) has no four-eyes check but still needs the role")
    void legacyQuoteStillNeedsTheRole() {
        TestCallers.signInUser("reviewer-1", "DealDesk");
        Quote quote = quote(QuoteStatus.IN_REVIEW, null);
        expectSaveAndDto(quote, QuoteStatus.APPROVED);

        quoteService().approve(7L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
    }

    @Test
    @DisplayName("the reviewer set is exactly Deal Desk, Sales VP and CFO")
    void reviewerSet() {
        assertThat(ReviewAuthority.REVIEWERS)
                .containsExactlyInAnyOrder("ROLE_DEAL_DESK", "ROLE_SALES_VP", "ROLE_CFO");
    }
}
