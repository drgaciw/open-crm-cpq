package com.opencrm.product.bundle;

import com.opencrm.product.config.GlobalExceptionHandler;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C16/C17 for the product-bundle PUT routes. Admin is the only allowed role.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ProductBundleUpdateSecurityTest.App.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@Tag("unit")
class ProductBundleUpdateSecurityTest {

    private static final String BUNDLE = "/api/v1/product-bundles/7";
    private static final String ITEM = "/api/v1/product-bundles/7/items/8";
    private static final String BUNDLE_BODY = """
            {"name":"Updated","description":"New details","discountType":"NONE","minItems":0,"maxItems":2}
            """;
    private static final String ITEM_BODY = """
            {"quantity":4,"optional":true,"sortOrder":3}
            """;

    private static final ProductBundleService BUNDLES = mock(ProductBundleService.class);

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ComponentScan(basePackages = "com.opencrm.product.security")
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, GlobalExceptionHandler.class, ProductBundleController.class})
    static class App {
        @Bean
        ProductBundleService productBundleService() {
            return BUNDLES;
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        reset(BUNDLES);
        when(BUNDLES.update(any(), any())).thenReturn(ProductBundleDto.builder().id(7L).name("Updated").build());
        when(BUNDLES.updateItem(any(), any(), any())).thenReturn(ProductBundleDto.builder().id(7L).name("Updated").build());
    }

    @Test
    void anonymousBundleUpdateIs401() throws Exception {
        mvc.perform(put(BUNDLE).contentType(MediaType.APPLICATION_JSON).content(BUNDLE_BODY))
                .andExpect(status().isUnauthorized());
        verify(BUNDLES, never()).update(any(), any());
    }

    @Test
    void anonymousBundleItemUpdateIs401() throws Exception {
        mvc.perform(put(ITEM).contentType(MediaType.APPLICATION_JSON).content(ITEM_BODY))
                .andExpect(status().isUnauthorized());
        verify(BUNDLES, never()).updateItem(any(), any(), any());
    }

    @ParameterizedTest(name = "{0} bundle update is 403")
    @ValueSource(strings = {"CPQ.User", "DealDesk", "SalesVP", "CFO", "InstalledBase.Read", "NotARealRole"})
    void refusedRoleBundleUpdateIs403(String role) throws Exception {
        asUser(BUNDLE, BUNDLE_BODY, role);
        verify(BUNDLES, never()).update(any(), any());
    }

    @Test
    void cpqUserBundleUpdateIs403() throws Exception {
        refusedRoleBundleUpdateIs403("CPQ.User");
    }

    @ParameterizedTest(name = "{0} bundle item update is 403")
    @ValueSource(strings = {"CPQ.User", "DealDesk", "SalesVP", "CFO", "InstalledBase.Read", "NotARealRole"})
    void refusedRoleBundleItemUpdateIs403(String role) throws Exception {
        asUser(ITEM, ITEM_BODY, role);
        verify(BUNDLES, never()).updateItem(any(), any(), any());
    }

    @Test
    void rolelessBundleUpdateIs403() throws Exception {
        bearer(BUNDLE, BUNDLE_BODY, TestTokens.user("alice").bearer());
        verify(BUNDLES, never()).update(any(), any());
    }

    @Test
    void rolelessBundleItemUpdateIs403() throws Exception {
        bearer(ITEM, ITEM_BODY, TestTokens.user("alice").bearer());
        verify(BUNDLES, never()).updateItem(any(), any(), any());
    }

    @Test
    void internalInvokeBundleUpdateIs403() throws Exception {
        bearer(BUNDLE, BUNDLE_BODY, TestTokens.service("catalog", "Internal.Invoke").bearer());
        verify(BUNDLES, never()).update(any(), any());
    }

    @Test
    void internalInvokeBundleItemUpdateIs403() throws Exception {
        bearer(ITEM, ITEM_BODY, TestTokens.service("catalog", "Internal.Invoke").bearer());
        verify(BUNDLES, never()).updateItem(any(), any(), any());
    }

    @Test
    void adminBundleUpdateSucceeds() throws Exception {
        mvc.perform(put(BUNDLE).contentType(MediaType.APPLICATION_JSON).content(BUNDLE_BODY)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("ada", "Admin").bearer()))
                .andExpect(status().isOk());
        verify(BUNDLES).update(any(), any());
    }

    @Test
    void adminBundleItemUpdateSucceeds() throws Exception {
        mvc.perform(put(ITEM).contentType(MediaType.APPLICATION_JSON).content(ITEM_BODY)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("ada", "Admin").bearer()))
                .andExpect(status().isOk());
        verify(BUNDLES).updateItem(any(), any(), any());
    }

    private void asUser(String path, String body, String role) throws Exception {
        bearer(path, body, TestTokens.user("alice", role).bearer());
    }

    private void bearer(String path, String body, String bearer) throws Exception {
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }
}
