import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { QuoteComparisonComponent } from './quote-comparison.component';
import { QuoteComparisonApiService } from './quote-comparison-api.service';
import { QuoteAlternative, QuoteComparison } from './quote-comparison.model';
import { QUOTES_ROUTES } from '../quotes.routes';

function alternative(id: number, overrides: Partial<QuoteAlternative> = {}): QuoteAlternative {
  return {
    id, quoteNumber: `Q-${id}`, name: id === 1 ? 'Good' : 'Better', status: 'DRAFT', dealType: 'NEW',
    currency: 'USD', region: 'US', durationInMonths: 36, startDate: '2027-01-01', endDate: '2029-12-31',
    subtotal: 1000, totalDiscount: 100, totalTax: 90, totalAmount: 990, arr: null, tcv: 2700, acv: 900,
    rampScheduleStale: false, margin: { status: 'UNAVAILABLE', value: null, reason: 'A verified cost basis is not available' },
    lines: [{ productExternalId: 'SKU-1', productName: 'Named seats', productType: 'RECURRING',
      lineItemType: 'SAAS_SEAT_NAMED', quantity: 25, unitOfMeasure: 'SEAT', billingFrequency: 'MONTHLY' }],
    ...overrides,
  };
}

function comparison(overrides: Partial<QuoteComparison> = {}): QuoteComparison {
  return {
    dealExternalId: 'deal-1', customerExternalId: 'account-1', primaryQuoteId: 1,
    sameCurrency: true, quotes: [alternative(1), alternative(2, { status: 'ACCEPTED', totalAmount: 1200 })],
    ...overrides,
  };
}

