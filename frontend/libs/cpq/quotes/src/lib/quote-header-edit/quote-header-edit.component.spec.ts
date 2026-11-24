import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { QuoteDto, QuoteUpdateRequest } from '@frontend/shared-models';
import { QuoteService } from '../quote.service';
import {
  QuoteHeaderEditComponent,
  quoteHeaderHttpErrorMessage,
} from './quote-header-edit.component';

function draftQuote(overrides: Partial<QuoteDto> = {}): QuoteDto {
  return {
    id: 1,
    uuid: 'u-1',
    quoteNumber: 'Q-2026-0001',
    name: 'Acme CRM Seat Rollout',
    description: 'Initial rollout',
    customerExternalId: 'cust-acme',
    dealExternalId: 'deal-1',
    status: 'DRAFT',
    subscriptionType: 'NEW',
    currency: 'USD',
    region: 'NA',
    durationInMonths: 12,
    startDate: '2026-01-01',
    endDate: '2026-12-31',
    notes: 'Seed notes',
    createdAt: '2026-08-01T10:00:00Z',
    updatedAt: '2026-08-01T10:00:00Z',
    ...overrides,
  };
}

describe('quoteHeaderHttpErrorMessage', () => {
  it('appends QUOTE_NOT_EDITABLE from HttpErrorResponse body', () => {
    expect(
      quoteHeaderHttpErrorMessage({
        error: {
          message: 'Cannot edit quote in APPROVED status',
          errorCode: 'QUOTE_NOT_EDITABLE',
        },
      }),
    ).toBe('Cannot edit quote in APPROVED status [QUOTE_NOT_EDITABLE]');
  });

  it('appends errorCode from ApiError-shaped object', () => {
    expect(
      quoteHeaderHttpErrorMessage({
        message: 'Not editable',
        errorCode: 'QUOTE_NOT_EDITABLE',
      }),
    ).toBe('Not editable [QUOTE_NOT_EDITABLE]');
  });
});

