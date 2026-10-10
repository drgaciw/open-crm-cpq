package com.opencrm.contract.controller;

import com.opencrm.contract.service.RampScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RampScheduleControllerTest {
    @Test void internalAndPublicAliasesUseTheSameDateAwareRead() throws Exception {
        var service = mock(RampScheduleService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new RampScheduleController(service)).build();
        mvc.perform(get("/api/v1/subscriptions/by-number/SUB-fixture/ramp-schedule").param("asOf", "2028-01-01"))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/v1/subscriptions/by-number/SUB-fixture/ramp-schedule").param("asOf", "2028-01-01"))
                .andExpect(status().isOk());
        verify(service, times(2)).byNumber("SUB-fixture", LocalDate.of(2028,1,1));
    }
    @Test void idReadAndInvalidIsoDateAreBoundedByTheController() throws Exception {
        var service = mock(RampScheduleService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new RampScheduleController(service)).build();
        mvc.perform(get("/api/v1/subscriptions/42/ramp-schedule")).andExpect(status().isOk());
        verify(service).byId(42L, null);
        mvc.perform(get("/api/v1/subscriptions/42/ramp-schedule").param("asOf", "not-a-date")).andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }
}
