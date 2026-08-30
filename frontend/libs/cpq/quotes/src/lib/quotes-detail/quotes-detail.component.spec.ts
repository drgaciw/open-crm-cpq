import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { QuotesDetailComponent } from './quotes-detail.component';
import { QuoteService } from '../quote.service';
import { MOCK_QUOTES } from '../quotes.mock';
import { QuoteDto, QuoteLineItemDto } from '@frontend/shared-models';
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
      productName: `Product ${i}`,
      quantity: 1,
      listPrice: 100 + i,
      netPrice: 100 + i,
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
  let approveSpy: ReturnType<typeof vi.fn>;
  let rejectSpy: ReturnType<typeof vi.fn>;
  let sendSpy: ReturnType<typeof vi.fn>;
  let acceptSpy: ReturnType<typeof vi.fn>;
  let addLineItemSpy: ReturnType<typeof vi.fn>;
  let updateLineItemSpy: ReturnType<typeof vi.fn>;
  let removeLineItemSpy: ReturnType<typeof vi.fn>;

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
    approveSpy = vi.fn(() =>
      of({
        ...toQuoteDto(MOCK_QUOTES[1]),
        status: 'APPROVED',
      }),
    );
    rejectSpy = vi.fn(() =>
      of({
        ...toQuoteDto(MOCK_QUOTES[1]),
        status: 'REJECTED',
      }),
    );
    sendSpy = vi.fn(() =>
      of({
        ...toQuoteDto(MOCK_QUOTES[3]),
        status: 'SENT',
      }),
    );
    acceptSpy = vi.fn(() =>
      of({
        ...toQuoteDto(MOCK_QUOTES[4]),
        status: 'ACCEPTED',
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
    updateLineItemSpy = vi.fn(() =>
      of({
        id: 1,
        uuid: 'li-1-0',
        productExternalId: 'prod-0',
        productName: 'Updated name',
        quantity: 3,
        listPrice: 250,
        sortOrder: 0,
        createdAt: '2026-08-01T10:00:00Z',
        updatedAt: '2026-08-30T12:00:00Z',
      } satisfies QuoteLineItemDto),
    );
    removeLineItemSpy = vi.fn(() => of(undefined));
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
      approve: approveSpy,
      reject: rejectSpy,
      send: sendSpy,
      accept: acceptSpy,
      addLineItem: addLineItemSpy,
      updateLineItem: updateLineItemSpy,
      removeLineItem: removeLineItemSpy,
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

  it('lists line items from quote.lineItems', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('prod-0');
    expect(compiled.textContent).toContain('Product 0');
    expect(compiled.textContent).toContain('prod-1');
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

  it('enables Approve/Reject for IN_REVIEW and PENDING_APPROVAL', () => {
    selectedQuote.set(toQuoteDto(MOCK_QUOTES[1])); // IN_REVIEW
    fixture.detectChanges();
    expect(component.canApproveReject()).toBe(true);

    component.onApprove();
    // Route input id stays "1"; actions use route id, not mock quote.id.
    expect(approveSpy).toHaveBeenCalledWith(1);
    expect(selectedQuote()?.status).toBe('APPROVED');

    selectedQuote.set(toQuoteDto(MOCK_QUOTES[2])); // PENDING_APPROVAL
    fixture.detectChanges();
    expect(component.canApproveReject()).toBe(true);
  });

  it('rejects with optional reason from prompt', () => {
    selectedQuote.set(toQuoteDto(MOCK_QUOTES[1]));
    fixture.detectChanges();
    vi.spyOn(window, 'prompt').mockReturnValue('No budget');

    component.onReject();
    expect(rejectSpy).toHaveBeenCalledWith(1, 'No budget');
    expect(selectedQuote()?.status).toBe('REJECTED');
  });

  it('cancels reject when prompt is dismissed', () => {
    selectedQuote.set(toQuoteDto(MOCK_QUOTES[1]));
    fixture.detectChanges();
    vi.spyOn(window, 'prompt').mockReturnValue(null);

    component.onReject();
    expect(rejectSpy).not.toHaveBeenCalled();
  });

  it('enables Send only for APPROVED', () => {
    selectedQuote.set(toQuoteDto(MOCK_QUOTES[3])); // APPROVED
    fixture.detectChanges();
    expect(component.canSend()).toBe(true);
    expect(component.canApproveReject()).toBe(false);
    expect(component.canAccept()).toBe(false);

    component.onSend();
    expect(sendSpy).toHaveBeenCalledWith(1);
    expect(selectedQuote()?.status).toBe('SENT');
  });

  it('enables Accept only for SENT', () => {
    selectedQuote.set(toQuoteDto(MOCK_QUOTES[4])); // SENT
    fixture.detectChanges();
    expect(component.canAccept()).toBe(true);
    expect(component.canSend()).toBe(false);

    component.onAccept();
    expect(acceptSpy).toHaveBeenCalledWith(1);
    expect(selectedQuote()?.status).toBe('ACCEPTED');
  });

  it('does not call approve/send/accept when status gates fail', () => {
    // DRAFT (id 1)
    component.onApprove();
    component.onSend();
    component.onAccept();
    expect(approveSpy).not.toHaveBeenCalled();
    expect(sendSpy).not.toHaveBeenCalled();
    expect(acceptSpy).not.toHaveBeenCalled();
  });

  it('adds a line item when editable and reloads quote', () => {
    getByIdSpy.mockClear();
    component.lineForm.setValue({
      productExternalId: 'prod-x',
      productName: 'Extra Seat',
      listPrice: 100,
      quantity: 2,
      notes: 'addon',
    });
    component.onAddLineItem();

    expect(addLineItemSpy).toHaveBeenCalledWith(1, {
      productExternalId: 'prod-x',
      productName: 'Extra Seat',
      listPrice: 100,
      quantity: 2,
      notes: 'addon',
    });
    expect(getByIdSpy).toHaveBeenCalledWith(1);
  });

  it('omits blank productName/notes on add', () => {
    component.lineForm.setValue({
      productExternalId: 'prod-y',
      productName: '  ',
      listPrice: 50,
      quantity: 1,
      notes: '',
    });
    component.onAddLineItem();

    expect(addLineItemSpy).toHaveBeenCalledWith(1, {
      productExternalId: 'prod-y',
      listPrice: 50,
      quantity: 1,
    });
  });

  it('updates a line item via updateLineItem and reloads', () => {
    getByIdSpy.mockClear();
    const line = selectedQuote()!.lineItems![0];
    component.startLineEdit(line);
    component.editForm.setValue({
      productName: 'Updated name',
      listPrice: 250,
      quantity: 3,
    });
    component.onSaveLineEdit(line);

    expect(updateLineItemSpy).toHaveBeenCalledWith(1, line.id, {
      productExternalId: line.productExternalId,
      productName: 'Updated name',
      listPrice: 250,
      quantity: 3,
    });
    expect(getByIdSpy).toHaveBeenCalledWith(1);
    expect(component.editingLineId()).toBeNull();
  });

  it('removes a line item via removeLineItem and reloads', () => {
    getByIdSpy.mockClear();
    const line = selectedQuote()!.lineItems![0];
    component.onRemoveLineItem(line);

    expect(removeLineItemSpy).toHaveBeenCalledWith(1, line.id);
    expect(getByIdSpy).toHaveBeenCalledWith(1);
  });

  it('does not edit or remove lines when not editable', () => {
    selectedQuote.set(toQuoteDto(MOCK_QUOTES[1]));
    fixture.detectChanges();
    const line = selectedQuote()!.lineItems![0];

    component.startLineEdit(line);
    expect(component.editingLineId()).toBeNull();

    component.onRemoveLineItem(line);
    expect(removeLineItemSpy).not.toHaveBeenCalled();
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

  it('shows action error banner when approve fails', () => {
    selectedQuote.set(toQuoteDto(MOCK_QUOTES[1]));
    fixture.detectChanges();
    approveSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'Not authorized to approve' } })),
    );
    component.onApprove();
    fixture.detectChanges();

    expect(component.actionError()).toBe('Not authorized to approve');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Not authorized to approve',
    );
  });
});
