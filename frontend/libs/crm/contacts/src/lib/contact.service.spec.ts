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
