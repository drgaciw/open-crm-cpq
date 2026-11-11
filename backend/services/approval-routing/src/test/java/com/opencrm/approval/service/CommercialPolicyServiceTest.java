package com.opencrm.approval.service;

import com.opencrm.approval.domain.CommercialPolicy;
import com.opencrm.approval.domain.PolicyStatus;
import com.opencrm.approval.domain.ThresholdType;
import com.opencrm.approval.dto.CommercialPolicyCreateRequest;
import com.opencrm.approval.dto.CommercialPolicyDto;
import com.opencrm.approval.dto.PolicyThresholdDto;
import com.opencrm.approval.dto.PolicyThresholdRequest;
import com.opencrm.approval.mapper.CommercialPolicyMapper;
import com.opencrm.approval.repository.CommercialPolicyRepository;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.PolicyDefinedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@code DefinePolicy} / {@code PolicyDefined} (BC-04-01, DRG-441): threshold validation, scope,
 * local-integer versioning and supersede-on-revision, and the single outbox publish.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Commercial Policy - DefinePolicy")
class CommercialPolicyServiceTest {

    @Mock
    private CommercialPolicyRepository commercialPolicyRepository;

    @Mock
    private CommercialPolicyMapper commercialPolicyMapper;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private CommercialPolicyService commercialPolicyService;

