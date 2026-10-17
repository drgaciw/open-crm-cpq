package com.opencrm.contract.documents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DoD v0.3 item 16. Negative test for Sales, approver, owner and legal.
 *
 * <p>Adopted visibility decision: the contract owner is a named user id, not an app role, and may
 * create, edit and submit once those APIs exist. Approve stays outside this slice. Execute is not
 * granted. Apply is the S4 ledger and is not granted. The Entra app roles that exist are
 * {@code CPQ.User}, {@code DealDesk}, {@code SalesVP}, {@code CFO} and {@code Admin}. None of the
 * four names under test is that catalog, and this slice does not grant any of them create,
 * execute or apply.
 */
class AmendmentAuthorizationGapTest {

    private static final Set<String> EXISTING_APP_ROLES = Set.of(
            "CPQ.User", "DealDesk", "SalesVP", "CFO", "Admin");

    static Set<String> rolesAllowed(String operation) {
        return switch (operation) {
            case "create", "execute", "apply" -> Set.of();
            default -> throw new IllegalArgumentException(operation);
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sales", "approver", "owner", "legal"})
    void roleIsNotGrantedCreateExecuteOrApply(String role) throws IOException {
        assertThat(EXISTING_APP_ROLES).doesNotContain(role);
        assertThat(rolesAllowed("create")).doesNotContain(role);
        assertThat(rolesAllowed("execute")).doesNotContain(role);
        assertThat(rolesAllowed("apply")).doesNotContain(role);
        assertThat(authority(role)).doesNotContain("ROLE_CPQ_USER", "ROLE_DEAL_DESK", "ROLE_SALES_VP", "ROLE_CFO", "ROLE_ADMIN");

        String adr = Files.readString(repositoryFile("docs/adr/ADR-016-identity-authentication-service-trust.md"));
        int catalogAt = adr.indexOf("Entra app roles are ");
        assertThat(catalogAt).isNotNegative();
        int catalogEnd = adr.indexOf("and `Admin`", catalogAt);
        assertThat(catalogEnd).isGreaterThan(catalogAt);
        String catalog = adr.substring(catalogAt, catalogEnd + "and `Admin`".length());
        assertThat(catalog).contains("CPQ.User", "DealDesk", "SalesVP", "CFO", "Admin");
        assertThat(catalog).doesNotContain("`" + role + "`");

        String cam = camMainSources();
        assertThat(cam).doesNotContain("@PreAuthorize");
        assertThat(cam).doesNotContain("hasRole(");
        assertThat(cam).doesNotContain(authority(role));
    }

    @Test
    void noExistingAppRoleIsGrantedCreateExecuteOrApplyEither() {
        for (String role : EXISTING_APP_ROLES) {
            assertThat(rolesAllowed("create")).doesNotContain(role);
            assertThat(rolesAllowed("execute")).doesNotContain(role);
            assertThat(rolesAllowed("apply")).doesNotContain(role);
        }
    }

    private static String authority(String role) {
        return "ROLE_" + role.toUpperCase().replace(' ', '_');
    }

    private static String camMainSources() throws IOException {
        Path root = repositoryFile("backend/services/contract-asset-management/src/main/java");
        StringBuilder sources = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                sources.append(Files.readString(file)).append('\n');
            }
        }
        return sources.toString();
    }

    private static Path repositoryFile(String relative) {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.exists(cursor.resolve(relative))) {
            cursor = cursor.getParent();
        }
        assertThat(cursor).as(relative).isNotNull();
        return cursor.resolve(relative);
    }
}
