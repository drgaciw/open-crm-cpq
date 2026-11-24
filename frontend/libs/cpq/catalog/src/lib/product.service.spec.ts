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

  it('keeps the latest search rows and pagination when an earlier list resolves last', () => {
    service.list(2, 10, 'name,asc', { productType: 'SAAS_FLAT_RATE' });
    const older = httpMock.expectOne((r) => r.url === '/api/v1/products');
    service.search('SKU-2', 1, 25, 'sku,desc', { category: 'SUPPORT', status: 'DRAFT' });
    const latest = httpMock.expectOne((r) => r.url === '/api/v1/products/search' && r.params.get('q') === 'SKU-2');
    const match = product({ id: 2, sku: 'SKU-2', name: 'Support', status: 'DRAFT', category: 'SUPPORT' });
    latest.flush({ content: [match], number: 1, size: 25, totalElements: 26, totalPages: 2 });

    if (!older.cancelled) {
      const stale = product({ id: 1, sku: 'SKU-1', name: 'Core' });
      older.flush({ content: [stale], number: 2, size: 10, totalElements: 21, totalPages: 3 });
    }

    expect(service.products()).toEqual([match]);
    expect(service.pagination()).toEqual({ page: 1, size: 25, totalElements: 26, totalPages: 2 });
    expect(service.loading()).toBe(false);
  });

  it('keeps loading the current list when an older search fails', () => {
    service.search('obsolete');
    const older = httpMock.expectOne((r) => r.url === '/api/v1/products/search');
    service.list();
    const latest = httpMock.expectOne((r) => r.url === '/api/v1/products');

    if (!older.cancelled) {
      older.flush({ message: 'Old search unavailable' }, { status: 503, statusText: 'Service Unavailable' });
    }

    expect(service.loading()).toBe(true);
    expect(service.error()).toBeNull();
    latest.flush([]);
    expect(service.loading()).toBe(false);
  });

  it('keeps the current search error and excludes an older search response', () => {
    service.search('old');
    const older = httpMock.expectOne((r) => r.params.get('q') === 'old');
    service.search('current');
    const latest = httpMock.expectOne((r) => r.params.get('q') === 'current');
    latest.flush(
      { message: 'Search unavailable', errorCode: 'PRODUCT_SEARCH_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    if (!older.cancelled) {
      older.flush([product({ id: 1, sku: 'SKU-1', name: 'Old result' })]);
    }

    expect(service.products()).toEqual([]);
    expect(service.error()).toBe('Search unavailable [PRODUCT_SEARCH_UNAVAILABLE]');
    expect(service.loading()).toBe(false);
  });

  it('lets the product detail read complete independently of collection supersession', () => {
    service.getById(9);
    const detail = httpMock.expectOne('/api/v1/products/9');
    service.list();
    const list = httpMock.expectOne((r) => r.url === '/api/v1/products');
    service.search('current');
    const search = httpMock.expectOne((r) => r.url === '/api/v1/products/search');

    if (!list.cancelled) list.flush([]);
    search.flush([]);
    expect(detail.cancelled).toBe(false);
    detail.flush(product({ id: 9, sku: 'SKU-9', name: 'Detail' }));

    expect(service.selectedProduct()?.id).toBe(9);
    expect(service.products()).toEqual([]);
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

  it('search sends the column sort only when one is chosen (H-CPQ-P-002)', () => {
    service.search('core', 0, 20, 'sku,desc');
    const sorted = httpMock.expectOne((r) => r.url === '/api/v1/products/search');
    expect(sorted.request.params.get('sort')).toBe('sku,desc');
    sorted.flush({ content: [], totalElements: 0, number: 0, size: 20 });

    service.search('core');
    const unsorted = httpMock.expectOne((r) => r.url === '/api/v1/products/search');
    expect(unsorted.request.params.has('sort')).toBe(false);
    unsorted.flush({ content: [], totalElements: 0, number: 0, size: 20 });
  });

  it('search sends category and status filters so they apply to every page (DRG-754)', () => {
    service.search('core', 0, 20, undefined, { category: 'SUPPORT', status: 'DRAFT' });
    const filtered = httpMock.expectOne((r) => r.url === '/api/v1/products/search');
    expect(filtered.request.params.get('category')).toBe('SUPPORT');
    expect(filtered.request.params.get('status')).toBe('DRAFT');
    filtered.flush({ content: [], totalElements: 0, number: 0, size: 20 });

    service.search('core');
    const plain = httpMock.expectOne((r) => r.url === '/api/v1/products/search');
    expect(plain.request.params.has('category')).toBe(false);
    expect(plain.request.params.has('status')).toBe(false);
    plain.flush({ content: [], totalElements: 0, number: 0, size: 20 });
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

  it('retains native status for a detail-read failure and resets it on the next detail request', () => {
    service.getById(99);
    httpMock.expectOne('/api/v1/products/99').flush(
      { message: 'Temporarily unavailable' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.detailReadStatus()).toBe(503);

    service.getById(100);
    expect(service.detailReadStatus()).toBeNull();
    httpMock.expectOne('/api/v1/products/100').flush(
      { message: 'Missing fixture' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.detailReadStatus()).toBe(404);
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
