package com.opencrm.pricebook;

import com.opencrm.pricebook.config.GlobalExceptionHandler;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C16 for the price-book PUT clear flags. Ordinary field updates stay open to any authenticated caller.
 * Admin is the only role that may send {@code clearRegion} or {@code clearExpirationDate}.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PriceBookUpdateSecurityTest.App.class)
@TestPropertySource(properties = "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
@Tag("unit")
class PriceBookUpdateSecurityTest {

    private static final String PATH = "/api/v1/price-books/42";
    private static final String CLEAR = """
            {"clearRegion":true,"clearExpirationDate":true}
            """;
    private static final String RENAME = """
            {"name":"Renamed"}
            """;

    private static final PriceBookService BOOKS = mock(PriceBookService.class);

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ImportAutoConfiguration(OpenCrmSecurityAutoConfiguration.class)
    @Import({TestJwtDecoderConfiguration.class, GlobalExceptionHandler.class, PriceBookController.class})
    static class App {
        @Bean
        PriceBookService priceBookService() {
            return BOOKS;
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
        reset(BOOKS);
        when(BOOKS.update(any(), any())).thenReturn(PriceBookDto.builder().id(42L).name("Draft").build());
    }

    @Test
    void anonymousPriceBookUpdateIs401() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(CLEAR))
                .andExpect(status().isUnauthorized());
        verify(BOOKS, never()).update(any(), any());
    }

    @ParameterizedTest(name = "{0} clear flags are 403")
    @ValueSource(strings = {"CPQ.User", "DealDesk", "SalesVP", "CFO", "InstalledBase.Read", "NotARealRole"})
    void refusedRoleClearIs403(String role) throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(CLEAR)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", role).bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PRICE_BOOK_CLEAR_FORBIDDEN"));
        verify(BOOKS, never()).update(any(), any());
    }

    @Test
    void cpqUserClearRegionIs403() throws Exception {
        refusedRoleClearIs403("CPQ.User");
    }

    @Test
    void rolelessClearIs403() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(CLEAR)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice").bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PRICE_BOOK_CLEAR_FORBIDDEN"));
        verify(BOOKS, never()).update(any(), any());
    }

    @Test
    void internalInvokeClearIs403() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(CLEAR)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.service("pricebook", "Internal.Invoke").bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PRICE_BOOK_CLEAR_FORBIDDEN"));
        verify(BOOKS, never()).update(any(), any());
    }

    @Test
    void adminClearSucceeds() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(CLEAR)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("ada", "Admin").bearer()))
                .andExpect(status().isOk());
        verify(BOOKS).update(any(), any());
    }

    @Test
    void cpqUserRenameWithoutClearFlagsSucceeds() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(RENAME)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.user("alice", "CPQ.User").bearer()))
                .andExpect(status().isOk());
        verify(BOOKS).update(any(), any());
    }
}
