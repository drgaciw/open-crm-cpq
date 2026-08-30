import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { PricingCustomerService } from './pricing-customer.service';
import { PricingCustomerDto, SpringPageResponse } from '@frontend/shared-models';

function customer(
  partial: Partial<PricingCustomerDto> & Pick<PricingCustomerDto, 'id' | 'externalId' | 'name'>,
): PricingCustomerDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('PricingCustomerService', () => {
  let service: PricingCustomerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PricingCustomerService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PricingCustomerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list GETs /api/v1/pricing-customers', () => {
    const page: SpringPageResponse<PricingCustomerDto> = {
      content: [customer({ id: 1, externalId: 'c1', name: 'Acme', segment: 'ENTERPRISE' })],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };
    service.list(0, 20, 'name,asc', 'ENTERPRISE');
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/pricing-customers' && r.params.get('segment') === 'ENTERPRISE',
    );
    expect(req.request.method).toBe('GET');
    req.flush(page);
    expect(service.customers()).toEqual(page.content);
  });

  it('listBySegment forwards segment filter', () => {
    service.listBySegment('SMB');
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/pricing-customers' && r.params.get('segment') === 'SMB',
    );
    req.flush({
      content: [],
      number: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    } satisfies SpringPageResponse<PricingCustomerDto>);
  });

  it('create POSTs to /api/v1/pricing-customers', async () => {
    const promise = firstValueFrom(
      service.create({ externalId: 'c2', name: 'Beta' }),
    );
    const req = httpMock.expectOne('/api/v1/pricing-customers');
    expect(req.request.method).toBe('POST');
    req.flush(customer({ id: 2, externalId: 'c2', name: 'Beta' }));
    await expect(promise).resolves.toMatchObject({ id: 2 });
  });
});
