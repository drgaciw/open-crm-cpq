import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  ASSET_CHURN_KINDS,
  ASSET_STATUSES,
  type AssetChurnEventDto,
  type AssetChurnReport,
  type AssetDto,
  type AssetHistoryEntryDto,
  type InstalledAssetDto,
  type UpcomingRenewalsReport,
  isAssetChurnKind,
  isAssetStatus,
} from './asset.model';
import {
  QUOTE_CONTINUATION_OPERATIONS,
  type QuoteContinuationRequest,
  type QuoteDto,
} from './quote.model';
import type { DealCreateRequest, DealDto } from './deal.model';
import {
  SUBSCRIPTION_STATUSES,
  type SubscriptionDto,
  isSubscriptionStatus,
} from './subscription.model';
import type { AmendmentView, CreateAmendment } from './amendment.model';

/**
 * Limited source field-name check for Java DTOs used by
 * Asset Lifecycle Management (quotes, deals, assets/OHS/reports). This does not validate
 * serialized JSON names, types, nullability, enum values or full wire compatibility.
 *
 * Parses Java DTO source for `private <Type> <name>;` fields and asserts every backend
 * field exists on the corresponding TypeScript interface (via keyof compile-time + runtime
 * expected-name lists derived from the Java files in this monorepo).
 */

const REPO = resolve(__dirname, '../../../../../../');

function javaFields(relativePath: string): string[] {
  const src = readFileSync(resolve(REPO, relativePath), 'utf8');
  if (/\b(?:public|protected|private)\s+record\s+\w+/.test(src)) {
    throw new Error(`${relativePath}: Java records require a serialization-aware contract test`);
  }
  const fields: string[] = [];
  const re = /private\s+(?:static\s+)?(?:final\s+)?[\w.<>,\s?]+\s+(\w+)\s*[;=]/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(src)) !== null) {
    fields.push(match[1]);
  }
  if (fields.length === 0) throw new Error(`${relativePath}: no private fields extracted; record or unsupported Java syntax requires a different parser`);
  return fields;
}

function expectFeCoversBackend(label: string, backendFields: string[], feKeys: string[]) {
  expect(backendFields.length, `${label}: no backend fields extracted`).toBeGreaterThan(0);
  const missing = backendFields.filter((f) => !feKeys.includes(f));
  expect(missing, `${label}: FE missing backend fields ${missing.join(', ')}`).toEqual([]);
}

