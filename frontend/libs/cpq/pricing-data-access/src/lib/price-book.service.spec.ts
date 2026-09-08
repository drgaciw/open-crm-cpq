import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { PriceBookService } from './price-book.service';
import {
  PriceBookDto,
  PriceBookEntryDto,
  SpringPageResponse,
} from '@frontend/shared-models';

function book(
  partial: Partial<PriceBookDto> & Pick<PriceBookDto, 'id' | 'name'>,
): PriceBookDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

function entry(
  partial: Partial<PriceBookEntryDto> &
    Pick<PriceBookEntryDto, 'id' | 'priceBookId' | 'productExternalId' | 'listPrice'>,
): PriceBookEntryDto {
  return {
    uuid: `e-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('PriceBookService', () => {
  let service: PriceBookService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PriceBookService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PriceBookService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('listByRegion GETs /api/v1/price-books?region=', () => {
    service.listByRegion('NA');
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/price-books' && r.params.get('region') === 'NA',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      content: [book({ id: 1, name: 'NA Default', region: 'NA' })],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    } satisfies SpringPageResponse<PriceBookDto>);
    expect(service.priceBooks()[0].name).toBe('NA Default');
  });

  it('list error.set appends errorCode as message [CODE]', () => {
    service.list();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/price-books');
    req.flush(
      { message: 'Failed to load price books', errorCode: 'PRICE_BOOKS_UNAVAILABLE' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.error()).toBe(
      'Failed to load price books [PRICE_BOOKS_UNAVAILABLE]',
    );
  });

  it('getById error.set appends errorCode as message [CODE]', () => {
    service.getById(99);
    const req = httpMock.expectOne('/api/v1/price-books/99');
    req.flush(
      { message: 'Price book not found', errorCode: 'RESOURCE_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.error()).toBe('Price book not found [RESOURCE_NOT_FOUND]');
  });

  it('entry CRUD uses /api/v1/price-books/:id/entries', async () => {
    service.getEntries(3);
    const listReq = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/price-books/3/entries' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '20',
    );
    expect(listReq.request.method).toBe('GET');
    listReq.flush({
      content: [
        entry({ id: 10, priceBookId: 3, productExternalId: 'p1', listPrice: 100 }),
      ],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    } satisfies SpringPageResponse<PriceBookEntryDto>);
    expect(service.entries()).toHaveLength(1);

    const create = firstValueFrom(
      service.createEntry(3, { productExternalId: 'p2', listPrice: 200 }),
    );
    const createReq = httpMock.expectOne('/api/v1/price-books/3/entries');
    expect(createReq.request.method).toBe('POST');
    createReq.flush(
      entry({ id: 11, priceBookId: 3, productExternalId: 'p2', listPrice: 200 }),
    );
    await expect(create).resolves.toMatchObject({ id: 11 });

    const update = firstValueFrom(
      service.updateEntry(3, 11, { listPrice: 250 }),
    );
    const updateReq = httpMock.expectOne('/api/v1/price-books/3/entries/11');
    expect(updateReq.request.method).toBe('PUT');
    updateReq.flush(
      entry({ id: 11, priceBookId: 3, productExternalId: 'p2', listPrice: 250 }),
    );
    await expect(update).resolves.toMatchObject({ listPrice: 250 });

    const del = firstValueFrom(service.deleteEntry(3, 11));
    const delReq = httpMock.expectOne('/api/v1/price-books/3/entries/11');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush(null);
    await expect(del).resolves.toBeNull();
  });
});
