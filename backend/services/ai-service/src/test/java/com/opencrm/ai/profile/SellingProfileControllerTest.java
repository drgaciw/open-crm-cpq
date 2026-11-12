package com.opencrm.ai.profile;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SellingProfileControllerTest {
    SellingProfileService service=mock(SellingProfileService.class);
    MockMvc mvc=MockMvcBuilders.standaloneSetup(new SellingProfileController(service)).build();
    String root="/api/v1/ai/selling-profile";
    @BeforeEach void auth() { authenticate("tenant",true); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void authenticate(String tenant,boolean admin) {
        var token=Jwt.withTokenValue("test").header("alg","none").subject("actor");
        if(tenant!=null) token.claim("tid",tenant);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token.build(),
            admin?List.of(new SimpleGrantedAuthority("ROLE_ADMIN")):List.of()));
    }
    @Test void getUsesJwtTenantNotCallerHeader() throws Exception {
        when(service.get("tenant")).thenReturn(Optional.empty());
        mvc.perform(get(root).header("X-Tenant-Id","other")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).get("tenant");
    }
    @Test void putDelegatesWithActor() throws Exception {
        mvc.perform(put(root).contentType(MediaType.APPLICATION_JSON).content("{\"companyOverview\":\"Company\"}"))
            .andExpect(status().isOk());
        verify(service).put(eq("tenant"),argThat(f->f.companyOverview().equals("Company")),eq("actor"));
    }
    @Test void ingestUsesDedicatedGuardedRoute() throws Exception {
        mvc.perform(post(root+"/ingest-url").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\"}"))
            .andExpect(status().isOk()); verify(service).ingest("tenant","https://example.com","actor");
    }
    @Test void nonAdminCannotMutateButMayRead() throws Exception {
        authenticate("tenant",false); when(service.get("tenant")).thenReturn(Optional.empty());
        mvc.perform(put(root).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(root+"/ingest-url").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\"}"))
            .andExpect(status().isForbidden()); mvc.perform(get(root)).andExpect(status().isOk());
        verify(service).get("tenant"); verifyNoMoreInteractions(service);
    }
    @Test void anonymousAndMissingTenantFailClosed() throws Exception {
        clear(); mvc.perform(get(root)).andExpect(status().isUnauthorized());
        authenticate(null,true); mvc.perform(get(root)).andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void blankUrlIsBadRequest() throws Exception {
        mvc.perform(post(root+"/ingest-url").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}"))
            .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void unsafeUrlIsBadRequest() throws Exception {
        when(service.ingest(anyString(),anyString(),anyString())).thenThrow(new IllegalArgumentException());
        mvc.perform(post(root+"/ingest-url").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://127.0.0.1\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }
    @Test void oversizedFieldIsRejected() throws Exception {
        mvc.perform(put(root).contentType(MediaType.APPLICATION_JSON).content("{\"companyOverview\":\""+"x".repeat(4001)+"\"}"))
            .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
}
