package com.opencrm.rules.drools.helpers;

import com.opencrm.rules.drools.DroolsCompilationMessage;
import com.opencrm.rules.drools.DroolsCompilationResult;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Source {@code DroolsServiceImpl} compile path: {@code KieFileSystem} + {@code KieBuilder.buildAll()}.
 * No kmodule XML — Drools 8.44 in this reactor does not ship {@code drools-xml-support}
 * (that stays a T1-5 concern with the 10.2.0 bump).
 */
@Slf4j
public final class KieFileSystemCompiler {

    private KieFileSystemCompiler() {
    }

    public static DroolsCompilationResult compile(List<DrlResource> resources) {
        List<DrlResource> safeResources = resources == null ? List.of() : resources;
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();

        for (DrlResource resource : safeResources) {
            kfs.write(resource.path(), resource.content());
        }

        try {
            KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
            kieBuilder.buildAll();
            Results results = kieBuilder.getResults();

            List<DroolsCompilationMessage> errors = mapMessages(results, Message.Level.ERROR);
            List<DroolsCompilationMessage> warnings = mapMessages(results, Message.Level.WARNING);
            List<DroolsCompilationMessage> infos = mapMessages(results, Message.Level.INFO);

            if (!errors.isEmpty()) {
                for (DroolsCompilationMessage error : errors) {
                    log.error("Rule compilation error: {}", error.format());
                }
                return DroolsCompilationResult.builder()
                        .valid(false)
                        .errors(errors)
                        .warnings(warnings)
                        .infos(infos)
                        .build();
            }

            KieContainer container = kieServices.newKieContainer(kieBuilder.getKieModule().getReleaseId());
            return DroolsCompilationResult.builder()
                    .valid(true)
                    .errors(Collections.emptyList())
                    .warnings(warnings)
                    .infos(infos)
                    .kieBase(container.getKieBase())
                    .build();
        } catch (Exception e) {
            log.error("Error compiling rules: {}", e.getMessage(), e);
            return DroolsCompilationResult.builder()
                    .valid(false)
                    .errors(List.of(DroolsCompilationMessage.builder()
                            .code("COMPILE")
                            .message(e.getMessage() == null ? "Failed to compile rules" : e.getMessage())
                            .severity(DroolsCompilationMessage.Severity.ERROR)
                            .build()))
                    .warnings(Collections.emptyList())
                    .infos(Collections.emptyList())
                    .build();
        }
    }

    private static List<DroolsCompilationMessage> mapMessages(Results results, Message.Level level) {
        List<DroolsCompilationMessage> mapped = new ArrayList<>();
        for (Message message : results.getMessages(level)) {
            mapped.add(DroolsCompilationMessage.builder()
                    .code("COMPILE")
                    .message(message.getText())
                    .severity(toSeverity(level))
                    .line(message.getLine() >= 0 ? message.getLine() : null)
                    .column(message.getColumn() >= 0 ? message.getColumn() : null)
                    .path(message.getPath())
                    .build());
        }
        return mapped;
    }

    private static DroolsCompilationMessage.Severity toSeverity(Message.Level level) {
        return switch (level) {
            case ERROR -> DroolsCompilationMessage.Severity.ERROR;
            case WARNING -> DroolsCompilationMessage.Severity.WARNING;
            default -> DroolsCompilationMessage.Severity.INFO;
        };
    }
}
