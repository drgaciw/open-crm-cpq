package com.opencrm.ai.ledger;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.*;
import static com.opencrm.ai.ledger.LedgerTypes.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentLedgerControllerTest {
    AgentLedgerService ledger=mock(AgentLedgerService.class);
    MockMvc mvc=MockMvcBuilders.standaloneSetup(new AgentLedgerController(ledger)).build();
    UUID id=UUID.randomUUID();
    @BeforeEach void auth() { authenticate("tenant"); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void authenticate(String tenant) {
        var builder=Jwt.withTokenValue("test").header("alg","none").subject("actor");
        if(tenant!=null) builder.claim("tid",tenant);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(),List.of()));
    }
    @Test void startUsesVerifiedTenantRatherThanHeader() throws Exception {
        mvc.perform(post("/api/v1/ai/runs").header("X-Tenant-Id","other").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":\""+id+"\",\"provider\":\"azure\",\"model\":\"gpt\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true));
        verify(ledger).start("tenant",new Start(id,"azure","gpt"),"actor");
    }
    @Test void readsRunActionsAndAudits() throws Exception {
        mvc.perform(get("/api/v1/ai/runs/"+id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ai/runs/"+id+"/actions")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ai/runs/"+id+"/audit-events")).andExpect(status().isOk());
        verify(ledger).get("tenant",id); verify(ledger).actions("tenant",id); verify(ledger).audits("tenant",id);
    }
    @Test void completeAndFailRoutes() throws Exception {
        mvc.perform(post("/api/v1/ai/runs/"+id+"/complete")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/ai/runs/"+id+"/fail").contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorCode\":\"TIMEOUT\"}")).andExpect(status().isOk());
        verify(ledger).complete("tenant",id,"actor"); verify(ledger).fail("tenant",id,"TIMEOUT","actor");
    }
    @Test void actionAndAuditRoutes() throws Exception {
        mvc.perform(post("/api/v1/ai/runs/"+id+"/actions").contentType(MediaType.APPLICATION_JSON).content("""
                {"kind":"LLM","tool":"chat","provider":"azure","model":"gpt","promptTokens":1,
                "completionTokens":2,"inputUsdPerMillion":0.125,"outputUsdPerMillion":0.375,"latencyMs":7}
                """)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/ai/runs/"+id+"/audit-events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"REVIEWED\"}")).andExpect(status().isCreated());
        verify(ledger).recordAction(eq("tenant"),eq(id),any(ActionInput.class),eq("actor"));
        verify(ledger).audit("tenant",id,"REVIEWED","actor");
    }
    @Test void usagePreservesDecimalValuesAndTaskFilter() throws Exception {
        when(ledger.usage("tenant",id)).thenReturn(new Usage(2,5,7,new BigDecimal("0.000000001234")));
        mvc.perform(get("/api/v1/ai/usage").param("taskId",id.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.runs").value(2));
        verify(ledger).usage("tenant",id);
    }
    @Test void negativeUsageAndBlankStartAreBadRequests() throws Exception {
        mvc.perform(post("/api/v1/ai/runs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ai/runs/"+id+"/actions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"promptTokens\":-1}")).andExpect(status().isBadRequest());
        verifyNoInteractions(ledger);
    }
    @Test void missingTenantIsForbidden() throws Exception {
        authenticate(null); mvc.perform(get("/api/v1/ai/usage")).andExpect(status().isForbidden());
        verifyNoInteractions(ledger);
    }
    @Test void anonymousIsUnauthorizedEvenIfHeaderPresent() throws Exception {
        clear(); mvc.perform(get("/api/v1/ai/usage").header("X-Tenant-Id","tenant"))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(ledger);
    }
    @Test void auditUpdatesAndDeletionHaveNoEndpoint() throws Exception {
        mvc.perform(put("/api/v1/ai/runs/"+id+"/audit-events")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/v1/ai/runs/"+id+"/audit-events")).andExpect(status().isMethodNotAllowed());
    }
}
