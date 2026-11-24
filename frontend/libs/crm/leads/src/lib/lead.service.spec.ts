import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LeadService } from './lead.service';
import { LeadDto, SpringPageResponse } from '@frontend/shared-models';

describe('LeadService', () => {
  let service: LeadService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [LeadService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LeadService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('search sets leads signal from page content array', () => {
    const page: SpringPageResponse<LeadDto> = {
      content: [
        { id: 1, uuid: 'u1', title: 'Enterprise A', status: 'NEW', priority: 'MEDIUM', createdAt: '', updatedAt: '' },
        { id: 2, uuid: 'u2', title: 'Enterprise B', status: 'NEW', priority: 'HIGH', createdAt: '', updatedAt: '' },
      ],
      number: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    };

    service.search('Enterprise');
    const req = httpMock.expectOne((r) => r.url === '/api/v1/leads/search' && r.params.get('q') === 'Enterprise');
    expect(req.request.method).toBe('GET');
    req.flush(page);

    expect(service.leads()).toEqual(page.content);
    expect(service.pagination().totalElements).toBe(2);
    expect(service.loading()).toBe(false);
  });

  it('convertToDeal posts and returns LeadDto with convertedDealId', async () => {
    const converted: LeadDto = {
      id: 10,
      uuid: 'u10',
      title: 'Converted lead',
      status: 'CONVERTED',
      priority: 'MEDIUM',
      convertedDealId: 99,
      createdAt: '',
      updatedAt: '',
    };

    const promise = firstValueFrom(service.convertToDeal(10));
    const req = httpMock.expectOne('/api/v1/leads/10/convert');
    expect(req.request.method).toBe('POST');
    req.flush(converted);

    const result = await promise;
    expect(result.convertedDealId).toBe(99);
    expect(result.id).toBe(10);
  });

  it('list forwards status and priority query params', () => {
    service.list(1, 10, 'title,asc', 'UNQUALIFIED', 'HIGH');
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/leads' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '10' &&
        r.params.get('sort') === 'title,asc' &&
        r.params.get('status') === 'UNQUALIFIED' &&
        r.params.get('priority') === 'HIGH',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      content: [],
      number: 1,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    });
  });

  it('loads the last valid page without publishing an out-of-range empty result', () => {
    service.list(1, 10, 'title,asc', 'NEW', 'HIGH', 'Enterprise');
    const outside = httpMock.expectOne((r) => r.url === '/api/v1/leads' && r.params.get('page') === '1');
    outside.flush({ content: [], number: 1, size: 10, totalElements: 10, totalPages: 1 });

    expect(service.loading()).toBe(true);
    expect(service.isEmpty()).toBe(false);
    const recovered = httpMock.expectOne((r) =>
      r.url === '/api/v1/leads' &&
      r.params.get('page') === '0' &&
      r.params.get('size') === '10' &&
      r.params.get('sort') === 'title,asc' &&
      r.params.get('status') === 'NEW' &&
      r.params.get('priority') === 'HIGH' &&
      r.params.get('q') === 'Enterprise',
    );
    const lead: LeadDto = {
      id: 2, uuid: 'u2', title: 'Enterprise', status: 'NEW', priority: 'HIGH', createdAt: '', updatedAt: '',
    };
    recovered.flush({ content: [lead], number: 0, size: 10, totalElements: 10, totalPages: 1 });

    expect(service.leads()).toEqual([lead]);
    expect(service.pagination()).toEqual({ page: 0, size: 10, totalElements: 10, totalPages: 1 });
    expect(service.loading()).toBe(false);
  });

  it('keeps a genuine zero result without another page request', () => {
    service.list(1, 10, 'title,asc', 'NEW', 'HIGH', 'Enterprise');
    httpMock.expectOne((r) => r.params.get('page') === '1')
      .flush({ content: [], number: 1, size: 10, totalElements: 0, totalPages: 0 });

    httpMock.expectNone((r) => r.url === '/api/v1/leads');
    expect(service.isEmpty()).toBe(true);
    expect(service.pagination().totalElements).toBe(0);
  });

  it('cancels a pending last-page recovery when newer filters start loading', () => {
    service.list(1, 10, 'title,asc', 'NEW', 'HIGH', 'Old query');
    httpMock.expectOne((r) => r.params.get('page') === '1')
      .flush({ content: [], number: 1, size: 10, totalElements: 10, totalPages: 1 });
    const recovery = httpMock.expectOne((r) => r.params.get('page') === '0' && r.params.get('q') === 'Old query');

    service.list(0, 20, 'createdAt,desc', 'CONTACTED', 'LOW', 'New query');
    const current = httpMock.expectOne((r) => r.params.get('q') === 'New query');
    expect(recovery.cancelled).toBe(true);
    expect(service.loading()).toBe(true);
    expect(service.error()).toBeNull();
    const lead: LeadDto = {
      id: 3, uuid: 'u3', title: 'New query', status: 'CONTACTED', priority: 'LOW', createdAt: '', updatedAt: '',
    };
    current.flush({ content: [lead], number: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(service.leads()).toEqual([lead]);
    expect(service.pagination()).toEqual({ page: 0, size: 20, totalElements: 1, totalPages: 1 });
    expect(service.loading()).toBe(false);
  });

  it('keeps filtered rows and pagination when an older unfiltered list finishes later', () => {
    service.list();
    const previous = httpMock.expectOne((r) => r.url === '/api/v1/leads' && !r.params.has('status'));

    service.list(1, 10, 'title,asc', 'NEW');
    const latest = httpMock.expectOne((r) => r.url === '/api/v1/leads' && r.params.get('status') === 'NEW');
    const fresh: LeadDto = {
      id: 1, uuid: 'u1', title: 'New lead', status: 'NEW', priority: 'MEDIUM', createdAt: '', updatedAt: '',
    };
    latest.flush({ content: [fresh], number: 1, size: 10, totalElements: 11, totalPages: 2 });
    if (!previous.cancelled) {
      const contacted: LeadDto = { ...fresh, id: 2, uuid: 'u2', title: 'Contacted lead', status: 'CONTACTED' };
      previous.flush({ content: [contacted, fresh], number: 0, size: 20, totalElements: 22, totalPages: 2 });
    }

    expect(service.leads()).toEqual([fresh]);
    expect(service.pagination()).toEqual({ page: 1, size: 10, totalElements: 11, totalPages: 2 });
    expect(service.loading()).toBe(false);
  });

  it('keeps the latest list loading without an error when a superseded search fails', () => {
    service.search('old search');
    const previous = httpMock.expectOne((r) => r.url === '/api/v1/leads/search');

    service.list(0, 10, 'createdAt,desc', 'NEW');
    const latest = httpMock.expectOne((r) => r.url === '/api/v1/leads' && r.params.get('status') === 'NEW');
    if (!previous.cancelled) {
      previous.flush({ message: 'Old search failed' }, { status: 503, statusText: 'Service Unavailable' });
    }

    expect(service.loading()).toBe(true);
    expect(service.error()).toBeNull();
    latest.flush({ content: [], number: 0, size: 10, totalElements: 0, totalPages: 0 });
    expect(service.loading()).toBe(false);
    expect(service.error()).toBeNull();
  });

  it('qualify posts to /qualify', async () => {
    const promise = firstValueFrom(service.qualify(4));
    const req = httpMock.expectOne('/api/v1/leads/4/qualify');
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 4,
      uuid: 'u4',
      title: 'Qualified',
      status: 'QUALIFIED',
      priority: 'MEDIUM',
      createdAt: '',
      updatedAt: '',
    } satisfies LeadDto);
    await expect(promise).resolves.toMatchObject({ id: 4, status: 'QUALIFIED' });
  });

  it('list error.set is message [CODE] when errorCode present', () => {
    service.list();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/leads');
    req.flush(
      { message: 'List failed', errorCode: 'LEAD_LIST_DENIED' },
      { status: 403, statusText: 'Forbidden' },
    );
    expect(service.error()).toBe('List failed [LEAD_LIST_DENIED]');
  });

  it('getById error.set is message [CODE] when errorCode present', () => {
    service.getById(42);
    const req = httpMock.expectOne('/api/v1/leads/42');
    req.flush(
      { message: 'Lead missing', errorCode: 'LEAD_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    expect(service.error()).toBe('Lead missing [LEAD_NOT_FOUND]');
  });

  it('search error.set is message [CODE] when errorCode present', () => {
    service.search('acme');
    const req = httpMock.expectOne((r) => r.url === '/api/v1/leads/search');
    req.flush(
      { message: 'Search failed', errorCode: 'LEAD_SEARCH_DENIED' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    expect(service.error()).toBe('Search failed [LEAD_SEARCH_DENIED]');
  });
});
