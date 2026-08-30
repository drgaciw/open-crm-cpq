import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { QuotesListComponent } from './quotes-list.component';
import { MOCK_QUOTES } from '../quotes.mock';

describe('QuotesListComponent', () => {
  let fixture: ComponentFixture<QuotesListComponent>;
  let component: QuotesListComponent;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuotesListComponent],
      providers: [provideRouter([]), provideNoopAnimations()],
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

  it('renders page header and mock quote rows', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Quotes');
    expect(component.tableRows().length).toBe(MOCK_QUOTES.length);
    expect(compiled.textContent).toContain('Q-2026-0001');
  });

  it('filters by search term (name/number/customer)', () => {
    component.onSearch('Q-2026-0003');
    fixture.detectChanges();

    expect(component.tableRows().length).toBe(1);
    expect(component.tableRows()[0].quoteNumber).toBe('Q-2026-0003');
  });

  it('filters by status and deal type', () => {
    component.onStatusChange('DRAFT');
    component.onDealTypeChange('NEW');
    fixture.detectChanges();

    expect(component.tableRows().every((r) => r.status === 'DRAFT')).toBe(true);
    expect(component.tableRows().every((r) => r.dealType === 'NEW')).toBe(true);
    expect(component.tableRows().length).toBe(1);
  });

  it('shows empty state when filters match nothing', () => {
    component.onSearch('zzz-no-match');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(component.isEmpty()).toBe(true);
    expect(compiled.textContent).toContain('No matching quotes');
  });

  it('shows empty state when quotes list is empty', () => {
    component.setQuotesForTest([]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No quotes yet');
  });

  it('shows error banner when error is set', () => {
    component.setErrorForTest('Failed to load quotes');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load quotes');

    component.clearError();
    fixture.detectChanges();
    expect(component.error()).toBeNull();
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
