package com.opencrm.ai.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.audit.entity.AiAuditLog;
import com.opencrm.ai.audit.repository.AiAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentAuditPrivacyTest {
    @Test void routineLogsExcludePayloadWhileDurableAuditRetainsIt() {
        var repository = mock(AiAuditLogRepository.class);
        var service = new AgentAuditLogger(repository, new ObjectMapper(),
                new com.opencrm.security.CurrentActor("service:ai-service", false));
        Logger logger = (Logger) LoggerFactory.getLogger(AgentAuditLogger.class);
        var messages = new ListAppender<ILoggingEvent>();
        messages.start(); logger.addAppender(messages);
        try {
            service.logInvocation("agent", "session", Map.of("customerNote", "synthetic-private-payload"));
            assertThat(messages.list).allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain("synthetic-private-payload"));
            var record = ArgumentCaptor.forClass(AiAuditLog.class);
            verify(repository).save(record.capture());
            assertThat(record.getValue().getInputContext()).contains("synthetic-private-payload");
        } finally { logger.detachAppender(messages); messages.stop(); }
    }
}
