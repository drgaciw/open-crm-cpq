import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { apiResponseInterceptor } from '@frontend/shared-data-access';
import { of } from 'rxjs';
import { OrdersDetailComponent } from './orders-detail.component';

describe('Evidence disavowal', () => {
  let http: HttpTestingController;
  const wrap = (data: unknown) => ({
    success: true,
    data,
    timestamp: '2026-09-20T12:00:00Z',
  });
  const conflict = {
    id: 'conflict',
    lineId: 'line',
    kind: 'SHIPMENT',
    quantity: 2,
    externalReference: 'incorrect-observation',
    allocationReference: null,
    shipmentReference: null,
    occurredAt: '2026-09-20T10:00:00Z',
    expiresAt: null,
    actor: 'recorder',
    reason: 'observation',
    status: 'CONFLICT',
    detail: 'contradictory',
  };
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([apiResponseInterceptor])),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ id: 'order' })) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  function load() {
    const fixture = TestBed.createComponent(OrdersDetailComponent);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/orders/order')
      .flush(
        wrap({
          id: 'order',
          orderNumber: 'ORD',
          version: 7,
          status: 'IN_PROGRESS',
          financeStatus: 'PENDING',
          holds: [],
          lines: [{ id: 'line', shipped: 0, delivered: 0 }],
          snapshot: { lines: [] },
        }),
      );
    for (const path of ['plan', 'timeline', 'allocations', 'finance-requests'])
      http.expectOne('/api/v1/orders/order/' + path).flush(wrap([]));
    http
      .expectOne('/api/v1/orders/order/fulfillment-evidence')
      .flush(
        wrap([
          conflict,
          { ...conflict, id: 'applied', status: 'APPLIED' },
          { ...conflict, id: 'disavowed' },
        ]),
      );
    http
      .expectOne('/api/v1/orders/order/evidence-adjudications')
      .flush(
        wrap([
          {
            id: 'a',
            evidenceId: 'disavowed',
            authorityReference: 'previous-proof',
            occurredAt: '2026-09-20T11:00:00Z',
            actor: 'reviewer',
            reason: 'disavowed',
            recordedAt: '2026-09-20T11:01:00Z',
          },
        ]),
      );
    fixture.detectChanges();
    return fixture;
  }
  it('allows only unadjudicated conflicts on current order and renders immutable dispositions', () => {
    const fixture = load();
    const c = fixture.componentInstance;
    expect(c.adjudicableEvidence().map((e) => e.id)).toEqual(['conflict']);
    expect(fixture.nativeElement.textContent).toContain('previous-proof');
    expect(c.evidence().find((e) => e.id === 'disavowed')?.status).toBe(
      'CONFLICT',
    );
    c.adjudicationEvidence = 'applied';
    c.adjudicationConfirmed = true;
    c.adjudicationReference = 'proof';
    c.adjudicationAt = '2026-09-20T12:00:00Z';
    c.adjudicationReason = 'reviewed';
    c.adjudicateEvidence();
    http.expectNone((r) => r.method === 'POST');
  });
  it('requires confirmation and actual authority facts then sends only a versioned disavowal', () => {
    const c = load().componentInstance;
    c.adjudicationEvidence = 'conflict';
    c.adjudicationReason = 'independently disproved';
    c.adjudicateEvidence();
    http.expectNone((r) => r.method === 'POST');
    c.adjudicationConfirmed = true;
    c.adjudicationReference = ' authority-proof ';
    c.adjudicationAt = '2026-09-20T12:00:00Z';
    c.adjudicateEvidence();
    const req = http.expectOne(
      '/api/v1/orders/order/evidence/conflict/adjudications',
    );
    expect(req.request.body).toEqual({
      expectedVersion: 7,
      operationKey: expect.any(String),
      reason: 'independently disproved',
      authorityReference: 'authority-proof',
      occurredAt: '2026-09-20T12:00:00.000Z',
    });
    expect(c.order()?.lines[0].shipped).toBe(0);
    expect(c.order()?.status).toBe('IN_PROGRESS');
    req.error(new ProgressEvent('lost'));
    c.adjudicateEvidence();
    http.expectNone((r) => r.method === 'POST');
    expect(sessionStorage.getItem('oms:pending:order:order')).not.toContain(
      'authority-proof',
    );
  });
  it('rejects authority predating the observation and handles forbidden authority without changing facts', () => {
    const c = load().componentInstance;
    c.adjudicationEvidence = 'conflict';
    c.adjudicationConfirmed = true;
    c.adjudicationReference = 'proof';
    c.adjudicationReason = 'reviewed';
    c.adjudicationAt = '2020-01-01T00:00:00Z';
    c.adjudicateEvidence();
    http.expectNone((r) => r.method === 'POST');
    c.adjudicationAt = '2026-09-20T12:00:00Z';
    c.adjudicateEvidence();
    http
      .expectOne('/api/v1/orders/order/evidence/conflict/adjudications')
      .flush({}, { status: 403, statusText: 'Forbidden' });
    expect(c.error()).toContain('permission');
    expect(c.evidence()[0].status).toBe('CONFLICT');
    expect(c.order()?.lines[0].shipped).toBe(0);
  });
});
