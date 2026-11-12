import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { QuoteComparisonComponent } from './quote-comparison.component';
import { ComparisonColumn, QuoteComparison, QuoteComparisonService } from './quote-comparison.service';

const column: ComparisonColumn = {
  id: 1, quoteNumber: 'Q-1', name: 'First option', primary: true, status: 'DRAFT', currency: 'USD',
  subtotal: 100, listTotal: 100, discountTotal: 10, discountPercent: 10, tax: 9, grandTotal: 99,
  netAmount: 90, marginAmount: null, marginPercent: null, billingFrequencies: ['MONTHLY'], termMonths: 12,
  validUntil: null, paymentTerms: null, lineCount: 1,
  delta: { comparableCurrency: true, subtotal: 0, discountTotal: 0, tax: 0, grandTotal: 0, grandTotalPercent: 0 },
  skuDiff: { added: [], removed: [], changed: [] },
};
const result: QuoteComparison = {
  dealExternalId: 'deal', baselineQuoteId: 1,
  quotes: [column, { ...column, id: 2, quoteNumber: 'Q-2', name: 'Second option', primary: false }],
  unavailableFields: ['marginAmount', 'marginPercent', 'validUntil', 'paymentTerms'],
};

describe('QuoteComparisonComponent', () => {
  let fixture: ComponentFixture<QuoteComparisonComponent>;
  let component: QuoteComparisonComponent;
  const service = { compare: vi.fn(), markPrimary: vi.fn() };
  beforeEach(async () => {
    service.compare.mockReset().mockReturnValue(of(result));
    service.markPrimary.mockReset().mockReturnValue(of({ id: 2, primary: true }));
    await TestBed.configureTestingModule({ imports: [QuoteComparisonComponent],
      providers: [{ provide: QuoteComparisonService, useValue: service }] }).compileComponents();
    fixture = TestBed.createComponent(QuoteComparisonComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('dealExternalId', 'deal');
    fixture.detectChanges();
  });

  it('loads on demand and renders quote columns, primary badge and unavailable margin', () => {
    expect(service.compare).not.toHaveBeenCalled();
    component.load(); fixture.detectChanges();
    expect(service.compare).toHaveBeenCalledWith('deal');
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('caption')?.textContent).toContain('deal');
    expect(el.querySelector('.badge')?.textContent).toContain('Primary');
    expect(el.textContent).toContain('Q-2');
    expect(el.textContent).toContain('Unavailable');
    expect(component.baselineName()).toBe('Q-1');
  });

  it('marks primary, emits primaryChanged, and reloads server-authoritative alternatives', () => {
    const primaryChanged = vi.fn();
    component.primaryChanged.subscribe(primaryChanged);
    component.load();
    component.markPrimary(2);
    expect(service.markPrimary).toHaveBeenCalledWith(2);
    expect(primaryChanged).toHaveBeenCalledTimes(1);
    expect(service.compare).toHaveBeenCalledTimes(2);
  });

  it('does not emit primaryChanged when markPrimary fails', () => {
    const primaryChanged = vi.fn();
    component.primaryChanged.subscribe(primaryChanged);
    component.load();
    service.markPrimary.mockReturnValueOnce(throwError(() => new Error('forbidden')));
    component.markPrimary(2);
    expect(primaryChanged).not.toHaveBeenCalled();
  });

  it('shows failure and allows retry', () => {
    service.compare.mockReturnValueOnce(throwError(() => new Error('offline')));
    component.load(); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent).toContain('Could not load');
    expect(component.busy()).toBe(false);
    component.load();
    expect(component.result()).toEqual(result);
  });

  it('shows primary failure without changing server data', () => {
    component.load();
    service.markPrimary.mockReturnValueOnce(throwError(() => new Error('forbidden')));
    component.markPrimary(2);
    expect(component.error()).toContain('Could not mark');
    expect(component.result()?.quotes[0].primary).toBe(true);
    expect(component.busy()).toBe(false);
  });

  it('blocks duplicate requests while loading', () => {
    const pending = new Subject<QuoteComparison>();
    service.compare.mockReturnValueOnce(pending);
    component.load(); component.load(); component.markPrimary(2);
    expect(service.compare).toHaveBeenCalledTimes(1);
    expect(service.markPrimary).not.toHaveBeenCalled();
    pending.next(result); pending.complete();
    expect(component.busy()).toBe(false);
  });

  it('discards late response after opportunity changes', () => {
    const pending = new Subject<QuoteComparison>();
    service.compare.mockReturnValueOnce(pending);
    component.load();
    fixture.componentRef.setInput('dealExternalId', 'other'); fixture.detectChanges();
    pending.next(result); pending.complete();
    expect(component.result()).toBeNull();
    expect(component.busy()).toBe(false);
  });

  it('explains cross-currency deltas and empty comparisons', () => {
    service.compare.mockReturnValueOnce(of({ ...result, quotes: [{ ...column, delta: { ...column.delta, comparableCurrency: false } }] }));
    component.load(); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Different currency');
    service.compare.mockReturnValueOnce(of({ ...result, quotes: [] }));
    component.load(); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No quotes for this opportunity');
  });
});
