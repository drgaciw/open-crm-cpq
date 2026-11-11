import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuoteComparisonService } from './quote-comparison.service';

describe('QuoteComparisonService', () => {
  let service: QuoteComparisonService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(QuoteComparisonService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('scopes comparison by encoded opportunity and optional quote IDs', () => {
    service.compare('deal & 1', [1, 2]).subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/quotes/compare');
    expect(req.request.params.get('dealExternalId')).toBe('deal & 1');
    expect(req.request.params.get('quoteIds')).toBe('1,2');
    req.flush({ dealExternalId: 'deal & 1', quotes: [] });
  });
  it('omits empty filters and posts primary mutation without authoring a tenant', () => {
    service.compare('deal').subscribe();
    const compare = http.expectOne(r => r.url === '/api/v1/quotes/compare');
    expect(compare.request.params.has('quoteIds')).toBe(false);
    compare.flush({ quotes: [] });
    service.markPrimary(2).subscribe();
    const primary = http.expectOne('/api/v1/quotes/2/primary');
    expect(primary.request.method).toBe('POST');
    expect(primary.request.body).toEqual({});
    primary.flush({ id: 2, primary: true });
  });
});
