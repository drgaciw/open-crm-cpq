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
});
