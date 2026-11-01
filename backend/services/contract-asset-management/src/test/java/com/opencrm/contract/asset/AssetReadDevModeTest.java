package com.opencrm.contract.asset;

import com.opencrm.contract.config.GlobalExceptionHandler;
import com.opencrm.security.web.OpenCrmSecurityAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AssetReadDevModeTest.App.class)
@TestPropertySource(properties = "opencrm.security.mode=dev")
class AssetReadDevModeTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({AssetController.class, AssetQueryController.class, GlobalExceptionHandler.class})
    static class App {
        @Bean AssetQueryService query() { return new AssetQueryService(mock(AssetRepository.class), mock(AssetMapper.class)); }
        @Bean AssetHistoryProjection history() { return new AssetHistoryProjection(mock(AssetHistoryStore.class)); }
    }

    @Autowired WebApplicationContext context;

    @Test void devModePermitAllStillDeniesAnonymousAssetReads() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        mvc.perform(get("/api/v1/assets")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/1")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/a/history")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets/a/supersede-chain")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assets").param("accountId", "a").param("asOf", "2026-01-01"))
                .andExpect(status().isForbidden());
    }
}
