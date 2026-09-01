import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ProductBundleService } from './product-bundle.service';
import { MOCK_PRODUCT_BUNDLES } from './product-bundles.mock';

describe('ProductBundleService', () => {
  let service: ProductBundleService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ProductBundleService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ProductBundleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists bundles from /api/v1/product-bundles', () => {
    service.list(0, 20, 'name,asc');

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/product-bundles' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '20',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      content: MOCK_PRODUCT_BUNDLES,
      number: 0,
      size: 20,
      totalElements: MOCK_PRODUCT_BUNDLES.length,
      totalPages: 1,
    });

    expect(service.bundles().length).toBe(MOCK_PRODUCT_BUNDLES.length);
    expect(service.pagination().totalElements).toBe(MOCK_PRODUCT_BUNDLES.length);
  });

  it('gets bundle by id', () => {
    service.getById(1);

    const req = httpMock.expectOne('/api/v1/product-bundles/1');
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_PRODUCT_BUNDLES[0]);

    expect(service.selectedBundle()?.id).toBe(1);
  });

  it('creates a bundle via POST', () => {
    let createdId: number | undefined;
    service
      .create({ productId: 10, name: 'New Bundle' })
      .subscribe((b) => {
        createdId = b.id;
      });

    const req = httpMock.expectOne('/api/v1/product-bundles');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ productId: 10, name: 'New Bundle' });
    req.flush({ ...MOCK_PRODUCT_BUNDLES[0], id: 99, name: 'New Bundle' });

    expect(createdId).toBe(99);
  });

  it('adds and removes items on correct paths', () => {
    service.addItem(1, { productId: 20, quantity: 1 }).subscribe();
    const addReq = httpMock.expectOne('/api/v1/product-bundles/1/items');
    expect(addReq.request.method).toBe('POST');
    addReq.flush(MOCK_PRODUCT_BUNDLES[0]);

    service.removeItem(1, 101).subscribe();
    const removeReq = httpMock.expectOne(
      '/api/v1/product-bundles/1/items/101',
    );
    expect(removeReq.request.method).toBe('DELETE');
    removeReq.flush(null);
  });

  it('deletes a bundle', () => {
    service.delete(1).subscribe();
    const req = httpMock.expectOne('/api/v1/product-bundles/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('lists by product id via /product/{productId}', () => {
    service.listByProductId(10);

    const req = httpMock.expectOne('/api/v1/product-bundles/product/10');
    expect(req.request.method).toBe('GET');
    req.flush([MOCK_PRODUCT_BUNDLES[0], MOCK_PRODUCT_BUNDLES[2]]);

    expect(service.bundles().length).toBe(2);
  });
});
