import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ContractService, isAccessDenied } from './contract.service';
import { MOCK_ASSETS, MOCK_CONTRACTS } from './contracts.mock';

describe('ContractService', () => {
  let service: ContractService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ContractService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists contracts from the paginated subscriptions endpoint, newest first', async () => {
    const page = firstValueFrom(service.list(1, 25));
    const req = http.expectOne((r) => r.url === '/api/v1/subscriptions');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('25');
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    req.flush({ content: MOCK_CONTRACTS, number: 1, size: 25, totalElements: 27, totalPages: 2 });
    expect((await page).content).toEqual(MOCK_CONTRACTS);
  });

  it('passes a column sort through', () => {
    service.list(0, 20, 'planName,asc').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/subscriptions');
    expect(req.request.params.get('sort')).toBe('planName,asc');
    req.flush({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('gets one contract by id', async () => {
    const one = firstValueFrom(service.get(7));
    http.expectOne('/api/v1/subscriptions/7').flush(MOCK_CONTRACTS[0]);
    expect(await one).toEqual(MOCK_CONTRACTS[0]);
  });

  it('lists all assets, or a contract\'s assets by subscriptionId', async () => {
    const all = firstValueFrom(service.assets());
    const allReq = http.expectOne((r) => r.url === '/api/v1/assets');
    expect(allReq.request.params.keys()).toEqual([]);
    allReq.flush(MOCK_ASSETS);
    expect(await all).toEqual(MOCK_ASSETS);

    const forContract = firstValueFrom(service.assets({ subscriptionId: 7 }));
    const req = http.expectOne((r) => r.url === '/api/v1/assets');
    expect(req.request.params.get('subscriptionId')).toBe('7');
    req.flush([]);
    expect(await forContract).toEqual([]);
  });
});

describe('isAccessDenied', () => {
  it('is true only for a 403 answer', () => {
    expect(isAccessDenied(new HttpErrorResponse({ status: 403 }))).toBe(true);
    expect(isAccessDenied(new HttpErrorResponse({ status: 404 }))).toBe(false);
    expect(isAccessDenied(new Error('boom'))).toBe(false);
  });
});
