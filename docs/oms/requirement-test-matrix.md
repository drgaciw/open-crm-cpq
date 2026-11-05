# OMS requirement-to-test matrix

Baseline and result limitations: [pilot evidence](pilot-evidence.md). Source:
[requirements](../requirements/order-management-requirements.md), narrowed by the
[authorized P0 wave](../forge/36-oms-pilot.md). **Mapped** means concrete automated coverage exists,
not that the entire requirement or deployment acceptance is complete. **Partial** names the
uncovered scope. **Not run** is not a pass. No new execution results are claimed by this document.

Integration references now include CI `5287093`, database-role policy and complete-schema test
`31b52cf`/`7cb90ea`, recovery wiring/live browser `a1cd8c7`, composed scenarios `fcbccfb`
(workstream `27ac695`), gateway recovery `d0c9423`, and runtime Kafka retention `c0ceb85`
(workstream `9df963b`). These references identify code, not a published final-SHA CI result.

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
| OM-003 | `PilotScenarioIntegrationTest.hundredConcurrentMixedAliasesCreateOneOrderAndInitialPlan`; inherited PostgreSQL `concurrentCrossOrderOperationKeyConflictRollsBackLoser`; quote `AcceptedSnapshotIntegrationTest.hundredConcurrentAcceptancesProduceOneOwnedSnapshot` | One concurrent mixed UUID/number-alias scenario against PostgreSQL; not sustained intake throughput. |
| OM-004 | `OrderServiceTest.missingSourceEvidenceCannotBeClearedByManualHoldRelease`, `unmappedLineIsVisibleHeldWithoutDefaultServiceTasks`; `ExecutionIntegrationTest.heldOrdersAndBlockedDependenciesCannotClaim` | Unsupported terms remain held/rejected; no invented source correction. Operational ownership of each hold needs acceptance. |
| OM-005 | `SnapshotValidatorTest.exactDecimalPasses`, `rejectsUsdPrecisionBeyondCents`; `FinanceIntegrationTest.requestIsPendingAndPartialActualAcknowledgmentsAllocateEveryCent` | USD cents only; no multicurrency/refund rounding claim. |
| OM-007 | `OrderServiceTest.submitHoldVersionAndBodyConflict`, `fullCancellationIsTerminalAndPlanCancelled`; `ExecutionIntegrationTest.evidenceMutationAndExecutionCompletionRollbackTogether` | Derived states and rollback mapped; no client completion shortcut. |
| OM-008 | `FulfillmentIntegrationTest.partialShipmentReplayAndCancellationConserveQuantity`, `repeatedEvidenceDoesNotIncrementQuantity`; `ExecutionIntegrationTest.partialEvidenceReleasesOnlyAvailableDownstreamQuantity` | Simple source lines only; bundle parent/child decomposition is P1. |
| OM-009 | `PilotScenarioIntegrationTest.simultaneousShipmentAndCancellationHaveOneVersionWinnerAndConserveQuantity`; `FulfillmentIntegrationTest.releasedAllocationReopensReadiness`, `unknownInFlightOperationBlocksCancellation`; `RecoveryWiringIntegrationTest.adjudicatedConflictAllowsEligibleCancellationButPendingDeliveryStillBlocks`; `ExecutionIntegrationTest.holdBetweenClaimAndDispatchFencesExternalIntent` | Manual cutoffs and competing local commands mapped; actual provider cancellation/release acceptance pending. |
| OM-010 | `OrderServiceTest.hundredConcurrentConversionsCreateOneOrderAndPlan`, `unmappedLineIsVisibleHeldWithoutDefaultServiceTasks`; `FulfillmentIntegrationTest.serviceAndDigitalProofCompleteOnlyTheirMatchingTask` | Fixed approved templates, not a publishable mapping designer/version rollout. |
| OM-012 | `ExecutionIntegrationTest.expiredClaimIsReclaimedAndOldWorkerCannotCommit`, `unknownOutcomeNeverBlindlyRetries`, `deadlineRecoveryPreservesUnknownInsteadOfRedispatch`, `schedulerExhaustionAlwaysCreatesVisibleException` | Local crash/lease model; authoritative provider status lookup is a controlled manual procedure. |
| OM-015 | `FulfillmentIntegrationTest.allocationRequiresExplicitFutureExpiry`, `expiryRequiresManualReleaseAndCannotAuthorizeShipment`, `allocationAndPartialShipmentUseExactQuantities` | Controlled manual allocation; no atomic stock availability promise or inventory authority implementation. |
| OM-016 | `ExecutionIntegrationTest.ackWrongScopeOrAlteredBodyIsRejected`, `authoritativeNoEffectAllowsRetryWithSameStableOperationKey`; `MessagingTrustConfigurationTest`; `BrokerOutboxIntegrationTest` | Durable keys/inbox/fences mapped. Real authenticated broker/callback deployment remains NOT RUN. |
| OM-017 | `FulfillmentIntegrationTest.pendingDeliveryIsRetainedThenAppliedAfterShipment`, `deliveryCannotOvercountOneReferencedShipment`, `fullPhysicalEvidenceReleasesFinanceButDoesNotInventAcknowledgment` | Synthetic named manual references, not carrier/warehouse integration. |
| OM-018 | `PilotScenarioIntegrationTest.mixedOrderCompletesOnlyAfterIndependentFulfillmentActivationAndFullFinance`; `ExecutionIntegrationTest.activationDispatchIsOnceAndAcknowledgmentReplayCompletesOnlyActivation`; contract `OmsActivationConsumerTest.concurrentReplayCreatesOneSubscriptionAndAcknowledgment`, `registryFencesBothLegacyIdentitiesAndIsImmutable`; `ActivationTermsTest` | Monthly/annual terms and local line ownership covered. Composed OMS test injects trusted synthetic acknowledgments; deployment-wide legacy fence and real acknowledgment path still require verification. |
| OM-019 | `PilotScenarioIntegrationTest.mixedOrderCompletesOnlyAfterIndependentFulfillmentActivationAndFullFinance`; `FinanceIntegrationTest.requestIsPendingAndPartialActualAcknowledgmentsAllocateEveryCent`, `unknownAndRejectedCannotCompleteOrRedispatch` | Independent frozen economics and partial/full evidence across four lines; manual acknowledgment contract, not ERP or invoice creation. |
| OM-023 | `RecoveryIntegrationTest.assignmentUsesExceptionVersionAndDurableReplay`, `assignOnlyPermissionCanAssignWithoutRecoveryPermission`, `waiverCannotCompleteOrUnblockUnknown`; `RecoveryHttpIntegrationTest` | Durable actionable queue mapped; deployment <=60-second visibility and named response ownership not measured. |
| OM-024 | `OrderSearchIntegrationTest.externalReferenceAndOpenExceptionFiltersAreAppliedWithinAccountScope`; Angular component/service specs; committed `OrderLiveBrowserUat` (`a1cd8c7`); gateway `OrderGatewayRoutesTest`, `OrderBrowserSecurityTest`, `OrderTokenRelayTest` | Local live HTTP/JWT/PostgreSQL evidence complements component mocks. Browser uses direct OMS and synthetic authorities; production gateway/IdP journey pending. Returns view is P1. |
| OM-025 | `RecoveryIntegrationTest.dailyScanIncludesTerminalOrdersAndIsBoundedAndDurable`, `completedDailyWindowCatchesOrdersArrivingLaterWithoutRewritingHistory`, `financeReconciliationUsesCumulativeExactAcknowledgments`, `authoritativeAdjudicationRetainsFactsAndCannotCompleteWork` | Daily persisted local facts and manual authority references; no automatic remote reconciliation. Production daily completion/lag not demonstrated. |