describe('QuoteHeaderEditComponent', () => {
  let fixture: ComponentFixture<QuoteHeaderEditComponent>;
  let component: QuoteHeaderEditComponent;
  let updateSpy: ReturnType<typeof vi.fn>;
  let getByIdSpy: ReturnType<typeof vi.fn>;
  let notify: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    updateSpy = vi.fn(() =>
      of(
        draftQuote({
          name: 'Updated name',
          region: 'EMEA',
        }),
      ),
    );
    getByIdSpy = vi.fn();
    notify = vi.fn();

    await TestBed.configureTestingModule({
      imports: [QuoteHeaderEditComponent],
      providers: [
        provideNoopAnimations(),
        { provide: MatSnackBar, useValue: { open: notify } },
        {
          provide: QuoteService,
          useValue: {
            update: updateSpy,
            getById: getByIdSpy,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QuoteHeaderEditComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('quote', draftQuote());
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();
  });

  it('confirms a persisted save and does not show confirmation on error', () => {
    component.startEdit();
    component.onSave();
    expect(notify).toHaveBeenCalledWith('Quote saved', 'Dismiss', { duration: 3000 });
    notify.mockClear();
    updateSpy.mockReturnValue(throwError(() => ({ message: 'Write failed' })));
    component.startEdit();
    component.onSave();
    expect(component.error()).toBe('Write failed');
    expect(notify).not.toHaveBeenCalled();
  });

  it('shows Edit when editable and hides form until Edit', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toMatch(/Edit/);
    expect(el.querySelector('form')).toBeNull();
  });

  it('renders Save with mat-icon when editing and not saving (NG8011 icon slot)', () => {
    component.startEdit();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const saveBtn = Array.from(el.querySelectorAll('button')).find((b) =>
      (b.textContent ?? '').includes('Save'),
    );
    expect(saveBtn).toBeTruthy();
    expect(saveBtn!.querySelector('mat-icon')?.textContent?.trim()).toBe('save');
    expect(component.saving()).toBe(false);
  });

  it('hides Edit/Save when not editable', () => {
    fixture.componentRef.setInput('editable', false);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toMatch(/DRAFT or REJECTED/);
    expect(el.textContent).not.toMatch(/\bEdit\b/);
    component.startEdit();
    expect(component.editing()).toBe(false);
  });

  it('offers Subscription type as a choice of the values the API accepts (H-CPQ-Q-009)', () => {
    const el = fixture.nativeElement as HTMLElement;
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Edit'))?.click();
    fixture.detectChanges();
    expect(el.querySelector('input[formcontrolname="subscriptionType"]')).toBeNull();
    expect(el.querySelector('mat-select[formcontrolname="subscriptionType"]')).toBeTruthy();
    expect(component.subscriptionTypes.map((t) => t.value)).toEqual(['SAAS', 'ONPREM', 'HYBRID']);
  });

  it('opens form on Edit and PUTs QuoteUpdateRequest without customer/status/money', () => {
    component.startEdit();
    fixture.detectChanges();
    expect(component.form.controls.name.value).toBe('Acme CRM Seat Rollout');

    component.form.patchValue({
      name: 'Updated name',
      region: 'EMEA',
      description: 'New desc',
      currency: 'eur',
    });

    const saved: QuoteDto[] = [];
    component.saved.subscribe((q) => saved.push(q));

    component.onSave();

    expect(updateSpy).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        name: 'Updated name',
        region: 'EMEA',
        description: 'New desc',
        currency: 'EUR',
        dealExternalId: 'deal-1',
        subscriptionType: 'NEW',
        durationInMonths: 12,
        startDate: '2026-01-01',
        endDate: '2026-12-31',
        notes: 'Seed notes',
      } satisfies QuoteUpdateRequest),
    );
    const body = updateSpy.mock.calls[0][1] as Record<string, unknown>;
    expect(body).not.toHaveProperty('customerExternalId');
    expect(body).not.toHaveProperty('dealType');
    expect(body).not.toHaveProperty('status');
    expect(body).not.toHaveProperty('totalAmount');
    expect(body).not.toHaveProperty('arr');
    expect(getByIdSpy).toHaveBeenCalledWith(1);
    expect(saved[0]?.name).toBe('Updated name');
    expect(component.editing()).toBe(false);
  });

  it('rejects a whitespace-only name and saves a valid name trimmed', () => {
    component.startEdit();
    component.form.controls.name.setValue('  \n\t ');
    component.onSave();

    expect(component.form.controls.name.hasError('required')).toBe(true);
    expect(updateSpy).not.toHaveBeenCalled();
    expect(component.editing()).toBe(true);

    component.form.controls.name.setValue('  Updated name  ');
    component.onSave();

    expect(updateSpy).toHaveBeenCalledWith(1, expect.objectContaining({ name: 'Updated name' }));
  });

  it('sends explicit empty strings for populated Description and Notes cleared by the user', () => {
    component.startEdit();
    component.form.patchValue({ description: '', notes: '   ' });
    component.onSave();

    expect(updateSpy).toHaveBeenCalledWith(1, expect.objectContaining({ description: '', notes: '' }));
  });

  it('omits untouched blank Description and legacy whitespace Notes after reopening', () => {
    component.startEdit();
    component.cancelEdit();
    fixture.componentRef.setInput('quote', draftQuote({ description: '', notes: '   ' }));
    fixture.detectChanges();
    component.startEdit();
    component.form.controls.name.setValue('Unrelated name change');
    component.onSave();

    const body = updateSpy.mock.calls[0][1] as QuoteUpdateRequest;
    expect(body).not.toHaveProperty('description');
    expect(body).not.toHaveProperty('notes');
  });

  it.each([
    ['startDate', 'clearStartDate', 'endDate', 'clearEndDate'],
    ['endDate', 'clearEndDate', 'startDate', 'clearStartDate'],
  ] as const)('clears only the previously populated %s and completes when the response omits it', (date, flag, other, otherFlag) => {
    const quote = draftQuote({ dealType: 'NEW' });
    fixture.componentRef.setInput('quote', quote);
    updateSpy.mockReturnValueOnce(of({ ...quote, [date]: undefined }));
    component.startEdit();
    component.form.controls[date].setValue('');
    const saved = vi.fn();
    component.saved.subscribe(saved);

    component.onSave();

    expect(updateSpy).toHaveBeenCalledWith(quote.id, expect.objectContaining({
      [flag]: true,
      [other]: quote[other],
      notes: quote.notes,
    }));
    const body = updateSpy.mock.calls[0][1];
    expect(body).not.toHaveProperty(date);
    expect(body).not.toHaveProperty(otherFlag);
    expect(component.editing()).toBe(false);
    expect(saved).toHaveBeenCalledOnce();
    expect(getByIdSpy).toHaveBeenCalledWith(quote.id);
    expect(notify).toHaveBeenCalledOnce();
  });

  it('omits an initially blank date and sends a date entered into an initially blank field', () => {
    const quote = draftQuote({ dealType: 'NEW', startDate: undefined, endDate: undefined });
    fixture.componentRef.setInput('quote', quote);
    component.startEdit();
    component.form.controls.endDate.setValue('2030-01-31');

    component.onSave();

    const body = updateSpy.mock.calls[0][1];
    expect(body).toHaveProperty('endDate', '2030-01-31');
    expect(body).not.toHaveProperty('startDate');
    expect(body).not.toHaveProperty('clearStartDate');
    expect(body).not.toHaveProperty('clearEndDate');
  });

  it('restores dates when Cancel discards a clear before saving', () => {
    const quote = draftQuote({ dealType: 'NEW' });
    fixture.componentRef.setInput('quote', quote);
    component.startEdit();
    component.form.patchValue({ startDate: '', endDate: '' });

    component.cancelEdit();
    component.startEdit();

    expect(updateSpy).not.toHaveBeenCalled();
    expect(component.form.controls.startDate.value).toBe(quote.startDate);
    expect(component.form.controls.endDate.value).toBe(quote.endDate);
  });

  it('keeps edits open without confirming a save when the response retains a requested clear', () => {
    const quote = draftQuote({ dealType: 'NEW' });
    fixture.componentRef.setInput('quote', quote);
    updateSpy.mockReturnValueOnce(of({ ...quote, startDate: undefined }));
    component.startEdit();
    component.form.patchValue({ startDate: '', endDate: '', notes: 'User edit' });
    const saved = vi.fn();
    component.saved.subscribe(saved);

    component.onSave();
    fixture.detectChanges();

    expect(updateSpy).toHaveBeenCalledWith(quote.id, expect.objectContaining({
      clearStartDate: true, clearEndDate: true, notes: 'User edit',
    }));
    expect(component.editing()).toBe(true);
    expect(component.saving()).toBe(false);
    expect(component.form.getRawValue()).toMatchObject({ startDate: '', endDate: '', notes: 'User edit' });
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent)
      .toContain('The server kept a date you cleared');
    expect(saved).not.toHaveBeenCalled();
    expect(notify).not.toHaveBeenCalled();
    expect(getByIdSpy).not.toHaveBeenCalled();
  });

  it.each([
    draftQuote({ dealType: 'RENEWAL', continuationOperation: 'RENEWAL' }),
    draftQuote({ dealType: 'NEW', targetSubscriptionNumber: 'SUB-1' }),
    draftQuote({ dealType: 'NEW', status: 'PENDING_APPROVAL' }),
  ])('preserves the date hold for $dealType $status quotes', (quote) => {
    fixture.componentRef.setInput('quote', quote);
    component.startEdit();
    component.form.controls.startDate.setValue('');

    component.onSave();

    expect(updateSpy).not.toHaveBeenCalled();
    expect(component.editing()).toBe(true);
    expect(component.error()).toContain('only be cleared on new draft or rejected quotes');
  });

  it('shows error banner including QUOTE_NOT_EDITABLE', () => {
    updateSpy.mockReturnValueOnce(
      throwError(() => ({
        error: {
          message: 'Cannot edit quote in SENT status',
          errorCode: 'QUOTE_NOT_EDITABLE',
        },
      })),
    );
    component.startEdit();
    fixture.detectChanges();
    component.onSave();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toMatch(/QUOTE_NOT_EDITABLE/);
    expect(el.textContent).toMatch(/Cannot edit quote/);
    expect(getByIdSpy).not.toHaveBeenCalled();
    expect(component.editing()).toBe(true);
  });

  it('applies cdkTextareaAutosize with min/max rows to the description textarea', () => {
    component.startEdit();
    fixture.detectChanges();
    const textareaDe = fixture.debugElement.query(By.directive(CdkTextareaAutosize));
    expect(textareaDe).toBeTruthy();
    const autosize = textareaDe.injector.get(CdkTextareaAutosize);
    expect(autosize.minRows).toBe(3);
    expect(autosize.maxRows).toBe(10);
  });
});
