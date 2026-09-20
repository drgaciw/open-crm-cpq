package com.opencrm.quote.snapshot;
import com.opencrm.quote.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@ExtendWith(SpringExtension.class) @WebAppConfiguration @ActiveProfiles("dev")
@ContextConfiguration(classes=SnapshotSecurityConfigTest.App.class)
class SnapshotSecurityConfigTest {
    @Configuration @EnableWebMvc @Import({SecurityConfig.class, SnapshotSecurityConfig.class, Probe.class}) static class App {}
    @RestController static class Probe { @GetMapping("/internal/oms/test") String probe() { return "secret"; } }
    @Autowired WebApplicationContext context;
    @Test void missingIssuerFailsClosedEvenInDev() throws Exception {
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
            .perform(get("/internal/oms/test")).andExpect(status().isForbidden());
    }
}