## P0 nonfunctional requirements

| ID | Concrete automated coverage | Remaining gate |
|---|---|---|
| NFR-01 | `OrderAccessTest`, `OrderJwtDecoderTest`, `OrderHttpIntegrationTest.realHttpEnforcesJwtAndAccountScopesAndPersistsCommands`; scoped recovery tests; `DbRoleIntegrationTest`; quote `AcceptedSnapshotControllerTest`; gateway browser security/token relay tests | Runtime role privileges verified locally against full migrations. Production role provisioning, real IdP/gateway callback and SASL ACLs pending. No shared multitenancy certification. |
| NFR-02 | quote atomic snapshot rollback; finance audit rollback; common relay/store tests; `BrokerOutboxIntegrationTest`; contract `ContractOmsBrokerRetentionIntegrationTest.fatalSourceFailureSurvivesTenAttemptsAndConsumerRestartWithoutSkipping` | Actual runtime listener factory/source consumer retain failure across restart despite global auto-commit=true. Registry and trust are mocked in that broker test; no durable downstream business-effect or production retention claim. |
| NFR-03 | `OrderPostgresIntegrationTest.databaseProtectsAuditAndSourceImmutability`; `OrderCommandJournalIntegrationTest.commandCorrelationCapturesExactVersionsIncludingNestedFinanceCompletion`; `RecoveryWiringIntegrationTest.lookupReportsAbandonmentAndJournalUsesOrderVersion`, `exceptionVersionDoesNotReplaceAggregateVersionInJournal`; `DbRoleIntegrationTest` | Recovery journal integration and local role restrictions tested; production retention, privacy and log-access policy sign-off pending. |
| NFR-04 | `OperationsIntegrationTest.backlogIsScopedRetryableAndNeverDropsAcceptedReceipts`, `oversizedLineCollectionNeverEntersDurableInbox`; `RecoverySchedulerTest.defaultsProvideHeadroomForOneHundredThousandOrdersDaily` | **100k orders / 200 lines / 25 users / 10 intake RPS / 30 minutes NOT RUN**. No p95 or representative overload acceptance claim. |
| NFR-05 | `RestoreDrillIntegrationTest.restoreRetainsAcknowledgedLocalStateAndDoesNotRedispatchUnknownWork`; `OperationsIntegrationTest.cachedScopedGaugesMeasureBacklogAndScrapesNeverQuery`; `RecoveryIntegrationTest.healthSamplingTimesOutOnDatabaseLockWithoutHangingScrapes`; `RecoveryMetricsTest` | Local functional recovery only. RPO <=15m, RTO <=4h, 99.5% availability, real alerts and infrastructure-loss recovery NOT demonstrated. |
| NFR-06 | Angular confirmation/error/journal component specs; committed `OrderLiveBrowserUat` final-screen axe check and one Tab probe | Manual screen-reader/critical-task keyboard **NOT RUN**; local automated results are not WCAG 2.2 AA conformance. |
| NFR-07 | `common PublishedLanguageCompatibilityTest.strictJackson2And3ReadAdditiveEventSnapshotAndLineWithoutChangingHashInput`; `OrderErrorContractTest`, `OrderEvidenceControllerTest`; committed [OpenAPI](../contracts/oms/openapi.json) and event schemas | Compatibility is limited to tested supported readers; final facade/route contract checks must follow integration changes. |
| NFR-08 | `scripts/ci/frontend.sh` explicitly includes `cpq-orders`; Maven Docker integration stage; required `scripts/ci/oms-browser.sh` invoked by Jenkins/GitHub Actions through `run-core.sh`, with missing/zero/skipped live-test report rejection and artifact archive paths | Wiring is committed. Final published CI results remain required; local targeted runs and the narrow backend-unit stage alone are insufficient. |

