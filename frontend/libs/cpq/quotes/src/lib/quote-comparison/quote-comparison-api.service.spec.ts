import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { apiResponseInterceptor } from '@frontend/shared-data-access';
import { QuoteComparisonApiService } from './quote-comparison-api.service';
import { QuoteComparison } from './quote-comparison.model';

describe('QuoteComparisonApiService', () => {
  let service: QuoteComparisonApiService;
  let http: HttpTestingController;
  const comparison: QuoteComparison = {
    dealExternalId: 'deal-1', customerExternalId: 'account-1', primaryQuoteId: null,
    sameCurrency: true, quotes: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([apiResponseInterceptor])), provideHttpClientTesting(),
    ] });
    service = TestBed.inject(QuoteComparisonApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('loads the anchor comparison and unwraps the actual API envelope', async () => {
    const result = firstValueFrom(service.compare(7));
    const request = http.expectOne('/api/v1/quotes/7/comparison');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush({ success: true, timestamp: '2026-11-06T09:00:00Z', data: comparison });
    expect(await result).toEqual(comparison);
  });

  it('marks a primary without sending caller identity or group fields', async () => {
    const result = firstValueFrom(service.markPrimary(8));
    const request = http.expectOne('/api/v1/quotes/8/primary');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({ success: true, timestamp: '2026-11-06T09:00:00Z', data: { ...comparison, primaryQuoteId: 8 } });
    expect((await result).primaryQuoteId).toBe(8);
  });

  it('preserves backend scope refusals instead of returning an empty comparison', async () => {
    const result = firstValueFrom(service.compare(7));
    const rejected = expect(result).rejects.toMatchObject({ status: 400 });
    http.expectOne('/api/v1/quotes/7/comparison').flush({
      success: false, errorCode: 'QUOTE_COMPARISON_CUSTOMER_MISMATCH', message: 'Customer mismatch',
    }, { status: 400, statusText: 'Bad Request' });
    await rejected;
  });
});
