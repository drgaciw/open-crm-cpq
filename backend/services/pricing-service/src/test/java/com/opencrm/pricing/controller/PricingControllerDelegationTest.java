package com.opencrm.pricing.controller;

import com.opencrm.pricing.customer.CustomerController;
import com.opencrm.pricing.customer.CustomerCreateRequest;
import com.opencrm.pricing.customer.CustomerDto;
import com.opencrm.pricing.customer.CustomerSegment;
import com.opencrm.pricing.customer.CustomerService;
import com.opencrm.pricing.customer.CustomerUpdateRequest;
import com.opencrm.pricing.execution.BulkPricingSimulationRequest;
import com.opencrm.pricing.execution.BulkPricingSimulationResponse;
import com.opencrm.pricing.execution.PricingAuditLog;
import com.opencrm.pricing.execution.PricingExecutionController;
import com.opencrm.pricing.execution.PricingExecutionService;
import com.opencrm.pricing.rule.RuleController;
import com.opencrm.pricing.rule.RuleCreateRequest;
import com.opencrm.pricing.rule.RuleDto;
import com.opencrm.pricing.rule.RuleImpactDto;
import com.opencrm.pricing.rule.RuleService;
import com.opencrm.pricing.rule.RuleUpdateRequest;
import com.opencrm.pricing.ruleset.RuleSetController;
import com.opencrm.pricing.ruleset.RuleSetCreateRequest;
import com.opencrm.pricing.ruleset.RuleSetDto;
import com.opencrm.pricing.ruleset.RuleSetService;
import com.opencrm.pricing.ruleset.RuleSetStatus;
import com.opencrm.pricing.ruleset.RuleSetUpdateRequest;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pricing controllers delegate to services")
class PricingControllerDelegationTest {

    @Mock private RuleService ruleService;
    @Mock private RuleSetService ruleSetService;
    @Mock private CustomerService customerService;
    @Mock private PricingExecutionService pricingExecutionService;

    @Test
    void ruleController_coversEndpoints() {
        RuleController controller = new RuleController(ruleService);
        RuleDto dto = RuleDto.builder().id(1L).build();
        RuleCreateRequest create = new RuleCreateRequest();
        RuleUpdateRequest update = new RuleUpdateRequest();
        Pageable pageable = PageRequest.of(0, 20);
        when(ruleService.create(create)).thenReturn(dto);
        when(ruleService.listByRuleSet(9L, pageable)).thenReturn(new PageImpl<>(List.of(dto)));
        when(ruleService.getById(1L)).thenReturn(dto);
        when(ruleService.update(1L, update)).thenReturn(dto);
        when(ruleService.getImpact(1L)).thenReturn(RuleImpactDto.builder().ruleId(1L).build());
        assertThat(controller.create(create).getData().getId()).isEqualTo(1L);
        assertThat(controller.listByRuleSet(9L, pageable).getPagination()).isNotNull();
        assertThat(controller.getById(1L).getData()).isEqualTo(dto);
        assertThat(controller.update(1L, update).getData()).isEqualTo(dto);
        controller.delete(1L);
        verify(ruleService).delete(1L);
        assertThat(controller.validate(1L).getMessage()).contains("validated");
        assertThat(controller.impact(1L).getData().getRuleId()).isEqualTo(1L);
    }

    @Test
    void ruleSetController_coversEndpoints() {
        RuleSetController controller = new RuleSetController(ruleSetService);
        RuleSetDto dto = RuleSetDto.builder().id(2L).build();
        RuleSetCreateRequest create = new RuleSetCreateRequest();
        RuleSetUpdateRequest update = new RuleSetUpdateRequest();
        Pageable pageable = PageRequest.of(0, 20);
        when(ruleSetService.create(create)).thenReturn(dto);
        when(ruleSetService.list(pageable, RuleSetStatus.DRAFT)).thenReturn(new PageImpl<>(List.of(dto)));
        when(ruleSetService.getById(2L)).thenReturn(dto);
        when(ruleSetService.update(2L, update)).thenReturn(dto);
        when(ruleSetService.activate(2L)).thenReturn(dto);
        when(ruleSetService.deprecate(2L)).thenReturn(dto);
        assertThat(controller.create(create).getData().getId()).isEqualTo(2L);
        assertThat(controller.list(pageable, RuleSetStatus.DRAFT).getPagination().getTotalElements()).isEqualTo(1);
        assertThat(controller.getById(2L).getData()).isEqualTo(dto);
        assertThat(controller.update(2L, update).getData()).isEqualTo(dto);
        controller.delete(2L);
        verify(ruleSetService).delete(2L);
        assertThat(controller.activate(2L).getData()).isEqualTo(dto);
        assertThat(controller.deprecate(2L).getData()).isEqualTo(dto);
    }

    @Test
    void customerController_coversEndpoints() {
        CustomerController controller = new CustomerController(customerService);
        CustomerDto dto = CustomerDto.builder().id(3L).build();
        CustomerCreateRequest create = CustomerCreateRequest.builder().externalId("e").name("n").build();
        CustomerUpdateRequest update = new CustomerUpdateRequest();
        Pageable pageable = PageRequest.of(0, 20);
        when(customerService.create(create)).thenReturn(dto);
        when(customerService.list(pageable, CustomerSegment.ENTERPRISE)).thenReturn(new PageImpl<>(List.of(dto)));
        when(customerService.getById(3L)).thenReturn(dto);
        when(customerService.update(3L, update)).thenReturn(dto);
        assertThat(controller.create(create).getData().getId()).isEqualTo(3L);
        assertThat(controller.list(pageable, CustomerSegment.ENTERPRISE).getPagination()).isNotNull();
        assertThat(controller.getById(3L).getData()).isEqualTo(dto);
        assertThat(controller.update(3L, update).getData()).isEqualTo(dto);
        controller.delete(3L);
        verify(customerService).delete(3L);
    }

    @Test
    void executionController_coversEndpoints() {
        PricingExecutionController controller = new PricingExecutionController(pricingExecutionService);
        PricingContext ctx = PricingContext.builder().quoteExternalId("Q1").build();
        PricingResult result = new PricingResult();
        BulkPricingSimulationResponse bulk = new BulkPricingSimulationResponse();
        when(pricingExecutionService.execute(ctx)).thenReturn(result);
        when(pricingExecutionService.simulate(ctx)).thenReturn(result);
        when(pricingExecutionService.simulateBulk(List.of(ctx))).thenReturn(bulk);
        when(pricingExecutionService.getAuditTrail("Q1")).thenReturn(List.of(new PricingAuditLog()));
        assertThat(controller.execute(ctx).getData()).isEqualTo(result);
        assertThat(controller.simulate(ctx).getData()).isEqualTo(result);
        BulkPricingSimulationRequest bulkReq = new BulkPricingSimulationRequest();
        bulkReq.setContexts(List.of(ctx));
        assertThat(controller.simulateBulk(bulkReq).getData()).isEqualTo(bulk);
        assertThat(controller.getAuditTrail("Q1").getData()).hasSize(1);
    }
}
