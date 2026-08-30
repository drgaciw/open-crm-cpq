import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { QuotesDetailComponent } from './quotes-detail.component';
import { QuoteService } from '../quote.service';
import { MOCK_QUOTES } from '../quotes.mock';
import { QuoteDto } from '@frontend/shared-models';
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
    lineItems: Array.from({ length: item.lineItemCount }, (_, i) => ({
      id: i + 1,
      uuid: `li-${item.id}-${i}`,
      productExternalId: `prod-${i}`,
      quantity: 1,
      sortOrder: i,
      createdAt: item.createdAt,
      updatedAt: item.createdAt,
    })),
    createdAt: item.createdAt,
    updatedAt: item.createdAt,
  };
}

describe('QuotesDetailComponent', () => {
  let fixture: ComponentFixture<QuotesDetailComponent>;
  let component: QuotesDetailComponent;
  let router: Router;
  let getByIdSpy: ReturnType<typeof vi.fn>;
  let calculateSpy: ReturnType<typeof vi.fn>;
  let submitSpy: ReturnType<typeof vi.fn>;
  let addLineItemSpy: ReturnType<typeof vi.fn>;

  const selectedQuote = signal<QuoteDto | null>(null);
  const loading = signal(false);
  const error = signal<string | null>(null);

  beforeEach(async () => {
    getByIdSpy = vi.fn((id: number) => {
      const match = MOCK_QUOTES.find((q) => q.id === id);
      selectedQuote.set(match ? toQuoteDto(match) : null);
      if (!match) {
        error.set('Quote not found');
      }
    });
    calculateSpy = vi.fn(() =>
      of({
        ...toQuoteDto(MOCK_QUOTES[0]),
        totalAmount: 99999,
      }),
    );
    submitSpy = vi.fn(() =>
      of({
        ...toQuoteDto(MOCK_QUOTES[0]),
        status: 'IN_REVIEW',
      }),
    );
    addLineItemSpy = vi.fn(() =>
      of({
        id: 50,
        uuid: 'li-new',
        productExternalId: 'prod-x',
        quantity: 1,
        sortOrder: 0,
        createdAt: '2026-08-30T12:00:00Z',
        updatedAt: '2026-08-30T12:00:00Z',
      }),
    );
    selectedQuote.set(null);
    loading.set(false);
    error.set(null);

    const serviceMock = {
      selectedQuote,
      loading,
      error,
      getById: getByIdSpy,
      calculate: calculateSpy,
      submit: submitSpy,
      addLineItem: addLineItemSpy,
    };

    await TestBed.configureTestingModule({
      imports: [QuotesDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: QuoteService, useValue: serviceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QuotesDetailComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('id', '1');
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads quote via QuoteService.getById on init', () => {
    expect(getByIdSpy).toHaveBeenCalledWith(1);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Acme CRM Seat Rollout');
    expect(compiled.textContent).toContain('Q-2026-0001');
    expect(compiled.textContent).toContain('cust-acme');
  });

  it('shows loading spinner while QuoteService.loading', () => {
    selectedQuote.set(null);
    loading.set(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeTruthy();
  });

  it('shows error banner and retry calls getById again', () => {
    selectedQuote.set(null);
    error.set('Network failure');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Network failure');

    getByIdSpy.mockClear();
    component.retry();
    expect(getByIdSpy).toHaveBeenCalledWith(1);
  });

  it('shows empty state when quote is missing after load', () => {
    selectedQuote.set(null);
    error.set(null);
    loading.set(false);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Quote not found');
  });

  it('navigates back to quotes list', () => {
    component.goBack();
    expect(router.navigate).toHaveBeenCalledWith(['/quotes']);
  });

  it('enables Calculate and Submit for DRAFT and calls QuoteService', () => {
    expect(component.canCalculate()).toBe(true);
    expect(component.canSubmit()).toBe(true);

    component.onCalculate();
    expect(calculateSpy).toHaveBeenCalledWith(1);
    expect(selectedQuote()?.totalAmount).toBe(99999);

    component.onSubmit();
    expect(submitSpy).toHaveBeenCalledWith(1);
    expect(selectedQuote()?.status).toBe('IN_REVIEW');
  });

  it('disables Calculate/Submit for non-editable status', () => {
    selectedQuote.set(toQuoteDto(MOCK_QUOTES[1])); // IN_REVIEW
    fixture.detectChanges();

    expect(component.canCalculate()).toBe(false);
    expect(component.canSubmit()).toBe(false);
    expect(component.canEditLines()).toBe(false);

    component.onCalculate();
    component.onSubmit();
    expect(calculateSpy).not.toHaveBeenCalled();
    expect(submitSpy).not.toHaveBeenCalled();
  });

  it('adds a line item when editable and reloads quote', () => {
    getByIdSpy.mockClear();
    component.lineForm.setValue({
      productExternalId: 'prod-x',
      listPrice: 100,
      quantity: 2,
    });
    component.onAddLineItem();

    expect(addLineItemSpy).toHaveBeenCalledWith(1, {
      productExternalId: 'prod-x',
      listPrice: 100,
      quantity: 2,
    });
    expect(getByIdSpy).toHaveBeenCalledWith(1);
  });

  it('shows action error banner when calculate fails', () => {
    calculateSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'Quote must have at least one line item' } })),
    );
    component.onCalculate();
    fixture.detectChanges();

    expect(component.actionError()).toBe('Quote must have at least one line item');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Quote must have at least one line item',
    );
  });
});
