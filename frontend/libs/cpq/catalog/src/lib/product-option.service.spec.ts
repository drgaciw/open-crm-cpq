import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ProductOptionService } from './product-option.service';
import {
  OptionCreateRequest,
  OptionGroupCreateRequest,
  ProductOptionGroupDto,
} from '@frontend/shared-models';

function group(
  partial: Partial<ProductOptionGroupDto> &
    Pick<ProductOptionGroupDto, 'id' | 'productId' | 'name'>,
): ProductOptionGroupDto {
  return {
    uuid: `g-${partial.id}`,
    selectionType: 'SINGLE',
    minSelections: 0,
    sortOrder: 0,
    options: [],
    ...partial,
  };
}

describe('ProductOptionService', () => {
  let service: ProductOptionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ProductOptionService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ProductOptionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listGroupsByProduct GETs /api/v1/product-options/groups/product/:id', async () => {
    const promise = firstValueFrom(service.listGroupsByProduct(12));
    const req = httpMock.expectOne(
      '/api/v1/product-options/groups/product/12',
    );
    expect(req.request.method).toBe('GET');
    req.flush([group({ id: 1, productId: 12, name: 'Tier' })]);
    await expect(promise).resolves.toEqual([
      expect.objectContaining({ id: 1, name: 'Tier' }),
    ]);
  });

  it('createGroup POSTs OptionGroupCreateRequest', async () => {
    const body: OptionGroupCreateRequest = {
      productId: 12,
      name: 'Features',
      selectionType: 'MULTIPLE',
      minSelections: 0,
      maxSelections: 3,
    };
    const promise = firstValueFrom(service.createGroup(body));
    const req = httpMock.expectOne('/api/v1/product-options/groups');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(group({ id: 2, productId: 12, name: 'Features', selectionType: 'MULTIPLE' }));
    await expect(promise).resolves.toMatchObject({ id: 2, name: 'Features' });
  });

  it('addOption POSTs to groups/:id/options', async () => {
    const body: OptionCreateRequest = {
      name: 'SSO',
      priceAdjustment: 10,
      adjustmentType: 'ABSOLUTE',
    };
    const promise = firstValueFrom(service.addOption(5, body));
    const req = httpMock.expectOne('/api/v1/product-options/groups/5/options');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(
      group({
        id: 5,
        productId: 12,
        name: 'Features',
        options: [
          {
            id: 9,
            uuid: 'o-9',
            productId: 12,
            name: 'SSO',
            priceAdjustment: 10,
            adjustmentType: 'ABSOLUTE',
            defaultOption: false,
            sortOrder: 0,
          },
        ],
      }),
    );
    await expect(promise).resolves.toMatchObject({
      id: 5,
      options: [expect.objectContaining({ name: 'SSO' })],
    });
  });

  it('deleteGroup DELETEs /api/v1/product-options/groups/:id', async () => {
    const promise = firstValueFrom(service.deleteGroup(5));
    const req = httpMock.expectOne('/api/v1/product-options/groups/5');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await expect(promise).resolves.toBeNull();
  });

  it('never calls CRM /api/v1/customers', async () => {
    const promise = firstValueFrom(service.listGroupsByProduct(1));
    const req = httpMock.expectOne(
      '/api/v1/product-options/groups/product/1',
    );
    expect(req.request.url).not.toContain('/api/v1/customers');
    req.flush([]);
    await promise;
  });
});
