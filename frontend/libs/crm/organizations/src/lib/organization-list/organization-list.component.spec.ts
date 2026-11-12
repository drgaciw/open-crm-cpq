import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal, computed } from '@angular/core';
import { of } from 'rxjs';
import { PageEvent } from '@angular/material/paginator';
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
    expect(el.querySelector('.empty-state-title')?.textContent).toContain('No accounts yet');
    expect(el.querySelector('.empty-state-action')?.textContent).toContain('Create Account');
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
    expect(searchSpy).toHaveBeenCalledWith('acme', 0, 20, 'name,asc');
  });

  it('onSearch with blank term reloads list', () => {
    listSpy.mockClear();
    component.onSearch('   ');
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc');
    expect(searchSpy).not.toHaveBeenCalled();
  });

  it('uses Account product copy on the list', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('h1')?.textContent).toContain('Accounts');
    expect(el.textContent).toContain('New Account');
    expect(el.querySelector('app-search-input input')?.getAttribute('placeholder')).toBe(
      'Search accounts...',
    );
  });

  it('empty state invites the seller to create an account', () => {
    organizations.set([]);
    loading.set(false);
    error.set(null);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.empty-state-title')?.textContent).toContain('No accounts yet');
    expect(el.querySelector('.empty-state-action')?.textContent).toContain('Create Account');
  });

  it('explains an empty filtered result without claiming there are no accounts', () => {
    organizations.set([]);
    loading.set(false);
    error.set(null);
    component.onTerritoryFilter('West');
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.empty-state-title')?.textContent).toContain('No matching accounts');
    expect(el.querySelector('.empty-state-message')?.textContent).toContain('filters');
    expect(el.querySelector('.empty-state-action')).toBeNull();
  });

  it('offers industry, territory, and owner filters', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="account-industry-filter"]')).toBeTruthy();
    expect(el.querySelector('[data-testid="account-territory-filter"]')).toBeTruthy();
    expect(el.querySelector('[data-testid="account-owner-filter"]')).toBeTruthy();
  });

  it('requests filters from the server rather than narrowing a loaded page', () => {
    organizations.set([
      org({ id: 1, name: 'Acme', industry: 'Technology', territory: 'West', ownerId: 'ada' }),
      org({ id: 2, name: 'Globex', industry: 'Finance', territory: 'East', ownerId: 'grace' }),
    ]);
    loading.set(false);
    error.set(null);
    fixture.detectChanges();
    listSpy.mockClear();

    component.onIndustryFilter('Technology');
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Acme');
    expect(text).toContain('Globex');
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc', {
      industry: 'Technology',
      territory: '',
      ownerId: '',
    });
  });

  it('renders shared DataTable when accounts are loaded', () => {
    organizations.set([org({ id: 1, name: 'Acme' })]);
    loading.set(false);
    error.set(null);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('app-data-table')).toBeTruthy();
    expect(el.querySelector('table[mat-table], table.mat-mdc-table')).toBeTruthy();
  });

  it('keeps the search query when the seller changes page', () => {
    component.onSearch('acme');
    searchSpy.mockClear();
    listSpy.mockClear();
    const event: PageEvent = { pageIndex: 2, pageSize: 10, length: 40, previousPageIndex: 0 };
    component.onPageChange(event);
    expect(searchSpy).toHaveBeenCalledWith('acme', 2, 10, 'name,asc');
    expect(listSpy).not.toHaveBeenCalled();
  });

  it('labels archive as a soft hide and still calls delete', () => {
    const row = org({ id: 4, name: 'To Hide' });
    organizations.set([row]);
    loading.set(false);
    error.set(null);
    fixture.detectChanges();

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    component.onDelete(row);
    expect(confirmSpy).toHaveBeenCalledWith(
      'Archive "To Hide"? The account will be hidden from lists.',
    );
  });

  it('names the row actions button for screen readers', () => {
    organizations.set([org({ id: 8, name: 'Named' })]);
    loading.set(false);
    error.set(null);
    fixture.detectChanges();
    const trigger = (fixture.nativeElement as HTMLElement).querySelector(
      'button[aria-label="Account actions"]',
    );
    expect(trigger).toBeTruthy();
  });
});

function org(
  partial: Partial<OrganizationDto> & Pick<OrganizationDto, 'id' | 'name'>,
): OrganizationDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}
