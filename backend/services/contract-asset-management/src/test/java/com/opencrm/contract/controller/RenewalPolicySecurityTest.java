package com.opencrm.contract.controller;

import com.opencrm.contract.config.GlobalExceptionHandler;
import com.opencrm.contract.service.RenewalPolicyRecordService;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = RenewalPolicySecurityTest.App.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@Tag("unit")
class RenewalPolicySecurityTest {

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, RenewalPolicyController.class, GlobalExceptionHandler.class})
    static class App {
        @Bean
        RenewalPolicyRecordService renewalPolicyRecordService() {
            RenewalPolicyRecordService service = Mockito.mock(RenewalPolicyRecordService.class);
            when(service.read(any(), any(), any(), any(), any())).thenReturn(List.of());
            return service;
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RenewalPolicyRecordService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void anonymousAndUserRolesCannotReadAndAServiceTokenCan() throws Exception {
        mvc.perform(get(RenewalPolicyController.PATH).param("accountId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        for (String role : new String[] {"CPQ.User", "SalesVP", "DealDesk", "CFO", "Admin", "Legal"}) {
            mvc.perform(get(RenewalPolicyController.PATH)
                            .param("accountId", UUID.randomUUID().toString())
                            .header(HttpHeaders.AUTHORIZATION, TestTokens.user("person", role).bearer()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(RenewalPolicyController.PATH)
                        .param("accountId", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("crm-service").bearer()))
                .andExpect(status().isForbidden());
        verify(service, never()).read(any(), any(), any(), any(), any());

        mvc.perform(get(RenewalPolicyController.PATH)
                        .param("accountId", "cccccccc-cccc-cccc-cccc-cccccccccccc")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("quote-service", "Internal.Invoke").bearer()))
                .andExpect(status().isOk());
        verify(service).read(
                UUID.fromString("00000000-0000-4000-8000-00000000c0de"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                null,
                null,
                null);
    }
}
