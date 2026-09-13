package com.opencrm.pricing.ruleset;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleSetService")
class RuleSetServiceTest {

    @Mock
    private RuleSetRepository ruleSetRepository;
    @Mock
    private RuleSetMapper ruleSetMapper;
    @InjectMocks
    private RuleSetService ruleSetService;

    @Test
    void create_saves() {
        RuleSetCreateRequest request = RuleSetCreateRequest.builder().name("s1").build();
        RuleSet entity = RuleSet.builder().name("s1").build();
        RuleSet saved = RuleSet.builder().name("s1").build();
        saved.setId(2L);
        RuleSetDto dto = RuleSetDto.builder().id(2L).name("s1").build();
        when(ruleSetMapper.toEntity(request)).thenReturn(entity);
        when(ruleSetRepository.save(entity)).thenReturn(saved);
        when(ruleSetMapper.toDto(saved)).thenReturn(dto);
        assertThat(ruleSetService.create(request).getId()).isEqualTo(2L);
    }

    @Test
    void getById_missing() {
        when(ruleSetRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ruleSetService.getById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_ok() {
        RuleSet entity = RuleSet.builder().name("s1").build();
        RuleSetDto dto = RuleSetDto.builder().id(1L).name("s1").build();
        when(ruleSetRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        when(ruleSetMapper.toDto(entity)).thenReturn(dto);
        assertThat(ruleSetService.getById(1L).getName()).isEqualTo("s1");
    }

    @Test
    void update_rejectsActive() {
        RuleSet entity = RuleSet.builder().name("s1").status(RuleSetStatus.ACTIVE).build();
        when(ruleSetRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> ruleSetService.update(1L, new RuleSetUpdateRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_savesDraft() {
        RuleSet entity = RuleSet.builder().name("s1").status(RuleSetStatus.DRAFT).build();
        RuleSetDto dto = RuleSetDto.builder().id(1L).name("s2").build();
        RuleSetUpdateRequest request = new RuleSetUpdateRequest();
        when(ruleSetRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        when(ruleSetRepository.save(entity)).thenReturn(entity);
        when(ruleSetMapper.toDto(entity)).thenReturn(dto);
        assertThat(ruleSetService.update(1L, request).getName()).isEqualTo("s2");
        verify(ruleSetMapper).updateEntity(request, entity);
    }

    @Test
    void delete_rejectsActive() {
        RuleSet entity = RuleSet.builder().name("s1").status(RuleSetStatus.ACTIVE).build();
        when(ruleSetRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> ruleSetService.delete(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void delete_softDeletesDraft() {
        RuleSet entity = RuleSet.builder().name("s1").status(RuleSetStatus.DRAFT).build();
        when(ruleSetRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        ruleSetService.delete(1L);
        assertThat(entity.isDeleted()).isTrue();
        verify(ruleSetRepository).save(entity);
    }

    @Test
    void list_filtersByStatus() {
        RuleSet entity = RuleSet.builder().name("s1").status(RuleSetStatus.DRAFT).build();
        RuleSetDto dto = RuleSetDto.builder().name("s1").build();
        when(ruleSetRepository.findByStatusAndDeletedFalse(RuleSetStatus.DRAFT, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(ruleSetMapper.toDto(entity)).thenReturn(dto);
        assertThat(ruleSetService.list(Pageable.unpaged(), RuleSetStatus.DRAFT).getContent()).containsExactly(dto);
    }

    @Test
    void list_allWhenStatusNull() {
        when(ruleSetRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertThat(ruleSetService.list(Pageable.unpaged(), null)).isEmpty();
    }

    @Test
    void activate_fromDraft() {
        RuleSet entity = RuleSet.builder().name("s1").status(RuleSetStatus.DRAFT).build();
        RuleSetDto dto = RuleSetDto.builder().status(RuleSetStatus.ACTIVE).build();
        when(ruleSetRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        when(ruleSetRepository.save(entity)).thenReturn(entity);
        when(ruleSetMapper.toDto(entity)).thenReturn(dto);
        assertThat(ruleSetService.activate(1L).getStatus()).isEqualTo(RuleSetStatus.ACTIVE);
    }

    @Test
    void activate_rejectsNonDraft() {
        RuleSet entity = RuleSet.builder().name("s1").status(RuleSetStatus.ACTIVE).build();
        when(ruleSetRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> ruleSetService.activate(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void deprecate_fromActive() {
        RuleSet entity = RuleSet.builder().name("s1").status(RuleSetStatus.ACTIVE).build();
        RuleSetDto dto = RuleSetDto.builder().status(RuleSetStatus.DEPRECATED).build();
        when(ruleSetRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        when(ruleSetRepository.save(entity)).thenReturn(entity);
        when(ruleSetMapper.toDto(entity)).thenReturn(dto);
        assertThat(ruleSetService.deprecate(1L).getStatus()).isEqualTo(RuleSetStatus.DEPRECATED);
    }

    @Test
    void deprecate_rejectsNonActive() {
        RuleSet entity = RuleSet.builder().name("s1").status(RuleSetStatus.DRAFT).build();
        when(ruleSetRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> ruleSetService.deprecate(1L)).isInstanceOf(BusinessException.class);
    }
}
