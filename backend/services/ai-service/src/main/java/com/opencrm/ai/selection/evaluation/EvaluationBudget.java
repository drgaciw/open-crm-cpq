package com.opencrm.ai.selection.evaluation;

import java.math.BigDecimal;

/** A run-scoped conservative ledger: reserve a maximum before every network attempt. */
public final class EvaluationBudget {
    public static final class Exhausted extends RuntimeException {
        public Exhausted() { super("Evaluation call or spend ceiling reached"); }
    }
    private final int maxCalls;
    private final BigDecimal ceiling;
    private int calls;
    private BigDecimal spent = BigDecimal.ZERO;
    private boolean breached;

    public EvaluationBudget(int maxCalls, BigDecimal ceiling) {
        if (maxCalls < 1 || ceiling == null || ceiling.signum() < 0)
            throw new IllegalArgumentException("Explicit call and spend ceilings required");
        this.maxCalls = maxCalls;
        this.ceiling = ceiling;
    }
    public synchronized Reservation reserve(BigDecimal maximum) {
        if (maximum == null || maximum.signum() < 0) throw new IllegalArgumentException("Unknown attempt cost");
        if (breached || calls >= maxCalls || spent.add(maximum).compareTo(ceiling) > 0) throw new Exhausted();
        calls++;
        spent = spent.add(maximum);
        return new Reservation(maximum);
    }
    public synchronized BigDecimal spent() { return spent; }
    public synchronized int calls() { return calls; }
    public synchronized boolean breached() { return breached; }

    public final class Reservation {
        private final BigDecimal reserved;
        private boolean reconciled;
        private Reservation(BigDecimal reserved) { this.reserved = reserved; }
        /** Unknown billing keeps the reservation, including timeout/error responses. */
        public void reconcile(BigDecimal actual) {
            synchronized (EvaluationBudget.this) {
                if (reconciled) throw new IllegalStateException("Already reconciled");
                reconciled = true;
                if (actual == null) return;
                if (actual.signum() < 0) throw new IllegalArgumentException("Negative billed cost");
                spent = spent.subtract(reserved).add(actual);
                if (actual.compareTo(reserved) > 0 || spent.compareTo(ceiling) > 0) breached = true;
            }
        }
    }
}
