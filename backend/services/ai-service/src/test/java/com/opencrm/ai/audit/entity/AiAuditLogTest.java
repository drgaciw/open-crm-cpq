package com.opencrm.ai.audit.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AiAuditLogTest {
    @Test void missingModelIsUnknownAndExplicitModelIsPreserved() {
        var absent = new AiAuditLog();
        absent.prePersist();
        assertThat(absent.getModel()).isEqualTo("unknown");
        var explicit = new AiAuditLog();
        explicit.setModel("provider/model-version");
        explicit.prePersist();
        assertThat(explicit.getModel()).isEqualTo("provider/model-version");
    }
}
