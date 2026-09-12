import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { LeadService } from '@frontend/crm-leads';

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let httpMock: HttpTestingController;
  let getStatusCountsSpy: ReturnType<typeof vi.fn>;

  function flushSuccess(): void {
    httpMock
      .expectOne('/api/v1/deals/pipeline')
      .flush([{ stage: 'PROSPECTING', count: 1, totalAmount: 1000 }]);
    httpMock
      .expectOne((req) => req.url === '/api/v1/deals')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
    httpMock
      .expectOne((req) => req.url === '/api/v1/leads')
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 });
  }

  /** Cancel/flush any in-flight requests left after a forkJoin error. */
  function drainOutstanding(): void {
    httpMock.match(() => true).forEach((req) => {
      if (!req.cancelled) {
        req.flush({}, { status: 503, statusText: 'Service Unavailable' });
      }
    });
  }

  beforeEach(async () => {
    getStatusCountsSpy = vi.fn(() => of({ NEW: 2, QUALIFIED: 1 }));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: LeadService,
          useValue: {
            getStatusCounts: getStatusCountsSpy,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    drainOutstanding();
    httpMock.verify();
    vi.restoreAllMocks();
  });

  it('renders app-page-header', () => {
    fixture.detectChanges();
    flushSuccess();
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('app-page-header')).toBeTruthy();
    expect(el.textContent).toContain('Dashboard');
  });

  it('loading spinner wrapper exposes role=status and aria-live=polite', () => {
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const loadingEl = el.querySelector('.loading') as HTMLElement;
    expect(loadingEl).toBeTruthy();
    expect(loadingEl.getAttribute('role')).toBe('status');
    expect(loadingEl.getAttribute('aria-live')).toBe('polite');

    flushSuccess();
  });

  it('compact empty-text keeps role=status without app-empty-state', () => {
    fixture.detectChanges();
    flushSuccess();
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('app-empty-state')).toBeNull();

    const empties = el.querySelectorAll('.empty-text');
    expect(empties.length).toBe(2);
    empties.forEach((node) => {
      expect(node.getAttribute('role')).toBe('status');
    });
    expect(el.textContent).toContain('No recent deals.');
    expect(el.textContent).toContain('No recent leads.');
  });

  it('shows role=alert banner with Retry when forkJoin fails', () => {
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/deals/pipeline').flush(
      { message: 'Pipeline unavailable' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    drainOutstanding();
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const banner = el.querySelector('.error-banner') as HTMLElement;
    expect(banner).toBeTruthy();
    expect(banner.getAttribute('role')).toBe('alert');
    expect(banner.textContent).toContain('Retry');
    expect(component.loadError()).toBeTruthy();
    expect(el.querySelector('.kpi-grid')).toBeNull();
  });

  it('Retry reloads dashboard data after a failure', () => {
    fixture.detectChanges();

    httpMock.expectOne('/api/v1/deals/pipeline').flush(
      { message: 'Pipeline unavailable' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    drainOutstanding();
    fixture.detectChanges();
    expect(component.loadError()).toBeTruthy();

    const retryBtn = (fixture.nativeElement as HTMLElement).querySelector(
      '.error-banner button',
    ) as HTMLButtonElement;
    expect(retryBtn).toBeTruthy();
    retryBtn.click();
    fixture.detectChanges();

    expect(component.loading()).toBe(true);
    expect(component.loadError()).toBeNull();

    flushSuccess();
    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    expect(component.loadError()).toBeNull();
    expect(component.totalLeadCount()).toBe(3);
    expect((fixture.nativeElement as HTMLElement).querySelector('.error-banner')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('.kpi-grid')).toBeTruthy();
  });
});
