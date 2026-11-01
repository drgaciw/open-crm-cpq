import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, Subject, throwError } from 'rxjs';
import {
  QuoteLineItemDto,
  RampStep,
} from '@frontend/shared-models';
import { QuoteService } from '../quote.service';
import {
  QuoteRampEditorComponent,
  validateRampSchedule,
} from './quote-ramp-editor.component';

function line(overrides: Partial<QuoteLineItemDto> = {}): QuoteLineItemDto {
  return {
    id: 1,
    uuid: 'li-1',
    productExternalId: 'seat',
    productName: 'Named seat',
    lineItemType: 'SUBSCRIPTION',
    billingFrequency: 'MONTHLY',
    quantity: 50,
    listPrice: 40,
    sortOrder: 0,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
    ...overrides,
  };
}

function step(overrides: Partial<RampStep> = {}): RampStep {
  return {
    sequence: 1,
    startMonth: 1,
    months: 12,
    quantity: 50,
    priceBasis: 'ABSOLUTE',
    unitPrice: 40,
    ...overrides,
  };
}

describe('validateRampSchedule', () => {
  it('accepts three contiguous years that match a 36 month term', () => {
    const issues = validateRampSchedule(
      [
        step({ sequence: 1, startMonth: 1, months: 12, unitPrice: 40 }),
        step({ sequence: 2, startMonth: 13, months: 12, unitPrice: 45 }),
        step({ sequence: 3, startMonth: 25, months: 12, unitPrice: 50 }),
      ],
      36,
      true,
    );
    expect(issues).toEqual([]);
  });

  it('reports a gap when a start month is missing', () => {
    const issues = validateRampSchedule(
      [step({ startMonth: Number.NaN, months: 12, unitPrice: 40 })],
      12,
      true,
    );
    expect(issues.map((issue) => issue.code)).toEqual(['RAMP_GAP']);
  });

  it('reports a gap when the schedule does not start at month 1', () => {
    expect(
      validateRampSchedule([step({ startMonth: 0, months: 12 })], 12, true).map(
        (issue) => issue.code,
      ),
    ).toEqual(['RAMP_GAP']);
    expect(
      validateRampSchedule([step({ startMonth: 5, months: 12 })], 12, true).map(
        (issue) => issue.code,
      ),
    ).toEqual(['RAMP_GAP']);
  });

  it('reports more than 36 periods as RAMP_TOO_MANY_PERIODS', () => {
    const steps = Array.from({ length: 37 }, (_, index) =>
      step({ sequence: index + 1, startMonth: index + 1, months: 1, unitPrice: 1 }),
    );
    expect(validateRampSchedule(steps, 37, true).map((issue) => issue.code)).toEqual([
      'RAMP_TOO_MANY_PERIODS',
    ]);
  });

  it('reports RAMP_PRICE_BASIS when the price fields disagree with the basis', () => {
    expect(
      validateRampSchedule(
        [step({ unitPrice: 40, discountFraction: 0.05 })],
        12,
        true,
      ).map((issue) => issue.code),
    ).toEqual(['RAMP_PRICE_BASIS']);
    expect(
      validateRampSchedule(
        [
          step({
            priceBasis: 'DISCOUNT_FRACTION',
            unitPrice: undefined,
            discountFraction: undefined,
          }),
        ],
        12,
        true,
      ).map((issue) => issue.code),
    ).toEqual(['RAMP_PRICE_BASIS']);
    expect(
      validateRampSchedule(
        [
          step({
            priceBasis: 'DISCOUNT_FRACTION',
            unitPrice: undefined,
            discountFraction: 0.05,
          }),
        ],
        12,
        true,
      ),
    ).toEqual([]);
  });

  it('reports a gap between month 12 and month 14', () => {
    const issues = validateRampSchedule(
      [
        step({ sequence: 1, startMonth: 1, months: 12 }),
        step({ sequence: 2, startMonth: 14, months: 22, unitPrice: 45 }),
      ],
      34,
      true,
    );
    expect(issues.map((issue) => issue.code)).toEqual(['RAMP_GAP']);
  });

  it('reports an overlap when the next period starts too early', () => {
    const issues = validateRampSchedule(
      [
        step({ sequence: 1, startMonth: 1, months: 12 }),
        step({ sequence: 2, startMonth: 12, months: 24, unitPrice: 45 }),
      ],
      36,
      true,
    );
    expect(issues.map((issue) => issue.code)).toEqual(['RAMP_OVERLAP']);
  });

  it('reports a term mismatch when periods cover 30 of 36 months', () => {
    const issues = validateRampSchedule(
      [
        step({ sequence: 1, startMonth: 1, months: 12 }),
        step({ sequence: 2, startMonth: 13, months: 18, unitPrice: 45 }),
      ],
      36,
      true,
    );
    expect(issues.map((issue) => issue.code)).toEqual(['RAMP_TERM_MISMATCH']);
  });

  it('reports an empty period when months are zero', () => {
    const issues = validateRampSchedule(
      [step({ months: 0, unitPrice: 40 })],
      0,
      true,
    );
    expect(issues.map((issue) => issue.code)).toEqual(['RAMP_PERIOD_EMPTY']);
  });

  it('reports a negative price and a negative quantity', () => {
    expect(
      validateRampSchedule([step({ unitPrice: -1 })], 12, true).map((issue) => issue.code),
    ).toEqual(['RAMP_NEGATIVE_VALUE']);
    expect(
      validateRampSchedule([step({ quantity: -5, unitPrice: 40 })], 12, true).map(
        (issue) => issue.code,
      ),
    ).toEqual(['RAMP_NEGATIVE_VALUE']);
  });

  it('reports a disallowed line type when a schedule is present', () => {
    const issues = validateRampSchedule([step()], 12, false);
    expect(issues.map((issue) => issue.code)).toEqual(['RAMP_NOT_ALLOWED_FOR_LINE_TYPE']);
  });

  it('treats an empty step list as no schedule', () => {
    expect(validateRampSchedule([], 36, true)).toEqual([]);
    expect(validateRampSchedule([], 36, false)).toEqual([]);
  });
});

