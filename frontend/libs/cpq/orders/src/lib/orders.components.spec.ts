import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { apiResponseInterceptor } from '@frontend/shared-data-access';
import { of } from 'rxjs';
import { OrdersListComponent } from './orders-list.component';
import { OrdersDetailComponent } from './orders-detail.component';
import { OrdersExceptionsComponent } from './orders-exceptions.component';

const snapshot = {
  snapshotId: 's',
  quoteId: 'q',
  quoteNumber: 'Q-1',
  acceptedRevision: 1,
  currency: 'USD',
  total: '100.00',
  lines: [],
};
const order = {
  id: 'o',
  orderNumber: 'ORD-1',
  status: 'DRAFT',
  version: 4,
  customerId: 'a',
  scopeId: 'pilot',
  snapshot,
  lines: [
    {
      id: 'l',
      description: 'Widget',
      ordered: 5,
      remaining: 5,
      shipped: 0,
      delivered: 0,
      cancelled: 0,
      allocated: 0,
      status: 'PENDING',
    },
  ],
  holds: [],
  financeStatus: 'PENDING',
};
const envelope = (data: unknown) => ({
  success: true,
  timestamp: new Date().toISOString(),
  data,
});

describe('Orders workspace', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([apiResponseInterceptor])),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: 'o' })) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function loadDetail() {
    const fixture = TestBed.createComponent(OrdersDetailComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/orders/o').flush(envelope(order));
    http.expectOne('/api/v1/orders/o/plan').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/timeline').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/fulfillment-evidence').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/allocations').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/finance-requests').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/evidence-adjudications').flush(envelope([]));
    fixture.detectChanges();
    return fixture;
  }
  it('requires allocation expiry before dispatching real allocation evidence', () => {
    const c = loadDetail().componentInstance;
    c.evidenceLine = 'l';
    c.evidenceKind = 'ALLOCATION';
    c.evidenceReference = 'warehouse-1';
    c.evidenceAt = '2026-09-20T12:30';
    c.evidenceReason = 'Observed reservation';
    c.recordEvidence();
    http.expectNone((r) => r.method === 'POST');
    expect(c.error()).toContain('expiry');
  });
  it('sends allocation expiry and the exact causal shipment or release reference', () => {
    const c = loadDetail().componentInstance;
    c.evidenceLine = 'l';
    c.evidenceKind = 'ALLOCATION';
    c.evidenceReference = 'lot-1';
    c.evidenceAt = '2026-09-20T12:30';
    c.evidenceExpiresAt = '2026-09-21T12:30';
    c.evidenceReason = 'Reserved';
    c.recordEvidence();
    const request = http.expectOne('/api/v1/orders/o/evidence');
    expect(request.request.body.expiresAt).toBe(
      new Date(c.evidenceExpiresAt).toISOString(),
    );
    request.flush({}, { status: 422, statusText: 'Rejected' });
    c.stale.set(false);
    c.unresolved.set(0);
    c.evidenceKind = 'RELEASE';
    c.recordEvidence();
    http.expectNone((r) => r.method === 'POST');
    c.allocationReference = 'lot-1';
    c.evidenceReference = 'release-1';
    c.recordEvidence();
    const release = http.expectOne('/api/v1/orders/o/evidence');
    expect(release.request.body.allocationReference).toBe('lot-1');
    expect(release.request.body.expiresAt).toBeUndefined();
    release.flush({}, { status: 403, statusText: 'Forbidden' });
  });
  it('requires a recorded request and sends actual finance outcomes without optimistic completion', () => {
    const c = loadDetail().componentInstance;
    c.financeRequestKey = 'request-1';
    c.financeReference = 'provider-lookup';
    c.financeAt = '2026-09-20T12:30';
    c.financeReason = 'Verified response';
    c.financeOutcome = 'UNKNOWN';
    c.recordFinance();
    http.expectNone((r) => r.method === 'POST');
    c.finance.set([
      {
        requestKey: 'request-1',
        lineId: 'l',
        quantity: 5,
        acknowledgedQuantity: 0,
        status: 'PENDING',
        payload: '{}',
      },
    ]);
    c.recordFinance();
    const req = http.expectOne('/api/v1/orders/o/finance-acknowledgments');
    expect(req.request.body).toEqual(
      expect.objectContaining({
        requestKey: 'request-1',
        lineId: 'l',
        outcome: 'UNKNOWN',
        externalReference: 'provider-lookup',
        expectedVersion: 4,
      }),
    );
    expect(c.order()?.financeStatus).toBe('PENDING');
    req.error(new ProgressEvent('error'));
    c.recordFinance();
    http.expectNone((r) => r.method === 'POST');
    expect(sessionStorage.getItem('oms:pending:order:o')).not.toContain(
      'provider-lookup',
    );
  });
  it('prepares a selected finance line with a versioned reason', () => {
    const c = loadDetail().componentInstance;
    c.financeLine = 'l';
    c.financeReason = 'All obligations fulfilled';
    c.prepareFinance();
    const req = http.expectOne('/api/v1/orders/o/finance-requests');
    expect(req.request.body).toEqual(
      expect.objectContaining({
        lineId: 'l',
        reason: 'All obligations fulfilled',
        expectedVersion: 4,
      }),
    );
    req.flush({}, { status: 409, statusText: 'Not fulfilled' });
    expect(c.order()?.financeStatus).toBe('PENDING');
  });
  it('requires whole-order replacement confirmation and forbids known in-flight replacement', () => {
    const c = loadDetail().componentInstance;
    c.replacementReason = 'Correct source price';
    c.replace();
    http.expectNone((r) => r.method === 'POST');
    c.replacementConfirmed = true;
    c.replace();
    const req = http.expectOne('/api/v1/orders/o/replacement-requests');
    expect(req.request.body.reason).toBe('Correct source price');
    expect(req.request.body.expectedVersion).toBe(4);
    req.flush({}, { status: 409, statusText: 'Execution found' });
    expect(c.order()?.status).toBe('DRAFT');
    c.stale.set(false);
    c.order.update((value) => value && { ...value, status: 'IN_PROGRESS' });
    c.replace();
    http.expectNone((r) => r.method === 'POST');
  });
  it('requires authoritative no-effect confirmation and never submits a retry with it', () => {
    const c = loadDetail().componentInstance;
    c.tasks.set([
      {
        id: 'task/1',
        lineId: 'l',
        kind: 'ACTIVATION',
        status: 'UNKNOWN',
        templateVersion: 1,
        dispatchedAt: '2026-09-19T12:00:00Z',
      },
    ]);
    c.noEffectTask = 'task/1';
    c.noEffectReference = 'provider-proof';
    c.noEffectAt = '2026-09-20T12:30';
    c.noEffectReason = 'Authoritative lookup';
    c.recordNoEffect();
    http.expectNone((r) => r.method === 'POST');
    c.noEffectConfirmed = true;
    c.recordNoEffect();
    const req = http.expectOne('/api/v1/orders/o/tasks/task%2F1/no-effect');
    expect(req.request.body.evidenceReference).toBe('provider-proof');
    http.expectNone((r) => r.url.endsWith('/retry'));
    req.flush({}, { status: 403, statusText: 'Forbidden' });
  });
  it('keeps the original uncertain key until authoritative ABANDONED and fresh version are observed', () => {
    const c = loadDetail().componentInstance;
    c.commandReason = 'Submit';
    c.submit();
    const original = http.expectOne('/api/v1/orders/o/submit');
    const key = original.request.body.operationKey;
    original.error(new ProgressEvent('error'));
    c.stale.set(false);
    c.abandonKey = key;
    c.abandonReason = 'Never received';
    c.abandonConfirmed = true;
    c.abandon();
    const recovery = http.expectOne(
      `/api/v1/orders/o/operations/${key}/abandon`,
    );
    expect(recovery.request.body.operationKey).not.toBe(key);
    recovery.flush(envelope({ status: 'ABANDONED' }));
    expect(sessionStorage.getItem('oms:pending:order:o')).toContain(key);
    http
      .expectOne('/api/v1/orders/o')
      .flush(envelope({ ...order, version: 5 }));
    http.expectOne('/api/v1/orders/o/plan').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/timeline').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/fulfillment-evidence').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/allocations').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/finance-requests').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/evidence-adjudications').flush(envelope([]));
    http
      .expectOne(`/api/v1/orders/o/operations/${key}`)
      .flush(
        envelope({
          operationKey: key,
          orderId: 'o',
          status: 'ABANDONED',
          orderVersion: 5,
        }),
      );
    expect(c.unresolved()).toBe(0);
    expect(sessionStorage.getItem('oms:pending:order:o')).toBeNull();
  });
  it('shows initial loading and never disguises a forbidden list as empty', () => {
    const fixture = TestBed.createComponent(OrdersListComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading orders');
    http
      .expectOne((r) => r.url === '/api/v1/orders')
      .flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[role="alert"]').textContent,
    ).toContain('permission');
    expect(fixture.nativeElement.textContent).not.toContain('No orders match');
  });
  it('requires positive whole cancellation quantity, reason and explicit confirmation before HTTP', () => {
    const fixture = loadDetail();
    const c = fixture.componentInstance;
    c.cancelLine = 'l';
    c.cancelQuantity = 2;
    c.cancelReason = 'Requested';
    c.cancel();
    http.expectNone((r) => r.method === 'POST');
    c.cancelConfirmed = true;
    c.cancelQuantity = 1.5;
    c.cancel();
    http.expectNone((r) => r.method === 'POST');
    c.cancelQuantity = 2;
    c.cancel();
    const req = http.expectOne('/api/v1/orders/o/cancellation-requests');
    expect(req.request.body).toEqual(
      expect.objectContaining({
        lineId: 'l',
        quantity: 2,
        reason: 'Requested',
        expectedVersion: 4,
      }),
    );
    expect(req.request.body.operationKey).toBeTruthy();
    c.cancel();
    http.expectNone((r) => r.method === 'POST');
    req.flush({}, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Reload');
    expect(c.order()?.status).toBe('DRAFT');
  });
  it('records only real evidence and keeps 202 pending until server refresh', () => {
    const fixture = loadDetail();
    const c = fixture.componentInstance;
    c.evidenceLine = 'l';
    c.evidenceKind = 'DELIVERY';
    c.shipmentReference = 'shipment-123';
    c.evidenceQuantity = 1;
    c.evidenceReason = 'Carrier proof';
    c.recordEvidence();
    http.expectNone((r) => r.method === 'POST');
    c.evidenceReference = 'POD-123';
    c.evidenceAt = '2026-09-20T12:30';
    c.recordEvidence();
    const req = http.expectOne('/api/v1/orders/o/evidence');
    expect(req.request.body).toEqual(
      expect.objectContaining({
        kind: 'DELIVERY',
        externalReference: 'POD-123',
        quantity: 1,
      }),
    );
    req.flush(envelope({}), { status: 202, statusText: 'Accepted' });
    http
      .expectOne(`/api/v1/orders/o/operations/${req.request.body.operationKey}`)
      .flush(
        envelope({
          operationKey: req.request.body.operationKey,
          orderId: 'o',
          status: 'PENDING',
          orderVersion: 4,
        }),
      );
    http.expectOne('/api/v1/orders/o').flush(envelope(order));
    http.expectOne('/api/v1/orders/o/plan').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/timeline').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/fulfillment-evidence').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/allocations').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/finance-requests').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/evidence-adjudications').flush(envelope([]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('pending');
    expect(c.order()?.lines[0].delivered).toBe(0);
    expect(c.unresolved()).toBe(1);
  });
  it('keeps unknown command outcomes blocked until the operator reloads', () => {
    const fixture = loadDetail();
    const c = fixture.componentInstance;
    c.commandReason = 'Validated';
    c.submit();
    http.expectOne('/api/v1/orders/o/submit').error(new ProgressEvent('error'));
    c.submit();
    http.expectNone((r) => r.method === 'POST');
    expect(c.stale()).toBe(true);
    expect(c.error()).toContain('unknown');
  });
  it('does not create a new evidence operation after an uncertain outcome and reload', () => {
    const fixture = loadDetail();
    const c = fixture.componentInstance;
    c.evidenceLine = 'l';
    c.evidenceKind = 'DELIVERY';
    c.shipmentReference = 'shipment-123';
    c.evidenceQuantity = 1;
    c.evidenceReference = 'POD-private';
    c.evidenceAt = '2026-09-20T12:30';
    c.evidenceReason = 'private reason';
    c.recordEvidence();
    const req = http.expectOne('/api/v1/orders/o/evidence');
    const key = req.request.body.operationKey;
    req.error(new ProgressEvent('error'));
    fixture.destroy();
    const restored = TestBed.createComponent(OrdersDetailComponent);
    restored.detectChanges();
    http.expectOne('/api/v1/orders/o').flush(envelope(order));
    http.expectOne('/api/v1/orders/o/plan').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/timeline').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/fulfillment-evidence').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/allocations').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/finance-requests').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/evidence-adjudications').flush(envelope([]));
    const lookup = http.expectOne(`/api/v1/orders/o/operations/${key}`);
    lookup.flush(
      envelope({
        operationKey: key,
        orderId: 'o',
        status: 'NOT_FOUND',
        orderVersion: 4,
      }),
    );
    restored.componentInstance.commandReason = 'Changed request';
    restored.componentInstance.submit();
    http.expectNone((r) => r.method === 'POST');
    expect(sessionStorage.getItem('oms:pending:order:o')).not.toContain(
      'POD-private',
    );
    expect(sessionStorage.getItem('oms:pending:order:o')).not.toContain(
      'private reason',
    );
    restored.componentInstance.load();
    http.expectOne('/api/v1/orders/o').flush(envelope(order));
    http.expectOne('/api/v1/orders/o/plan').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/timeline').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/fulfillment-evidence').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/allocations').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/finance-requests').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/evidence-adjudications').flush(envelope([]));
    http.expectOne(`/api/v1/orders/o/operations/${key}`).flush(
      envelope({
        operationKey: key,
        orderId: 'o',
        status: 'SUCCEEDED',
        orderVersion: 4,
      }),
    );
    expect(restored.componentInstance.unresolved()).toBe(0);
    expect(sessionStorage.getItem('oms:pending:order:o')).toBeNull();
    restored.destroy();
  });
  it('submits and holds with reasons and trusts only the refreshed server projection', () => {
    const fixture = loadDetail();
    const c = fixture.componentInstance;
    c.submit();
    http.expectNone((r) => r.method === 'POST');
    c.commandReason = 'Ready for fulfillment';
    c.submit();
    expect(c.order()?.status).toBe('DRAFT');
    http
      .expectOne('/api/v1/orders/o/submit')
      .flush(envelope({ ...order, status: 'SUBMITTED' }));
    http
      .expectOne('/api/v1/orders/o')
      .flush(envelope({ ...order, status: 'SUBMITTED', version: 5 }));
    http.expectOne('/api/v1/orders/o/plan').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/timeline').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/fulfillment-evidence').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/allocations').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/finance-requests').flush(envelope([]));
    http.expectOne('/api/v1/orders/o/evidence-adjudications').flush(envelope([]));
    expect(c.order()?.status).toBe('SUBMITTED');
    c.holdActive = true;
    c.commandReason = 'Review shipping instructions';
    c.hold();
    const req = http.expectOne('/api/v1/orders/o/holds');
    expect(req.request.body).toEqual(
      expect.objectContaining({
        active: true,
        expectedVersion: 5,
        reason: 'Review shipping instructions',
      }),
    );
    req.flush({}, { status: 422, statusText: 'Unprocessable Entity' });
    expect(c.order()?.holds).toEqual([]);
    expect(c.stale()).toBe(true);
  });
  it('retains the conversion idempotency key after a lost response', () => {
    const fixture = TestBed.createComponent(OrdersListComponent);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/v1/orders')
      .flush({
        ...envelope([]),
        pagination: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
      });
    const c = fixture.componentInstance;
    c.snapshotId = 's';
    c.quoteId = 'q';
    c.revision = 1;
    c.convert();
    const first = http.expectOne('/api/v1/orders/from-quote');
    const key = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error'));
    c.convert();
    const second = http.expectOne('/api/v1/orders/from-quote');
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    second.flush({}, { status: 403, statusText: 'Forbidden' });
    expect(c.conversionError()).toContain('permission');
  });
  it('sends finance evidence without claiming success when permission is denied', () => {
    const fixture = loadDetail();
    const c = fixture.componentInstance;
    c.evidenceLine = 'l';
    c.evidenceKind = 'FINANCE_ACK';
    c.evidenceQuantity = 1;
    c.evidenceReference = 'INV-1';
    c.evidenceAt = '2026-09-20T12:30';
    c.evidenceReason = 'Invoice acknowledged';
    c.recordEvidence();
    const req = http.expectOne('/api/v1/orders/o/evidence');
    expect(req.request.body.kind).toBe('FINANCE_ACK');
    req.flush({}, { status: 403, statusText: 'Forbidden' });
    expect(c.notice()).toBe('');
    expect(c.order()?.financeStatus).toBe('PENDING');
  });
  it('refreshes list filters at page zero and clears stale rows on failure', () => {
    const fixture = TestBed.createComponent(OrdersListComponent);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/v1/orders')
      .flush({
        ...envelope([order]),
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      });
    fixture.componentInstance.q = 'widget';
    fixture.componentInstance.load(0);
    const req = http.expectOne((r) => r.url === '/api/v1/orders');
    expect(req.request.params.get('q')).toBe('widget');
    req.flush({}, { status: 503, statusText: 'Unavailable' });
    expect(fixture.componentInstance.rows()).toEqual([]);
  });
  it('requires an owner and reason for exception assignment and sends the exception version', () => {
    const fixture = TestBed.createComponent(OrdersExceptionsComponent);
    fixture.detectChanges();
    const exception = {
      id: 'e',
      orderId: 'o',
      status: 'OPEN',
      category: 'UNKNOWN_OUTCOME',
      version: 7,
      reason: 'Timeout',
      nextAction: 'RECONCILE',
    };
    http
      .expectOne((r) => r.url === '/api/v1/order-exceptions')
      .flush({
        ...envelope([exception]),
        pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      });
    const c = fixture.componentInstance;
    c.select(c.rows()[0]);
    c.assign();
    http.expectNone((r) => r.method === 'POST');
    c.owner = 'operator';
    c.reason = 'Investigating';
    c.assign();
    const req = http.expectOne('/api/v1/order-exceptions/e/assign');
    expect(req.request.body).toEqual(
      expect.objectContaining({
        owner: 'operator',
        reason: 'Investigating',
        expectedVersion: 7,
      }),
    );
    req.flush({}, { status: 403, statusText: 'Forbidden' });
    expect(c.error()).toContain('permission');
  });
  it('blocks repeated exception recovery across a reload while the earlier operation is unknown', () => {
    const fixture = TestBed.createComponent(OrdersExceptionsComponent);
    fixture.detectChanges();
    const exception = {
      id: 'e',
      orderId: 'o',
      status: 'OPEN',
      category: 'ERROR',
      version: 7,
    };
    const page = {
      ...envelope([exception]),
      pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
    };
    http.expectOne((r) => r.url === '/api/v1/order-exceptions').flush(page);
    const c = fixture.componentInstance;
    c.select(c.rows()[0]);
    c.owner = 'operator';
    c.reason = 'Investigating';
    c.assign();
    const req = http.expectOne('/api/v1/order-exceptions/e/assign');
    const key = req.request.body.operationKey;
    req.error(new ProgressEvent('error'));
    fixture.destroy();
    const restored = TestBed.createComponent(OrdersExceptionsComponent);
    restored.detectChanges();
    http.expectOne((r) => r.url === '/api/v1/order-exceptions').flush(page);
    http.expectOne(`/api/v1/order-exceptions/e/operations/${key}`).flush(
      envelope({
        operationKey: key,
        orderId: 'o',
        status: 'UNKNOWN',
        orderVersion: 4,
      }),
    );
    const next = restored.componentInstance;
    next.select(next.rows()[0]);
    next.owner = 'other';
    next.reason = 'Changed';
    next.assign();
    http.expectNone((r) => r.method === 'POST');
    expect(next.blocked().has('e')).toBe(true);
    restored.destroy();
  });
});
