import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';

// Only the orchestrator's isolated loopback acceptance processes may receive this synthetic data.
// Launch ownership/database isolation are checked by the orchestrator before invocation.
if (process.env.RAMP_ACCEPTANCE_DATABASE !== 'ramp_wave_20261009') {
  throw new Error('Set RAMP_ACCEPTANCE_DATABASE=ramp_wave_20261009 after verifying isolated service configuration.');
}
const origins = { quote: 'http://127.0.0.1:18084', pricing: 'http://127.0.0.1:18082', pricebook: 'http://127.0.0.1:18087' };
const runId = `RAMP-AC01-${Date.now()}`;
const region = `RW-${runId.slice('RAMP-AC01-'.length)}`;
const customerExternalId = `${runId}-CUSTOMER`;
const productExternalId = `${runId}-SEAT`;
const request = async (service, path, body, expectedStatus, method) => {
  const response = await fetch(`${origins[service]}${path}`, {
    method: method ?? (body === undefined ? 'GET' : 'POST'),
    headers: { 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(30000),
  });
  const envelope = await response.json();
  if (expectedStatus !== undefined) {
    assert.equal(response.status, expectedStatus, `${service} ${path} HTTP status`);
    return envelope;
  }
  assert.ok(response.ok, `${service} ${path}: HTTP ${response.status} ${envelope.errorCode ?? ''}`);
  assert.notEqual(envelope.success, false, `${service} ${path} returned unsuccessful envelope`);
  return envelope.data;
};
const assertMetrics = (quote) => {
  for (const [key, value] of Object.entries({ tcv: 81000, acv: 27000, arr: 24000, exitArr: 30000 })) {
    assert.equal(Number(quote[key]), value, `${key} must match independently specified AC-01`);
  }
};

await request('pricing', '/api/v1/pricing-customers', {
  externalId: customerExternalId, name: `Synthetic ${runId}`, segment: 'SMB', region: 'NA',
});
const priorBooks = await request('pricebook', '/api/v1/price-books?page=0&size=100');
assert.ok(priorBooks.content.every((book) => book.name.startsWith('Synthetic RAMP-AC01-')), 'isolated database may contain only this script\'s synthetic books');
assert.equal(priorBooks.totalElements, priorBooks.content.length, 'inspect all prior fixture books before adding a new isolated region');
// Preserve earlier published fixtures. Author a new draft in a unique region, then publish after adding its entry.
const book = await request('pricebook', '/api/v1/price-books', {
  name: `Synthetic ${runId}`, region, currency: 'USD', isDefault: false, effectiveDate: null,
});
await request('pricebook', `/api/v1/price-books/${book.id}/entries`, { productExternalId, listPrice: 50, currency: 'USD' });
await request('pricebook', `/api/v1/price-books/${book.id}`, { effectiveDate: '2020-01-01' }, undefined, 'PUT');
const resolved = await request('pricebook', '/internal/v1/price-books/resolve-batch', { region, productExternalIds: [productExternalId] });
assert.equal(resolved.priceBookId, book.id, 'real regional resolution selects the newly published fixture');
assert.equal(resolved.entries[productExternalId].entryMatched, true);
assert.equal(Number(resolved.entries[productExternalId].listPrice), 50);
const quote = await request('quote', '/api/v1/quotes', {
  name: `Synthetic ${runId}`, customerExternalId, currency: 'USD', region, durationInMonths: 36,
  startDate: '2027-01-01', endDate: '2029-12-31',
});
const steps = [40, 45, 50].map((unitPrice, index) => ({
  sequence: index + 1, startMonth: index * 12 + 1, months: 12, quantity: 50, priceBasis: 'ABSOLUTE', unitPrice,
}));
const line = {
  productExternalId, productName: `Synthetic ${runId} seats`, productType: 'SAAS_SEAT_NAMED',
  lineItemType: 'SAAS_SEAT_NAMED', quantity: 50, sortOrder: 0, listPrice: 50, billingFrequency: 'MONTHLY', schedule: { steps },
};
await request('quote', `/api/v1/quotes/${quote.id}/line-items`, line);
const calculated = await request('quote', `/api/v1/quotes/${quote.id}/calculate`, {});
assertMetrics(calculated);
assert.equal(calculated.lineItems.length, 1);
const periods = calculated.lineItems[0].pricedPeriods;
assert.equal(periods.length, 3);
for (const [key, values] of Object.entries({ netUnitPrice: [40, 45, 50], netMonthly: [2000, 2250, 2500], periodAmount: [24000, 27000, 30000] })) {
  assert.deepEqual(periods.map((period) => Number(period[key])), values, key);
}
const reloaded = await request('quote', `/api/v1/quotes/${quote.id}`);
assertMetrics(reloaded);
assert.equal(reloaded.lineItems.length, 1);
assert.deepEqual(reloaded.lineItems[0].schedule.steps.map((step) => Number(step.unitPrice)), [40, 45, 50]);
assert.deepEqual(reloaded.lineItems[0].pricedPeriods, periods, 'priced preview must persist after GET');
const document = await request('quote', `/api/v1/quotes/${quote.id}/documents/generate`, {});
const pdfResponse = await fetch(`${origins.quote}/api/v1/quotes/${quote.id}/documents/${document.id}/download`, {
  signal: AbortSignal.timeout(30000),
});
assert.ok(pdfResponse.ok, 'generated quote PDF must download');
assert.ok(pdfResponse.headers.get('content-type')?.startsWith('application/pdf'));
const pdf = Buffer.from(await pdfResponse.arrayBuffer());
assert.equal(pdf.subarray(0, 5).toString(), '%PDF-');
await writeFile('ramp-r1-live-acceptance.pdf', pdf);

// Rejected new line must leave the persisted line count and existing totals untouched.
const invalid = structuredClone(line);
invalid.schedule.steps[1].startMonth = 14;
const rejected = await request('quote', `/api/v1/quotes/${quote.id}/line-items`, invalid, 422);
assert.equal(rejected.errorCode, 'RAMP_SCHEDULE_INVALID');
assert.equal(rejected.message, 'RAMP_GAP');
const afterRejection = await request('quote', `/api/v1/quotes/${quote.id}`);
assertMetrics(afterRejection);
assert.equal(afterRejection.lineItems.length, 1);
const replayed = await request('quote', `/api/v1/quotes/${quote.id}/calculate`, {});
assertMetrics(replayed);
assert.deepEqual(replayed.lineItems[0].pricedPeriods, periods, 'unchanged pricing context must replay');

// R2 policy is checked through the same real stack, while canonical event consumption stays out of scope.
const governedQuote = await request('quote', '/api/v1/quotes', {
  name: `Synthetic ${runId} governance`, customerExternalId, currency: 'USD', region, durationInMonths: 36,
  startDate: '2027-01-01', endDate: '2029-12-31',
});
const governedLine = structuredClone(line);
governedLine.schedule.steps[0].unitPrice = 34;
await request('quote', `/api/v1/quotes/${governedQuote.id}/line-items`, governedLine);
const governed = await request('quote', `/api/v1/quotes/${governedQuote.id}/calculate`, {});
assert.equal(governed.approvalTier, 'SALES_VP', 'worst period must govern, not blended discount');
assert.equal(governed.approvalState, 'PENDING');
assert.equal(governed.rampGovernance.governingPeriod, 1);
assert.equal(Number(governed.rampGovernance.discountPercent), 32);
assert.ok(governed.rampGovernance.reasons.includes('RAMP_DISCOUNT_THRESHOLD'));
const governedReload = await request('quote', `/api/v1/quotes/${governedQuote.id}`);
assert.deepEqual(governedReload.rampGovernance, governed.rampGovernance, 'governing period persists after GET');

const report = {
  runId, region, database: process.env.RAMP_ACCEPTANCE_DATABASE, origins, quoteId: quote.id, bookId: book.id,
  checks: ['real quote-to-pricing-to-pricebook HTTP', 'AC-01 arithmetic', 'schedule and totals persisted after GET', 'AC-03 rejected gap leaves no new line', 'unchanged-context period replay', 'R2 AC-05 period-one SALES_VP policy and persisted banner'],
  governedQuoteId: governedQuote.id,
  documentId: document.id,
  pdfArtifact: 'ramp-r1-live-acceptance.pdf',
  metrics: { tcv: calculated.tcv, acv: calculated.acv, arr: calculated.arr, exitArr: calculated.exitArr },
  reloadHasPricedPeriods: Array.isArray(reloaded.lineItems[0].pricedPeriods) && reloaded.lineItems[0].pricedPeriods.length === 3,
  knownGaps: ['browser rendering', 'PDF visual/text review pending artifact inspection', 'gateway', 'authenticated identity', 'canonical approval event consumption/supersession', 'submit/approval/accept', 'flag-off live transport', 'pricing-service rollback fault injection'],
};
await writeFile('ramp-r1-live-acceptance-result.json', `${JSON.stringify(report, null, 2)}\n`);
console.log(JSON.stringify(report, null, 2));
