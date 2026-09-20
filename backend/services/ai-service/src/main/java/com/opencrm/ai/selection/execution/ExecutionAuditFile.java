package com.opencrm.ai.selection.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.selection.execution.ExecutionTypes.Audit;
import com.opencrm.ai.selection.execution.ExecutionTypes.Unavailable;
import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

/** Append-only metadata audit. Storage failure is explicit and never causes a provider replay. */
public final class ExecutionAuditFile implements Consumer<Audit> {
    private final Path path;
    private final ObjectMapper mapper;
    public ExecutionAuditFile(Path path, ObjectMapper mapper) throws IOException {
        this.path = path.toAbsolutePath(); this.mapper = mapper;
        Files.createDirectories(this.path.getParent());
    }
    @Override public synchronized void accept(Audit audit) {
        try {
            Files.writeString(path, mapper.writeValueAsString(audit) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) { throw new Unavailable("AUDIT_STORAGE_UNAVAILABLE"); }
    }
}
