import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ProductService } from './product.service';
import { ProductDto, SpringPageResponse } from '@frontend/shared-models';

function product(
  partial: Partial<ProductDto> & Pick<ProductDto, 'id' | 'sku' | 'name'>,
): ProductDto {
  return {
    uuid: `u-${partial.id}`,
    productType: 'SAAS_FLAT_RATE',
    category: 'SUBSCRIPTION',
    status: 'ACTIVE',
    configurable: false,
    bundle: false,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('ProductService', () => {
  let service: ProductService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProductService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list GETs /api/v1/products and sets products signal', () => {
    const page: SpringPageResponse<ProductDto> = {
      content: [product({ id: 1, sku: 'SKU-1', name: 'Core' })],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };

    service.list(0, 20, 'name,asc', { status: 'ACTIVE', category: 'SUBSCRIPTION' });
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/products' &&
        r.params.get('status') === 'ACTIVE' &&
        r.params.get('category') === 'SUBSCRIPTION',
    );
    expect(req.request.method).toBe('GET');
    req.flush(page);

    expect(service.products()).toEqual(page.content);
    expect(service.loading()).toBe(false);
    expect(service.pagination().totalElements).toBe(1);
  });

  it('getById sets selectedProduct', () => {
    service.getById(9);
    const req = httpMock.expectOne('/api/v1/products/9');
    expect(req.request.method).toBe('GET');
    req.flush(product({ id: 9, sku: 'SKU-9', name: 'Addon' }));
    expect(service.selectedProduct()?.id).toBe(9);
  });

  it('search GETs /api/v1/products/search', () => {
    service.search('core');
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/products/search' && r.params.get('q') === 'core',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      content: [product({ id: 2, sku: 'SKU-2', name: 'Core Plus' })],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    } satisfies SpringPageResponse<ProductDto>);
    expect(service.products()[0].name).toBe('Core Plus');
  });

  it('list error.set is message [CODE] when errorCode present', () => {
    service.list();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/products');
    req.flush(
      { message: 'List failed', errorCode: 'PRODUCT_LIST_DENIED' },
      { status: 403, statusText: 'Forbidden' },
    );
    expect(service.error()).toBe('List failed [PRODUCT_LIST_DENIED]');
  });

  it('getById error.set is message [CODE] when errorCode present', () => {
    service.getById(9);
    const req = httpMock.expectOne('/api/v1/products/9');
    req.flush(
      { message: 'Product missing', errorCode: 'PRODUCT_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.error()).toBe('Product missing [PRODUCT_NOT_FOUND]');
  });

  it('search error.set is message [CODE] when errorCode present', () => {
    service.search('core');
    const req = httpMock.expectOne((r) => r.url === '/api/v1/products/search');
    req.flush(
      { message: 'Search failed', errorCode: 'PRODUCT_SEARCH_DENIED' },
      { status: 403, statusText: 'Forbidden' },
    );
    expect(service.error()).toBe('Search failed [PRODUCT_SEARCH_DENIED]');
  });

  it('create POSTs to /api/v1/products', async () => {
    const promise = firstValueFrom(
      service.create({
        sku: 'NEW',
        name: 'New Product',
        productType: 'HARDWARE',
        category: 'ADD_ON',
      }),
    );
    const req = httpMock.expectOne('/api/v1/products');
    expect(req.request.method).toBe('POST');
    req.flush(product({ id: 3, sku: 'NEW', name: 'New Product' }));
    await expect(promise).resolves.toMatchObject({ id: 3, sku: 'NEW' });
  });

  it('activate / deprecate / retire hit lifecycle paths', async () => {
    const activate = firstValueFrom(service.activate(4));
    httpMock.expectOne('/api/v1/products/4/activate').flush(
      product({ id: 4, sku: 'S4', name: 'A', status: 'ACTIVE' }),
    );
    await expect(activate).resolves.toMatchObject({ status: 'ACTIVE' });

    const deprecate = firstValueFrom(service.deprecate(4));
    httpMock.expectOne('/api/v1/products/4/deprecate').flush(
      product({ id: 4, sku: 'S4', name: 'A', status: 'DEPRECATED' }),
    );
    await expect(deprecate).resolves.toMatchObject({ status: 'DEPRECATED' });

    const retire = firstValueFrom(service.retire(4, 'EOL'));
    const retireReq = httpMock.expectOne(
      (r) => r.url === '/api/v1/products/4/retire' && r.params.get('reason') === 'EOL',
    );
    expect(retireReq.request.method).toBe('POST');
    retireReq.flush(product({ id: 4, sku: 'S4', name: 'A', status: 'RETIRED' }));
    await expect(retire).resolves.toMatchObject({ status: 'RETIRED' });
  });

  it('getBySku GETs /api/v1/products/sku/:sku', async () => {
    const promise = firstValueFrom(service.getBySku('SKU-1'));
    const req = httpMock.expectOne('/api/v1/products/sku/SKU-1');
    expect(req.request.method).toBe('GET');
    req.flush(product({ id: 1, sku: 'SKU-1', name: 'Core' }));
    await expect(promise).resolves.toMatchObject({ sku: 'SKU-1' });
  });
});
