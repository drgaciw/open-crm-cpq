import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal, computed } from '@angular/core';
import { of } from 'rxjs';
import { OrganizationListComponent } from './organization-list.component';
import { OrganizationService } from '../organization.service';
import { OrganizationDto, PaginationMeta } from '@frontend/shared-models';

describe('OrganizationListComponent SearchInput wiring', () => {
  let fixture: ComponentFixture<OrganizationListComponent>;
  let component: OrganizationListComponent;
  let listSpy: ReturnType<typeof vi.fn>;
  let searchSpy: ReturnType<typeof vi.fn>;

  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  const organizations = signal<OrganizationDto[]>([]);
  const loading = signal(false);
  const error = signal<string | null>(null);

  // Angular TestBed compile can exceed Vitest's default 10s hookTimeout under load.
  beforeEach(async () => {
    listSpy = vi.fn();
    searchSpy = vi.fn();
    error.set(null);

    await TestBed.configureTestingModule({
      imports: [OrganizationListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: OrganizationService,
          useValue: {
            organizations,
            loading,
            error,
            pagination,
            isEmpty: computed(() => organizations().length === 0 && !loading()),
            list: listSpy,
            search: searchSpy,
            delete: vi.fn(() => of(void 0)),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrganizationListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }, 30000);

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('ngOnInit loads the organization list', () => {
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc');
  });

  it('renders shared SearchInput instead of raw input', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('app-search-input')).toBeTruthy();
    expect(el.querySelector('input[aria-label="Search organizations"]')).toBeNull();
  });

  it('renders app-page-header and app-empty-state with Create Organization CTA', () => {
    organizations.set([]);
    loading.set(false);
    error.set(null);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('app-page-header')).toBeTruthy();
    expect(el.querySelector('app-empty-state')).toBeTruthy();
    expect(el.querySelector('.empty-state-title')?.textContent).toContain('No organizations yet');
    expect(el.querySelector('.empty-state-action')?.textContent).toContain('Create Organization');
  });

  it('shows error banner and hides empty-state on failed load', () => {
    organizations.set([]);
    loading.set(false);
    error.set('Failed to load organizations [ORGS_UNAVAILABLE]');
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const banner = el.querySelector('.error-banner') as HTMLElement;
    expect(banner).toBeTruthy();
    expect(banner.getAttribute('role')).toBe('alert');
    expect(banner.textContent).toContain('Failed to load organizations [ORGS_UNAVAILABLE]');
    expect(el.querySelector('app-empty-state')).toBeNull();
  });

  it('clearError dismisses the banner', () => {
    error.set('Failed to load organizations [ORGS_UNAVAILABLE]');
    fixture.detectChanges();
    component.clearError();
    fixture.detectChanges();
    expect(error()).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('.error-banner')).toBeNull();
  });

  it('onRetry reloads list when not searching', () => {
    listSpy.mockClear();
    error.set('Failed to load organizations [ORGS_UNAVAILABLE]');
    component.onRetry();
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc');
  });

  it('loading spinner wrapper exposes role=status and aria-live=polite', () => {
    loading.set(true);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const loadingEl = el.querySelector('.loading-overlay') as HTMLElement;
    expect(loadingEl).toBeTruthy();
    expect(loadingEl.getAttribute('role')).toBe('status');
    expect(loadingEl.getAttribute('aria-live')).toBe('polite');
  });

  it('onSearch with term calls OrganizationService.search', () => {
    component.onSearch('  acme  ');
    expect(searchSpy).toHaveBeenCalledWith('acme');
  });

  it('onSearch with blank term reloads list', () => {
    listSpy.mockClear();
    component.onSearch('   ');
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc');
    expect(searchSpy).not.toHaveBeenCalled();
  });
});
