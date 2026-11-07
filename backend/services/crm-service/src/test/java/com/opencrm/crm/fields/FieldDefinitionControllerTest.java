package com.opencrm.crm.fields;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.crm.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FieldDefinitionControllerTest {

    @Mock
    private FieldRegistryService fieldRegistryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new FieldDefinitionController(fieldRegistryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createValidatesBeforeTheServiceRuns() throws Exception {
        mockMvc.perform(post("/api/v1/field-definitions")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entity\":\"ORGANIZATION\",\"key\":\" \",\"label\":\"Region\",\"type\":\"TEXT\",\"required\":false,\"showOnRecord\":true,\"showOnList\":true,\"position\":1,\"agentFilled\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        verify(fieldRegistryService, never()).create(any(), any());
    }

    @Test
    void definitionsHaveNoHardDeleteRoute() throws Exception {
        // The existing handler maps an unmapped method to 500. The definition service is not invoked.
        mockMvc.perform(delete("/api/v1/field-definitions/9").param("tenant", FixtureTenants.A))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"));

        verifyNoInteractions(fieldRegistryService);
    }

    @Test
    void agentValueRejectionIsReturnedAsForbidden() throws Exception {
        when(fieldRegistryService.upsertValue(eq(FixtureTenants.A), eq(4L), any()))
                .thenThrow(new BusinessException(
                        "Agents cannot write field values",
                        HttpStatus.FORBIDDEN,
                        "AGENT_FIELD_VALUE_FORBIDDEN"));

        mockMvc.perform(put("/api/v1/field-definitions/4/values")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":3,\"text\":\"west\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AGENT_FIELD_VALUE_FORBIDDEN"));
    }

    @Test
    void relabelRouteReturnsTheUpdatedDefinition() throws Exception {
        when(fieldRegistryService.update(eq(FixtureTenants.A), eq(4L), any()))
                .thenReturn(FieldDefinitionResponse.builder()
                        .id(4L)
                        .tenant(FixtureTenants.A)
                        .entity(FieldEntityKind.ORGANIZATION)
                        .key("region_code")
                        .label("Territory code")
                        .type(FieldType.TEXT)
                        .storageClass(StorageClass.TEXT)
                        .createdBy("person-create")
                        .updatedBy("person-relabel")
                        .version(1)
                        .options(List.of())
                        .build());

        mockMvc.perform(put("/api/v1/field-definitions/4")
                        .param("tenant", FixtureTenants.A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Territory code\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("region_code"))
                .andExpect(jsonPath("$.data.label").value("Territory code"))
                .andExpect(jsonPath("$.data.createdBy").value("person-create"))
                .andExpect(jsonPath("$.data.updatedBy").value("person-relabel"));
    }
}
