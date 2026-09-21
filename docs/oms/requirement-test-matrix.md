# OMS requirement-to-test matrix

Baseline and result limitations: [pilot evidence](pilot-evidence.md). Source:
[requirements](../requirements/order-management-requirements.md), narrowed by the
[authorized P0 wave](../forge/36-oms-pilot.md). **Mapped** means concrete automated coverage exists,
not that the entire requirement or deployment acceptance is complete. **Partial** names the
uncovered scope. **Not run** is not a pass. No new execution results are claimed by this document.

Test classes are under `backend/services/order-management/src/test/java/com/opencrm/order/`
unless prefixed `quote` (quote-service), `contract` (contract-asset-management), or `common`
(`backend/libs/common`). Angular specs are under `frontend/libs/cpq/orders/src/lib/`.
`OrderPostgresIntegrationTest` inherits the behavioral methods of `OrderServiceTest` against real
PostgreSQL; H2 execution alone does not prove those database guarantees.

## P0 functional requirements

| ID | Concrete automated coverage | Remaining scope / evidence boundary |
|---|---|---|
| OM-001 | quote `OmsAcceptancePathsTest.bothPathsPersistOwnershipSnapshotAndFenceLegacyEvent`; `OrderServiceTest.aliasesAndReplayHaveCanonicalIdentity` | Selected accepted-quote/manual pilot only; production cohort acceptance pending. |
| OM-002 | quote `AcceptedSnapshotIntegrationTest.atomicallyPersistsExactPayloadAliasAndImmutableDatabaseRecord`; `OrderServiceTest.snapshotHashAndVerbatimBytesArePreserved`, `incompleteCommercialFactsRemainVisibleAndHeld` | Verbatim snapshots and holds mapped; live master-system edit journey not established. |
| OM-003 | `OrderPostgresIntegrationTest` inherited `hundredConcurrentConversionsCreateOneOrderAndPlan`, `aliasesAndReplayHaveCanonicalIdentity`, `concurrentCrossOrderOperationKeyConflictRollsBackLoser`; quote `AcceptedSnapshotIntegrationTest.hundredConcurrentAcceptancesProduceOneOwnedSnapshot` | Local database concurrency, not sustained intake throughput. |
| OM-004 | `OrderServiceTest.missingSourceEvidenceCannotBeClearedByManualHoldRelease`, `unmappedLineIsVisibleHeldWithoutDefaultServiceTasks`; `ExecutionIntegrationTest.heldOrdersAndBlockedDependenciesCannotClaim` | Unsupported terms remain held/rejected; no invented source correction. Operational ownership of each hold needs acceptance. |
| OM-005 | `SnapshotValidatorTest.exactDecimalPasses`, `rejectsUsdPrecisionBeyondCents`; `FinanceIntegrationTest.requestIsPendingAndPartialActualAcknowledgmentsAllocateEveryCent` | USD cents only; no multicurrency/refund rounding claim. |
| OM-007 | `OrderServiceTest.submitHoldVersionAndBodyConflict`, `fullCancellationIsTerminalAndPlanCancelled`; `ExecutionIntegrationTest.evidenceMutationAndExecutionCompletionRollbackTogether` | Derived states and rollback mapped; no client completion shortcut. |
| OM-008 | `FulfillmentIntegrationTest.partialShipmentReplayAndCancellationConserveQuantity`, `repeatedEvidenceDoesNotIncrementQuantity`; `ExecutionIntegrationTest.partialEvidenceReleasesOnlyAvailableDownstreamQuantity` | Simple source lines only; bundle parent/child decomposition is P1. |
| OM-009 | `FulfillmentIntegrationTest.releasedAllocationReopensReadiness`, `unknownInFlightOperationBlocksCancellation`, `competingCancellationAndAllocationHaveOneVersionWinner`; `ExecutionIntegrationTest.holdBetweenClaimAndDispatchFencesExternalIntent` | Manual cutoffs mapped; actual provider cancellation/release acceptance pending. |
| OM-010 | `OrderServiceTest.hundredConcurrentConversionsCreateOneOrderAndPlan`, `unmappedLineIsVisibleHeldWithoutDefaultServiceTasks`; `FulfillmentIntegrationTest.serviceAndDigitalProofCompleteOnlyTheirMatchingTask` | Fixed approved templates, not a publishable mapping designer/version rollout. |
| OM-012 | `ExecutionIntegrationTest.expiredClaimIsReclaimedAndOldWorkerCannotCommit`, `unknownOutcomeNeverBlindlyRetries`, `deadlineRecoveryPreservesUnknownInsteadOfRedispatch`, `schedulerExhaustionAlwaysCreatesVisibleException` | Local crash/lease model; authoritative provider status lookup is a controlled manual procedure. |
| OM-015 | `FulfillmentIntegrationTest.allocationRequiresExplicitFutureExpiry`, `expiryRequiresManualReleaseAndCannotAuthorizeShipment`, `allocationAndPartialShipmentUseExactQuantities` | Controlled manual allocation; no atomic stock availability promise or inventory authority implementation. |
| OM-016 | `ExecutionIntegrationTest.ackWrongScopeOrAlteredBodyIsRejected`, `authoritativeNoEffectAllowsRetryWithSameStableOperationKey`; `MessagingTrustConfigurationTest`; `BrokerOutboxIntegrationTest` | Durable keys/inbox/fences mapped. Real authenticated broker/callback deployment remains NOT RUN. |
| OM-017 | `FulfillmentIntegrationTest.pendingDeliveryIsRetainedThenAppliedAfterShipment`, `deliveryCannotOvercountOneReferencedShipment`, `fullPhysicalEvidenceReleasesFinanceButDoesNotInventAcknowledgment` | Synthetic named manual references, not carrier/warehouse integration. |
| OM-018 | `ExecutionIntegrationTest.activationDispatchIsOnceAndAcknowledgmentReplayCompletesOnlyActivation`; contract `OmsActivationConsumerTest.concurrentReplayCreatesOneSubscriptionAndAcknowledgment`, `registryFencesBothLegacyIdentitiesAndIsImmutable`; `ActivationTermsTest` | Local line-level terms/ownership covered. Deployment-wide legacy fence and real activation acknowledgment path must be verified before intake. |
| OM-019 | `FinanceIntegrationTest.requestIsPendingAndPartialActualAcknowledgmentsAllocateEveryCent`, `unknownAndRejectedCannotCompleteOrRedispatch`, `multipleLinesKeepIndependentSourceEconomicsAndRecurringTerms` | Manual external acknowledgment contract, not an ERP or invoice-creation integration. |
| OM-023 | `RecoveryIntegrationTest.assignmentUsesExceptionVersionAndDurableReplay`, `assignOnlyPermissionCanAssignWithoutRecoveryPermission`, `waiverCannotCompleteOrUnblockUnknown`; `RecoveryHttpIntegrationTest` | Durable actionable queue mapped; deployment <=60-second visibility and named response ownership not measured. |
| OM-024 | `OrderSearchIntegrationTest.externalReferenceAndOpenExceptionFiltersAreAppliedWithinAccountScope`; Angular `orders.components.spec.ts` tests failed loads, filters, confirmation and uncertain outcomes; `orders.service.spec.ts` tests envelope pagination | Post-baseline live browser passed locally; final commit association pending. Returns view is P1; component mocks are not live acceptance. |
| OM-025 | `RecoveryIntegrationTest.dailyScanIncludesTerminalOrdersAndIsBoundedAndDurable`, `completedDailyWindowCatchesOrdersArrivingLaterWithoutRewritingHistory`, `financeReconciliationUsesCumulativeExactAcknowledgments`, `authoritativeAdjudicationRetainsFactsAndCannotCompleteWork` | Daily persisted local facts and manual authority references; no automatic remote reconciliation. Production daily completion/lag not demonstrated. |

