import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { computed, signal } from '@angular/core';
import { QuotesListComponent } from './quotes-list.component';
import { QuoteService } from '../quote.service';
import { MOCK_QUOTES } from '../quotes.mock';
import { QuoteDto, PaginationMeta, QUOTE_STATUS_LABELS } from '@frontend/shared-models';
import { QuoteListItem } from '../quotes.model';

function toQuoteDto(item: QuoteListItem): QuoteDto {
  return {
    id: item.id,
    uuid: item.uuid,
    quoteNumber: item.quoteNumber,
    name: item.name,
    description: item.description,
    customerExternalId: item.customerExternalId,
    dealExternalId: item.dealExternalId,
    status: item.status,
    dealType: item.dealType,
    currency: item.currency,
    region: item.region,
    durationInMonths: item.durationInMonths,
    totalAmount: item.totalAmount,
    arr: item.arr,
    tcv: item.tcv,
    createdAt: item.createdAt,
    updatedAt: item.createdAt,
  };
}

describe('QuotesListComponent', () => {
  let fixture: ComponentFixture<QuotesListComponent>;
  let component: QuotesListComponent;
  let router: Router;
  let listSpy: ReturnType<typeof vi.fn>;

  const quotes = signal<QuoteDto[]>(MOCK_QUOTES.map(toQuoteDto));
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 10,
    totalElements: MOCK_QUOTES.length,
    totalPages: 1,
  });

  beforeEach(async () => {
    listSpy = vi.fn();
    quotes.set(MOCK_QUOTES.map(toQuoteDto));
    loading.set(false);
    error.set(null);
    pagination.set({
      page: 0,
      size: 10,
      totalElements: MOCK_QUOTES.length,
      totalPages: 1,
    });

    const serviceMock = {
      quotes,
      loading,
      error,
      pagination,
      isEmpty: computed(() => quotes().length === 0 && !loading()),
      list: listSpy,
    };

    await TestBed.configureTestingModule({
      imports: [QuotesListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: QuoteService, useValue: serviceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QuotesListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads quotes via QuoteService on init (not mock-only happy path)', () => {
    expect(listSpy).toHaveBeenCalledWith(0, 10, 'createdAt,desc', {
      status: undefined,
      dealType: undefined,
    });
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Quotes');
    expect(compiled.textContent).toContain('via gateway');
    expect(component.tableRows().length).toBe(MOCK_QUOTES.length);
    expect(compiled.textContent).toContain('Q-2026-0001');
  });

  it('renders the Status column as a quote status badge with friendly labels (H-CPQ-Q-002)', () => {
    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('tr.mat-mdc-row'));
    expect(rows.length).toBeGreaterThan(0);
    const badges = rows.map((r) => r.querySelector('app-status-badge')?.textContent?.trim());
    expect(badges.every((b) => b !== undefined)).toBe(true);
    const labels = Object.values(QUOTE_STATUS_LABELS);
    for (const b of badges) expect(labels).toContain(b);
  });

  it('labels the status filter options in words but filters by the enum (H-CPQ-Q-002)', () => {
    expect(component.statusOptions.map((o) => o.value)).toEqual(component.statuses);
    expect(component.statusOptions.map((o) => o.label)).toEqual(
      component.statuses.map((s) => QUOTE_STATUS_LABELS[s]),
    );
    expect(component.statusOptions[0]).toEqual({ value: 'DRAFT', label: 'Draft' });
  });

  it('filters by search term (name/number/customer) on loaded page', () => {
    component.onSearch('Q-2026-0003');
    fixture.detectChanges();

    expect(component.tableRows().length).toBe(1);
    expect(component.tableRows()[0].quoteNumber).toBe('Q-2026-0003');
  });

  it('forwards status and deal type filters to QuoteService.list', () => {
    listSpy.mockClear();
    component.onStatusChange('DRAFT');
    expect(listSpy).toHaveBeenCalledWith(0, 10, 'createdAt,desc', {
      status: 'DRAFT',
      dealType: undefined,
    });

    listSpy.mockClear();
    component.onDealTypeChange('NEW');
    expect(listSpy).toHaveBeenCalledWith(0, 10, 'createdAt,desc', {
      status: 'DRAFT',
      dealType: 'NEW',
    });
  });

  it('shows empty state when search matches nothing', () => {
    component.onSearch('zzz-no-match');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(component.isEmpty()).toBe(true);
    expect(compiled.textContent).toContain('No matching quotes');
  });

  it('shows empty state when quotes list is empty', () => {
    quotes.set([]);
    pagination.set({ page: 0, size: 10, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No quotes yet');
  });

  it('shows New Quote empty-state CTA when unfiltered and navigates on action', () => {
    quotes.set([]);
    pagination.set({ page: 0, size: 10, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const emptyState = compiled.querySelector('app-empty-state');
    expect(emptyState).toBeTruthy();
    expect(emptyState?.textContent).toContain('New Quote');

    const actionBtn = Array.from(
      emptyState?.querySelectorAll('button') ?? [],
    ).find((b) => b.textContent?.includes('New Quote')) as
      | HTMLButtonElement
      | undefined;
    expect(actionBtn).toBeTruthy();
    actionBtn?.click();
    expect(router.navigate).toHaveBeenCalledWith(['/quotes', 'new']);
  });

  it('hides New Quote empty-state CTA when filters are active', () => {
    quotes.set([]);
    pagination.set({ page: 0, size: 10, totalElements: 0, totalPages: 0 });
    component.onSearch('zzz-no-match');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const emptyState = compiled.querySelector('app-empty-state');
    expect(emptyState).toBeTruthy();
    expect(compiled.textContent).toContain('No matching quotes');
    const actionBtn = Array.from(
      emptyState?.querySelectorAll('button') ?? [],
    ).find((b) => b.textContent?.trim() === 'New Quote');
    expect(actionBtn).toBeUndefined();
  });

  it('does not present a failed load as a successfully empty list', () => {
    quotes.set([]);
    error.set('Service unavailable');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain(
      'Service unavailable',
    );
    expect(compiled.querySelector('app-empty-state')).toBeNull();
    expect(compiled.textContent).not.toContain('No data available');
    expect(compiled.querySelector('app-data-table')).toBeNull();
    expect(compiled.textContent).not.toContain('No quotes yet');
  });

  it('shows error banner when QuoteService error is set', () => {
    error.set('Failed to load quotes');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load quotes');

    component.clearError();
    fixture.detectChanges();
    expect(error()).toBeNull();
  });

  it('navigates to detail on row click', () => {
    component.onRowClick({
      id: 2,
      quoteNumber: 'Q-2026-0002',
      name: 'Globex CPQ Flat + Support',
      customerExternalId: 'cust-globex',
      status: 'IN_REVIEW',
      dealType: 'NEW',
      totalAmount: 17976,
    });

    expect(router.navigate).toHaveBeenCalledWith(['/quotes', 2]);
  });

  it('enables New Quote and navigates to create route', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const newBtn = Array.from(compiled.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('New Quote'),
    ) as HTMLButtonElement | undefined;
    expect(newBtn).toBeTruthy();
    expect(newBtn?.disabled).toBe(false);

    component.onNewQuote();
    expect(router.navigate).toHaveBeenCalledWith(['/quotes', 'new']);
  });

  it('uses shared-ui data table (not inline MatTable happy path)', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-data-table')).toBeTruthy();
    expect(compiled.querySelector('app-page-header')).toBeTruthy();
    expect(compiled.querySelector('app-search-input')).toBeTruthy();
  });
});
