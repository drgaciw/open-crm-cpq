package com.opencrm.ai.selection.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit synthetic evaluation artifact, separate from default application logs. Never activates a policy. */
public final class EvaluationReplayStore {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public void save(Path path, EvaluationHarness.Report report) throws IOException {
        if (path.toAbsolutePath().getParent() != null) Files.createDirectories(path.toAbsolutePath().getParent());
        // Do not silently replace previous evaluation evidence.
        Files.writeString(path, json.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                java.nio.file.StandardOpenOption.CREATE_NEW);
    }
    public EvaluationHarness.Report load(Path path) throws IOException {
        return json.readValue(Files.readString(path), EvaluationHarness.Report.class);
    }
}
