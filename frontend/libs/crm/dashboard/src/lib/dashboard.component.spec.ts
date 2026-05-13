import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard.component';
import { DealDto, LeadDto, PipelineSummaryDto } from '@frontend/shared-models';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let http: HttpTestingController;

  const createdAt = '2026-05-12T00:00:00Z';
  const updatedAt = '2026-05-12T01:00:00Z';

  const pipeline: PipelineSummaryDto[] = [
    { stage: 'PROSPECTING', count: 2, totalAmount: 25000, weightedAmount: 5000 },
    { stage: 'WON', count: 1, totalAmount: 10000, weightedAmount: 10000 },
  ];

  const recentDeal: DealDto = {
    id: 10,
    uuid: 'deal-10',
    name: 'Acme expansion',
    stage: 'PROPOSAL',
    amount: 15000,
    createdAt,
    updatedAt,
  };

  const recentLead: LeadDto = {
    id: 20,
    uuid: 'lead-20',
    title: 'Acme renewal lead',
    status: 'QUALIFIED',
    estimatedValue: 30000,
    createdAt,
    updatedAt,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('loads dashboard data from gateway-backed CRM endpoints', () => {
    fixture.detectChanges();

    const pipelineRequest = http.expectOne('/api/v1/deals/pipeline');
    pipelineRequest.flush(pipeline);

    const dealsRequest = http.expectOne((req) =>
      req.url === '/api/v1/deals' &&
      req.params.get('page') === '0' &&
      req.params.get('size') === '5' &&
      req.params.get('sort') === 'createdAt,desc'
    );
    dealsRequest.flush({ content: [recentDeal], totalElements: 1 });

    const leadsRequest = http.expectOne((req) =>
      req.url === '/api/v1/leads' &&
      req.params.get('page') === '0' &&
      req.params.get('size') === '5' &&
      req.params.get('sort') === 'createdAt,desc'
    );
    leadsRequest.flush({ content: [recentLead], totalElements: 1 });

    const leadStatsRequest = http.expectOne('/api/v1/leads/stats');
    leadStatsRequest.flush({ NEW: 3, CONTACTED: 2, QUALIFIED: 1 });

    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    expect(component.totalDeals()).toBe(3);
    expect(component.totalValue()).toBe(35000);
    expect(component.wonValue()).toBe(10000);
    expect(component.totalLeadCount()).toBe(6);
    expect(component.recentDeals()).toEqual([recentDeal]);
    expect(component.recentLeads()).toEqual([recentLead]);

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Pipeline Breakdown');
    expect(text).toContain('Acme expansion');
    expect(text).toContain('Acme renewal lead');
  });

  it('clears the loading state when dashboard aggregation fails', () => {
    fixture.detectChanges();

    http.expectOne((req) => req.url === '/api/v1/deals').flush({ content: [] });
    http.expectOne((req) => req.url === '/api/v1/leads').flush({ content: [] });
    http.expectOne('/api/v1/leads/stats').flush({});
    http.expectOne('/api/v1/deals/pipeline').flush('unavailable', { status: 503, statusText: 'Unavailable' });

    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('No recent deals.');
    expect(fixture.nativeElement.textContent).toContain('No recent leads.');
  });
});
