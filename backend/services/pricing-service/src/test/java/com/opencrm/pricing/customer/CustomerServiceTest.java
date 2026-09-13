package com.opencrm.pricing.customer;

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
@DisplayName("CustomerService")
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerMapper customerMapper;
    @InjectMocks
    private CustomerService customerService;

    @Test
    void create_savesNewExternalId() {
        CustomerCreateRequest request = CustomerCreateRequest.builder().externalId("EXT-1").name("Acme").build();
        Customer entity = Customer.builder().externalId("EXT-1").name("Acme").build();
        Customer saved = Customer.builder().externalId("EXT-1").name("Acme").build();
        saved.setId(4L);
        CustomerDto dto = CustomerDto.builder().id(4L).externalId("EXT-1").build();
        when(customerRepository.findByExternalIdAndDeletedFalse("EXT-1")).thenReturn(Optional.empty());
        when(customerMapper.toEntity(request)).thenReturn(entity);
        when(customerRepository.save(entity)).thenReturn(saved);
        when(customerMapper.toDto(saved)).thenReturn(dto);
        assertThat(customerService.create(request).getId()).isEqualTo(4L);
    }

    @Test
    void create_rejectsDuplicateExternalId() {
        CustomerCreateRequest request = CustomerCreateRequest.builder().externalId("EXT-1").name("Acme").build();
        when(customerRepository.findByExternalIdAndDeletedFalse("EXT-1"))
                .thenReturn(Optional.of(Customer.builder().externalId("EXT-1").build()));
        assertThatThrownBy(() -> customerService.create(request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void getById_ok() {
        Customer entity = Customer.builder().name("Acme").build();
        CustomerDto dto = CustomerDto.builder().id(1L).name("Acme").build();
        when(customerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        when(customerMapper.toDto(entity)).thenReturn(dto);
        assertThat(customerService.getById(1L).getName()).isEqualTo("Acme");
    }

    @Test
    void getById_missing() {
        when(customerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> customerService.getById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByExternalId_ok() {
        Customer entity = Customer.builder().externalId("EXT-1").build();
        CustomerDto dto = CustomerDto.builder().externalId("EXT-1").build();
        when(customerRepository.findByExternalIdAndDeletedFalse("EXT-1")).thenReturn(Optional.of(entity));
        when(customerMapper.toDto(entity)).thenReturn(dto);
        assertThat(customerService.getByExternalId("EXT-1").getExternalId()).isEqualTo("EXT-1");
    }

    @Test
    void getByExternalId_missing() {
        when(customerRepository.findByExternalIdAndDeletedFalse("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> customerService.getByExternalId("x")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_saves() {
        Customer entity = Customer.builder().name("old").build();
        CustomerUpdateRequest request = new CustomerUpdateRequest();
        CustomerDto dto = CustomerDto.builder().id(1L).name("new").build();
        when(customerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        when(customerRepository.save(entity)).thenReturn(entity);
        when(customerMapper.toDto(entity)).thenReturn(dto);
        assertThat(customerService.update(1L, request).getName()).isEqualTo("new");
        verify(customerMapper).updateEntity(request, entity);
    }

    @Test
    void delete_softDeletes() {
        Customer entity = Customer.builder().name("Acme").build();
        when(customerRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        customerService.delete(1L);
        assertThat(entity.isDeleted()).isTrue();
        verify(customerRepository).save(entity);
    }

    @Test
    void list_bySegment() {
        Customer entity = Customer.builder().name("Acme").segment(CustomerSegment.ENTERPRISE).build();
        CustomerDto dto = CustomerDto.builder().name("Acme").build();
        when(customerRepository.findBySegmentAndDeletedFalse(CustomerSegment.ENTERPRISE, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(customerMapper.toDto(entity)).thenReturn(dto);
        assertThat(customerService.list(Pageable.unpaged(), CustomerSegment.ENTERPRISE).getContent()).containsExactly(dto);
    }

    @Test
    void list_allWhenSegmentNull() {
        when(customerRepository.findByDeletedFalse(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertThat(customerService.list(Pageable.unpaged(), null)).isEmpty();
    }
}
