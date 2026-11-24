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

  it('list error.set appends errorCode as message [CODE]', () => {
    service.list();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/pricing-customers');
    req.flush(
      {
        message: 'Failed to load pricing customers',
        errorCode: 'PRICING_CUSTOMERS_UNAVAILABLE',
      },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.error()).toBe(
      'Failed to load pricing customers [PRICING_CUSTOMERS_UNAVAILABLE]',
    );
  });

  it('keeps the newer segment rows and pagination after a superseded success', () => {
    service.listBySegment('ENTERPRISE');
    const older = httpMock.expectOne(
      (r) => r.url === '/api/v1/pricing-customers' && r.params.get('segment') === 'ENTERPRISE',
    );
    service.listBySegment('SMB', 0, 10);
    const current = httpMock.expectOne(
      (r) => r.url === '/api/v1/pricing-customers' && r.params.get('segment') === 'SMB',
    );
    const currentRow = customer({ id: 2, externalId: 'c', name: 'Current result', segment: 'SMB' });
    current.flush({ content: [currentRow], number: 0, size: 10, totalElements: 1, totalPages: 1 });
    older.flush({
      content: [customer({ id: 1, externalId: 'c', name: 'Older result', segment: 'ENTERPRISE' })],
      number: 0, size: 20, totalElements: 1, totalPages: 1,
    });

    expect(service.customers()).toEqual([currentRow]);
    expect(service.pagination()).toEqual({ page: 0, size: 10, totalElements: 1, totalPages: 1 });
    expect(service.loading()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('keeps the current list loading and error when a superseded request fails', () => {
    service.listBySegment('ENTERPRISE');
    const older = httpMock.expectOne(
      (r) => r.url === '/api/v1/pricing-customers' && r.params.get('segment') === 'ENTERPRISE',
    );
    service.listBySegment('SMB');
    const current = httpMock.expectOne(
      (r) => r.url === '/api/v1/pricing-customers' && r.params.get('segment') === 'SMB',
    );

    older.flush({ message: 'Superseded query failed' }, { status: 503, statusText: 'Service Unavailable' });
    expect(service.loading()).toBe(true);
    expect(service.error()).toBeNull();

    current.flush({ message: 'Current query failed' }, { status: 503, statusText: 'Service Unavailable' });
    expect(service.loading()).toBe(false);
    expect(service.error()).toBe('Current query failed');
  });

  it('getById error.set appends errorCode as message [CODE]', () => {
    service.getById(99);
    const req = httpMock.expectOne('/api/v1/pricing-customers/99');
    req.flush(
      { message: 'Pricing customer not found', errorCode: 'RESOURCE_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.error()).toBe(
      'Pricing customer not found [RESOURCE_NOT_FOUND]',
    );
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
