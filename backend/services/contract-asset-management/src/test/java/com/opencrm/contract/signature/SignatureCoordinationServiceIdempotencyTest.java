package com.opencrm.contract.signature;

import com.opencrm.common.event.dto.SignatureCompletedEvent;
import com.opencrm.contract.signature.acl.ESignatureProviderAdapter;
import com.opencrm.contract.signature.acl.EnvelopeHandle;
import com.opencrm.contract.signature.acl.EnvelopeStatusSnapshot;
import com.opencrm.contract.signature.acl.ProviderSignerEvidence;
import com.opencrm.contract.signature.acl.SignatureRequest;
import com.opencrm.contract.signature.acl.SignerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-457 / BC-06-04. Proves "GetEnvelopeStatus polls or receives webhooks idempotently" for real: delivering the
 * exact same provider event twice (webhook redelivery, or an unchanged poll result) must transition the envelope
 * and publish {@code SignatureCompleted} exactly once, not twice.
 *
 * <p>{@code eventRepository.saveAndFlush} is stubbed to reproduce exactly what the real unique constraint
 * ({@code uq_signature_envelope_event} on {@code (envelope_id, provider_event_id)}, added in
 * {@code V1_22__create_signature_envelopes.sql}) does in Postgres: the first insert of a key succeeds, a repeat of
 * the same key throws {@link DataIntegrityViolationException}. That is what
 * {@link SignatureCoordinationService#handleProviderWebhook} and {@link SignatureCoordinationService#getEnvelopeStatus}
 * rely on to make redelivery a no-op, so the test exercises the real claim-then-act code path, not a shortcut.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DRG-457: SignatureCoordinationService idempotent webhook/poll handling")
class SignatureCoordinationServiceIdempotencyTest {

    @Mock
    private SignatureEnvelopeRepository envelopeRepository;
    @Mock
    private SignatureEnvelopeEventRepository eventRepository;
    @Mock
    private ESignatureProviderAdapter providerAdapter;
    @Mock
    private SignatureEventPublisher eventPublisher;

    private final Set<String> claimedKeys = new HashSet<>();
    private final AtomicLong nextEventId = new AtomicLong(1);

    private SignatureCoordinationService service;
    private SignatureEnvelope envelope;

    @BeforeEach
    void setUp() {
        claimedKeys.clear();
        service = new SignatureCoordinationService(envelopeRepository, eventRepository, providerAdapter,
                eventPublisher);

        envelope = SignatureEnvelope.builder()
                .aggregateType("ContractDocument")
                .aggregateId("doc-1")
                .status(SignatureEnvelopeStatus.SENT)
                .providerEnvelopeRef("stub-env-abc")
                .build();
        envelope.setId(42L);
        envelope.setUuid("envelope-uuid-1");

        when(envelopeRepository.findByUuid("envelope-uuid-1")).thenReturn(Optional.of(envelope));
        when(envelopeRepository.save(any(SignatureEnvelope.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Reproduces the uq_signature_envelope_event unique-constraint behaviour without a database.
        when(eventRepository.saveAndFlush(any(SignatureEnvelopeEvent.class))).thenAnswer(invocation -> {
            SignatureEnvelopeEvent row = invocation.getArgument(0);
            String key = row.getEnvelopeId() + "::" + row.getProviderEventId();
            if (!claimedKeys.add(key)) {
                throw new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_signature_envelope_event\"");
            }
            row.setId(nextEventId.getAndIncrement());
            return row;
        });
    }

    @Test
    @DisplayName("the same webhook delivery id processed twice completes the envelope and publishes exactly once")
    void duplicateWebhookDeliveryDoesNotDoubleProcess() {
        List<ProviderSignerEvidence> signers = List.of(
                new ProviderSignerEvidence("signer-1", "Ada Lovelace", "ada@example.com", Instant.now(),
                        "203.0.113.5", "email-link"));
        EnvelopeStatusSnapshot completedSnapshot =
                new EnvelopeStatusSnapshot("delivery-id-123", SignatureEnvelopeStatus.COMPLETED, signers);

        SignatureEnvelope first = service.handleProviderWebhook("envelope-uuid-1", completedSnapshot);
        SignatureEnvelope second = service.handleProviderWebhook("envelope-uuid-1", completedSnapshot);

        assertThat(first.getStatus()).isEqualTo(SignatureEnvelopeStatus.COMPLETED);
        assertThat(second.getStatus()).isEqualTo(SignatureEnvelopeStatus.COMPLETED);

        // The real idempotency proof: the ledger's claim was attempted twice but only one actually got through,
        // and the outbox publish happened exactly once.
        verify(eventRepository, times(2)).saveAndFlush(any(SignatureEnvelopeEvent.class));
        assertThat(claimedKeys).containsExactly("42::delivery-id-123");
        verify(eventPublisher, times(1)).publishCompleted(any(SignatureEnvelope.class),
                any(SignatureCompletedEvent.class));
    }

    @Test
    @DisplayName("the published SignatureCompleted carries the envelope id and signer evidence, not the provider shape")
    void publishedEventCarriesEnvelopeIdAndSignerEvidence() {
        List<ProviderSignerEvidence> signers = List.of(
                new ProviderSignerEvidence("signer-1", "Ada Lovelace", "ada@example.com", Instant.now(),
                        "203.0.113.5", "email-link"));
        EnvelopeStatusSnapshot completedSnapshot =
                new EnvelopeStatusSnapshot("delivery-id-999", SignatureEnvelopeStatus.COMPLETED, signers);

        service.handleProviderWebhook("envelope-uuid-1", completedSnapshot);

        ArgumentCaptor<SignatureCompletedEvent> captor = ArgumentCaptor.forClass(SignatureCompletedEvent.class);
        verify(eventPublisher).publishCompleted(any(SignatureEnvelope.class), captor.capture());
        SignatureCompletedEvent published = captor.getValue();

        assertThat(published.envelopeId()).isEqualTo("envelope-uuid-1");
        assertThat(published.aggregateType()).isEqualTo("ContractDocument");
        assertThat(published.aggregateId()).isEqualTo("doc-1");
        assertThat(published.signers()).hasSize(1);
        assertThat(published.signers().get(0).signerRef()).isEqualTo("signer-1");
        assertThat(published.signers().get(0).email()).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("two poll calls for an unchanged state share one providerEventId and dedupe to a single claim")
    void repeatedPollOfUnchangedStateDedupes() {
        when(providerAdapter.getEnvelopeStatus("stub-env-abc"))
                .thenReturn(new EnvelopeStatusSnapshot("poll:stub-env-abc:SENT", SignatureEnvelopeStatus.SENT,
                        List.of()));

        SignatureEnvelope firstPoll = service.getEnvelopeStatus("envelope-uuid-1");
        SignatureEnvelope secondPoll = service.getEnvelopeStatus("envelope-uuid-1");

        assertThat(firstPoll.getStatus()).isEqualTo(SignatureEnvelopeStatus.SENT);
        assertThat(secondPoll.getStatus()).isEqualTo(SignatureEnvelopeStatus.SENT);
        assertThat(claimedKeys).containsExactly("42::poll:stub-env-abc:SENT");
        verify(eventPublisher, never()).publishCompleted(any(), any());
    }

    @Test
    @DisplayName("an already-terminal envelope is not re-polled; the provider is not called again")
    void terminalEnvelopeIsNotPolledAgain() {
        envelope.setStatus(SignatureEnvelopeStatus.COMPLETED);

        SignatureEnvelope result = service.getEnvelopeStatus("envelope-uuid-1");

        assertThat(result.getStatus()).isEqualTo(SignatureEnvelopeStatus.COMPLETED);
        verify(providerAdapter, never()).getEnvelopeStatus(any());
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("SendForSignature stores the ACL handle and transitions DRAFT -> SENT")
    void sendForSignatureStoresTheAclHandle() {
        when(providerAdapter.sendForSignature(any(SignatureRequest.class)))
                .thenReturn(new EnvelopeHandle("stub-env-new"));

        SignatureEnvelope created = service.sendForSignature("ContractDocument", "doc-2", "MSA",
                List.of(new SignerRequest("signer-1", "Ada Lovelace", "ada@example.com")));

        assertThat(created.getStatus()).isEqualTo(SignatureEnvelopeStatus.SENT);
        assertThat(created.getProviderEnvelopeRef()).isEqualTo("stub-env-new");
        assertThat(created.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("VoidEnvelope refuses an already-terminal envelope instead of calling the provider")
    void voidRefusesATerminalEnvelope() {
        envelope.setStatus(SignatureEnvelopeStatus.COMPLETED);

        assertThatThrownBy(() -> service.voidEnvelope("envelope-uuid-1", "no longer needed"))
                .isInstanceOf(InvalidSignatureTransitionException.class);
        verify(providerAdapter, never()).voidEnvelope(eq("stub-env-abc"), anyString());
    }

    @Test
    @DisplayName("an out-of-order report (e.g. DRAFT after SENT) is rejected, not silently applied")
    void invalidForwardTransitionIsRejected() {
        EnvelopeStatusSnapshot backwards =
                new EnvelopeStatusSnapshot("delivery-bad-1", SignatureEnvelopeStatus.DRAFT, List.of());

        assertThatThrownBy(() -> service.handleProviderWebhook("envelope-uuid-1", backwards))
                .isInstanceOf(InvalidSignatureTransitionException.class);
        verify(eventPublisher, never()).publishCompleted(any(), any());
    }
}
