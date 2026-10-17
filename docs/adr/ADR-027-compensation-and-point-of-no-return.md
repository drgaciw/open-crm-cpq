# ADR-027: Compensation, point of no return and exception handling

**Status:** Accepted on 2026-10-17. The project owner told, as relayed by the Projects Manager bot on 2026-10-17 at 7:31 AM CT, "yes to accepting ADR-017, ADR-024 to 027 and DRG-533 (#644); authorized opening their status-change PRs". The owner's merge of this PR is the confirming act and the status takes effect on merge. This supersedes the "retain their existing statuses" wording of PRs #662 and #663 only for ADR-027.
**Date:** 2026-10-04
**Deciders:** Project owner (accepted).
**Relates to:** [ADR-025](ADR-025-saga-process-manager.md) (process managers), [ADR-024](ADR-024-order-orchestration-context.md) (order lifecycle and change orders), [ADR-026](ADR-026-command-messages.md) (commands), [ADR-016](ADR-016-identity-authentication-service-trust.md) (actor), [ADR-018](ADR-018-event-delivery-guarantees-idempotency.md) (DLT tooling). Backlog: SAGA-02, SAGA-03, BC-08-03, BC-08-04, BC-15-03.
**Does not authorize:** any code.

---

## Context

The backlog assumes every saga step can be undone. SAGA-02's acceptance criterion is that failing any step leaves "no orphaned contracts, subscriptions or schedules". Some fulfillment steps cannot be undone:

- a licence key already emailed;
- a shipment already sent;
- a posting in the ERP, once a real adapter replaces the simulated `ErpAdapter`.

The older Temporal plan says the opposite of SAGA-02 for ERP failures: retry for up to 72 hours, then route to a human exception queue "without auto-rollback" (`cpq-microservice-ddd-analysis.xml:132`). Both cannot be the rule.

BC-15-03 says `LicenseRequired` "pauses the fulfillment saga", but no story defines what a pause is or what resumes it. BC-08-04 allows cancelling and changing orders without saying until when.

What exists to build on: ADR-018's dead-letter topics and the replay and skip tool (`DeadLetterTool.java:23-24`), event actor headers (ADR-016 step 4), and the BC-20 audit stories in Phase 0.

## Decision

### D-01 — Every step declares a class

| Class | Meaning | On failure |
|---|---|---|
| Compensatable | Has an undo command that restores the prior business state | Retried; if the deadline passes, the current step and the earlier steps are compensated, in reverse order |
| Pivot | After it succeeds, the saga only moves forward | Retried until success or deadline; then parked |
| Retriable | Idempotent and must eventually succeed; follows a pivot | Retried until success or deadline; then parked |

A saga-definition test rejects any step without a declared class ([ADR-025](ADR-025-saga-process-manager.md) fitness functions).

**A timed-out step is compensated too.** A command whose reply was lost may still have applied its effect, so compensating only the earlier steps could leave the current one orphaned. Every compensation must therefore succeed as a no-op when the step it undoes never took effect. The handler checks the business state, for example whether a subscription exists for that order line, rather than its own history.

### D-02 — Point of no return, per order line

A line passes its point of no return when its first fulfillment request in a non-compensatable class is dispatched. Examples: an ERP sales order posted, a licence issued, a shipment released.

- **Before** it, `CancelOrder` compensates that line's completed steps, and an in-flight revision may change the line.
- **After** it, the line can only change through a new order acting on what was delivered ([ADR-024](ADR-024-order-orchestration-context.md) D-07).

The point of no return is recorded on the line with the step that crossed it.

It is crossed on **dispatch**, not on success. Once a non-compensatable request has left, its external effect may happen even if the reply is lost, so the line cannot be treated as undoable. A pivot's success, by contrast, is what commits the saga to moving forward (D-01).

### D-03 — Retries and deadlines

Each step has a bounded exponential backoff with jitter and a deadline. Deadlines are configured per target, not in code. The 72-hour ERP figure in the Temporal plan is a starting value for that target, not a platform rule.

### D-04 — Parked sagas and the exception queue

When a pivot or retriable step exhausts its deadline, or a compensation itself fails, the saga moves to `NEEDS_ATTENTION` and stays there. Nothing is rolled back automatically past a pivot.

- An operations API under `/api/v1/**`, restricted to the existing `Admin` app role until a dedicated operations role is added to `infra/entra/opencrm-identity.json` (O-1), offers three actions on a parked saga: retry the step, mark the step done with evidence, or compensate the steps that still can be. It is not under `/internal/**`, which accepts only service tokens: ADR-016 routes any call that must carry a person's identity through `/api/v1/**`.
- Every action records the actor (ADR-016 `user:<oid>`) and a reason in `saga_step_log`, and in the BC-20 audit trail once BC-20 exists ([ADR-025](ADR-025-saga-process-manager.md) O-2).
- A parked saga older than a configurable age raises an alert (SAGA-03).

### D-05 — Compensations are commands too

A compensation is a command with its own replies ([ADR-026](ADR-026-command-messages.md)). It is idempotent and retried like any step. A compensation that cannot complete parks the saga under D-04; it is never dropped.

### D-06 — A hold is not a failure

`ON_HOLD` ([ADR-024](ADR-024-order-orchestration-context.md) D-05) pauses the saga. It does not consume retries, does not move deadlines toward expiry, and does not compensate anything. Releasing the hold resumes the step that was waiting. Export licence (BC-15-03), credit and unmapped products (BC-08-02) are holds.

### D-07 — SAGA-02 and the epic exit criteria change

SAGA-02's "no orphaned contracts, subscriptions or schedules remain", and the matching exit criteria on DRG-289 ("tested compensation for every step") and DRG-290 ("compensations tested for each step"), become:

- every compensatable step is proven, by fault injection in CI, to be undone when a later step fails before the pivot;
- every pivot and retriable step is proven to park the saga in `NEEDS_ATTENTION` with no automatic rollback;
- no saga ends in a state that is neither completed, compensated nor parked.

## Options considered

| Option | Verdict |
|---|---|
| **A. Classified steps, per-line point of no return, parking (chosen)** | Matches what can actually be undone; a person decides only the cases that need one |
| B. Compensate everything always | Impossible for irreversible steps; would hide failures behind compensations that cannot work |
| C. Never compensate; park every failure | Safe but noisy; leaves reversible mistakes for a person |

## Consequences

- **Positive:** SAGA-02 becomes testable and honest. Cancellation has a precise cut-off that sales and support can explain.
- **Negative:** Someone has to own the exception queue. Each new fulfillment target must declare its class and compensation before it ships.

## Fitness functions (on acceptance)

- A saga-definition test fails on any step without a class, and on a compensatable step without a compensation command.
- SAGA-02 fault-injection tests as stated in D-07.
- A test fails if a manual action on a parked saga writes no audit record with an actor.

## Open questions

- O-1: Who owns the exception queue operationally, the alert age, and whether a dedicated operations app role replaces `Admin` for these actions.
- O-2: Whether cancellation after the point of no return carries a fee priced by BC-03, and who approves it under BC-04.
