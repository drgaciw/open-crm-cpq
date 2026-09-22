package com.opencrm.ai.selection;

import static com.opencrm.ai.selection.SelectionTypes.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Offline dry-run only. Explicitly separate from runtime policy activation. */
public final class SelectionReplayCli {
    private SelectionReplayCli() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 7 || !(args[6].equals("complete") || args[6].equals("partial")))
            throw new IllegalArgumentException("Usage: SelectionReplayCli profile.json catalog.json evidence.json output.json evaluated-at policy-version complete|partial");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var profile = mapper.readValue(Path.of(args[0]).toFile(), TaskProfile.class);
        var catalog = mapper.readValue(Path.of(args[1]).toFile(), CatalogSnapshot.class);
        List<EvaluationEvidence> evidence = mapper.readValue(Path.of(args[2]).toFile(), new TypeReference<>() { });
        var report = new SelectionEngine().select(profile, catalog, evidence, Instant.parse(args[4]),
                args[5], null, args[6].equals("complete"));
        Path output = Path.of(args[3]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("Dry-run report: " + output + " (" + report.reason() + "; no policy activated)");
    }
}
