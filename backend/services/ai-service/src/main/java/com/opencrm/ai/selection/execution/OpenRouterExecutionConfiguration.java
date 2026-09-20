package com.opencrm.ai.selection.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.selection.SelectionTypes.*;
import com.opencrm.ai.selection.execution.ExecutionTypes.Unavailable;
import com.opencrm.ai.selection.policy.FilePolicyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

/** Explicit opt-in requires approved files, an activated policy and a server-side credential. */
@Configuration
@ConditionalOnProperty(name = "opencrm.ai.selection.execution-enabled", havingValue = "true")
public class OpenRouterExecutionConfiguration {
    /** Legacy direct-client workflows have no approved selection profile or policy bridge. */
    @Bean
    @Primary
    UnavailableLegacyChatClient unavailableLegacyChatClient() {
        return new UnavailableLegacyChatClient();
    }

    @Bean
    @Primary
    OpenRouterLlmAdapter openRouterLlmAdapter(
            @Value("${opencrm.ai.selection.profile-file}") String profileFile,
            @Value("${opencrm.ai.selection.catalog-file}") String catalogFile,
            @Value("${opencrm.ai.selection.policy-directory}") String policyDirectory,
            @Value("${opencrm.ai.selection.audit-file}") String auditFile,
            @Value("${opencrm.ai.selection.api-key}") String key,
            @Value("${opencrm.ai.selection.completions-url:https://openrouter.ai/api/v1/chat/completions}") URI uri,
            @Value("${opencrm.ai.selection.timeout-seconds:30}") int timeoutSeconds,
            @Value("${opencrm.ai.selection.max-attempts:1}") int maxAttempts) throws IOException {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        FilePolicyRepository policies = new FilePolicyRepository(Path.of(policyDirectory), mapper);
        OpenRouterExecutionClient client = new OpenRouterExecutionClient(uri, key, Duration.ofSeconds(timeoutSeconds), mapper);
        return new OpenRouterLlmAdapter(new PolicyExecutor(policies,
                () -> read(profileFile, TaskProfile.class, mapper), () -> read(catalogFile, CatalogSnapshot.class, mapper),
                client, Clock.systemUTC(), new ExecutionAuditFile(Path.of(auditFile), mapper), maxAttempts));
    }

    private <T> T read(String file, Class<T> type, ObjectMapper mapper) {
        try { return mapper.readValue(Path.of(file).toFile(), type); }
        catch (IOException | IllegalArgumentException e) { throw new Unavailable("RUNTIME_EVIDENCE_UNAVAILABLE"); }
    }
}