    @BeforeEach
    void setUp() {
        commercialPolicyService = new CommercialPolicyService(
                commercialPolicyRepository, commercialPolicyMapper, outboxEventPublisher);

        lenient().when(commercialPolicyRepository.save(any(CommercialPolicy.class)))
                .thenAnswer(inv -> {
                    CommercialPolicy entity = inv.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(1L);
                        entity.setUuid("policy-uuid-1");
                    }
                    return entity;
                });
        lenient().when(commercialPolicyMapper.toDto(any(CommercialPolicy.class)))
                .thenAnswer(inv -> toDto(inv.getArgument(0)));
    }

    private static CommercialPolicyDto toDto(CommercialPolicy entity) {
        return CommercialPolicyDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .status(entity.getStatus())
                .policyVersion(entity.getPolicyVersion())
                .supersedesId(entity.getSupersedesId())
                .segment(entity.getSegment())
                .productFamily(entity.getProductFamily())
                .region(entity.getRegion())
                .thresholds(entity.getThresholds().stream()
                        .map(t -> PolicyThresholdDto.builder()
                                .thresholdType(t.getThresholdType())
                                .limitValue(t.getLimitValue())
                                .clauseCode(t.getClauseCode())
                                .build())
                        .toList())
                .build();
    }

    private static CommercialPolicyCreateRequest.CommercialPolicyCreateRequestBuilder baseRequest() {
        return CommercialPolicyCreateRequest.builder()
                .name("Enterprise discount policy")
                .segment("ENTERPRISE")
                .productFamily("PLATFORM")
                .region("NA")
                .thresholds(List.of(
                        PolicyThresholdRequest.builder()
                                .thresholdType(ThresholdType.DISCOUNT_PERCENT)
                                .limitValue(BigDecimal.valueOf(25))
                                .build(),
                        PolicyThresholdRequest.builder()
                                .thresholdType(ThresholdType.MARGIN_FLOOR)
                                .limitValue(BigDecimal.valueOf(15))
                                .build(),
                        PolicyThresholdRequest.builder()
                                .thresholdType(ThresholdType.TERM_MONTHS)
                                .limitValue(BigDecimal.valueOf(36))
                                .build(),
                        PolicyThresholdRequest.builder()
                                .thresholdType(ThresholdType.NON_STANDARD_CLAUSE)
                                .clauseCode("INDEMNITY_CAP_WAIVER")
                                .description("Waiving the standard indemnity cap")
                                .build()
                ));
    }

    @Test
    @DisplayName("define() persists an ACTIVE version 1 policy with its thresholds and emits PolicyDefined")
    void defineCreatesVersionOneAndEmitsEvent() {
        CommercialPolicyDto result = commercialPolicyService.define(baseRequest().build());

        assertEquals(PolicyStatus.ACTIVE, result.getStatus());
        assertEquals(1, result.getPolicyVersion());
        assertNull(result.getSupersedesId());
        assertEquals(4, result.getThresholds().size());

        ArgumentCaptor<PolicyDefinedEvent> eventCaptor = ArgumentCaptor.forClass(PolicyDefinedEvent.class);
        verify(outboxEventPublisher).publish(
                eq("CommercialPolicy"), eq("1"), eq(EventContractRegistry.POLICY_DEFINED_V1), eventCaptor.capture());

        PolicyDefinedEvent event = eventCaptor.getValue();
        assertEquals(1L, event.policyId());
        assertEquals(1, event.policyVersion());
        assertNull(event.supersedesPolicyId());
        assertEquals("ENTERPRISE", event.segment());
        assertEquals(4, event.thresholds().size());
    }

    @Test
    @DisplayName("define() with supersedesPolicyId deprecates the predecessor and continues its version lineage")
    void defineAsRevisionSupersedesPredecessor() {
        CommercialPolicy predecessor = CommercialPolicy.builder()
                .id(1L)
                .uuid("policy-uuid-1")
                .name("Enterprise discount policy")
                .status(PolicyStatus.ACTIVE)
                .policyVersion(1)
                .build();
        when(commercialPolicyRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(predecessor));
        when(commercialPolicyRepository.save(predecessor)).thenReturn(predecessor);

        CommercialPolicyDto result = commercialPolicyService.define(
                baseRequest().supersedesPolicyId(1L).build());

        assertEquals(2, result.getPolicyVersion());
        assertEquals(1L, result.getSupersedesId());
        assertEquals(PolicyStatus.DEPRECATED, predecessor.getStatus());
        // One save for the new version, one for deprecating the predecessor.
        verify(commercialPolicyRepository, times(2)).save(any(CommercialPolicy.class));

        ArgumentCaptor<PolicyDefinedEvent> eventCaptor = ArgumentCaptor.forClass(PolicyDefinedEvent.class);
        verify(outboxEventPublisher).publish(any(), any(), eq(EventContractRegistry.POLICY_DEFINED_V1),
                eventCaptor.capture());
        assertEquals(1L, eventCaptor.getValue().supersedesPolicyId());
    }

    @Test
    @DisplayName("define() rejects a discount % threshold outside 0-100")
    void defineRejectsOutOfRangeDiscountThreshold() {
        CommercialPolicyCreateRequest request = baseRequest()
                .thresholds(List.of(PolicyThresholdRequest.builder()
                        .thresholdType(ThresholdType.DISCOUNT_PERCENT)
                        .limitValue(BigDecimal.valueOf(150))
                        .build()))
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> commercialPolicyService.define(request));
        assertEquals("INVALID_THRESHOLD_LIMIT", exception.getErrorCode());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("define() rejects a NON_STANDARD_CLAUSE threshold without a clauseCode")
    void defineRejectsNonStandardClauseWithoutCode() {
        CommercialPolicyCreateRequest request = baseRequest()
                .thresholds(List.of(PolicyThresholdRequest.builder()
                        .thresholdType(ThresholdType.NON_STANDARD_CLAUSE)
                        .build()))
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> commercialPolicyService.define(request));
        assertEquals("MISSING_CLAUSE_CODE", exception.getErrorCode());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("define() rejects a request with no thresholds")
    void defineRejectsEmptyThresholds() {
        CommercialPolicyCreateRequest request = baseRequest().thresholds(List.of()).build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> commercialPolicyService.define(request));
        assertEquals("MISSING_POLICY_THRESHOLDS", exception.getErrorCode());
    }

    @Test
    @DisplayName("define() with an unknown supersedesPolicyId raises ResourceNotFoundException")
    void defineWithUnknownPredecessorRaisesNotFound() {
        when(commercialPolicyRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> commercialPolicyService.define(baseRequest().supersedesPolicyId(99L).build()));
    }

    @Test
    @DisplayName("findApplicable() matches on segment/productFamily/region, treating a null policy field as wildcard")
    void findApplicableMatchesScope() {
        CommercialPolicy scoped = CommercialPolicy.builder()
                .id(1L).status(PolicyStatus.ACTIVE).policyVersion(1)
                .segment("ENTERPRISE").productFamily("PLATFORM").region("NA")
                .build();
        CommercialPolicy global = CommercialPolicy.builder()
                .id(2L).status(PolicyStatus.ACTIVE).policyVersion(1)
                .build();
        when(commercialPolicyRepository.findByStatusAndDeletedFalse(PolicyStatus.ACTIVE))
                .thenReturn(List.of(scoped, global));

        List<CommercialPolicyDto> matches = commercialPolicyService.findApplicable("ENTERPRISE", "PLATFORM", "NA");
        assertEquals(2, matches.size());

        List<CommercialPolicyDto> noMatch = commercialPolicyService.findApplicable("SMB", "PLATFORM", "NA");
        assertEquals(1, noMatch.size());
        assertEquals(2L, noMatch.get(0).getId());
    }

    @Test
    @DisplayName("deprecate() transitions an ACTIVE policy to DEPRECATED without emitting an event")
    void deprecateTransitionsStatus() {
        CommercialPolicy policy = CommercialPolicy.builder()
                .id(5L).status(PolicyStatus.ACTIVE).policyVersion(1).build();
        when(commercialPolicyRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(policy));

        CommercialPolicyDto result = commercialPolicyService.deprecate(5L);

        assertEquals(PolicyStatus.DEPRECATED, result.getStatus());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    @DisplayName("deprecate() on an already-DEPRECATED policy throws")
    void deprecateTwiceThrows() {
        CommercialPolicy policy = CommercialPolicy.builder()
                .id(5L).status(PolicyStatus.DEPRECATED).policyVersion(1).build();
        when(commercialPolicyRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(policy));

        assertThrows(IllegalStateException.class, () -> commercialPolicyService.deprecate(5L));
    }
}
