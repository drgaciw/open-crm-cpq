import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
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
    ).toBe('Cannot edit quote in APPROVED status (QUOTE_NOT_EDITABLE)');
  });

  it('appends errorCode from ApiError-shaped object', () => {
    expect(
      quoteHeaderHttpErrorMessage({
        message: 'Not editable',
        errorCode: 'QUOTE_NOT_EDITABLE',
      }),
    ).toBe('Not editable (QUOTE_NOT_EDITABLE)');
  });
});

describe('QuoteHeaderEditComponent', () => {
  let fixture: ComponentFixture<QuoteHeaderEditComponent>;
  let component: QuoteHeaderEditComponent;
  let updateSpy: ReturnType<typeof vi.fn>;
  let getByIdSpy: ReturnType<typeof vi.fn>;

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

    await TestBed.configureTestingModule({
      imports: [QuoteHeaderEditComponent],
      providers: [
        provideNoopAnimations(),
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
});
