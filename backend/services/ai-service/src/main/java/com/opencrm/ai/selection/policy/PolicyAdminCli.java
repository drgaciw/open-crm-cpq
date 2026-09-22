package com.opencrm.ai.selection.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.selection.SelectionTypes.*;
import java.nio.file.Path;
import java.time.Instant;

/** Offline policy operations; no credentials and no provider calls. */
public final class PolicyAdminCli {
    private PolicyAdminCli() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException(
                "Usage: PolicyAdminCli <save|activate|rollback|revoke> <policy-directory> <profile.json> <catalog.json> <policy-file|version>");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        TaskProfile profile = mapper.readValue(Path.of(args[2]).toFile(), TaskProfile.class);
        CatalogSnapshot catalog = mapper.readValue(Path.of(args[3]).toFile(), CatalogSnapshot.class);
        FilePolicyRepository repository = new FilePolicyRepository(Path.of(args[1]), mapper);
        switch (args[0]) {
            case "save" -> repository.saveProposal(mapper.readValue(Path.of(args[4]).toFile(), SelectionPolicy.class));
            case "activate" -> repository.activate(args[4], p -> PolicyEligibility.valid(p, profile, catalog, Instant.now()));
            case "rollback" -> repository.rollback(profile.id(), p -> PolicyEligibility.valid(p, profile, catalog, Instant.now()));
            case "revoke" -> repository.revoke(args[4]);
            default -> throw new IllegalArgumentException("Unknown policy operation");
        }
        System.out.println("Policy operation completed: " + args[0]);
    }
}