describe('QuoteComparisonComponent', () => {
  let fixture: ComponentFixture<QuoteComparisonComponent>;
  let component: QuoteComparisonComponent;
  let compare: ReturnType<typeof vi.fn>;
  let markPrimary: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    compare = vi.fn(() => of(comparison()));
    markPrimary = vi.fn(() => of(comparison({ primaryQuoteId: 2 })));
    await TestBed.configureTestingModule({ imports: [QuoteComparisonComponent], providers: [
      provideRouter([]), { provide: QuoteComparisonApiService, useValue: { compare, markPrimary } },
    ] }).compileComponents();
    fixture = TestBed.createComponent(QuoteComparisonComponent);
    component = fixture.componentInstance;
  });

  function load(id = '1'): void {
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }

  function text(): string { return (fixture.nativeElement as HTMLElement).textContent ?? ''; }

  it('renders persisted totals, unavailable margin and key attributes in semantic columns', () => {
    load();
    expect(compare).toHaveBeenCalledWith(1);
    expect(fixture.nativeElement.querySelectorAll('thead th[scope="col"]')).toHaveLength(3);
    expect(text()).toContain('USD 990.00');
    expect(text()).toContain('USD 1,200.00');
    expect(text()).toContain('36 months');
    expect(text()).toContain('2027-01-01');
    expect(text()).toContain('Named seats');
    expect(text()).toContain('25 SEAT');
    const rows = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr'));
    const margin = rows.find((row) => row.querySelector('th')?.textContent === 'Margin');
    expect(margin?.textContent).toContain('Not available');
    expect(margin?.textContent).not.toContain('40');
    const arr = rows.find((row) => row.querySelector('th')?.textContent === 'ARR');
    expect(arr?.textContent).toContain('Not available');
    expect(arr?.textContent).not.toContain('0.00');
  });

  it('selects any alternative by keyboard-native button, including accepted quotes', () => {
    load();
    const button = fixture.nativeElement.querySelector('button[aria-label="Mark Q-2 primary"]') as HTMLButtonElement;
    expect(button.disabled).toBe(false);
    button.click();
    fixture.detectChanges();
    expect(markPrimary).toHaveBeenCalledWith(2);
    expect(component.comparison()?.primaryQuoteId).toBe(2);
    expect(text()).toContain('Primary preference saved.');
    expect(text()).toContain('It does not accept a quote or change the forecast.');
  });

  it('blocks overlapping writes and does not show an uncommitted preference on failure', () => {
    const response = new Subject<QuoteComparison>();
    markPrimary.mockReturnValue(response);
    load();
    component.markPrimary(2);
    component.markPrimary(2);
    fixture.detectChanges();
    expect(markPrimary).toHaveBeenCalledTimes(1);
    expect(component.comparison()?.primaryQuoteId).toBe(1);
    expect(Array.from(fixture.nativeElement.querySelectorAll('thead button')).every((button) => (button as HTMLButtonElement).disabled)).toBe(true);
    response.error({ error: { message: 'Save refused', errorCode: 'QUOTE_COMPARISON_CUSTOMER_MISMATCH' } });
    fixture.detectChanges();
    expect(component.comparison()?.primaryQuoteId).toBe(1);
    expect(component.saving()).toBe(false);
    expect(text()).toContain('Save refused [QUOTE_COMPARISON_CUSTOMER_MISMATCH]');
  });

  it('cancels a late GET when the route changes and retains the current group', () => {
    const oldRead = new Subject<QuoteComparison>();
    const currentRead = new Subject<QuoteComparison>();
    compare.mockReturnValueOnce(oldRead).mockReturnValueOnce(currentRead);
    load();
    expect(component.loading()).toBe(true);
    load('3');
    currentRead.next(comparison({ dealExternalId: 'deal-3', primaryQuoteId: null, quotes: [alternative(3)] }));
    currentRead.complete();
    oldRead.next(comparison());
    expect(component.comparison()?.dealExternalId).toBe('deal-3');
    expect(component.loading()).toBe(false);
  });

  it('reconciles the current group after a primary response from a previous route', () => {
    const save = new Subject<QuoteComparison>();
    markPrimary.mockReturnValue(save);
    const current = comparison({ dealExternalId: 'deal-3', primaryQuoteId: 3, quotes: [alternative(3)] });
    compare.mockReturnValueOnce(of(comparison())).mockReturnValue(of(current));
    load();
    component.markPrimary(2);
    load('3');
    save.next(comparison({ primaryQuoteId: 2 }));
    save.complete();
    expect(component.comparison()?.dealExternalId).toBe('deal-3');
    expect(component.comparison()?.primaryQuoteId).toBe(3);
    expect(compare.mock.calls.map((call) => call[0])).toEqual([1, 3, 3]);
  });

  it('shows mixed currencies explicitly without ranking or converting the alternatives', () => {
    compare.mockReturnValue(of(comparison({ sameCurrency: false, quotes: [alternative(1), alternative(2, { currency: 'EUR' })] })));
    load();
    expect(text()).toContain('Currencies differ or are unavailable');
    expect(text()).toContain('USD 990.00');
    expect(text()).toContain('EUR 990.00');
    expect(text()).not.toContain('Cheapest');
    expect(text()).not.toContain('Savings');
  });

  it('refetches the current route after a previous write loses its response', () => {
    const save = new Subject<QuoteComparison>();
    markPrimary.mockReturnValue(save);
    const current = comparison({ dealExternalId: 'deal-3', primaryQuoteId: 3, quotes: [alternative(3)] });
    compare.mockReturnValueOnce(of(comparison())).mockReturnValue(of(current));
    load();
    component.markPrimary(2);
    load('3');
    save.error({ status: 0, message: 'Response lost after commit' });
    expect(component.comparison()?.dealExternalId).toBe('deal-3');
    expect(component.error()).toBeNull();
    expect(compare.mock.calls.map((call) => call[0])).toEqual([1, 3, 3]);
  });

  it('shows errors and retry recovers instead of disguising a refused group as empty', () => {
    compare.mockReturnValueOnce(throwError(() => ({ error: { message: 'Group limit', errorCode: 'QUOTE_COMPARISON_LIMIT' } })))
      .mockReturnValueOnce(of(comparison()));
    load();
    expect(text()).toContain('Group limit [QUOTE_COMPARISON_LIMIT]');
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.comparison()?.quotes).toHaveLength(2);
    expect(component.error()).toBeNull();
  });

  it('handles invalid routes, empty groups and an unavailable single alternative', () => {
    load('not-a-number');
    expect(compare).not.toHaveBeenCalled();
    expect(text()).toContain('Choose a valid quote');
    compare.mockReturnValueOnce(of(comparison({ quotes: [] })));
    load('1');
    expect(text()).toContain('No quote alternatives are available');
    compare.mockReturnValueOnce(of(comparison({ quotes: [alternative(3)], primaryQuoteId: null })));
    load('3');
    expect(text()).toContain('No other alternatives are available');
    expect(text()).toContain('Mark primary');
  });

  it('registers the comparison child route before quote detail', () => {
    const comparisonIndex = QUOTES_ROUTES.findIndex((route) => route.path === ':id/compare');
    expect(comparisonIndex).toBeGreaterThan(-1);
    expect(comparisonIndex).toBeLessThan(QUOTES_ROUTES.findIndex((route) => route.path === ':id'));
    expect(QUOTES_ROUTES[comparisonIndex].component).toBe(QuoteComparisonComponent);
  });
});
