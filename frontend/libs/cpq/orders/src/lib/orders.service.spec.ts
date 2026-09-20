import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { apiResponseInterceptor } from '@frontend/shared-data-access';
import { OrdersService, orderError } from './orders.service';

describe('OrdersService HTTP contract', () => {
  let service: OrdersService;
  let http: HttpTestingController;
  const envelope = (data: unknown) => ({
    success: true,
    timestamp: new Date().toISOString(),
    data,
  });
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiResponseInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(OrdersService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('preserves envelope pagination and filters through the real app interceptor', () => {
    const received = vi.fn();
    service
      .list(2, {
        q: 'ACME',
        status: 'DRAFT',
        customerId: 'a',
        externalReference: 'POD-1',
        hasExceptions: 'false',
      })
      .subscribe(received);
    const req = http.expectOne((r) => r.url === '/api/v1/orders');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('q')).toBe('ACME');
    expect(req.request.params.get('customerId')).toBe('a');
    expect(req.request.params.get('status')).toBe('DRAFT');
    expect(req.request.params.get('externalReference')).toBe('POD-1');
    expect(req.request.params.get('hasExceptions')).toBe('false');
    req.flush({
      ...envelope([{ id: 'o' }]),
      pagination: { page: 2, size: 20, totalElements: 43, totalPages: 3 },
    });
    expect(received).toHaveBeenCalledWith(
      expect.objectContaining({
        number: 2,
        totalElements: 43,
        content: [{ id: 'o' }],
      }),
    );
  });
  it('converts references only and supplies a stable caller-owned idempotency key', () => {
    service
      .convert(
        { snapshotId: 's', quoteId: 'q', acceptedRevision: 2 },
        'conversion-key',
      )
      .subscribe();
    const req = http.expectOne('/api/v1/orders/from-quote');
    expect(req.request.headers.get('Idempotency-Key')).toBe('conversion-key');
    expect(req.request.body).toEqual({
      snapshotId: 's',
      quoteId: 'q',
      acceptedRevision: 2,
    });
    req.flush(envelope({ id: 'o' }));
  });
  it('sends fenced commands and preserves 202 as pending rather than completed', () => {
    const received = vi.fn();
    const body = {
      expectedVersion: 4,
      operationKey: 'op',
      reason: 'Customer requested',
      lineId: 'l',
      quantity: 2,
    };
    service.command('a/b', 'cancellation-requests', body).subscribe(received);
    const req = http.expectOne('/api/v1/orders/a%2Fb/cancellation-requests');
    expect(req.request.body).toEqual(body);
    req.flush(envelope({ id: 'o' }), { status: 202, statusText: 'Accepted' });
    expect(received).toHaveBeenCalledWith(
      expect.objectContaining({ status: 202 }),
    );
  });
  it('maps forbidden, conflicts and ambiguous network outcomes to actionable errors', () => {
    expect(orderError({ status: 403 })).toContain('permission');
    expect(orderError({ status: 409 })).toContain('Reload');
    expect(orderError({ status: 0 })).toContain('unknown');
    expect(orderError({ status: 404 })).toContain('unavailable');
  });
  it('uses versioned exception assignment and resolution endpoints', () => {
    const body = {
      expectedVersion: 3,
      reason: 'Review',
      operationKey: 'x',
      owner: 'operator',
    };
    service.exceptionCommand('e', 'assign', body).subscribe();
    const req = http.expectOne('/api/v1/order-exceptions/e/assign');
    expect(req.request.body).toEqual(body);
    req.flush(envelope({}));
  });
});
