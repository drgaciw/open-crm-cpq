import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ContactService } from './contact.service';
import { ContactDto, SpringPageResponse } from '@frontend/shared-models';

describe('ContactService', () => {
  let service: ContactService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ContactService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ContactService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list sets contacts signal from page content', () => {
    const page: SpringPageResponse<ContactDto> = {
      content: [
        {
          id: 1,
          uuid: 'u1',
          firstName: 'Ada',
          lastName: 'Lovelace',
          email: 'ada@example.com',
          createdAt: '',
          updatedAt: '',
        },
      ],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };

    service.list();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/contacts');
    expect(req.request.method).toBe('GET');
    req.flush(page);

    expect(service.contacts()).toEqual(page.content);
    expect(service.pagination().totalElements).toBe(1);
    expect(service.loading()).toBe(false);
  });

  const matchingContact: ContactDto = {
    id: 1, uuid: 'u1', firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', createdAt: '', updatedAt: '',
  };
  const otherContact: ContactDto = {
    id: 2, uuid: 'u2', firstName: 'Grace', lastName: 'Hopper', email: 'grace@example.com', createdAt: '', updatedAt: '',
  };
  const resultPage = (content: ContactDto[]): SpringPageResponse<ContactDto> => ({
    content, number: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0,
  });

  it.each([
    { query: 'Lovelace', rows: [matchingContact] },
    { query: 'unmatched', rows: [] },
  ])('keeps the confirmed $query search result when the old list response arrives later', ({ query, rows }) => {
    service.list();
    const earlier = httpMock.expectOne((req) => req.url === '/api/v1/contacts');

    service.search(query);
    const latest = httpMock.expectOne((req) => req.url === '/api/v1/contacts/search' && req.params.get('q') === query);
    latest.flush(resultPage(rows));
    if (!earlier.cancelled) {
      earlier.flush(resultPage([otherContact, matchingContact]));
    }

    expect(service.contacts()).toEqual(rows);
    expect(service.pagination().totalElements).toBe(rows.length);
    expect(service.hasConfirmedPage()).toBe(true);
    expect(service.isEmpty()).toBe(rows.length === 0);
  });

  it('keeps a newer list loading when a superseded search fails', () => {
    service.search('old search');
    const earlier = httpMock.expectOne((req) => req.url === '/api/v1/contacts/search');

    service.list();
    const latest = httpMock.expectOne((req) => req.url === '/api/v1/contacts');
    if (!earlier.cancelled) {
      earlier.flush({ message: 'Old search failed' }, { status: 503, statusText: 'Service Unavailable' });
    }

    expect(service.loading()).toBe(true);
    expect(service.error()).toBeNull();
    expect(service.hasConfirmedPage()).toBe(false);
    latest.flush(resultPage([matchingContact]));
    expect(service.contacts()).toEqual([matchingContact]);
    expect(service.loading()).toBe(false);
    expect(service.hasConfirmedPage()).toBe(true);
  });

  it('list error.set is message [CODE] when errorCode present', () => {
    service.list();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/contacts');
    req.flush(
      { message: 'List failed', errorCode: 'CONTACT_LIST_DENIED' },
      { status: 403, statusText: 'Forbidden' },
    );
    expect(service.error()).toBe('List failed [CONTACT_LIST_DENIED]');
  });

  it('getById error.set is message [CODE] when errorCode present', () => {
    service.getById(7);
    const req = httpMock.expectOne('/api/v1/contacts/7');
    req.flush(
      { message: 'Contact missing', errorCode: 'CONTACT_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.error()).toBe('Contact missing [CONTACT_NOT_FOUND]');
  });

  it('search error.set is message [CODE] when errorCode present', () => {
    service.search('ada');
    const req = httpMock.expectOne((r) => r.url === '/api/v1/contacts/search');
    req.flush(
      { message: 'Search failed', errorCode: 'CONTACT_SEARCH_DENIED' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.error()).toBe('Search failed [CONTACT_SEARCH_DENIED]');
  });
});
