package com.opencrm.crm.merge;

import com.opencrm.crm.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountMergeControllerTest {

    @Mock
    private AccountMergeService accountMergeService;

    @InjectMocks
    private AccountMergeController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void mergePassesTheCallerChosenIds() throws Exception {
        when(accountMergeService.merge(any())).thenReturn(AccountMergeResult.builder()
                .survivorId(1L)
                .mergedAwayId(2L)
                .survivorAccountId("550e8400-e29b-41d4-a716-446655440000")
                .mergedAwayAccountId("550e8400-e29b-41d4-a716-446655440001")
                .decidedBy("user:sales-ops")
                .mergeId("merge-audit-1")
                .contactsRepointed(1)
                .leadsRepointed(0)
                .dealsRepointed(0)
                .build());

        mockMvc.perform(post("/api/v1/organizations/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"survivorId\":1,\"mergedAwayId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.survivorId").value(1))
                .andExpect(jsonPath("$.data.mergedAwayId").value(2))
                .andExpect(jsonPath("$.data.decidedBy").value("user:sales-ops"));

        verify(accountMergeService).merge(AccountMergeRequest.builder().survivorId(1L).mergedAwayId(2L).build());
    }

    @Test
    void mergeRequiresBothIds() throws Exception {
        mockMvc.perform(post("/api/v1/organizations/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"survivorId\":1}"))
                .andExpect(status().isUnprocessableContent());

        verify(accountMergeService, never()).merge(any());
    }
}