## End-to-end scenario coverage

These are scenario mappings, not a claim that every row ran as one cross-service browser journey.

| ID | Evidence / status | Boundary |
|---|---|---|
| E2E-01 | **Mapped within OMS:** `PilotScenarioIntegrationTest.mixedOrderCompletesOnlyAfterIndependentFulfillmentActivationAndFullFinance` | One physical, one manual-service and two monthly/annual recurring lines through real OMS services/PostgreSQL. Synthetic evidence/trusted acknowledgments; no live quote/contract processes or providers. |
| E2E-02 | **Mapped:** `PilotScenarioIntegrationTest.hundredConcurrentMixedAliasesCreateOneOrderAndInitialPlan` | 100 simultaneous mixed canonical UUID/legacy-number conversions produce one order/initial plan; not sustained load. |
| E2E-03 | **Partial:** immutable snapshot tests; `ReplacementIntegrationTest`; quote revision tests | Historical bytes protected; live catalog/address edit journey and P1 in-flight amendment not claimed. |
| E2E-04 | **NOT RUN / outside manual authority capability:** allocation evidence tests only | Two competing reservations for the last real stock unit require a selected external inventory authority. No atomic inventory promise. |
| E2E-05 | **Mapped:** `FulfillmentIntegrationTest.partialShipmentReplayAndCancellationConserveQuantity` | PostgreSQL asserts four fulfilled, two cancelled, four remaining. |
| E2E-06 | **Partial:** `ExecutionIntegrationTest.unknownOutcomeNeverBlindlyRetries`, `authoritativeNoEffectAllowsRetryWithSameStableOperationKey` | Durable ambiguity and manual proof tested; actual provider shipment plus HTTP timeout not exercised. |
| E2E-07 | **Partial, real broker:** `BrokerOutboxIntegrationTest.actualBrokerAckIsRequiredAndExpiredLeaseReplaysIdenticalEvent`; contract `ContractOmsBrokerRetentionIntegrationTest.fatalSourceFailureSurvivesTenAttemptsAndConsumerRestartWithoutSkipping`; common relay/store tests | Real ack/lease replay plus runtime consumer failure/restart without offset advance. Mocked registry/trust in source-consumer test; not broker outage or end-to-end durable business-effect proof. |
| E2E-08 | **Mapped:** `FulfillmentIntegrationTest.pendingDeliveryIsRetainedThenAppliedAfterShipment` | Real PG ledger; synthetic operator facts. |
| E2E-09 | **Mapped locally:** `PilotScenarioIntegrationTest.simultaneousShipmentAndCancellationHaveOneVersionWinnerAndConserveQuantity`; unknown-operation cancellation tests | Actual service commands race at the same version against PostgreSQL; exactly one commits and quantities remain conserved. No real provider race claim. |
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
