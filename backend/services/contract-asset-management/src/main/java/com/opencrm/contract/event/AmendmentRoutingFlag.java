package com.opencrm.contract.event;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Per-tenant document-path flag. The property defaults empty, so the code default is OFF.
 * {@code QuoteAcceptedEvent} carries no tenant, so {@link #decide} is always {@link AmendmentRoutingDecision#LEGACY}.
 *
 * <p>Alert condition {@code document path selected}: {@link #DOCUMENT_PATH_SELECTED} must stay at zero.
 * No p95 target is stated for this step.
 */
@Component
public class AmendmentRoutingFlag {

    public static final String PROPERTY = "opencrm.amendment.routing.document-path-tenants";

    /** Incremented once for each acceptance routed to the legacy apply. */
    public static final String ROUTED_LEGACY = "opencrm.amendment.routing.routed_legacy";

    /**
     * Alert condition "document path selected": this counter must stay at zero. This story has no document path,
     * so the counter is registered and never incremented.
     */
    public static final String DOCUMENT_PATH_SELECTED = "opencrm.amendment.routing.document_path_selected";

    private final List<String> documentPathTenants;

    public AmendmentRoutingFlag(@Value("${opencrm.amendment.routing.document-path-tenants:}") String raw) {
        this.documentPathTenants = parse(raw);
    }

    /** Code default: no tenant is on the document path. */
    public static AmendmentRoutingFlag off() {
        return new AmendmentRoutingFlag("");
    }

    /** Test and explicit wiring helper. An empty or blank value is still OFF. */
    public static AmendmentRoutingFlag forTenants(String csv) {
        return new AmendmentRoutingFlag(csv == null ? "" : csv);
    }

    public List<String> documentPathTenants() {
        return documentPathTenants;
    }

    /** Blank or unknown tenants are not on the document path. */
    public boolean isDocumentPathEnabledFor(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || documentPathTenants.isEmpty()) {
            return false;
        }
        return documentPathTenants.contains(tenantId);
    }

    /**
     * Flag off, or no event tenant, selects the legacy path. This event has no tenant, so the result is legacy
     * whether or not {@code amendmentDocumentId} is present. The id is not parsed and no document is loaded.
     */
    public AmendmentRoutingDecision decide(QuoteAcceptedEvent event) {
        Objects.requireNonNull(event, "event");
        return AmendmentRoutingDecision.LEGACY;
    }

    private static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
    }
}
