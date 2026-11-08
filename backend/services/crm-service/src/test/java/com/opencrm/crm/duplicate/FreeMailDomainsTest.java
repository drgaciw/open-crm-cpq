package com.opencrm.crm.duplicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the DRG-517 free-mail list. The expected domains are copied here; they are not read back from production.
 */
@DisplayName("DRG-517 free-mail domains")
class FreeMailDomainsTest {

    static final List<String> BRIEF = List.of(
            "gmail.com",
            "googlemail.com",
            "outlook.com",
            "hotmail.com",
            "hotmail.co.uk",
            "live.com",
            "msn.com",
            "passport.com",
            "yahoo.com",
            "yahoo.co.uk",
            "ymail.com",
            "rocketmail.com",
            "icloud.com",
            "me.com",
            "mac.com",
            "aol.com",
            "aim.com",
            "proton.me",
            "protonmail.com",
            "pm.me",
            "gmx.com",
            "gmx.net",
            "gmx.de",
            "mail.com",
            "yandex.com",
            "yandex.ru",
            "zoho.com",
            "zohomail.com",
            "fastmail.com",
            "hey.com",
            "tutanota.com",
            "tuta.io",
            "qq.com",
            "163.com",
            "126.com",
            "mail.ru",
            "web.de",
            "comcast.net",
            "verizon.net",
            "att.net",
            "sbcglobal.net"
    );

    static Stream<String> briefList() {
        return BRIEF.stream();
    }

    @Test
    @DisplayName("AC2: freeMailListMatchesBriefExactly")
    void freeMailListMatchesBriefExactly() {
        assertThat(BRIEF).hasSize(41);
        assertThat(FreeMailDomains.domains()).containsExactlyElementsOf(BRIEF);
    }

    @ParameterizedTest
    @MethodSource("briefList")
    @DisplayName("AC2: everyBriefFreeMailDomainIsExcluded")
    void everyBriefFreeMailDomainIsExcluded(String domain) {
        assertThat(FreeMailDomains.isFreeMail(domain)).isTrue();
        assertThat(FreeMailDomains.isFreeMail(domain.toUpperCase())).isTrue();
    }

    @Test
    @DisplayName("AC2: mailYahooComIsFreeMail")
    void mailYahooComIsFreeMail() {
        assertThat(FreeMailDomains.isFreeMail("mail.yahoo.com")).isTrue();
        assertThat(FreeMailDomains.isFreeMail("https://mail.yahoo.com/inbox")).isTrue();
    }

    @Test
    @DisplayName("AC2: notgmailComIsNotFreeMail")
    void notgmailComIsNotFreeMail() {
        assertThat(FreeMailDomains.isFreeMail("notgmail.com")).isFalse();
    }

    @Test
    @DisplayName("AC2: gmailCompanyComIsNotFreeMail")
    void gmailCompanyComIsNotFreeMail() {
        assertThat(FreeMailDomains.isFreeMail("gmail.company.com")).isFalse();
        assertThat(FreeMailDomains.isFreeMail("gmail.com.evil.com")).isFalse();
        assertThat(FreeMailDomains.isFreeMail(null)).isFalse();
        assertThat(FreeMailDomains.isFreeMail("   ")).isFalse();
    }
}
