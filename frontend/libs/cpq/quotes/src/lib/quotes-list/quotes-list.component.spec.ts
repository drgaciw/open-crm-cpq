import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { computed, signal } from '@angular/core';
import { QuotesListComponent } from './quotes-list.component';
import { QuoteService } from '../quote.service';
import { MOCK_QUOTES } from '../quotes.mock';
import { QuoteDto, PaginationMeta } from '@frontend/shared-models';
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

  it('uses shared-ui data table (not inline MatTable happy path)', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-data-table')).toBeTruthy();
    expect(compiled.querySelector('app-page-header')).toBeTruthy();
    expect(compiled.querySelector('app-search-input')).toBeTruthy();
  });
});
