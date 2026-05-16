import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { DashboardComponent } from './dashboard.component';
import { LeadService } from '@frontend/crm-leads';

describe('DashboardComponent (gateway-backed CRM)', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        LeadService,
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads pipeline, deals, leads, and lead stats via gateway CRM paths', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    const pipelineReq = httpMock.expectOne('/api/v1/deals/pipeline');
    expect(pipelineReq.request.method).toBe('GET');
    pipelineReq.flush([]);

    const dealsReq = httpMock.expectOne((r) =>
      r.url === '/api/v1/deals' && r.params.get('page') === '0'
    );
    dealsReq.flush({ content: [], totalElements: 0, totalPages: 0, size: 5, number: 0 });

    const leadsReq = httpMock.expectOne((r) =>
      r.url === '/api/v1/leads' && r.params.get('page') === '0'
    );
    leadsReq.flush({ content: [], totalElements: 0, totalPages: 0, size: 5, number: 0 });

    const statsReq = httpMock.expectOne('/api/v1/leads/stats');
    statsReq.flush({ NEW: 2, QUALIFIED: 1 });

    fixture.detectChanges();

    expect(fixture.componentInstance.totalLeadCount()).toBe(3);
    expect(fixture.componentInstance.loading()).toBe(false);
  });
});
