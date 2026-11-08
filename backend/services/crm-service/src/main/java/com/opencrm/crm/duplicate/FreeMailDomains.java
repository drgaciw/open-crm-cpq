package com.opencrm.crm.duplicate;

import java.util.List;
import java.util.Set;

/**
 * Consumer mailbox domains. They never imply an organization and never count as a same-company signal.
 * The set is fixed in code. There is no property override.
 */
public final class FreeMailDomains {

    public static final List<String> DOMAINS = List.of(
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

    private static final Set<String> DOMAIN_SET = Set.copyOf(DOMAINS);

    private FreeMailDomains() {
    }

    public static List<String> domains() {
        return DOMAINS;
    }

    /**
     * True when the normalized domain equals a free-mail domain or is a subdomain of one.
     * {@code mail.yahoo.com} matches. {@code notgmail.com} and {@code gmail.company.com} do not.
     */
    public static boolean isFreeMail(String domain) {
        String normalized = DomainNormalizer.normalizeDomain(domain);
        if (normalized == null) {
            return false;
        }
        String current = normalized;
        while (!current.isEmpty()) {
            if (DOMAIN_SET.contains(current)) {
                return true;
            }
            int dot = current.indexOf('.');
            if (dot < 0 || dot == current.length() - 1) {
                return false;
            }
            current = current.substring(dot + 1);
        }
        return false;
    }
}
