package com.opencrm.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencrm.jev.answer.JevAnswer;
import com.opencrm.jev.answer.JevAnswerValidator;
import com.opencrm.jev.answer.JevConfidence;
import com.opencrm.jev.error.JevException;
import com.opencrm.jev.question.JevQuestion;
import com.opencrm.jev.state.JevState;
import com.opencrm.jev.transport.JevTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for advisory decisions. {@link #decide} never throws and never blocks on a disabled
 * configuration; every failure becomes a {@link JevResult.Status}.
 */
public class JevClient {

    private static final Logger log = LoggerFactory.getLogger(JevClient.class);

    private final JevProperties properties;
    private final JevTransport transport;
    private final DailyCallBudget budget;
    private final Clock clock;

    public JevClient(JevProperties properties, JevTransport transport) {
        this(properties, transport, Clock.systemUTC());
    }

    public JevClient(JevProperties properties, JevTransport transport, Clock clock) {
        this.properties = properties;
        this.transport = transport;
        this.clock = clock;
        this.budget = new DailyCallBudget(properties.getDailyCallBudget(), clock);
    }

    /** True only when the global switch, the use case switch and a real transport are all on. */
    public boolean active(String useCase) {
        JevProperties.UseCase settings = properties.useCase(useCase);
        return properties.isEnabled() && transport.enabled() && settings != null && settings.isEnabled();
    }

    public JevResult decide(String useCase, JevState state, List<JevQuestion> questions) {
        JevProperties.UseCase settings = properties.useCase(useCase);
        String rubric = settings == null ? null : settings.getRubricVersion();
        if (!active(useCase)) {
            return result(JevResult.Status.DISABLED, Map.of(), 0, 0, null, null, rubric);
        }
        if (!budget.tryAcquire()) {
            log.warn("Jev daily call budget exhausted; dropping {} evaluation", useCase);
            return result(JevResult.Status.BUDGET, Map.of(), 0, 0, null, null, rubric);
        }
        long startedAt = clock.millis();
        try {
            JevTransport.Response response = transport.decide(
                    new JevTransport.Request(useCase, properties.getModel(), state, questions));
            Map<String, JevAnswer> answers = new LinkedHashMap<>();
            for (JevQuestion question : questions) {
                answers.put(question.id(),
                        JevAnswerValidator.validate(response.answers().get(question.id()), question));
            }
            double confidence = JevConfidence.aggregate(answers.values());
            JevResult.Status status = confidence < settings.getMinConfidence()
                    ? JevResult.Status.ABSTAINED
                    : JevResult.Status.OK;
            return new JevResult(status, answers, confidence, clock.millis() - startedAt,
                    response.attempts(), response.usage(), null, properties.getModel(), rubric,
                    transport.name(), transport.wireFormat());
        } catch (JevException.InvalidAnswer e) {
            return failed(JevResult.Status.INVALID, e, startedAt, useCase, rubric);
        } catch (JevException.Timeout e) {
            return failed(JevResult.Status.TIMEOUT, e, startedAt, useCase, rubric);
        } catch (Throwable e) {
            // Deliberately broad: an advisory evaluation must not be able to fail its caller.
            return failed(JevResult.Status.ERROR, e, startedAt, useCase, rubric);
        }
    }

    private JevResult failed(JevResult.Status status, Throwable e, long startedAt, String useCase,
                             String rubric) {
        log.warn("Jev {} evaluation ended {}: {}", useCase, status, e.getMessage());
        return result(status, Map.of(), clock.millis() - startedAt, 0, null,
                e.getClass().getSimpleName(), rubric);
    }

    private JevResult result(JevResult.Status status, Map<String, JevAnswer> answers, long latencyMs,
                             int attempts, JsonNode usage, String errorClass, String rubric) {
        return new JevResult(status, answers, 0, latencyMs, attempts, usage, errorClass,
                properties.getModel(), rubric, transport.name(), transport.wireFormat());
    }
}
