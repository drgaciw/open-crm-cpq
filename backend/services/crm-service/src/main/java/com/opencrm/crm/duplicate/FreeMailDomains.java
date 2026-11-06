package com.opencrm.crm.duplicate;

import java.util.List;

/**
 * Consumer mailbox domains. RED stub: the pinned list and matcher land after the failing tests.
 */
public final class FreeMailDomains {

    private FreeMailDomains() {
    }

    public static List<String> domains() {
        return List.of();
    }

    public static boolean isFreeMail(String domain) {
        return false;
    }
}
