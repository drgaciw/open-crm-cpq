package com.opencrm.crm.territory;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.TerritoryAssignedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DRG-483 territory assignment")
class TerritoryAssignmentServiceTest {

    private static final long ORG_ID = 7L;
    private static final String ORG_UUID = "11111111-1111-1111-1111-111111111111";
    private static final Instant NOW = Instant.parse("2026-11-01T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private TerritoryOrganizationLockRepository organizationLocks;
    @Mock
    private TerritoryAssignmentRuleRepository ruleRepository;
    @Mock
    private OrganizationTerritoryAssignmentRepository assignmentRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private jakarta.persistence.EntityManager entityManager;
    @Mock
    private TerritoryAssignmentReadiness readiness;

    private TerritoryAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new TerritoryAssignmentService(
                organizationRepository, organizationLocks, ruleRepository, assignmentRepository, outboxEventPublisher, entityManager, readiness, CLOCK);
        org.mockito.Mockito.lenient().when(readiness.isReady()).thenReturn(true);
    }

    @Test
    @DisplayName("a matching rule assigns territory and segment and publishes one TerritoryAssigned event")
    void matchPublishesExactlyOneEvent() {
        stubOrganization();
        when(ruleRepository.findApplicable()).thenReturn(List.of(matchingRule()));
        when(assignmentRepository.findByOrganizationId(ORG_ID)).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(invocation -> {
            OrganizationTerritoryAssignment row = invocation.getArgument(0);
            row.setId(50L);
            return row;
        });

        OrganizationSegmentResponse response = service.assign(ORG_ID);

        assertThat(response.isAssigned()).isTrue();
        assertThat(response.getOrganizationId()).isEqualTo(ORG_UUID);
        assertThat(response.getTerritory()).isEqualTo("WEST");
        assertThat(response.getSegment()).isEqualTo("MID_MARKET");
        assertThat(response.getRuleId()).isEqualTo(3L);

        ArgumentCaptor<TerritoryAssignedEvent> event = ArgumentCaptor.forClass(TerritoryAssignedEvent.class);
        verify(outboxEventPublisher).publish(
                eq("Organization"),
                eq(ORG_UUID),
                eq(EventContractRegistry.TERRITORY_ASSIGNED_V1),
                event.capture());
        assertThat(event.getValue().getOrganizationId()).isEqualTo(ORG_UUID);
        assertThat(event.getValue().getTerritory()).isEqualTo("WEST");
        assertThat(event.getValue().getSegment()).isEqualTo("MID_MARKET");
        assertThat(event.getValue().getRuleId()).isEqualTo(3L);
        assertThat(event.getValue().getAssignedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("assigning the same territory and segment again publishes nothing")
    void unchangedAssignmentPublishesNothing() {
        stubOrganization();
        when(ruleRepository.findApplicable()).thenReturn(List.of(matchingRule()));
        AtomicReference<OrganizationTerritoryAssignment> stored = new AtomicReference<>();
        when(assignmentRepository.findByOrganizationId(ORG_ID))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> {
            OrganizationTerritoryAssignment row = invocation.getArgument(0);
            row.setId(50L);
            stored.set(row);
            return row;
        });

        service.assign(ORG_ID);
        OrganizationSegmentResponse second = service.assign(ORG_ID);

        assertThat(second.isAssigned()).isTrue();
        assertThat(second.getSegment()).isEqualTo("MID_MARKET");
        verify(outboxEventPublisher).publish(
                eq("Organization"),
                eq(ORG_UUID),
                eq(EventContractRegistry.TERRITORY_ASSIGNED_V1),
                any(TerritoryAssignedEvent.class));
    }

    @Test
    @DisplayName("a changed segment publishes one new TerritoryAssigned event")
    void changedAssignmentPublishesOneEvent() {
        stubOrganization();
        TerritoryAssignmentRule updated = matchingRule();
        updated.setSegmentCode("ENTERPRISE");
        updated.setTerritoryCode("WEST");
        when(ruleRepository.findApplicable()).thenReturn(List.of(updated));
        OrganizationTerritoryAssignment existing = existingAssignment("WEST", "MID_MARKET", 3L);
        when(assignmentRepository.findByOrganizationId(ORG_ID)).thenReturn(Optional.of(existing));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationSegmentResponse response = service.assign(ORG_ID);

        assertThat(response.getSegment()).isEqualTo("ENTERPRISE");
        ArgumentCaptor<TerritoryAssignedEvent> event = ArgumentCaptor.forClass(TerritoryAssignedEvent.class);
        verify(outboxEventPublisher).publish(
                eq("Organization"),
                eq(ORG_UUID),
                eq(EventContractRegistry.TERRITORY_ASSIGNED_V1),
                event.capture());
        assertThat(event.getValue().getSegment()).isEqualTo("ENTERPRISE");
        assertThat(event.getValue().getTerritory()).isEqualTo("WEST");
    }

    @Test
    @DisplayName("no matching rule leaves a new organization unassigned and publishes nothing")
    void noMatchPublishesNothing() {
        stubOrganization();
        when(ruleRepository.findApplicable()).thenReturn(List.of());
        when(assignmentRepository.findByOrganizationId(ORG_ID)).thenReturn(Optional.empty());

        OrganizationSegmentResponse response = service.assign(ORG_ID);

        assertThat(response.isAssigned()).isFalse();
        assertThat(response.getSegment()).isNull();
        assertThat(response.getTerritory()).isNull();
        verify(assignmentRepository, never()).save(any());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("no matching rule clears an existing assignment and publishes nothing")
    void noMatchClearsExistingAssignmentWithoutPublishing() {
        stubOrganization();
        when(ruleRepository.findApplicable()).thenReturn(List.of());
        OrganizationTerritoryAssignment existing = existingAssignment("WEST", "MID_MARKET", 3L);
        when(assignmentRepository.findByOrganizationId(ORG_ID)).thenReturn(Optional.of(existing));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationSegmentResponse response = service.assign(ORG_ID);

        assertThat(response.isAssigned()).isFalse();
        assertThat(existing.isDeleted()).isTrue();
        verifyNoInteractions(outboxEventPublisher);
        assertThat(service.getSegment(ORG_ID).isAssigned()).isFalse();
    }

    @Test
    @DisplayName("GetSegment returns the stored segment")
    void getSegmentReturnsAssignment() {
        stubOrganization();
        when(assignmentRepository.findByOrganizationId(ORG_ID))
                .thenReturn(Optional.of(existingAssignment("WEST", "MID_MARKET", 3L)));

        OrganizationSegmentResponse response = service.getSegment(ORG_ID);

        assertThat(response.isAssigned()).isTrue();
        assertThat(response.getOrganizationId()).isEqualTo(ORG_UUID);
        assertThat(response.getSegment()).isEqualTo("MID_MARKET");
        assertThat(response.getTerritory()).isEqualTo("WEST");
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("a missing or deleted organization is not found and publishes nothing")
    void missingOrganizationPublishesNothing() {
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSegment(ORG_ID)).isInstanceOf(ResourceNotFoundException.class);

        Organization deleted = organization();
        deleted.setDeleted(true);
        when(organizationLocks.findForAssignment(ORG_ID)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.assign(ORG_ID)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("creating a rule stores trimmed keys and the default priority")
    void createRuleStoresTrimmedKeys() {
        when(ruleRepository.save(any())).thenAnswer(invocation -> {
            TerritoryAssignmentRule rule = invocation.getArgument(0);
            rule.setId(9L);
            return rule;
        });

        TerritoryRuleResponse response = service.createRule(TerritoryRuleRequest.builder()
                .name(" West software ")
                .country(" us ")
                .state("  ")
                .industry(" Software ")
                .minEmployeeCount(100)
                .maxEmployeeCount(500)
                .territoryCode(" WEST ")
                .segmentCode(" MID_MARKET ")
                .priority(null)
                .build());

        assertThat(response.getId()).isEqualTo(9L);
        assertThat(response.getCountry()).isEqualTo("us");
        assertThat(response.getState()).isNull();
        assertThat(response.getIndustry()).isEqualTo("Software");
        assertThat(response.getTerritoryCode()).isEqualTo("WEST");
        assertThat(response.getSegmentCode()).isEqualTo("MID_MARKET");
        assertThat(response.getPriority()).isEqualTo(100);
        assertThat(response.isActive()).isTrue();
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("an inverted employee window is rejected")
    void invertedEmployeeWindowIsRejected() {
        TerritoryRuleRequest request = TerritoryRuleRequest.builder()
                .name("Bad")
                .country("US")
                .industry("Software")
                .minEmployeeCount(500)
                .maxEmployeeCount(100)
                .territoryCode("WEST")
                .segmentCode("MID_MARKET")
                .build();

        assertThatThrownBy(() -> service.createRule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("minEmployeeCount");
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("the registry constant is literal in the single publish call")
    void publishNamesTheRegistryConstantLiterally() throws IOException {
        String source = Files.readString(serviceSource(), StandardCharsets.UTF_8);
        int publishCalls = countOf(source, ".publish(");
        assertThat(publishCalls).isEqualTo(1);
        int constantAt = source.indexOf("EventContractRegistry.TERRITORY_ASSIGNED_V1");
        int publishAt = source.indexOf(".publish(");
        int publishEnd = source.indexOf(");", publishAt);
        assertThat(constantAt).isBetween(publishAt, publishEnd);
    }

    private void stubOrganization() {
        org.mockito.Mockito.lenient().when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization()));
        org.mockito.Mockito.lenient().when(organizationLocks.findForAssignment(ORG_ID)).thenReturn(Optional.of(organization()));
    }

    private static Organization organization() {
        Organization organization = Organization.builder()
                .name("Acme")
                .country("US")
                .state("CA")
                .industry("Software")
                .employeeCount(200)
                .build();
        organization.setId(ORG_ID);
        organization.setUuid(ORG_UUID);
        organization.setDeleted(false);
        return organization;
    }

    private static TerritoryAssignmentRule matchingRule() {
        TerritoryAssignmentRule rule = TerritoryAssignmentRule.builder()
                .name("California software")
                .country("US")
                .state("CA")
                .industry("Software")
                .minEmployeeCount(100)
                .maxEmployeeCount(500)
                .territoryCode("WEST")
                .segmentCode("MID_MARKET")
                .priority(100)
                .active(true)
                .build();
        rule.setId(3L);
        rule.setDeleted(false);
        return rule;
    }

    private static OrganizationTerritoryAssignment existingAssignment(String territory, String segment, long ruleId) {
        OrganizationTerritoryAssignment row = OrganizationTerritoryAssignment.builder()
                .organizationId(ORG_ID)
                .territoryCode(territory)
                .segmentCode(segment)
                .ruleId(ruleId)
                .assignedAt(NOW.minusSeconds(60))
                .build();
        row.setId(50L);
        row.setDeleted(false);
        return row;
    }

    private static Path serviceSource() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path fromModule = dir.resolve(
                    "src/main/java/com/opencrm/crm/territory/TerritoryAssignmentService.java");
            if (Files.isRegularFile(fromModule)) {
                return fromModule;
            }
            Path fromBackend = dir.resolve(
                    "services/crm-service/src/main/java/com/opencrm/crm/territory/TerritoryAssignmentService.java");
            if (Files.isRegularFile(fromBackend)) {
                return fromBackend;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("TerritoryAssignmentService.java not found from " + Path.of("").toAbsolutePath());
    }

    private static int countOf(String text, String token) {
        int count = 0;
        int from = 0;
        while (true) {
            int found = text.indexOf(token, from);
            if (found < 0) {
                return count;
            }
            count++;
            from = found + token.length();
        }
    }
}
