# Dated ramp extension correction — DRG-635

Review of merged PR #750 found that extending a dated quote changed its duration
without changing its inclusive end date. The accepted event then contained an
inconsistent term, which the downstream anchored-calendar validator rejects.

For a growing term with a start date, extension now saves
`startDate.plusMonths(newDurationInMonths).minusDays(1)` in the same transaction as
the duration and schedule. The existing root lock/version check and invalidation
path still apply. Same-term catch-up preserves dates: a typed continuation may
inherit an original billing anchor that differs from its clipped start date.
Without a start date, extension preserves the existing optional dates.

The atomic extension response also returns its saved end date. The editor applies
that authoritative value before another header edit can send a stale end date
back. A legacy response that omits the field preserves the current client value;
an explicit null clears it.

This corrects standalone quote authoring. Typed continuation validation still
rebuilds its own plan and refuses changed continuation terms during calculation
and acceptance. This change does not add continuation-term extension support.
The commercial-audit follow-up DRG-716 remains separate and unresolved.

## Acceptance criteria and allowlist

- DATE-1: a growing dated term uses the original start-date anchor, including
  month-end and leap-day boundaries.
- DATE-2: same-term catch-up preserves inherited continuation dates, and a term
  without a start date retains its optional dates.
- DATE-3: PostgreSQL reload sees the new duration, dates and schedule together;
  a failure after SQL flush rolls all of them and the root version back.
- DATE-4: the saved end date reaches editor state and subsequent header edits;
  legacy omitted-field responses retain compatibility.
- CI-1: the CRM close-event integration fixture supplies the accepted-quote
  prerequisite for a Won transition, preserving its amount/time/event assertions
  and the production guard's rejection of a win without an accepted quote.

Exact allowlist:

```text
backend/services/crm-service/src/test/java/com/opencrm/crm/outbox/DealClosedEventTest.java
backend/services/quote-service/src/main/java/com/opencrm/quote/lineitem/QuoteLineItemService.java
backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampScheduleDto.java
backend/services/quote-service/src/main/java/com/opencrm/quote/ramp/RampScheduleQueryService.java
backend/services/quote-service/src/test/java/com/opencrm/quote/ramp/RampExtensionSaveTest.java
backend/services/quote-service/src/test/java/com/opencrm/quote/ramp/RampSchedulePersistenceIntegrationTest.java
frontend/libs/cpq/quotes/src/lib/quote.service.spec.ts
frontend/libs/cpq/quotes/src/lib/quote.service.ts
frontend/libs/shared/models/src/lib/quote.model.ts
docs/plans/ramp-extension-dated-term-review-20261101.md
```

Forbidden: held features, flags, migrations, workflows, proxy configuration,
security configuration, accepted event schema, continuation policy and downstream
asset/subscription implementation.

## Reproduction and review

Base: main at `cd9c23d9` in the isolated
`C:/Users/dgorn/cpq-wt/pr-review-dated-ramp-20261101` checkout.
Local GitNexus index was refreshed to that commit before impact analysis.
`persistExtendedRamp` impact is LOW: its two upstream callers are
`RampScheduleQueryService.extend` and `RampScheduleController.extend`; no indexed
process was returned. The event factory publishes the stored dates, and the
downstream validator checks the anchored term; these were also reviewed in source.

Before changing production code, `RampExtensionSaveTest` ran 16 cases with three
failures and no errors/skips. The failing cases retained the original end date
for January 1, January 31 and February 29 anchors. The failing source was base
`cd9c23d9` plus the captured test diff. Evidence lives in ignored local files
`.gitnexus/dated-ramp-red.log` and `.gitnexus/dated-ramp-red-test.patch`.

Before the client-state correction, 19 quote service tests ran with two failures
and 17 passes. A mocked HTTP extension result followed by a header rename/save
sent the old end date; an explicit null response also left the old date in state.
The local RED log and test diff are `.gitnexus/dated-ramp-fe-red.log` and
`.gitnexus/dated-ramp-fe-red-test.patch`. The initial focused GREEN database run
passed 17 unit tests plus all 13 PostgreSQL persistence tests, including repeated
extensions through February and the existing concurrency tests.

Final local frontend verification: clean `npm ci` on Node 22.22.3/npm 10.9.8,
154 quote tests, quote/shared-model lint (zero errors, 16 existing warnings),
and CRM production build all passed. The existing lockfile was unchanged;
installation reported 48 inherited dependency audit findings (33 moderate,
12 high, three critical). This correction does not remediate that baseline.
Independent review found no blocking issue in the final code diff.

The first full CI run, `41381833873` on `77353388`, failed in the existing CRM
`DealClosedEventTest.updateToWonFreezesEditedAmountAndPublishesThePersistedCloseTime`:
HTTP 200 was expected, but the accepted-quote win guard added by merged #762
correctly returned 400 `OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE`. Other matrix jobs
and the PR gate passed; the integration reactor stopped before quote-service.
The same class already supplies accepted-quote metadata in its standalone win
test. The bounded fixture repair supplies that prerequisite only for the Won
update case; production lifecycle behavior and all existing assertions remain.
The failure was reproduced locally on committed head `77353388` before the
fixture change: five cases, one failure, the same 400 guard error. The local RED
log and fixture diff are `.gitnexus/crm-closed-fixture-red.log` and
`.gitnexus/crm-closed-fixture-test.patch`.

Verification outcomes and exact-head CI belong in the PR body. Local logs are
not published CI artifacts. This correction has service/component and PostgreSQL
verification; browser UAT was not run. Tests of this fix do not establish gateway/production JWT/tenant, live pricing,
approval, outbox delivery or subscription acceptance end-to-end evidence.
