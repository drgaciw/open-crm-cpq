/** Browser fixture contract suite. No live commercial data is read or mutated.
 * Host: node node_modules/vite/bin/vite.js --config libs/cpq/orders/browser.vite.config.mts
 * Run: UAT_BASE_URL=http://127.0.0.1:4317 node e2e/uat/suites/orders.mjs
 */
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
import { chromium } from 'playwright';

export async function ordersSuite(page, baseUrl) {
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const stamp = '2026-09-20T12:00:00Z';
  const line = {
    id: 'line-1',
    description: 'Pilot widget',
    ordered: 5,
    allocated: 0,
    shipped: 0,
    delivered: 0,
    cancelled: 0,
    remaining: 5,
    status: 'PENDING',
  };
  const snapshot = {
    schemaVersion: 1,
    snapshotId: 'snapshot-1',
    quoteId: 'quote-1',
    quoteNumber: 'Q-101',
    acceptedRevision: 1,
    scopeId: 'pilot',
    customerId: 'account-1',
    acceptedAt: stamp,
    currency: 'USD',
    subtotal: '100.00',
    discount: '0.00',
    tax: '0.00',
    total: '100.00',
    address: 'Test address',
    approvalReference: 'approval-1',
    taxEvidence: 'tax-1',
    configurationReference: 'config-1',
    lines: [
      {
        sourceLineId: 'source-1',
        description: 'Pilot widget',
        productId: 'p1',
        fulfillmentType: 'PHYSICAL',
        chargeType: 'ONE_TIME',
        quantity: 5,
        uom: 'EA',
        unitPrice: '20.00',
        discount: '0.00',
        tax: '0.00',
        total: '100.00',
        billingFrequency: 'ONE_TIME',
        startDate: null,
        endDate: null,
      },
    ],
  };
  const order = {
    id: 'order-1',
    orderNumber: 'ORD-101',
    quoteId: 'quote-1',
    quoteNumber: 'Q-101',
    customerId: 'account-1',
    scopeId: 'pilot',
    status: 'DRAFT',
    version: 1,
    snapshot,
    lines: [line],
    holds: [],
    financeStatus: 'PENDING',
  };
  const exception = {
    id: 'exception-1',
    orderId: order.id,
    taskId: 'task-1',
    category: 'UNKNOWN_OUTCOME',
    severity: 'HIGH',
    owner: null,
    reason: 'Carrier timed out',
    nextAction: 'RECONCILE',
    status: 'OPEN',
    firstOccurredAt: stamp,
    lastOccurredAt: stamp,
    version: 1,
  };
  const commands = [];
  let deny = false;
  let conflict = false;
  let listDelay = false;
  let loseEvidenceResponse = false;
  let unresolvedKey = null;
  const wrap = (data) => ({ success: true, timestamp: stamp, data });
  const paged = (data) => ({
    ...wrap(data),
    pagination: {
      page: 0,
      size: 20,
      totalElements: data.length,
      totalPages: 1,
    },
  });
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const respond = (data, status = 200) =>
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(data),
      });
    if (req.method() === 'POST') {
      const body = req.postDataJSON();
      commands.push({
        path: url.pathname,
        body,
        key: req.headers()['idempotency-key'],
      });
      if (conflict)
        return respond(
          { ...wrap(null), success: false, message: 'Version conflict' },
          409,
        );
      if (url.pathname.endsWith('/from-quote')) return respond(wrap(order));
      assert.equal(typeof body.expectedVersion, 'number');
      assert.ok(body.reason);
      assert.ok(body.operationKey);
      if (loseEvidenceResponse && url.pathname.endsWith('/evidence')) {
        loseEvidenceResponse = false;
        unresolvedKey = body.operationKey;
        return route.abort('failed');
      }
      return respond(wrap({}), 202);
    }
    if (url.pathname.includes('/operations/')) {
      const key = url.pathname.split('/').at(-1);
      return respond(
        wrap({
          operationKey: key,
          orderId: order.id,
          status: key === unresolvedKey ? 'NOT_FOUND' : 'SUCCEEDED',
          orderVersion: order.version,
        }),
      );
    }
    if (url.pathname === '/api/v1/orders') {
      if (listDelay) await new Promise((resolve) => setTimeout(resolve, 400));
      if (deny) return respond({ ...wrap(null), success: false }, 403);
      return respond(
        paged(url.searchParams.get('q') === 'missing' ? [] : [order]),
      );
    }
    if (url.pathname === '/api/v1/order-exceptions')
      return respond(paged([exception]));
    if (url.pathname.endsWith('/plan'))
      return respond(
        wrap([
          {
            id: 'task-1',
            lineId: 'line-1',
            kind: 'SHIPMENT',
            templateVersion: 1,
            status: 'UNKNOWN_OUTCOME',
            dispatchedAt: stamp,
          },
        ]),
      );
    if (url.pathname.endsWith('/timeline'))
      return respond(
        wrap([
          {
            actor: 'operator',
            at: stamp,
            action: 'CAPTURED',
            reason: 'Accepted quote',
            version: 1,
            evidenceReference: 'snapshot-1',
          },
        ]),
      );
    return respond(wrap(order));
  });
  await page.goto(`${baseUrl}/orders`);
  await page.getByRole('link', { name: 'ORD-101' }).waitFor();
  await page.getByRole('textbox', { name: 'Search orders' }).fill('missing');
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await page.getByText('No orders match these filters.').waitFor();
  await page.getByRole('textbox', { name: 'Search orders' }).fill('');
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await page.getByRole('link', { name: 'ORD-101' }).click();
  await page
    .getByRole('heading', { name: 'Accepted commercial snapshot' })
    .waitFor();
  await page.getByRole('heading', { name: 'Execution plan' }).waitFor();
  assert.equal(
    await page
      .getByRole('button', { name: 'Retry task', exact: true })
      .isDisabled(),
    true,
  );
  await page.getByLabel('Cancellation line').selectOption('line-1');
  await page.getByLabel('Cancellation quantity').fill('2');
  await page
    .getByLabel('Cancellation reason', { exact: true })
    .fill('Customer reduced remaining request');
  await page.getByRole('button', { name: 'Request cancellation' }).click();
  await page.getByRole('alert').waitFor();
  assert.equal(commands.length, 0);
  await page
    .getByLabel('I confirm this line, quantity and cancellation reason.')
    .check();
  conflict = true;
  await page.getByRole('button', { name: 'Request cancellation' }).click();
  await page
    .getByText(/order changed or this operation key conflicts/)
    .waitFor();
  assert.equal(commands.length, 1);
  assert.equal(commands[0].body.quantity, 2);
  assert.equal(
    await page
      .getByRole('button', { name: 'Record evidence', exact: true })
      .isDisabled(),
    true,
  );
  conflict = false;
  await page.getByRole('button', { name: 'Reload order' }).click();
  await page.getByLabel('Evidence line').selectOption('line-1');
  await page.getByLabel('Evidence kind').selectOption('DELIVERY');
  await page.getByLabel('Evidence quantity').fill('1');
  await page.getByLabel('External reference').fill('POD-101');
  await page.getByLabel('Occurred at (local time)').fill('2026-09-20T12:00');
  await page.getByLabel('Evidence reason').fill('Proof of delivery received');
  await page
    .getByRole('button', { name: 'Record evidence', exact: true })
    .click();
  await page.getByText(/Command persisted; processing is pending/).waitFor();
  assert.equal(commands.at(-1).body.externalReference, 'POD-101');
  loseEvidenceResponse = true;
  await page.getByLabel('External reference', { exact: true }).fill('POD-102');
  await page
    .getByRole('button', { name: 'Record evidence', exact: true })
    .click();
  await page.getByRole('alert').filter({ hasText: 'unknown' }).waitFor();
  const uncertainCount = commands.length;
  await page.reload();
  await page.getByText(/earlier command outcome is unresolved/).waitFor();
  assert.equal(
    await page
      .getByRole('button', { name: 'Record evidence', exact: true })
      .isDisabled(),
    true,
  );
  assert.equal(commands.length, uncertainCount);
  const stored = await page.evaluate(() =>
    sessionStorage.getItem('oms:pending:order:order-1'),
  );
  assert.ok(stored.includes(unresolvedKey));
  assert.ok(!stored.includes('POD-102'));
  unresolvedKey = null;
  await page.getByRole('button', { name: 'Reload order' }).click();
  await page.waitForFunction(() =>
    [...document.querySelectorAll('button')].some(
      (button) =>
        button.textContent.trim() === 'Record evidence' &&
        !button.disabled &&
        !button.closest('fieldset[disabled]'),
    ),
  );
  await page.getByRole('link', { name: 'Exception queue' }).click();
  await page.getByRole('button', { name: 'Manage exception' }).click();
  await page.getByLabel('Owner', { exact: true }).fill('operator-1');
  await page
    .getByLabel('Recovery reason', { exact: true })
    .fill('Investigating carrier response');
  await page.getByRole('button', { name: 'Assign owner' }).click();
  await page.getByText(/Recovery command persisted/).waitFor();
  assert.equal(commands.at(-1).body.owner, 'operator-1');
  await page.getByRole('button', { name: 'Manage exception' }).click();
  await page
    .getByLabel('Recovery reason', { exact: true })
    .fill('Reviewing unknown outcome');
  await page.getByLabel('Resolution action').selectOption('RETRY');
  await page
    .getByLabel('I confirm the selected recovery action and reason.')
    .check();
  const beforeRetry = commands.length;
  await page.getByRole('button', { name: 'Apply recovery' }).click();
  await page
    .getByText('Reconcile the unknown outcome before retrying.')
    .waitFor();
  assert.equal(commands.length, beforeRetry);
  await page.getByRole('link', { name: 'All orders' }).click();
  await page
    .getByText('Create order from accepted quote', { exact: true })
    .click();
  await page.getByLabel('Snapshot ID', { exact: true }).fill('snapshot-1');
  await page.getByLabel('Quote ID', { exact: true }).fill('quote-1');
  await page.getByLabel('Accepted revision', { exact: true }).fill('1');
  await page.getByRole('button', { name: 'Create order', exact: true }).click();
  await page
    .getByRole('heading', { name: 'Accepted commercial snapshot' })
    .waitFor();
  assert.deepEqual(commands.at(-1).body, {
    snapshotId: 'snapshot-1',
    quoteId: 'quote-1',
    acceptedRevision: 1,
  });
  assert.ok(commands.at(-1).key);
  deny = true;
  listDelay = true;
  await page.getByRole('link', { name: 'All orders' }).click();
  await page.getByText('Loading orders…').waitFor();
  await page.getByRole('alert').filter({ hasText: 'permission' }).waitFor();
  assert.equal(
    await page.getByText('No orders match these filters.').count(),
    0,
  );
  assert.deepEqual(errors, []);
  return {
    suite: 'orders',
    result: 'PASS',
    scenarios: [
      'list and filters',
      'immutable detail and plan',
      'cancel confirmation',
      'version conflict blocks commands',
      'evidence 202 pending',
      'lost evidence response survives browser reload without duplicate',
      'exception assignment',
      'unknown outcome retry denied',
      'source reference conversion',
      'loading and scope denial',
    ],
    backend: 'intercepted fixtures only',
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  const base = process.env.UAT_BASE_URL;
  if (!base)
    throw new Error(
      'Set UAT_BASE_URL explicitly to the test host; this suite must not select a checkout implicitly.',
    );
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.CHROMIUM_PATH
      ? { executablePath: process.env.CHROMIUM_PATH }
      : {}),
  });
  try {
    console.log(
      JSON.stringify(await ordersSuite(await browser.newPage(), base), null, 2),
    );
  } finally {
    await browser.close();
  }
}
