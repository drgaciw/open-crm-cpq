package com.opencrm.rules.drools.helpers;

import com.opencrm.rules.drools.DroolsCompilationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KieFileSystemCompilerTest {

    @Test
    void compile_EmptyResourceListSucceeds() {
        DroolsCompilationResult result = KieFileSystemCompiler.compile(List.of());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getKieBase()).isNotNull();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void compile_SurfacesLineDiagnosticsForInvalidDrl() {
        DrlResource resource = new DrlResource("src/main/resources/rule_bad_0.drl", """
                package com.opencrm.rules.test;
                rule "Broken"
                when
                    not valid drools
                then
                end
                """);

        DroolsCompilationResult result = KieFileSystemCompiler.compile(List.of(resource));

        assertThat(result.isValid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.joinedErrors()).isNotBlank();
        assertThat(result.getErrors().get(0).getCode()).isEqualTo("COMPILE");
    }
}
