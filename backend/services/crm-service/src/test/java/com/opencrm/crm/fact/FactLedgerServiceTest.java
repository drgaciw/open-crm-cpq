package com.opencrm.crm.fact;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.fields.FixtureTenants;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FactLedgerServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T18:00:00Z");
    private static final String TENANT = FixtureTenants.A;

    @Mock
    private CrmFactRepository factRepository;
    @Mock
    private RecordFieldAccess recordFields;
    @Mock
    private FactDecisionAudit decisionAudit;
    @Mock
    private CurrentActor currentActor;

    private FactLedgerService service;

    @BeforeEach
    void setUp() {
        service = new FactLedgerService(
                factRepository,
                recordFields,
                new Adr028EvidenceWeights(),
                decisionAudit,
                currentActor,
                Clock.fixed(NOW, ZoneOffset.UTC));
        signIn(TENANT, false);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anAgentVerifiedFactIsScoredAndAppliedByTheService() {
        agent();
        when(factRepository.findCurrent(anyString(), any(), anyString(), anyString())).thenReturn(List.of());
        when(recordFields.read(any(), anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
        when(recordFields.write(eq(FactSubjectType.CONTACT), eq(4L), eq("jobTitle"), eq("VP Sales"), eq(TENANT)))
                .thenReturn(true);
        when(factRepository.save(any(CrmFact.class))).thenAnswer(invocation -> {
            CrmFact fact = invocation.getArgument(0);
            fact.setId(15L);
            return fact;
        });

        FactResponse response = service.propose(
                FactSubjectType.CONTACT, 4L, TENANT, proposal("jobTitle", "VP Sales", EvidenceKind.EXACT_EMAIL_MATCH));

        assertThat(response.getScore()).isEqualByComparingTo("0.9500");
        assertThat(response.getStatus()).isEqualTo(FactStatus.APPLIED);
        assertThat(response.getBand()).isEqualTo(FactBand.VERIFIED);
        assertThat(response.isAppliedToRecord()).isTrue();
        assertThat(response.getTenantId()).isEqualTo(TENANT);
        assertThat(response.getEvidence()).singleElement().satisfies(item -> {
            assertThat(item.getKind()).isEqualTo(EvidenceKind.EXACT_EMAIL_MATCH);
            assertThat(item.getWeight()).isEqualByComparingTo("0.95");
        });
        assertThat(response.getWeightCatalogVersion()).isEqualTo(Adr028EvidenceWeights.VERSION);
        ArgumentCaptor<FactAuditEntry> audit = ArgumentCaptor.forClass(FactAuditEntry.class);
        verify(decisionAudit).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(FactStatus.APPLIED);
        assertThat(audit.getValue().actorId()).isEqualTo("service:enrichment-agent");
        assertThat(audit.getValue().factId()).isEqualTo(15L);
        verify(recordFields).requireSubject(FactSubjectType.CONTACT, 4L, TENANT);
    }

    @Test
    void aHumanValueIsNotOverwritten() {
        agent();
        CrmFact humanApplied = appliedByHuman();
        when(factRepository.findCurrent(eq(TENANT), eq(FactSubjectType.CONTACT), eq("4"), eq("jobTitle")))
                .thenReturn(List.of(humanApplied));
        when(factRepository.save(any(CrmFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FactResponse response = service.propose(
                FactSubjectType.CONTACT, 4L, TENANT, proposal("jobTitle", "VP Sales", EvidenceKind.EXACT_EMAIL_MATCH));

        assertThat(response.getStatus()).isEqualTo(FactStatus.PROPOSED);
        assertThat(response.isHumanProtected()).isTrue();
        assertThat(response.isAppliedToRecord()).isFalse();
        assertThat(humanApplied.getStatus()).isEqualTo(FactStatus.APPLIED);
        verify(recordFields, never()).write(any(), anyLong(), anyString(), anyString(), anyString());
        verifyNoInteractions(decisionAudit);
    }

    @Test
    void aColumnLastWrittenByAPersonBlocksTheAgent() {
        agent();
        when(factRepository.findCurrent(anyString(), any(), anyString(), anyString())).thenReturn(List.of());
        when(recordFields.read(FactSubjectType.CONTACT, 4L, "jobTitle", TENANT))
                .thenReturn(Optional.of(new ColumnState("Director", "person-oid")));
        when(factRepository.save(any(CrmFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FactResponse response = service.propose(
                FactSubjectType.CONTACT, 4L, TENANT, proposal("jobTitle", "VP Sales", EvidenceKind.SIGNATURE_BLOCK));

        assertThat(response.getScore()).isEqualByComparingTo("0.8000");
        assertThat(response.getStatus()).isEqualTo(FactStatus.PROPOSED);
        assertThat(response.isHumanProtected()).isTrue();
        verify(recordFields, never()).write(any(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void aSensitiveEmailIsProposedRatherThanApplied() {
        agent();
        when(factRepository.findCurrent(anyString(), any(), anyString(), anyString())).thenReturn(List.of());
        when(recordFields.read(any(), anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
        when(factRepository.save(any(CrmFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FactResponse response = service.propose(
                FactSubjectType.CONTACT, 4L, TENANT, proposal("email", "ada@example.com", EvidenceKind.EXACT_EMAIL_MATCH));

        assertThat(response.getScore()).isEqualByComparingTo("0.9500");
        assertThat(response.getBand()).isEqualTo(FactBand.PROBABLE);
        assertThat(response.getStatus()).isEqualTo(FactStatus.PROPOSED);
        assertThat(response.isSensitive()).isTrue();
        verify(recordFields, never()).write(any(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void aSensitivePossibleFactIsNotStored() {
        agent();
        when(factRepository.findCurrent(anyString(), any(), anyString(), anyString())).thenReturn(List.of());
        when(recordFields.read(any(), anyLong(), anyString(), anyString())).thenReturn(Optional.empty());

        FactResponse response = service.propose(
                FactSubjectType.CONTACT, 4L, TENANT, proposal("email", "ada@example.com", EvidenceKind.CITED_WEB_ANSWER));

        assertThat(response.isStored()).isFalse();
        assertThat(response.getScore()).isEqualByComparingTo("0.4000");
        verify(factRepository, never()).save(any());
        verifyNoInteractions(decisionAudit);
    }

    @Test
    void contradictingEvidenceIsStoredUnresolvedAndDoesNotWriteTheColumn() {
        agent();
        when(factRepository.findCurrent(anyString(), any(), anyString(), anyString())).thenReturn(List.of());
        when(recordFields.read(any(), anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
        when(factRepository.save(any(CrmFact.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProposeFactRequest request = proposal("jobTitle", "VP Sales", EvidenceKind.CITED_WEB_ANSWER);
        EvidenceRequest other = evidence(EvidenceKind.CITED_WEB_ANSWER);
        other.setAssertedValue("Analyst");
        request.getEvidence().add(other);

        FactResponse response = service.propose(FactSubjectType.CONTACT, 4L, TENANT, request);

        assertThat(response.getScore()).isEqualByComparingTo("0.6400");
        assertThat(response.getStatus()).isEqualTo(FactStatus.UNRESOLVED);
        assertThat(response.isContradicting()).isTrue();
        verify(recordFields, never()).write(any(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void aNewerProposalSupersedesThePreviousOpenProposal() {
        agent();
        CrmFact previous = new CrmFact();
        previous.setStatus(FactStatus.PROPOSED);
        previous.setWriterKind(WriterKind.AGENT);
        when(factRepository.findCurrent(anyString(), any(), anyString(), anyString())).thenReturn(List.of(previous));
        when(recordFields.read(any(), anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
        when(factRepository.save(any(CrmFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.propose(FactSubjectType.CONTACT, 4L, TENANT, proposal("jobTitle", "VP Sales", EvidenceKind.SIGNATURE_BLOCK));

        assertThat(previous.getStatus()).isEqualTo(FactStatus.SUPERSEDED);
        assertThat(previous.getSupersededAt()).isEqualTo(NOW);
    }

    @Test
    void aPersonCallerDoesNotAutoApply() {
        person();
        when(factRepository.findCurrent(anyString(), any(), anyString(), anyString())).thenReturn(List.of());
        when(recordFields.read(any(), anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
        when(factRepository.save(any(CrmFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FactResponse response = service.propose(
                FactSubjectType.CONTACT, 4L, TENANT, proposal("jobTitle", "VP Sales", EvidenceKind.EXACT_EMAIL_MATCH));

        assertThat(response.getStatus()).isEqualTo(FactStatus.PROPOSED);
        assertThat(response.getWriterKind()).isEqualTo(WriterKind.HUMAN);
        verify(recordFields, never()).write(any(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void inboxOmitsProvenanceAndOtherTenantsAreNotThisQuery() {
        signIn(TENANT, false);
        CrmFact proposed = fact(9L, FactStatus.PROPOSED, NOW);
        CrmFact unresolved = fact(8L, FactStatus.UNRESOLVED, NOW.minusSeconds(60));
        when(factRepository.findInbox(TENANT, FactSubjectType.CONTACT, "4", FactStatus.inbox()))
                .thenReturn(new ArrayList<>(List.of(unresolved, proposed)));

        List<FactResponse> proposals = service.listProposals(FactSubjectType.CONTACT, 4L, TENANT);

        assertThat(proposals).extracting(FactResponse::getStatus)
                .containsExactly(FactStatus.PROPOSED, FactStatus.UNRESOLVED);
        assertThat(FactStatus.inbox()).doesNotContain(FactStatus.PROVENANCE, FactStatus.APPLIED);
        verify(recordFields).requireSubject(FactSubjectType.CONTACT, 4L, TENANT);
    }

    @Test
    void aPersonAcceptsAProposalAndAServiceCallerCannot() {
        person();
        CrmFact proposed = fact(9L, FactStatus.PROPOSED, NOW);
        proposed.setFieldKey("jobTitle");
        proposed.setFactValue("VP Sales");
        proposed.setTenantId(TENANT);
        proposed.setSubjectType(FactSubjectType.CONTACT);
        proposed.setSubjectId("4");
        when(factRepository.findActiveById(9L)).thenReturn(Optional.of(proposed));
        when(factRepository.findCurrent(TENANT, FactSubjectType.CONTACT, "4", "jobTitle")).thenReturn(List.of(proposed));
        when(recordFields.write(FactSubjectType.CONTACT, 4L, "jobTitle", "VP Sales", TENANT)).thenReturn(true);
        when(factRepository.save(any(CrmFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FactResponse accepted = service.accept(FactSubjectType.CONTACT, 4L, 9L, TENANT);

        assertThat(accepted.getStatus()).isEqualTo(FactStatus.APPLIED);
        assertThat(accepted.getWriterKind()).isEqualTo(WriterKind.HUMAN);
        assertThat(accepted.getDecidedBy()).isEqualTo("person-oid");
        assertThat(accepted.isAppliedToRecord()).isTrue();
        verify(decisionAudit).record(any(FactAuditEntry.class));
        verify(recordFields).requireSubject(FactSubjectType.CONTACT, 4L, TENANT);

        agentCaller();
        assertThatThrownBy(() -> service.dismiss(FactSubjectType.CONTACT, 4L, 9L, TENANT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("AGENT_FIELD_VALUE_FORBIDDEN");
    }

    @Test
    void dismissRecordsTheActorAndLeavesTheColumnAlone() {
        person();
        CrmFact proposed = fact(9L, FactStatus.PROPOSED, NOW);
        proposed.setFieldKey("jobTitle");
        proposed.setFactValue("VP Sales");
        proposed.setTenantId(TENANT);
        proposed.setSubjectType(FactSubjectType.CONTACT);
        proposed.setSubjectId("4");
        when(factRepository.findActiveById(9L)).thenReturn(Optional.of(proposed));
        when(factRepository.save(any(CrmFact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FactResponse dismissed = service.dismiss(FactSubjectType.CONTACT, 4L, 9L, TENANT);

        assertThat(dismissed.getStatus()).isEqualTo(FactStatus.DISMISSED);
        assertThat(dismissed.getDecidedBy()).isEqualTo("person-oid");
        verify(recordFields, never()).write(any(), anyLong(), anyString(), anyString(), anyString());
        ArgumentCaptor<FactAuditEntry> audit = ArgumentCaptor.forClass(FactAuditEntry.class);
        verify(decisionAudit).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(FactStatus.DISMISSED);
    }

    @Test
    void acceptingAReparentFactDoesNotWriteTheColumn() {
        person();
        CrmFact proposed = fact(3L, FactStatus.PROPOSED, NOW);
        proposed.setFieldKey("parentOrganizationId");
        proposed.setFactValue("99");
        proposed.setTenantId(TENANT);
        proposed.setSubjectType(FactSubjectType.ORGANIZATION);
        proposed.setSubjectId("4");
        when(factRepository.findActiveById(3L)).thenReturn(Optional.of(proposed));

        assertThatThrownBy(() -> service.accept(FactSubjectType.ORGANIZATION, 4L, 3L, TENANT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("FACT_REPARENT_UNAVAILABLE");
        assertThat(proposed.getStatus()).isEqualTo(FactStatus.PROPOSED);
        verify(recordFields, never()).write(any(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void anotherTenantsFactIsNotFound() {
        person();
        CrmFact otherTenant = fact(9L, FactStatus.PROPOSED, NOW);
        otherTenant.setTenantId(FixtureTenants.B);
        otherTenant.setSubjectType(FactSubjectType.CONTACT);
        otherTenant.setSubjectId("4");
        when(factRepository.findActiveById(9L)).thenReturn(Optional.of(otherTenant));

        assertThatThrownBy(() -> service.accept(FactSubjectType.CONTACT, 4L, 9L, TENANT))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(recordFields, never()).write(any(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void spoofedTenantQueryParamCannotEscalateOnPropose() {
        signIn(TENANT, true);

        assertThatThrownBy(() -> service.propose(
                FactSubjectType.CONTACT,
                4L,
                FixtureTenants.B,
                proposal("jobTitle", "VP Sales", EvidenceKind.EXACT_EMAIL_MATCH)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("TENANT_MISMATCH");
        verify(recordFields, never()).requireSubject(any(), anyLong(), anyString());
        verify(factRepository, never()).save(any());
    }

    @Test
    void crossTenantSubjectOwnershipIsEnforcedBeforeWrite() {
        signIn(TENANT, false);
        doThrow(new ResourceNotFoundException("Contact", 4L))
                .when(recordFields).requireSubject(FactSubjectType.CONTACT, 4L, TENANT);

        assertThatThrownBy(() -> service.listProposals(FactSubjectType.CONTACT, 4L, TENANT))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(factRepository, never()).findInbox(anyString(), any(), anyString(), any());
    }

    @Test
    void omittedTenantUsesAuthenticatedJwtTid() {
        signIn(TENANT, false);
        when(factRepository.findInbox(TENANT, FactSubjectType.CONTACT, "4", FactStatus.inbox()))
                .thenReturn(List.of());

        assertThat(service.listProposals(FactSubjectType.CONTACT, 4L, null)).isEmpty();
        verify(recordFields).requireSubject(FactSubjectType.CONTACT, 4L, TENANT);
    }

    private void agent() {
        signIn(TENANT, true);
        agentCaller();
        when(currentActor.id()).thenReturn("service:enrichment-agent");
    }

    private void agentCaller() {
        when(currentActor.caller()).thenReturn(Optional.of(new Caller.Service("enrichment-agent", Set.of())));
    }

    private void person() {
        signIn(TENANT, false);
        when(currentActor.caller()).thenReturn(Optional.of(new Caller.User("person-oid", Set.of())));
        when(currentActor.id()).thenReturn("person-oid");
    }

    private static void signIn(String tid, boolean service) {
        Jwt.Builder jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .claim("tid", tid);
        if (service) {
            jwt.subject("enrichment-agent")
                    .claim("idtyp", "app")
                    .claim("azp", "enrichment-agent");
        } else {
            jwt.subject("person-oid")
                    .claim("oid", "person-oid")
                    .claim("scp", "user");
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build(), List.of()));
    }

    private static CrmFact appliedByHuman() {
        CrmFact fact = new CrmFact();
        fact.setStatus(FactStatus.APPLIED);
        fact.setWriterKind(WriterKind.HUMAN);
        fact.setFieldKey("jobTitle");
        fact.setFactValue("Director");
        return fact;
    }

    private static CrmFact fact(long id, FactStatus status, Instant observedAt) {
        CrmFact fact = new CrmFact();
        fact.setId(id);
        fact.setStatus(status);
        fact.setObservedAt(observedAt);
        fact.setTenantId(TENANT);
        fact.setSubjectType(FactSubjectType.CONTACT);
        fact.setSubjectId("4");
        return fact;
    }

    private static ProposeFactRequest proposal(String fieldKey, String value, EvidenceKind kind) {
        ProposeFactRequest request = new ProposeFactRequest();
        request.setFieldKey(fieldKey);
        request.setValue(value);
        request.setMethod("enrichment");
        request.setEvidence(new ArrayList<>(List.of(evidence(kind))));
        return request;
    }

    private static EvidenceRequest evidence(EvidenceKind kind) {
        EvidenceRequest evidence = new EvidenceRequest();
        evidence.setKind(kind);
        evidence.setUrl("https://example.com/source");
        evidence.setObservedAt(NOW.minusSeconds(30));
        return evidence;
    }
}
