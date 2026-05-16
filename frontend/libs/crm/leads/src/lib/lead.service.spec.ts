import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LeadService } from './lead.service';
import { LeadStatus } from '@frontend/shared-models';

describe('LeadService (gateway-backed CRM)', () => {
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

  it('lists leads through the gateway CRM route', async () => {
    const status: LeadStatus = 'NEW';
    service.list(0, 20, 'createdAt,desc', status);
    const req = httpMock.expectOne((r) => r.url === '/api/v1/leads' && r.method === 'GET');
    expect(req.request.params.get('status')).toBe('NEW');
    req.flush({ content: [{ id: 1, title: 'Acme' }], totalElements: 1, totalPages: 1, size: 20, number: 0 });
    await Promise.resolve();
    expect(service.leads().length).toBe(1);
  });

  it('fetches lead status counts from /api/v1/leads/stats', async () => {
    const countsPromise = firstValueFrom(service.getStatusCounts());
    const req = httpMock.expectOne('/api/v1/leads/stats');
    req.flush({ NEW: 4, QUALIFIED: 2 });
    const counts = await countsPromise;
    expect(counts.NEW).toBe(4);
    expect(counts.QUALIFIED).toBe(2);
  });

  it('posts lifecycle transitions on gateway lead commands', async () => {
    const qualifyPromise = firstValueFrom(service.qualify(42));
    const qualifyReq = httpMock.expectOne('/api/v1/leads/42/qualify');
    expect(qualifyReq.request.method).toBe('POST');
    qualifyReq.flush({ id: 42, status: 'QUALIFIED' });
    await qualifyPromise;

    const convertPromise = firstValueFrom(service.convertToDeal(42));
    const convertReq = httpMock.expectOne('/api/v1/leads/42/convert');
    convertReq.flush({ id: 99, name: 'Deal' });
    await convertPromise;
  });
});