## P0 nonfunctional requirements

| ID | Concrete automated coverage | Remaining gate |
|---|---|---|
| NFR-01 | `OrderAccessTest`, `OrderJwtDecoderTest`, `OrderHttpIntegrationTest.realHttpEnforcesJwtAndAccountScopesAndPersistsCommands`; `RecoveryIntegrationTest.exceptionPagesFilterAccountsBeforeLimit`, `dailyServiceScopeExcludesOtherDeployments`; quote `AcceptedSnapshotControllerTest` | Real IdP/gateway callback, SASL ACLs and runtime-role final integration pending. No shared multitenancy certification. |
| NFR-02 | quote `AcceptedSnapshotIntegrationTest.outboxFailureRollsBackOwnershipSnapshotAndAcceptedState`; `FinanceIntegrationTest.ledgerOutboxAndExecutorRollBackWhenAuditFails`; common `OutboxDeliveryStoreIntegrationTest`, `OutboxRelayPublisherTest`; `BrokerOutboxIntegrationTest` | At-least-once delivery plus business deduplication, not transport exactly-once. Actual downstream effects/retention recovery remain deployment gates. |
| NFR-03 | `OrderPostgresIntegrationTest.databaseProtectsAuditAndSourceImmutability`; `OrderCommandJournalIntegrationTest.commandCorrelationCapturesExactVersionsIncludingNestedFinanceCompletion`; `RecoveryIntegrationTest.tombstonesAndReceiptsAreImmutable` | Recovery shared journal hooks and runtime-role follow-up not included at baseline; policy/retention/log-access sign-off pending. |
| NFR-04 | `OperationsIntegrationTest.backlogIsScopedRetryableAndNeverDropsAcceptedReceipts`, `oversizedLineCollectionNeverEntersDurableInbox`; `RecoverySchedulerTest.defaultsProvideHeadroomForOneHundredThousandOrdersDaily` | **100k orders / 200 lines / 25 users / 10 intake RPS / 30 minutes NOT RUN**. No p95 or representative overload acceptance claim. |
| NFR-05 | `RestoreDrillIntegrationTest.restoreRetainsAcknowledgedLocalStateAndDoesNotRedispatchUnknownWork`; `OperationsIntegrationTest.cachedScopedGaugesMeasureBacklogAndScrapesNeverQuery`; `RecoveryIntegrationTest.healthSamplingTimesOutOnDatabaseLockWithoutHangingScrapes`; `RecoveryMetricsTest` | Local functional recovery only. RPO <=15m, RTO <=4h, 99.5% availability, real alerts and infrastructure-loss recovery NOT demonstrated. |
| NFR-06 | Angular confirmation/error/journal component specs; post-baseline `OrderLiveBrowserUat` final-screen axe check and one Tab probe | Manual screen-reader/critical-task keyboard **NOT RUN**; final commit association pending; no WCAG 2.2 AA conformance claim. |
| NFR-07 | `common PublishedLanguageCompatibilityTest.strictJackson2And3ReadAdditiveEventSnapshotAndLineWithoutChangingHashInput`; `OrderErrorContractTest`, `OrderEvidenceControllerTest`; committed [OpenAPI](../contracts/oms/openapi.json) and event schemas | Compatibility is limited to tested supported readers; final facade/route contract checks must follow integration changes. |
| NFR-08 | Docker-tagged Maven integration suites and `cpq-orders` Vitest specs exist | Final approved CI wiring/results pending at baseline. Existing frontend allowlist and backend-unit service selection alone omit OMS coverage. Require explicit OMS/UI/browser stages and no silently skipped Docker tests. |