function javaRecordFields(relativePath: string): string[] {
  const src = readFileSync(resolve(REPO, relativePath), 'utf8');
  const declaration = src.match(/\bpublic\s+record\s+\w+\s*\(([\s\S]*?)\)\s*\{/);
  if (!declaration) throw new Error(`${relativePath}: record header could not be extracted`);
  return declaration[1].split(',').map((component) => {
    const match = component.trim().match(/^[\w.]+(?:<\s*[\w.]+\s*>)?\s+(\w+)$/);
    if (!match) throw new Error(`${relativePath}: unsupported record component ${component.trim()}`);
    return match[1];
  });
}

describe('Java source field-name checks (ALM surfaces)', () => {
  it('AssetDto covers CAM AssetDto fields', () => {
    const backend = javaFields(
      'backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/AssetDto.java',
    );
    const feKeys: (keyof AssetDto)[] = [
      'id',
      'uuid',
      'accountId',
      'contractLineId',
      'subscriptionId',
      'entitlementId',
      'productRef',
      'quantity',
      'status',
      'effectiveStart',
      'effectiveEnd',
      'supersededBy',
      'supersedes',
      'createdAt',
      'updatedAt',
    ];
    expectFeCoversBackend('AssetDto', backend, feKeys);
    expect(ASSET_STATUSES).toEqual(['ACTIVE', 'AMENDED', 'SUPERSEDED', 'TERMINATED']);
    expect(isAssetStatus('ACTIVE')).toBe(true);
    expect(isAssetStatus('CHURNED')).toBe(false);
  });

  it('InstalledAssetDto / history / reports cover CAM OHS shapes', () => {
    expectFeCoversBackend(
      'InstalledAssetDto',
      javaFields(
        'backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/InstalledAssetDto.java',
      ),
      [
        'assetId',
        'accountId',
        'contractLineId',
        'subscriptionId',
        'productRef',
        'quantity',
        'effectiveStart',
        'effectiveEnd',
        'status',
      ] satisfies (keyof InstalledAssetDto)[],
    );

    expectFeCoversBackend(
      'AssetHistoryEntryDto',
      javaFields(
        'backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/AssetHistoryEntryDto.java',
      ),
      [
        'assetId',
        'accountId',
        'eventType',
        'effectiveDate',
        'effectiveEnd',
        'sourceAmendmentId',
        'sourceApplyUnit',
        'predecessorAssetId',
        'successorAssetId',
        'contractLineId',
        'subscriptionId',
        'productRef',
        'quantity',
      ] satisfies (keyof AssetHistoryEntryDto)[],
    );

    expectFeCoversBackend(
      'UpcomingRenewalsReport',
      javaFields(
        'backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/UpcomingRenewalsReport.java',
      ),
      ['accountId', 'windowStart', 'windowEnd', 'assetCount', 'assets'] satisfies (keyof UpcomingRenewalsReport)[],
    );

    expectFeCoversBackend(
      'AssetChurnReport',
      javaFields(
        'backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/AssetChurnReport.java',
      ),
      [
        'accountId',
        'periodStart',
        'periodEnd',
        'assetCount',
        'eventCount',
        'quantityChurned',
        'events',
      ] satisfies (keyof AssetChurnReport)[],
    );

    expectFeCoversBackend(
      'AssetChurnEventDto',
      javaFields(
        'backend/services/contract-asset-management/src/main/java/com/opencrm/contract/asset/AssetChurnEventDto.java',
      ),
      [
        'assetId',
        'accountId',
        'churnKind',
        'effectiveDate',
        'quantityBefore',
        'quantityAfter',
        'quantityChurned',
        'productRef',
      ] satisfies (keyof AssetChurnEventDto)[],
    );

    expect(ASSET_CHURN_KINDS).toEqual(['TERMINATED', 'DOWNSIZED']);
    expect(isAssetChurnKind('DOWNSIZED')).toBe(true);
  });

  it('QuoteDto covers quote-service QuoteDto fields used by ALM / continuations', () => {
    const backend = javaFields(
      'backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteDto.java',
    );
    const feKeys: (keyof QuoteDto)[] = [
      'id',
      'uuid',
      'quoteNumber',
      'name',
      'description',
      'customerExternalId',
      'dealExternalId',
      'primary',
      'status',
      'dealType',
      'subscriptionType',
      'currency',
      'region',
      'durationInMonths',
      'startDate',
      'endDate',
      'validUntil',
      'targetSubscriptionNumber',
      'observedRevision',
      'subscriptionEffectiveDate',
      'continuationOperation',
      'subtotal',
      'totalDiscount',
      'totalTax',
      'totalAmount',
      'arr',
      'tcv',
      'acv',
      'exitArr',
      'rampScheduleStale',
      'rampGovernance',
      'approvalState',
      'approvalTier',
      'notes',
      'lineItems',
      'createdAt',
      'updatedAt',
      'createdBy',
      'updatedBy',
      'version',
    ];
    expectFeCoversBackend('QuoteDto', backend, feKeys);
  });

  it('QuoteContinuationRequest covers renewal-from-assets request fields', () => {
    const backend = javaFields(
      'backend/services/quote-service/src/main/java/com/opencrm/quote/quote/QuoteContinuationRequest.java',
    ).filter((f) => f !== 'continuationIdentitiesPresent'); // @AssertTrue method, not a JSON field
    expectFeCoversBackend(
      'QuoteContinuationRequest',
      backend,
      [
        'operation',
        'targetSubscriptionNumber',
        'sourceSubscriptionLineUuid',
        'observedRevision',
        'customerExternalId',
        'currency',
        'quantity',
        'effectiveDate',
        'termMonths',
        'upliftPercent',
        'name',
        'renewalSourceMode',
        'contractLineId',
        'renewalPolicyAccountId',
        'renewalPolicyAgreementId',
        'renewalAssetIds',
      ] satisfies (keyof QuoteContinuationRequest)[],
    );
    expect(QUOTE_CONTINUATION_OPERATIONS).toContain('RENEWAL');
    expect(QUOTE_CONTINUATION_OPERATIONS).toContain('EXTENSION');
  });

  it('DealDto / DealCreateRequest cover CRM deal fields used as ALM precursors', () => {
    expectFeCoversBackend(
      'DealDto',
      javaFields('backend/services/crm-service/src/main/java/com/opencrm/crm/deal/DealDto.java'),
      [
        'id',
        'uuid',
        'name',
        'organizationId',
        'organizationName',
        'primaryContactId',
        'primaryContactName',
        'stage',
        'amount',
        'currency',
        'probability',
        'expectedCloseDate',
        'termMonths',
        'actualCloseDate',
        'description',
        'ownerId',
        'sourceLeadId',
        'type',
        'lostReason',
        'acceptedQuoteId',
        'wonAt',
        'lostAt',
        'nextStep',
        'nextStepDate',
        'competitors',
        'weightedAmount',
        'baseAmount',
        'baseCurrency',
        'baseWeightedAmount',
        'fxRate',
        'fxRateAsOf',
        'fxSource',
        'createdAt',
        'updatedAt',
        'createdBy',
        'updatedBy',
        'version',
      ] satisfies (keyof DealDto)[],
    );

    expectFeCoversBackend(
      'DealCreateRequest',
      javaFields('backend/services/crm-service/src/main/java/com/opencrm/crm/deal/DealCreateRequest.java'),
      [
        'name',
        'organizationId',
        'primaryContactId',
        'amount',
        'currency',
        'expectedCloseDate',
        'termMonths',
        'description',
        'ownerId',
        'type',
      ] satisfies (keyof DealCreateRequest)[],
    );
  });

  it('SubscriptionDto covers CAM SubscriptionDto fields (CLM)', () => {
    expectFeCoversBackend(
      'SubscriptionDto',
      javaFields(
        'backend/services/contract-asset-management/src/main/java/com/opencrm/contract/dto/SubscriptionDto.java',
      ),
      [
        'id',
        'uuid',
        'subscriptionNumber',
        'customerId',
        'quoteId',
        'status',
        'planName',
        'startDate',
        'endDate',
        'renewalDate',
        'mrr',
        'arr',
        'currencyCode',
        'usageBand',
        'erpSyncStatus',
        'erpSubscriptionId',
        'createdAt',
        'updatedAt',
        'createdBy',
        'updatedBy',
        'version',
      ] satisfies (keyof SubscriptionDto)[],
    );
    expect(SUBSCRIPTION_STATUSES).toEqual([
      'PENDING',
      'ACTIVE',
      'SUSPENDED',
      'CANCELLED',
      'EXPIRED',
    ]);
    expect(isSubscriptionStatus('ACTIVE')).toBe(true);
    expect(isSubscriptionStatus('TERMINATED')).toBe(false);
  });

  it('AmendmentView / CreateAmendment cover CAM amendment records (CLM)', () => {
    const viewFields = javaRecordFields(
      'backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/AmendmentView.java',
    );
    expect(viewFields).toContain('status'); // enum-typed record components must not be skipped
    expectFeCoversBackend(
      'AmendmentView',
      viewFields,
      [
        'id',
        'contractId',
        'provisionalNumber',
        'amendmentSeq',
        'amendmentNumber',
        'status',
        'version',
        'contentDigest',
        'approvalRef',
        'signatureEnvelopeRef',
        'tcvDelta',
        'arrDelta',
        'oneTimeTotal',
        'creditTotal',
        'revisions',
        'lines',
      ] satisfies (keyof AmendmentView)[],
    );

    const createFields = javaRecordFields(
      'backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/CreateAmendment.java',
    );
    expectFeCoversBackend(
      'CreateAmendment',
      createFields,
      [
        'lifecycleChangeId',
        'lifecycleChangeRevision',
        'targetQuoteId',
        'inputsSnapshotRef',
        'signaturePolicy',
        'actionDate',
        'contractEffectiveDate',
        'backdatingReasonCode',
        'currency',
        'lines',
      ] satisfies (keyof CreateAmendment)[],
    );
  });
});
