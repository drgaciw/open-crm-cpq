package com.opencrm.ai.ledger;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public final class LedgerTypes {
    private LedgerTypes() {}
    public enum Status { RUNNING, SUCCEEDED, FAILED }
    public record Run(UUID id, String tenantId, UUID taskId, Status status, String provider, String model,
                      long promptTokens, long completionTokens, long totalTokens, BigDecimal costUsd,
                      Instant startedAt, Instant finishedAt, Long latencyMs, String errorCode) {}
    public record Start(@NotNull UUID taskId, @NotBlank @Size(max=200) String provider,
                        @NotBlank @Size(max=200) String model) {}
    public record ActionInput(@NotBlank @Size(max=100) String kind, @NotBlank @Size(max=200) String tool,
                              @NotBlank @Size(max=200) String provider, @NotBlank @Size(max=200) String model,
                              @PositiveOrZero long promptTokens, @PositiveOrZero long completionTokens,
                              @NotNull @DecimalMin("0") @Digits(integer=12, fraction=12) BigDecimal inputUsdPerMillion,
                              @NotNull @DecimalMin("0") @Digits(integer=12, fraction=12) BigDecimal outputUsdPerMillion,
                              @PositiveOrZero long latencyMs) {
        public BigDecimal cost() {
            if (promptTokens < 0 || completionTokens < 0 || latencyMs < 0
                    || inputUsdPerMillion == null || outputUsdPerMillion == null
                    || inputUsdPerMillion.signum() < 0 || outputUsdPerMillion.signum() < 0)
                throw new IllegalArgumentException("invalid metering");
            Math.addExact(promptTokens, completionTokens);
            return inputUsdPerMillion.multiply(BigDecimal.valueOf(promptTokens))
                    .add(outputUsdPerMillion.multiply(BigDecimal.valueOf(completionTokens)))
                    .divide(BigDecimal.valueOf(1_000_000), 12, RoundingMode.HALF_UP);
        }
    }
    public record Action(UUID id, UUID runId, ActionInput usage, BigDecimal costUsd, Instant createdAt) {}
    public record Audit(UUID id, UUID runId, String eventType, String actor, Instant createdAt) {}
    public record Usage(long runs, long promptTokens, long completionTokens, BigDecimal costUsd) {}
    public record AuditInput(@NotBlank @Size(max=100) String eventType) {}
    public record Failure(@NotBlank @Pattern(regexp="[A-Z0-9_]{1,100}") String errorCode) {}
}