## End-to-end scenario coverage

These are scenario mappings, not a claim that every row ran as one cross-service browser journey.

| ID | Evidence / status | Boundary |
|---|---|---|
| E2E-01 | **Partial:** `FulfillmentIntegrationTest.serviceAndDigitalProofCompleteOnlyTheirMatchingTask`; `FinanceIntegrationTest.multipleLinesKeepIndependentSourceEconomicsAndRecurringTerms`; contract activation tests | Component/service scenarios; one complete mixed hardware/recurring/service journey through all real services not established. |
| E2E-02 | **Mapped:** inherited PostgreSQL `hundredConcurrentConversionsCreateOneOrderAndPlan` plus `aliasesAndReplayHaveCanonicalIdentity` | Concurrency and aliases are complementary tests, not a sustained-load result. |
| E2E-03 | **Partial:** immutable snapshot tests; `ReplacementIntegrationTest`; quote revision tests | Historical bytes protected; live catalog/address edit journey and P1 in-flight amendment not claimed. |
| E2E-04 | **NOT RUN / outside manual authority capability:** allocation evidence tests only | Two competing reservations for the last real stock unit require a selected external inventory authority. No atomic inventory promise. |
| E2E-05 | **Mapped:** `FulfillmentIntegrationTest.partialShipmentReplayAndCancellationConserveQuantity` | PostgreSQL asserts four fulfilled, two cancelled, four remaining. |
| E2E-06 | **Partial:** `ExecutionIntegrationTest.unknownOutcomeNeverBlindlyRetries`, `authoritativeNoEffectAllowsRetryWithSameStableOperationKey` | Durable ambiguity and manual proof tested; actual provider shipment plus HTTP timeout not exercised. |
| E2E-07 | **Partial, real broker:** `BrokerOutboxIntegrationTest.actualBrokerAckIsRequiredAndExpiredLeaseReplaysIdenticalEvent`; common relay/store tests | Real ack/lease replay, not broker outage or full downstream durable-inbox exactly-once journey. |
| E2E-08 | **Mapped:** `FulfillmentIntegrationTest.pendingDeliveryIsRetainedThenAppliedAfterShipment` | Real PG ledger; synthetic operator facts. |
| E2E-09 | **Partial:** `FulfillmentIntegrationTest.competingCancellationAndAllocationHaveOneVersionWinner`, `unknownInFlightOperationBlocksCancellation`; inherited `concurrentCancellationHasOneVersionWinner` | Direct simultaneous shipment-confirmation/cancellation and real provider race not established by these tests. |
| E2E-10 | **Partial:** `ExecutionIntegrationTest.callbackFailureCreatesExceptionAndDoesNotReleaseFinance`, retry/exhaustion tests; independent service/digital line tests | Full two-line provider failure/recovery journey not established. |
| E2E-11 | **Mapped locally:** contract `OmsActivationConsumerTest.registryFencesBothLegacyIdentitiesAndIsImmutable`, `legacyAliasSubscriptionBlocksCanonicalActivation`, `concurrentReplayCreatesOneSubscriptionAndAcknowledgment` | Every deployed legacy consumer must still be fenced before source ownership/intake. |
| E2E-12 | **P1 deferred:** returns/refunds | No pilot implementation or passing scenario claimed. |
| E2E-13 | **Mapped locally:** real HTTP scope denial; `ExecutionIntegrationTest.ackWrongScopeOrAlteredBodyIsRejected`; messaging trust tests | Production principal spoofing and authenticated callback/ACL negative tests NOT RUN. |
| E2E-14 | **Partial, actual local restore:** `RestoreDrillIntegrationTest`; stale-worker/deadline tests | No production infrastructure-loss or external-side-effect reconciliation drill. |
| E2E-15 | **Partial/P1:** deterministic pinned pilot plan tests | No mapping publication API or live old/new version rollout test; designer is P1. |
| E2E-16 | **Mapped:** `FinanceIntegrationTest.requestIsPendingAndPartialActualAcknowledgmentsAllocateEveryCent`, `unknownAndRejectedCannotCompleteOrRedispatch`; recovery cumulative-ACK test | Controlled manual evidence, not actual ERP. |
| E2E-17 | **Partial/P1:** exact USD cent allocation tests and unsupported-precision validation | Non-USD scales and refunds outside pilot; no multicurrency/refund pass. |
| E2E-18 | **Mapped locally:** `OrderServiceTest.disabledIntakeAllowsExistingReplayButBlocksNewOrder`, `disabledIntakeRejectsNewSource`; restore drill | Operational cohort rollback/drain and production legacy overlap still require deployment drill. |

Roadmap requirements remain unchanged: P1 OM-006, 011, 013, 014, 020–022, 026–030; P2 OM-031–035.
P0 fixed dependencies, unexecuted source replacement and manual evidence do not promote those
capabilities to delivered status. Any gap above remains visible until a named owner records the
missing scenario or explicitly approves a scope change.
