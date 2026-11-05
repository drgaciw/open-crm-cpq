/** Deterministic browser contract fixtures. Never connects to an OMS backend. */
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

const base = process.env.UAT_BASE_URL;
if (!base)
  throw new Error('Set UAT_BASE_URL to the isolated orders test host.');
const browser = await chromium.launch({
  headless: true,
  ...(process.env.CHROMIUM_PATH
    ? { executablePath: process.env.CHROMIUM_PATH }
    : {}),
});
try {
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const line = {
    id: 'line',
    description: 'Pilot widget',
    ordered: 3,
    allocated: 0,
    shipped: 0,
    delivered: 0,
    cancelled: 0,
    remaining: 3,
    status: 'PENDING',
  };
  const order = {
    id: 'order',
    orderNumber: 'ORD-FINAL',
    quoteId: 'quote',
    quoteNumber: 'Q-1',
    scopeId: 'pilot',
    customerId: 'customer',
    status: 'DRAFT',
    version: 1,
    financeStatus: 'PENDING',
    holds: [],
    lines: [line],
    snapshot: {
      snapshotId: 'snapshot',
      quoteId: 'quote',
      quoteNumber: 'Q-1',
      acceptedRevision: 1,
      acceptedAt: '2026-09-20T12:00:00Z',
      currency: 'USD',
      subtotal: '30.00',
      discount: '0.00',
      tax: '0.00',
      total: '30.00',
      address: 'Fixture',
      approvalReference: 'approval',
      configurationReference: 'config',
      taxEvidence: 'tax',
      lines: [],
    },
  };
  const calls = [];
  const adjudications = [];
  const outcomes = new Map();
  let loseNext = false;
  const wrap = (data) => ({
    success: true,
    data,
    timestamp: '2026-09-20T12:00:00Z',
  });
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    let data;
    if (request.method() === 'POST') {
      const body = request.postDataJSON();
      calls.push({ path, body });
      if (loseNext) {
        loseNext = false;
        return route.abort('failed');
      }
      order.version++;
      const target = path.match(/\/operations\/([^/]+)\/abandon$/)?.[1];
      if (target) outcomes.set(target, 'ABANDONED');
      outcomes.set(body.operationKey, 'SUCCEEDED');
      if (path.endsWith('/adjudications'))
        adjudications.push({
          id: 'disposition',
          evidenceId: 'conflict',
          authorityReference: body.authorityReference,
          occurredAt: body.occurredAt,
          actor: 'reviewer',
          reason: body.reason,
          recordedAt: '2026-09-20T15:00:00Z',
        });
      data = { status: 'SUCCEEDED' };
    } else if (path.endsWith('/plan'))
      data = [
        {
          id: 'task',
          lineId: 'line',
          kind: 'ACTIVATION',
          status: 'UNKNOWN',
          templateVersion: 1,
          dispatchedAt: '2026-09-20T12:00:00Z',
        },
      ];
    else if (path.endsWith('/timeline')) data = [];
    else if (path.endsWith('/evidence-adjudications')) data = adjudications;
    else if (path.endsWith('/allocations'))
      data = [
        {
          id: 'lot',
          lineId: 'line',
          reference: 'allocation-1',
          quantity: 3,
          consumed: 0,
          released: 0,
          available: 3,
          expiresAt: '2026-09-22T12:00:00Z',
        },
      ];
    else if (path.endsWith('/fulfillment-evidence'))
      data = [
        {
          id: 'evidence',
          lineId: 'line',
          kind: 'SHIPMENT',
          quantity: 1,
          externalReference: 'shipment-1',
          allocationReference: 'allocation-1',
          shipmentReference: null,
          occurredAt: '2026-09-20T12:00:00Z',
          expiresAt: null,
          actor: 'operator',
          reason: 'Carrier pickup',
          status: 'APPLIED',
          detail: null,
        },
        {
          id: 'conflict',
          lineId: 'line',
          kind: 'SHIPMENT',
          quantity: 2,
          externalReference: 'incorrect-shipment',
          allocationReference: 'allocation-1',
          shipmentReference: null,
          occurredAt: '2026-09-20T12:00:00Z',
          expiresAt: null,
          actor: 'operator',
          reason: 'Disputed observation',
          status: 'CONFLICT',
          detail: 'Reference conflict',
        },
      ];
    else if (path.endsWith('/finance-requests'))
      data = [
        {
          requestKey: 'finance-1',
          lineId: 'line',
          quantity: 3,
          acknowledgedQuantity: 0,
          status: 'PENDING',
          payload: '{"totalCents":3000}',
        },
      ];
    else if (path.includes('/operations/')) {
      const key = path.split('/').at(-1);
      data = {
        operationKey: key,
        orderId: 'order',
        status: outcomes.get(key) || 'NOT_FOUND',
        orderVersion: order.version,
      };
    } else if (path === '/api/v1/orders/order') data = order;
    else throw new Error('Unexpected fixture route ' + path);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(wrap(data)),
    });
  });
  const idle = () =>
    page.waitForFunction(
      () =>
        !document.body.textContent.includes('Loading order,') &&
        !document.body.textContent.includes('Recording command…'),
    );
  const commit = async (name) => {
    const refreshed = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === '/api/v1/orders/order',
    );
    await page.getByRole('button', { name, exact: true }).click();
    await refreshed;
    await page.waitForFunction(
      (version) =>
        document.body.textContent.includes('Version ' + version) &&
        !document.body.textContent.includes('Loading order,') &&
        !document.body.textContent.includes('Recording command…'),
      order.version,
    );
  };
  await page.goto(base + '/orders/order');
  await page.getByRole('heading', { name: 'ORD-FINAL' }).waitFor();
  await idle();
  assert.match(
    await page
      .getByRole('region', { name: 'Fulfillment evidence ledger' })
      .innerText(),
    /allocation-1[\s\S]*shipment-1/,
  );
  await page.getByLabel('Evidence line').selectOption('line');
  await page
    .getByLabel('External reference', { exact: true })
    .fill('new-allocation');
  await page
    .getByLabel('Occurred at (local time)', { exact: true })
    .fill('2026-09-20T13:00');
  await page
    .getByLabel('Evidence reason', { exact: true })
    .fill('Observed warehouse allocation');
  await page
    .getByRole('button', { name: 'Record evidence', exact: true })
    .click();
  assert.equal(calls.length, 0);
  await page
    .getByLabel('Allocation expiry (local time)', { exact: true })
    .fill('2026-09-22T13:00');
  await commit('Record evidence');
  assert.ok(calls.at(-1).body.expiresAt);
  await page.getByLabel('Evidence kind').selectOption('SHIPMENT');
  await page
    .getByLabel('Original allocation reference', { exact: true })
    .fill('allocation-1');
  await commit('Record evidence');
  assert.equal(calls.at(-1).body.allocationReference, 'allocation-1');
  await page.getByLabel('Evidence kind').selectOption('DELIVERY');
  await page
    .getByLabel('Causal shipment reference', { exact: true })
    .fill('shipment-1');
  await commit('Record evidence');
  assert.equal(calls.at(-1).body.shipmentReference, 'shipment-1');
  await page.getByLabel('Finance line').selectOption('line');
  await page
    .getByLabel('Finance reason', { exact: true })
    .fill('Verified fulfilled obligation');
  await commit('Prepare finance request');
  assert.equal(calls.at(-1).path, '/api/v1/orders/order/finance-requests');
  await page.getByLabel('Recorded finance request').selectOption('finance-1');
  await page.getByLabel('Actual finance outcome').selectOption('REJECTED');
  await page
    .getByLabel('Finance external reference', { exact: true })
    .fill('rejection-proof');
  await page
    .getByLabel('Finance occurred at (local time)', { exact: true })
    .fill('2026-09-20T14:00');
  await commit('Record actual finance outcome');
  assert.equal(calls.at(-1).body.outcome, 'REJECTED');
  assert.equal(calls.at(-1).body.requestKey, 'finance-1');
  await page.getByLabel('No-effect task').selectOption('task');
  await page
    .getByLabel('No-effect evidence reference', { exact: true })
    .fill('authoritative-proof');
  await page
    .getByLabel('No-effect occurred at (local time)', { exact: true })
    .fill('2026-09-20T14:00');
  await page
    .getByLabel('No-effect reason', { exact: true })
    .fill('Provider verified no effect');
  await page
    .getByLabel(
      'I have authoritative evidence that this dispatch had no external effect.',
    )
    .check();
  await commit('Record no-effect evidence');
  assert.equal(calls.at(-1).path, '/api/v1/orders/order/tasks/task/no-effect');
  await page
    .getByLabel('Replacement reason', { exact: true })
    .fill('Correct accepted source');
  await page
    .getByLabel(
      'I confirm cancellation of this entire order to request a newly approved commercial revision.',
    )
    .check();
  await commit('Cancel order and request correction');
  assert.equal(calls.at(-1).path, '/api/v1/orders/order/replacement-requests');
  loseNext = true;
  await page
    .getByLabel('Command reason', { exact: true })
    .fill('Observed command');
  await page
    .getByRole('button', { name: 'Apply hold change', exact: true })
    .click();
  await idle();
  await page.getByRole('alert').filter({ hasText: 'unknown' }).waitFor();
  const original = calls.at(-1).body.operationKey;
  await page.reload();
  await page.getByRole('heading', { name: 'ORD-FINAL' }).waitFor();
  await idle();
  assert.equal(
    await page
      .getByRole('button', { name: 'Record evidence', exact: true })
      .isDisabled(),
    true,
  );
  await page.getByLabel('Unresolved operation').selectOption(original);
  await page
    .getByLabel('Abandonment reason', { exact: true })
    .fill('Server verifies command never received');
  await page
    .getByLabel(
      'I request that this operation key be prevented from executing in the future.',
    )
    .check();
  await commit('Request safe abandonment');
  assert.notEqual(calls.at(-1).body.operationKey, original);
  await page.waitForFunction(
    () => sessionStorage.getItem('oms:pending:order:order') === null,
  );
  assert.equal(
    await page.evaluate(() =>
      sessionStorage.getItem('oms:pending:order:order'),
    ),
    null,
  );
  assert.equal(
    await page
      .getByRole('button', { name: 'Record evidence', exact: true })
      .isDisabled(),
    false,
  );
  await page.getByLabel('Conflicting observation').selectOption('conflict');
  await page
    .getByLabel('Adjudication authority reference')
    .fill('carrier-audit-proof');
  await page
    .getByLabel('Authority occurred at (local time)')
    .fill('2026-09-20T14:00');
  await page
    .getByLabel('Adjudication reason')
    .fill('Carrier audit disproves duplicate observation');
  const beforeDisavowal = calls.length;
  await page
    .getByRole('button', {
      name: 'Disavow conflicting observation',
      exact: true,
    })
    .click();
  assert.equal(calls.length, beforeDisavowal);
  await page
    .getByLabel(
      'I confirm the selected original observation is disavowed by this authority. This does not record fulfillment.',
    )
    .check();
  await commit('Disavow conflicting observation');
  assert.equal(
    calls.at(-1).path,
    '/api/v1/orders/order/evidence/conflict/adjudications',
  );
  assert.deepEqual(Object.keys(calls.at(-1).body).sort(), [
    'authorityReference',
    'expectedVersion',
    'occurredAt',
    'operationKey',
    'reason',
  ]);
  assert.equal(calls.at(-1).body.authorityReference, 'carrier-audit-proof');
  assert.match(
    await page
      .getByRole('region', { name: 'Evidence dispositions' })
      .innerText(),
    /carrier-audit-proof/,
  );
  assert.match(
    await page
      .getByRole('region', { name: 'Fulfillment evidence ledger' })
      .innerText(),
    /CONFLICT/,
  );
  assert.equal(
    await page
      .getByLabel('Conflicting observation')
      .locator('option[value="conflict"]')
      .count(),
    0,
  );
  assert.equal(line.shipped, 0);
  assert.equal(order.status, 'DRAFT');
  const fulfillment = page
    .getByRole('heading', { name: 'Fulfillment lines', exact: true })
    .locator('..');
  assert.equal(
    await fulfillment
      .locator('tbody tr')
      .first()
      .locator('td')
      .nth(3)
      .innerText(),
    '0',
  );
  assert.equal(await page.locator('.badge').innerText(), 'DRAFT');
  assert.deepEqual(errors, []);
  console.log(
    JSON.stringify(
      {
        scenarios: 9,
        commands: calls.length,
        backend: 'intercepted HTTP fixtures only',
        pageErrors: errors,
      },
      null,
      2,
    ),
  );
} finally {
  await browser.close();
}