describe('QuoteRampEditorComponent', () => {
  let fixture: ComponentFixture<QuoteRampEditorComponent>;
  let component: QuoteRampEditorComponent;
  let updateLineItem: ReturnType<typeof vi.fn>;
  let expandRampSchedule: ReturnType<typeof vi.fn>;
  let extendRampSchedule: ReturnType<typeof vi.fn>;
  let getById: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    updateLineItem = vi.fn(() => of(line()));
    expandRampSchedule = vi.fn(() => of([]));
    extendRampSchedule = vi.fn(() => of([]));
    getById = vi.fn();

    await TestBed.configureTestingModule({
      imports: [QuoteRampEditorComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: QuoteService,
          useValue: {
            updateLineItem,
            expandRampSchedule,
            extendRampSchedule,
            getById,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QuoteRampEditorComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('quoteId', 7);
    fixture.componentRef.setInput('lines', [line()]);
    fixture.componentRef.setInput('durationInMonths', 36);
    fixture.componentRef.setInput('currency', 'USD');
    fixture.componentRef.setInput('editable', true);
    fixture.componentRef.setInput('tcv', null);
    fixture.componentRef.setInput('acv', null);
    fixture.componentRef.setInput('exitArr', null);
    fixture.detectChanges();
  });

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function fill(periods: Array<Partial<RampStep>>): void {
    component.form.controls.steps.clear();
    for (const period of periods) {
      component.addPeriod(period);
    }
    fixture.detectChanges();
  }

  it('adds, edits, and removes a period', () => {
    const add = fixture.nativeElement.querySelector(
      '[data-testid="add-period"]',
    ) as HTMLButtonElement;
    add.click();
    fixture.detectChanges();
    expect(component.form.controls.steps.length).toBe(1);

    component.form.controls.steps.at(0).controls.months.setValue(6);
    fixture.detectChanges();
    const months = fixture.nativeElement.querySelector(
      '[data-testid="preview-months-1"]',
    ) as HTMLElement;
    expect(months.textContent?.trim()).toBe('6');

    const remove = fixture.nativeElement.querySelector(
      '[data-testid="remove-period"]',
    ) as HTMLButtonElement;
    remove.click();
    fixture.detectChanges();
    expect(component.form.controls.steps.length).toBe(0);
    expect(text()).toContain('No periods yet.');
  });

  it('shows inline gap, overlap, term, empty, and negative checks', () => {
    fill([
      { startMonth: 1, months: 12, quantity: 50, unitPrice: 40 },
      { startMonth: 14, months: 22, quantity: 50, unitPrice: 45 },
    ]);
    expect(text()).toContain('RAMP_GAP');

    fill([
      { startMonth: 1, months: 12, quantity: 50, unitPrice: 40 },
      { startMonth: 12, months: 24, quantity: 50, unitPrice: 45 },
    ]);
    expect(text()).toContain('RAMP_OVERLAP');
    expect(text()).not.toContain('RAMP_GAP');

    fill([
      { startMonth: 1, months: 12, quantity: 50, unitPrice: 40 },
      { startMonth: 13, months: 18, quantity: 50, unitPrice: 45 },
    ]);
    expect(text()).toContain('RAMP_TERM_MISMATCH');

    fill([{ startMonth: 1, months: 0, quantity: 50, unitPrice: 40 }]);
    expect(text()).toContain('RAMP_PERIOD_EMPTY');

    fill([{ startMonth: 1, months: 36, quantity: 50, unitPrice: -5 }]);
    expect(text()).toContain('RAMP_NEGATIVE_VALUE');
    expect(updateLineItem).not.toHaveBeenCalled();
  });

  it('does not save a period whose start month was cleared', () => {
    fill([{ startMonth: 1, months: 36, quantity: 50, unitPrice: 40 }]);
    component.form.controls.steps.at(0).controls.startMonth.setValue(null);
    fixture.detectChanges();

    expect(text()).toContain('RAMP_GAP');
    const save = fixture.nativeElement.querySelector(
      '[data-testid="save-schedule"]',
    ) as HTMLButtonElement;
    expect(save.disabled).toBe(true);
    component.onSave();
    expect(updateLineItem).not.toHaveBeenCalled();
  });

  it('shows RAMP_PRICE_BASIS and does not save when both price fields are set', () => {
    fill([
      {
        startMonth: 1,
        months: 36,
        quantity: 50,
        priceBasis: 'ABSOLUTE',
        unitPrice: 40,
        discountFraction: 0.05,
      },
    ]);
    expect(text()).toContain('RAMP_PRICE_BASIS');
    expect(text()).not.toContain('needs a unit price');
    component.onSave();
    expect(updateLineItem).not.toHaveBeenCalled();
  });

  it('shows RAMP_TERM_MISMATCH when the escalator length does not divide the term', () => {
    component.form.controls.escalator.setValue({
      startPrice: 40,
      mode: 'PERCENT',
      amount: 5,
      everyMonths: 10,
    });
    fixture.detectChanges();
    expect(text()).toContain('RAMP_TERM_MISMATCH');
    expect(text()).toContain('5 means 5%');
    expect(text()).not.toContain('0.05 = 5%');
    const generate = fixture.nativeElement.querySelector(
      '[data-testid="expand-escalator"]',
    ) as HTMLButtonElement;
    expect(generate.disabled).toBe(true);
    component.onExpand();
    expect(expandRampSchedule).not.toHaveBeenCalled();
  });

  it('saves a discount fraction of 0.05 and an escalator of 5 percent points', () => {
    fill([
      {
        startMonth: 1,
        months: 36,
        quantity: 50,
        priceBasis: 'DISCOUNT_FRACTION',
        discountFraction: 0.05,
      },
    ]);
    component.form.controls.steps.at(0).controls.unitPrice.setValue(null);
    component.form.controls.escalator.setValue({
      startPrice: 40,
      mode: 'PERCENT',
      amount: 5,
      everyMonths: 12,
    });
    fixture.detectChanges();
    expect(text()).toContain('0.05 means 5% off list');
    expect(text()).toContain('5 means 5%');
    const save = fixture.nativeElement.querySelector(
      '[data-testid="save-schedule"]',
    ) as HTMLButtonElement;
    expect(save.disabled).toBe(false);
    save.click();
    expect(updateLineItem).toHaveBeenCalledWith(7, 1, {
      productExternalId: 'seat',
      productName: 'Named seat',
      lineItemType: 'SUBSCRIPTION',
      billingFrequency: 'MONTHLY',
      listPrice: 40,
      quantity: 50,
      sortOrder: 0,
      schedule: {
        steps: [
          {
            sequence: 1,
            startMonth: 1,
            months: 36,
            quantity: 50,
            priceBasis: 'DISCOUNT_FRACTION',
            discountFraction: 0.05,
          },
        ],
        escalator: { startPrice: 40, mode: 'PERCENT', amount: 5, everyMonths: 12 },
      },
    });
  });

  it('shows the disallowed line type and does not save a schedule', () => {
    fixture.componentRef.setInput(
      'lines',
      [line({ id: 4, lineItemType: 'FEE', billingFrequency: 'MONTHLY' })],
    );
    fixture.detectChanges();
    expect(text()).toContain('RAMP_NOT_ALLOWED_FOR_LINE_TYPE');

    const add = fixture.nativeElement.querySelector(
      '[data-testid="add-period"]',
    ) as HTMLButtonElement;
    expect(add.disabled).toBe(true);
    component.addPeriod({ months: 36, unitPrice: 10 });
    component.onSave();
    expect(updateLineItem).not.toHaveBeenCalled();
    expect(expandRampSchedule).not.toHaveBeenCalled();
  });

  it('rejects a one-time billing frequency', () => {
    fixture.componentRef.setInput(
      'lines',
      [line({ billingFrequency: 'ONE_TIME', lineItemType: 'PRODUCT' })],
    );
    fixture.detectChanges();
    expect(text()).toContain('RAMP_NOT_ALLOWED_FOR_LINE_TYPE');
  });

  it('expands an escalator without persisting the line', () => {
    expandRampSchedule.mockReturnValue(
      of([
        step({ sequence: 1, startMonth: 1, months: 12, unitPrice: 40 }),
        step({ sequence: 2, startMonth: 13, months: 12, unitPrice: 42 }),
        step({ sequence: 3, startMonth: 25, months: 12, unitPrice: 44.1 }),
      ]),
    );
    component.form.controls.escalator.setValue({
      startPrice: 40,
      mode: 'PERCENT',
      amount: 5,
      everyMonths: 12,
    });
    fixture.detectChanges();

    const generate = fixture.nativeElement.querySelector(
      '[data-testid="expand-escalator"]',
    ) as HTMLButtonElement;
    generate.click();
    fixture.detectChanges();

    expect(expandRampSchedule).toHaveBeenCalledWith(7, {
      durationInMonths: 36,
      escalator: {
        startPrice: 40,
        mode: 'PERCENT',
        amount: 5,
        everyMonths: 12,
      },
    });
    expect(updateLineItem).not.toHaveBeenCalled();
    expect(getById).not.toHaveBeenCalled();
    expect(component.form.controls.steps.length).toBe(3);
    expect(component.form.controls.steps.at(0).controls.unitPrice.value).toBe(40);
    expect(component.form.controls.steps.at(1).controls.unitPrice.value).toBe(42);
    expect(component.form.controls.steps.at(2).controls.unitPrice.value).toBe(44.1);
    expect(text()).toContain('—');
  });

  it('saves explicit steps on the line and reloads the quote', () => {
    fill([
      { startMonth: 1, months: 12, quantity: 50, priceBasis: 'ABSOLUTE', unitPrice: 40 },
      { startMonth: 13, months: 12, quantity: 50, priceBasis: 'ABSOLUTE', unitPrice: 45 },
      { startMonth: 25, months: 12, quantity: 50, priceBasis: 'ABSOLUTE', unitPrice: 50 },
    ]);
    const save = fixture.nativeElement.querySelector(
      '[data-testid="save-schedule"]',
    ) as HTMLButtonElement;
    expect(save.disabled).toBe(false);
    save.click();

    expect(expandRampSchedule).not.toHaveBeenCalled();
    expect(updateLineItem).toHaveBeenCalledWith(7, 1, {
      productExternalId: 'seat',
      productName: 'Named seat',
      lineItemType: 'SUBSCRIPTION',
      billingFrequency: 'MONTHLY',
      listPrice: 40,
      quantity: 50,
      sortOrder: 0,
      schedule: {
        steps: [
          step({ sequence: 1, startMonth: 1, months: 12, quantity: 50, unitPrice: 40 }),
          step({ sequence: 2, startMonth: 13, months: 12, quantity: 50, unitPrice: 45 }),
          step({ sequence: 3, startMonth: 25, months: 12, quantity: 50, unitPrice: 50 }),
        ],
      },
    });
    expect(getById).toHaveBeenCalledWith(7);
  });

  it('previews priced periods and quote TCV, ACV, and exit ARR when present', () => {
    fixture.componentRef.setInput('lines', [
      line({
        schedule: {
          steps: [
            step({ sequence: 1, startMonth: 1, months: 12, unitPrice: 40 }),
            step({ sequence: 2, startMonth: 13, months: 12, unitPrice: 45 }),
            step({ sequence: 3, startMonth: 25, months: 12, unitPrice: 50 }),
          ],
        },
        pricedPeriods: [
          {
            sequence: 1,
            months: 12,
            quantity: 50,
            netUnitPrice: 40,
            netMonthly: 2000,
            periodAmount: 24000,
          },
          {
            sequence: 2,
            months: 12,
            quantity: 50,
            netUnitPrice: 45,
            netMonthly: 2250,
            periodAmount: 27000,
          },
          {
            sequence: 3,
            months: 12,
            quantity: 50,
            netUnitPrice: 50,
            netMonthly: 2500,
            periodAmount: 30000,
          },
        ],
      }),
    ]);
    fixture.componentRef.setInput('tcv', 81000);
    fixture.componentRef.setInput('acv', 27000);
    fixture.componentRef.setInput('exitArr', 30000);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[data-testid="preview-net-1"]')?.textContent).toContain(
      '$40.00',
    );
    expect(compiled.querySelector('[data-testid="preview-monthly-1"]')?.textContent).toContain(
      '$2,000.00',
    );
    expect(compiled.querySelector('[data-testid="preview-total-3"]')?.textContent).toContain(
      '$30,000.00',
    );
    expect(compiled.querySelector('[data-testid="preview-quantity-2"]')?.textContent?.trim()).toBe(
      '50',
    );
    expect(compiled.querySelector('[data-testid="metric-tcv"]')?.textContent).toContain(
      '$81,000.00',
    );
    expect(compiled.querySelector('[data-testid="metric-acv"]')?.textContent).toContain(
      '$27,000.00',
    );
    expect(compiled.querySelector('[data-testid="metric-exit-arr"]')?.textContent).toContain(
      '$30,000.00',
    );
    expect(text()).not.toContain('Governing period');

    fixture.componentRef.setInput('tcv', null);
    fixture.componentRef.setInput('acv', null);
    fixture.componentRef.setInput('exitArr', null);
    fixture.detectChanges();
    expect(compiled.querySelector('[data-testid="metric-tcv"]')).toBeNull();
    expect(compiled.querySelector('[data-testid="metric-acv"]')).toBeNull();
    expect(compiled.querySelector('[data-testid="metric-exit-arr"]')).toBeNull();
    expect(compiled.querySelector('[data-testid="preview-net-1"]')?.textContent).toContain(
      '$40.00',
    );
  });

  it('AC-532-9 one Generate action fills all 3 rows of a 36-month yearly ramp', () => {
    expandRampSchedule.mockReturnValue(
      of({
        steps: [
          step({ sequence: 1, startMonth: 1, months: 12, unitPrice: 42.8 }),
          step({ sequence: 2, startMonth: 13, months: 12, unitPrice: 45.796 }),
          step({ sequence: 3, startMonth: 25, months: 12, unitPrice: 49.0017 }),
        ],
      }),
    );
    component.form.controls.escalator.setValue({
      startPrice: 42.8,
      mode: 'PERCENT',
      amount: 7,
      everyMonths: 12,
    });
    fixture.detectChanges();

    const generate = fixture.nativeElement.querySelector(
      '[data-testid="expand-escalator"]',
    ) as HTMLButtonElement;
    generate.click();
    fixture.detectChanges();

    expect(expandRampSchedule).toHaveBeenCalledTimes(1);
    expect(updateLineItem).not.toHaveBeenCalled();
    expect(component.form.controls.steps.length).toBe(3);
    expect(component.form.controls.steps.at(0).controls.unitPrice.value).toBe(42.8);
    expect(component.form.controls.steps.at(1).controls.startMonth.value).toBe(13);
    expect(component.form.controls.steps.at(2).controls.unitPrice.value).toBe(49.0017);
    expect(fixture.nativeElement.querySelector('[data-testid="preview-period-3"]')).toBeTruthy();
  });

  it('AC-532-9 extend is labelled, keyboard reachable, and disabled while the schedule is empty', () => {
    expect(text()).toContain('No periods yet.');
    const extend = fixture.nativeElement.querySelector(
      '[data-testid="extend-escalator"]',
    ) as HTMLButtonElement;
    const duration = fixture.nativeElement.querySelector(
      '[data-testid="extend-duration"]',
    ) as HTMLInputElement;
    expect(extend.tagName).toBe('BUTTON');
    expect(extend.type).toBe('button');
    expect(extend.getAttribute('aria-label')).toBe('Extend ramp periods');
    expect(extend.disabled).toBe(true);
    expect(duration.getAttribute('aria-label')).toBe('Extend to months');
    expect(text()).toContain('Extend to months');
    duration.focus();
    expect(document.activeElement).toBe(duration);
    component.onExtend();
    expect(extendRampSchedule).not.toHaveBeenCalled();
  });

  it('AC-532-9 shows a term mismatch and does not extend when the new term is not longer', () => {
    fill([{ startMonth: 1, months: 36, quantity: 50, unitPrice: 42.8 }]);
    component.form.controls.escalator.setValue({
      startPrice: 42.8,
      mode: 'PERCENT',
      amount: 7,
      everyMonths: 12,
    });
    component.form.controls.extendDuration.setValue(36);
    fixture.detectChanges();

    expect(text()).toContain('RAMP_TERM_MISMATCH');
    const extend = fixture.nativeElement.querySelector(
      '[data-testid="extend-escalator"]',
    ) as HTMLButtonElement;
    expect(extend.disabled).toBe(true);
    component.onExtend();
    expect(extendRampSchedule).not.toHaveBeenCalled();
    expect(updateLineItem).not.toHaveBeenCalled();
  });

  it('AC-532-9 extend appends rows from the service and leaves edited prices untouched', () => {
    const pending = new Subject<RampStep[]>();
    extendRampSchedule.mockReturnValue(pending.asObservable());
    fill([
      { startMonth: 1, months: 12, quantity: 50, unitPrice: 42.8 },
      { startMonth: 13, months: 12, quantity: 50, unitPrice: 44 },
      { startMonth: 25, months: 12, quantity: 50, unitPrice: 47 },
    ]);
    component.form.controls.escalator.setValue({
      startPrice: 42.8,
      mode: 'PERCENT',
      amount: 7,
      everyMonths: 12,
    });
    component.form.controls.extendDuration.setValue(60);
    fixture.detectChanges();

    const extend = fixture.nativeElement.querySelector(
      '[data-testid="extend-escalator"]',
    ) as HTMLButtonElement;
    expect(extend.disabled).toBe(false);
    extend.focus();
    expect(document.activeElement).toBe(extend);
    extend.click();
    fixture.detectChanges();

    expect(component.form.controls.steps.length).toBe(3);
    expect(extendRampSchedule).toHaveBeenCalledWith(7, {
      durationInMonths: 60,
      steps: [
        expect.objectContaining({ sequence: 1, unitPrice: 42.8, startMonth: 1, months: 12 }),
        expect.objectContaining({ sequence: 2, unitPrice: 44, startMonth: 13, months: 12 }),
        expect.objectContaining({ sequence: 3, unitPrice: 47, startMonth: 25, months: 12 }),
      ],
      escalator: { startPrice: 42.8, mode: 'PERCENT', amount: 7, everyMonths: 12 },
    });
    expect(updateLineItem).not.toHaveBeenCalled();

    pending.next([
      step({ sequence: 1, startMonth: 1, months: 12, unitPrice: 42.8 }),
      step({ sequence: 2, startMonth: 13, months: 12, unitPrice: 44 }),
      step({ sequence: 3, startMonth: 25, months: 12, unitPrice: 47 }),
      step({ sequence: 4, startMonth: 37, months: 12, unitPrice: 50.29 }),
      step({ sequence: 5, startMonth: 49, months: 12, unitPrice: 53.8103 }),
    ]);
    fixture.detectChanges();

    expect(component.form.controls.steps.length).toBe(5);
    expect(component.form.controls.steps.at(1).controls.unitPrice.value).toBe(44);
    expect(component.form.controls.steps.at(2).controls.unitPrice.value).toBe(47);
    expect(component.form.controls.steps.at(3).controls.unitPrice.value).toBe(50.29);
    expect(updateLineItem).not.toHaveBeenCalled();
    expect(getById).not.toHaveBeenCalled();
  });

  it('AC-532-9 shows the extend error and keeps the edited rows', () => {
    extendRampSchedule.mockReturnValue(
      throwError(() => ({ message: 'RAMP_TERM_MISMATCH', errorCode: 'RAMP_SCHEDULE_INVALID' })),
    );
    fill([
      { startMonth: 1, months: 12, quantity: 50, unitPrice: 42.8 },
      { startMonth: 13, months: 12, quantity: 50, unitPrice: 44 },
      { startMonth: 25, months: 12, quantity: 50, unitPrice: 47 },
    ]);
    component.form.controls.escalator.setValue({
      startPrice: 42.8,
      mode: 'PERCENT',
      amount: 7,
      everyMonths: 12,
    });
    component.form.controls.extendDuration.setValue(60);
    fixture.detectChanges();

    component.onExtend();
    fixture.detectChanges();

    expect(text()).toContain('RAMP_TERM_MISMATCH');
    expect(component.form.controls.steps.length).toBe(3);
    expect(component.form.controls.steps.at(1).controls.unitPrice.value).toBe(44);
    expect(updateLineItem).not.toHaveBeenCalled();
  });

  it('hides extend when the quote is not editable', () => {
    fixture.componentRef.setInput('editable', false);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[data-testid="extend-escalator"]')).toBeNull();
    expect(compiled.querySelector('[data-testid="extend-duration"]')).toBeNull();
    component.onExtend();
    expect(extendRampSchedule).not.toHaveBeenCalled();
  });

  it('hides authoring actions when the quote is not editable', () => {
    fixture.componentRef.setInput('editable', false);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[data-testid="add-period"]')).toBeNull();
    expect(compiled.querySelector('[data-testid="save-schedule"]')).toBeNull();
    expect(compiled.querySelector('[data-testid="expand-escalator"]')).toBeNull();
    expect(compiled.querySelector('table')).toBeTruthy();
    component.onSave();
    component.onExpand();
    expect(updateLineItem).not.toHaveBeenCalled();
    expect(expandRampSchedule).not.toHaveBeenCalled();
  });
});

describe('QuoteRampEditorComponent HTTP', () => {
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it('POSTs ramp-schedule/expand and does not PUT the line', () => {
    TestBed.configureTestingModule({
      imports: [QuoteRampEditorComponent],
      providers: [
        QuoteService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
      ],
    });
    const fixture = TestBed.createComponent(QuoteRampEditorComponent);
    fixture.componentRef.setInput('quoteId', 7);
    fixture.componentRef.setInput('lines', [line()]);
    fixture.componentRef.setInput('durationInMonths', 36);
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();

    fixture.componentInstance.form.controls.escalator.setValue({
      startPrice: 40,
      mode: 'PERCENT',
      amount: 5,
      everyMonths: 12,
    });
    fixture.detectChanges();
    fixture.componentInstance.onExpand();

    const http = TestBed.inject(HttpTestingController);
    const req = http.expectOne('/api/v1/quotes/7/ramp-schedule/expand');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      durationInMonths: 36,
      escalator: {
        startPrice: 40,
        mode: 'PERCENT',
        amount: 5,
        everyMonths: 12,
      },
    });
    req.flush([
      step({ sequence: 1, startMonth: 1, months: 12, unitPrice: 40 }),
      step({ sequence: 2, startMonth: 13, months: 12, unitPrice: 42 }),
      step({ sequence: 3, startMonth: 25, months: 12, unitPrice: 44.1 }),
    ]);
    fixture.detectChanges();

    http.expectNone((request) => request.method === 'PUT');
    expect(fixture.componentInstance.form.controls.steps.length).toBe(3);
    expect(fixture.componentInstance.form.controls.steps.at(2).controls.unitPrice.value).toBe(
      44.1,
    );
  });

  it('AC-532-9 POSTs ramp-schedule/extend and does not PUT the line or the quote term', () => {
    TestBed.configureTestingModule({
      imports: [QuoteRampEditorComponent],
      providers: [
        QuoteService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
      ],
    });
    const fixture = TestBed.createComponent(QuoteRampEditorComponent);
    fixture.componentRef.setInput('quoteId', 7);
    fixture.componentRef.setInput('lines', [line()]);
    fixture.componentRef.setInput('durationInMonths', 36);
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();

    fixture.componentInstance.addPeriod({
      startMonth: 1,
      months: 36,
      quantity: 50,
      unitPrice: 42.8,
    });
    fixture.componentInstance.form.controls.escalator.setValue({
      startPrice: 42.8,
      mode: 'PERCENT',
      amount: 7,
      everyMonths: 12,
    });
    fixture.componentInstance.form.controls.extendDuration.setValue(48);
    fixture.detectChanges();
    fixture.componentInstance.onExtend();

    const http = TestBed.inject(HttpTestingController);
    const req = http.expectOne('/api/v1/quotes/7/ramp-schedule/extend');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      durationInMonths: 48,
      steps: [
        {
          sequence: 1,
          startMonth: 1,
          months: 36,
          quantity: 50,
          priceBasis: 'ABSOLUTE',
          unitPrice: 42.8,
        },
      ],
      escalator: {
        startPrice: 42.8,
        mode: 'PERCENT',
        amount: 7,
        everyMonths: 12,
      },
    });
    req.flush({
      steps: [
        step({ sequence: 1, startMonth: 1, months: 36, unitPrice: 42.8 }),
        step({ sequence: 2, startMonth: 37, months: 12, unitPrice: 45.796 }),
      ],
    });
    fixture.detectChanges();

    http.expectNone((request) => request.method === 'PUT');
    http.expectNone('/api/v1/quotes/7');
    expect(fixture.componentInstance.form.controls.steps.length).toBe(2);
    expect(fixture.componentInstance.form.controls.steps.at(0).controls.unitPrice.value).toBe(42.8);
    expect(fixture.componentInstance.form.controls.steps.at(1).controls.startMonth.value).toBe(37);
  });
});
