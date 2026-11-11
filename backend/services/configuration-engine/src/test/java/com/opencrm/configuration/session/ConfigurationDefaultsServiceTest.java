package com.opencrm.configuration.session;

import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.dto.ConfigurationViolation;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.configuration.constraint.ConstraintSolver;
import com.opencrm.configuration.dto.ConfigurationSessionDto;
import com.opencrm.configuration.mapper.ConfigurationSessionMapper;
import com.opencrm.configuration.product.ProductOptionDefaultsClient;
import com.opencrm.configuration.product.ProductOptionGroupSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ConfigurationDefaultsServiceTest {
    private final ConfigurationSessionRepository repository = mock(ConfigurationSessionRepository.class);
    private final ProductOptionDefaultsClient catalog = mock(ProductOptionDefaultsClient.class);
    private final ConstraintSolver solver = mock(ConstraintSolver.class);
    private final ConfigurationSessionMapper mapper = Mappers.getMapper(ConfigurationSessionMapper.class);
    private ConfigurationDefaultsService service;
    private ConfigurationSession session;

    @BeforeEach
    void setUp() {
        service = new ConfigurationDefaultsService(repository, catalog, solver, mapper);
        session = ConfigurationSession.builder().productId("10").userId("seller")
                .status(ConfigurationStatus.IN_PROGRESS).selections(new HashMap<>())
                .defaultedSelectionKeys(new HashSet<>()).build();
        session.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(solver.evaluateExplicitSelections(any())).thenReturn(ConfigurationValidationResult.ok());
    }

    @Test
    void appliesRequiredDefaultsAndExposesTheirProvenance() {
        when(catalog.getGroups(10L)).thenReturn(List.of(group(1, 1, "REQUIRED_SINGLE", option(11, 20, true))));

        ConfigurationSessionDto result = service.applyDefaults(1L, "seller");

        assertThat(result.getSelections()).containsEntry("option:1:11", "20");
        assertThat(result.getDefaultedSelectionKeys()).containsExactly("option:1:11");
        assertThat(result.isValid()).isTrue();
        verify(solver).evaluateExplicitSelections(argThat(candidate -> candidate != session
                && candidate.getSelections().equals(Map.of("option:1:11", "20"))));
        verify(repository).save(session);
        verify(catalog).requireProduct(20L);
    }

    @Test
    void preservesManualChoiceAndDoesNotAddAnotherSingleSelectionDefault() {
        session.getSelections().put("manual-choice", "30");
        when(catalog.getGroups(10L)).thenReturn(List.of(group(1, 1, "REQUIRED_SINGLE",
                option(11, 20, true), option(12, 30, false))));

        ConfigurationSessionDto result = service.applyDefaults(1L, "seller");

        assertThat(result.getSelections()).containsExactlyEntriesOf(Map.of("manual-choice", "30"));
        assertThat(result.getDefaultedSelectionKeys()).isEmpty();
        verify(catalog, never()).requireProduct(any());
    }

    @Test
    void validatesAllDefaultsTogetherAndRejectsConflictWithoutChangingManagedState() {
        session.getSelections().put("manual", "99");
        when(catalog.getGroups(10L)).thenReturn(List.of(
                group(1, 1, "REQUIRED_SINGLE", option(11, 20, true)),
                group(2, 1, "REQUIRED_SINGLE", option(21, 30, true))));
        when(solver.evaluateExplicitSelections(any())).thenReturn(ConfigurationValidationResult.of(List.of(
                ConfigurationViolation.builder().ruleType("EXCLUDES").description("20 excludes 30").build())));

        assertThatThrownBy(() -> service.applyDefaults(1L, "seller"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DEFAULTS_CONSTRAINT_CONFLICT"));
        assertThat(session.getSelections()).containsExactlyEntriesOf(Map.of("manual", "99"));
        assertThat(session.getDefaultedSelectionKeys()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void refusesInsufficientDefaultsInsteadOfChoosingAnUnmarkedOption() {
        when(catalog.getGroups(10L)).thenReturn(List.of(group(1, 1, "REQUIRED_SINGLE", option(11, 20, false))));
        assertFailure("DEFAULTS_INCOMPLETE");
    }

    @Test
    void refusesAmbiguousSingleDefaultsWithoutPickingByOrder() {
        when(catalog.getGroups(10L)).thenReturn(List.of(group(1, 1, "REQUIRED_SINGLE",
                option(11, 20, true), option(12, 30, true))));
        assertFailure("DEFAULTS_CARDINALITY_CONFLICT");
    }

    @Test
    void missingOrDeletedDefaultTargetRefusesTheWholeBatchWithoutWritingAnySelection() {
        when(catalog.getGroups(10L)).thenReturn(List.of(
                group(1, 1, "REQUIRED_SINGLE", option(11, 20, true)),
                group(2, 1, "REQUIRED_SINGLE", option(21, 30, true))));
        doThrow(new BusinessException("Deleted target", HttpStatus.NOT_FOUND, "DEFAULTS_PRODUCT_NOT_FOUND"))
                .when(catalog).requireProduct(30L);

        assertFailure("DEFAULTS_PRODUCT_NOT_FOUND");
        assertThat(session.getSelections()).isEmpty();
        verifyNoInteractions(solver);
    }

    @Test
    void missingSelectionTypeIsAContractFailureRatherThanAnUnhandledNullPointer() {
        when(catalog.getGroups(10L)).thenReturn(List.of(new ProductOptionGroupSnapshot(1L, 10L,
                null, 1, 1, List.of(option(11, 20, true)))));
        assertFailure("INVALID_DEFAULTS_CATALOG");
        verify(catalog, never()).requireProduct(any());
        verifyNoInteractions(solver);
    }

    @Test
    void optionalGroupsAreNotFilledAndSoftRecommendationsRemainVisible() {
        when(catalog.getGroups(10L)).thenReturn(List.of(group(1, 0, "MULTIPLE", option(11, 20, true))));
        when(solver.evaluateExplicitSelections(any())).thenReturn(ConfigurationValidationResult.of(List.of(
                ConfigurationViolation.builder().ruleType("RECOMMENDS").description("Consider support").build())));
        ConfigurationSessionDto result = service.applyDefaults(1L, "seller");
        assertThat(result.getSelections()).isEmpty();
        assertThat(result.isValid()).isTrue();
        assertThat(result.getValidationErrors()).isEqualTo("Consider support");
    }

    @Test
    void repeatedApplicationKeepsSelectionsAndMarkersStable() {
        when(catalog.getGroups(10L)).thenReturn(List.of(group(1, 1, "REQUIRED_SINGLE", option(11, 20, true))));
        service.applyDefaults(1L, "seller");
        ConfigurationSessionDto second = service.applyDefaults(1L, "seller");
        assertThat(second.getSelections()).hasSize(1);
        assertThat(second.getDefaultedSelectionKeys()).containsExactly("option:1:11");
        verify(catalog, times(1)).requireProduct(20L);
    }

    @Test
    void manualOverrideAndRemovalClearTheDefaultMarker() {
        session.getSelections().put("option:1:11", "20");
        session.getDefaultedSelectionKeys().add("option:1:11");
        session.addSelection("option:1:11", "30");
        assertThat(session.getDefaultedSelectionKeys()).isEmpty();
        session.getDefaultedSelectionKeys().add("option:1:11");
        session.removeSelection("option:1:11");
        assertThat(session.getDefaultedSelectionKeys()).isEmpty();
    }

    @Test
    void rejectsOtherOwnersBeforeReadingCatalog() {
        assertThatThrownBy(() -> service.applyDefaults(1L, "another-seller"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus().value()).isEqualTo(403));
        verifyNoInteractions(catalog, solver);
    }

    @Test
    void refusesCompletedExpiredAndDeletedSessions() {
        session.setStatus(ConfigurationStatus.COMPLETED);
        assertFailure("CONFIGURATION_NOT_EDITABLE");
        session.setStatus(ConfigurationStatus.IN_PROGRESS);
        session.setExpiresAt(Instant.EPOCH);
        assertFailure("CONFIGURATION_NOT_EDITABLE");
        session.setExpiresAt(null);
        session.setDeleted(true);
        assertFailure("CONFIGURATION_NOT_EDITABLE");
        verifyNoInteractions(catalog);
    }

    private void assertFailure(String code) {
        assertThatThrownBy(() -> service.applyDefaults(1L, "seller"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
        verify(repository, never()).save(any());
        assertThat(session.getDefaultedSelectionKeys()).isEmpty();
    }

    private static ProductOptionGroupSnapshot group(long id, int min, String type,
                                                    ProductOptionGroupSnapshot.Option... options) {
        return new ProductOptionGroupSnapshot(id, 10L, type, min,
                "MULTIPLE".equals(type) ? null : 1, List.of(options));
    }

    private static ProductOptionGroupSnapshot.Option option(long id, long productId, boolean isDefault) {
        return new ProductOptionGroupSnapshot.Option(id, productId, isDefault);
    }
}
