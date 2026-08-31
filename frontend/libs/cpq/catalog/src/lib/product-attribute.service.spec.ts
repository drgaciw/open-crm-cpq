import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ProductAttributeService } from './product-attribute.service';
import {
  AttributeValueDto,
  AttributeValueSetRequest,
  ProductAttributeDto,
  SpringPageResponse,
} from '@frontend/shared-models';

function attribute(
  partial: Partial<ProductAttributeDto> &
    Pick<ProductAttributeDto, 'id' | 'name'>,
): ProductAttributeDto {
  return {
    uuid: `a-${partial.id}`,
    dataType: 'STRING',
    required: false,
    sortOrder: 0,
    ...partial,
  };
}

describe('ProductAttributeService', () => {
  let service: ProductAttributeService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ProductAttributeService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ProductAttributeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listDefinitions GETs paged /api/v1/product-attributes', async () => {
    const page: SpringPageResponse<ProductAttributeDto> = {
      content: [attribute({ id: 1, name: 'region', displayName: 'Region' })],
      number: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    };
    const promise = firstValueFrom(service.listDefinitions(0, 50));
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/product-attributes' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '50',
    );
    expect(req.request.method).toBe('GET');
    req.flush(page);
    await expect(promise).resolves.toEqual(page);
  });

  it('listValues GETs /api/v1/product-attributes/products/:id/values', async () => {
    const values: AttributeValueDto[] = [
      {
        id: 10,
        uuid: 'v-10',
        productId: 12,
        attributeId: 1,
        attributeName: 'region',
        value: 'US',
      },
    ];
    const promise = firstValueFrom(service.listValues(12));
    const req = httpMock.expectOne(
      '/api/v1/product-attributes/products/12/values',
    );
    expect(req.request.method).toBe('GET');
    req.flush(values);
    await expect(promise).resolves.toEqual(values);
  });

  it('setValue POSTs AttributeValueSetRequest', async () => {
    const body: AttributeValueSetRequest = {
      attributeId: 1,
      value: 'EU',
    };
    const promise = firstValueFrom(service.setValue(12, body));
    const req = httpMock.expectOne(
      '/api/v1/product-attributes/products/12/values',
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({
      id: 11,
      uuid: 'v-11',
      productId: 12,
      attributeId: 1,
      attributeName: 'region',
      value: 'EU',
    } satisfies AttributeValueDto);
    await expect(promise).resolves.toMatchObject({ value: 'EU' });
  });

  it('never calls CRM /api/v1/customers', async () => {
    const promise = firstValueFrom(service.listValues(1));
    const req = httpMock.expectOne(
      '/api/v1/product-attributes/products/1/values',
    );
    expect(req.request.url).not.toContain('/api/v1/customers');
    req.flush([]);
    await promise;
  });
});
