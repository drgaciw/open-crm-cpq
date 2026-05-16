import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { CpqApiPaths } from './cpq-api-paths';
import { CpqPricingClient } from './cpq-pricing-client.service';
import { CpqQuoteClient } from './cpq-quote-flow-client.service';

describe('Cpq typed clients', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('CpqPricingClient lists via gateway pricing alias', async () => {
    const client = TestBed.inject(CpqPricingClient);
    const listPromise = firstValueFrom(client.list({ page: 0, size: 10 }));

    const req = httpMock.expectOne(
      (r) => r.url === CpqApiPaths.pricing && r.method === 'GET'
    );
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });

    await listPromise;
  });

  it('CpqQuoteClient reads by id via gateway quote alias', async () => {
    const client = TestBed.inject(CpqQuoteClient);
    const getPromise = firstValueFrom(client.getById('q-1'));

    const req = httpMock.expectOne(`${CpqApiPaths.quotes}/q-1`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'q-1' });

    await getPromise;
  });
});
