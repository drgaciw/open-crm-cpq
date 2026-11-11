package com.opencrm.configuration.session;

import com.opencrm.configuration.config.GlobalExceptionHandler;
import com.opencrm.configuration.dto.ConfigurationSessionDto;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConfigurationDefaultsControllerTest {
    @Test
    void usesAuthenticatedActorAndReturnsVisibleDefaultMarkers() throws Exception {
        var service = mock(ConfigurationDefaultsService.class);
        var actor = mock(CurrentActor.class);
        when(actor.id()).thenReturn("seller");
        when(service.applyDefaults(1L, "seller")).thenReturn(ConfigurationSessionDto.builder().id(1L)
                .selections(Map.of("option:1:11", "20")).defaultedSelectionKeys(Set.of("option:1:11"))
                .valid(true).build());
        var mvc = MockMvcBuilders.standaloneSetup(new ConfigurationDefaultsController(service, actor))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/configurations/1/defaults").param("userId", "forged"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.defaultedSelectionKeys[0]").value("option:1:11"))
                .andExpect(jsonPath("$.data.deleted").doesNotExist());
        verify(service).applyDefaults(1L, "seller");
    }

    @Test
    void exposesConstraintConflictAsFailureWithoutSuccessPayload() throws Exception {
        var service = mock(ConfigurationDefaultsService.class);
        var actor = mock(CurrentActor.class);
        when(actor.id()).thenReturn("seller");
        when(service.applyDefaults(1L, "seller")).thenThrow(new BusinessException("Conflicting defaults",
                HttpStatus.UNPROCESSABLE_CONTENT, "DEFAULTS_CONSTRAINT_CONFLICT"));
        var mvc = MockMvcBuilders.standaloneSetup(new ConfigurationDefaultsController(service, actor))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/configurations/1/defaults")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DEFAULTS_CONSTRAINT_CONFLICT"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
