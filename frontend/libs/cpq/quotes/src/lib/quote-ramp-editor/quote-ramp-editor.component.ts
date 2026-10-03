import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import {
  LineItemCreateRequest,
  QuoteLineItemDto,
  RAMP_ESCALATOR_MODE_LABELS,
  RAMP_ESCALATOR_MODES,
  RAMP_PRICE_BASIS_LABELS,
  RAMP_PRICE_BASES,
  RampEscalator,
  RampEscalatorMode,
  RampPricedPeriod,
  RampPriceBasis,
  RampSchedule,
  RampStep,
} from '@frontend/shared-models';
import { CurrencyFormatPipe } from '@frontend/shared-ui';
import { QuoteService } from '../quote.service';

/** Quote-service cap. The UI does not change opencrm.pricing.ramp.enabled. */
export const RAMP_MAX_PERIODS = 36;

const RAMP_BILLING_FREQUENCIES = [
  'MONTHLY',
  'QUARTERLY',
  'SEMI_ANNUAL',
  'ANNUAL',
  'BIENNIAL',
] as const;

const RAMP_DISALLOWED_LINE_TYPES = new Set(['DISCOUNT', 'FEE', 'TAX']);

export type RampScheduleIssueCode =
  | 'RAMP_GAP'
  | 'RAMP_OVERLAP'
  | 'RAMP_TERM_MISMATCH'
  | 'RAMP_PERIOD_EMPTY'
  | 'RAMP_NEGATIVE_VALUE'
  | 'RAMP_NOT_ALLOWED_FOR_LINE_TYPE';

export interface RampScheduleIssue {
  code: RampScheduleIssueCode;
  message: string;
}

interface PeriodControls {
  startMonth: FormControl<number | null>;
  months: FormControl<number | null>;
  quantity: FormControl<number | null>;
  priceBasis: FormControl<RampPriceBasis>;
  unitPrice: FormControl<number | null>;
  discountFraction: FormControl<number | null>;
}

type PeriodGroup = FormGroup<PeriodControls>;

export interface RampPreviewRow {
  sequence: number;
  months: number;
  quantity: number;
  netUnitPrice?: number;
  netMonthly?: number;
  periodAmount?: number;
}

/**
 * A line may carry a schedule only when billing is recurring and the line type
 * is not discount, fee, or tax. Matches quote-service RAMP_NOT_ALLOWED_FOR_LINE_TYPE.
 */
export function rampLineAllowsSchedule(line: {
  billingFrequency?: string | null;
  lineItemType?: string | null;
}): boolean {
  const frequency = line.billingFrequency ?? '';
  const lineType = (line.lineItemType ?? '').toUpperCase();
  return (
    (RAMP_BILLING_FREQUENCIES as readonly string[]).includes(frequency) &&
    !RAMP_DISALLOWED_LINE_TYPES.has(lineType)
  );
}

/** Inline checks for AC-03. Empty steps are "no schedule", which is valid. */
export function validateRampSchedule(
  steps: readonly RampStep[],
  durationInMonths: number,
  lineAllowed: boolean,
): RampScheduleIssue[] {
  const issues: RampScheduleIssue[] = [];
  if (!lineAllowed && steps.length > 0) {
    issues.push({
      code: 'RAMP_NOT_ALLOWED_FOR_LINE_TYPE',
      message: 'RAMP_NOT_ALLOWED_FOR_LINE_TYPE: this line cannot carry a ramp schedule.',
    });
  }

  for (const step of steps) {
    if (!Number.isInteger(step.months) || step.months < 1) {
      issues.push({
        code: 'RAMP_PERIOD_EMPTY',
        message: `RAMP_PERIOD_EMPTY: period ${step.sequence} must cover at least 1 whole month.`,
      });
    }
    const negativePrice =
      (step.unitPrice != null && step.unitPrice < 0) ||
      (step.discountFraction != null && step.discountFraction < 0);
    if (step.months < 0 || step.quantity < 0 || negativePrice) {
      issues.push({
        code: 'RAMP_NEGATIVE_VALUE',
        message: `RAMP_NEGATIVE_VALUE: period ${step.sequence} has a negative price or quantity.`,
      });
    }
    if (!Number.isInteger(step.startMonth)) {
      issues.push({
        code: 'RAMP_GAP',
        message: `RAMP_GAP: period ${step.sequence} has no whole start month, so the term is not covered.`,
      });
    } else if (step.startMonth < 1) {
      issues.push({
        code: 'RAMP_OVERLAP',
        message: 'RAMP_OVERLAP: a period starts before month 1.',
      });
    }
  }

  const comparable = steps
    .filter(
      (step) =>
        Number.isInteger(step.months) &&
        step.months >= 1 &&
        Number.isInteger(step.startMonth) &&
        step.startMonth >= 1,
    )
    .sort((a, b) => a.startMonth - b.startMonth || a.sequence - b.sequence);

  if (comparable.length > 0 && comparable[0].startMonth > 1) {
    issues.push({
      code: 'RAMP_GAP',
      message: `RAMP_GAP: the first period starts at month ${comparable[0].startMonth}; month 1 is not covered.`,
    });
  }
  for (let index = 0; index < comparable.length - 1; index += 1) {
    const current = comparable[index];
    const next = comparable[index + 1];
    const expectedStart = current.startMonth + current.months;
    if (next.startMonth > expectedStart) {
      issues.push({
        code: 'RAMP_GAP',
        message: `RAMP_GAP: months ${expectedStart}–${next.startMonth - 1} are not covered.`,
      });
    } else if (next.startMonth < expectedStart) {
      issues.push({
        code: 'RAMP_OVERLAP',
        message: `RAMP_OVERLAP: period ${next.sequence} overlaps the previous period.`,
      });
    }
  }

  if (steps.length > 0) {
    const covered = steps.reduce(
      (total, step) => total + (Number.isFinite(step.months) ? step.months : 0),
      0,
    );
    if (covered !== durationInMonths) {
      issues.push({
        code: 'RAMP_TERM_MISMATCH',
        message: `RAMP_TERM_MISMATCH: periods cover ${covered} of ${durationInMonths} months.`,
      });
    }
  }

  return issues;
}

function httpErrorMessage(err: unknown): string {
  const body = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = body?.errorCode || body?.error?.errorCode;
  const message = body?.error?.message || body?.message || 'An error occurred';
  if (code && !message.includes(code)) {
    return `${message} [${code}]`;
  }
  return message;
}

function finiteOrNull(value: number | null): number | null {
  if (value == null) {
    return null;
  }
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : null;
}

/**
 * Per-line ramp authoring on the quote page.
 * Escalator expansion calls the quote API and does not persist.
 * Saving a schedule is a line update. There is no governing-period banner.
 */
@Component({
  selector: 'cpq-quote-ramp-editor',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    CurrencyFormatPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quote-ramp-editor.component.html',
  styles: [
    `
      .ramp-card {
        margin-top: 16px;
      }

      .ramp-actions,
      .escalator-actions,
      .row-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
      }

      .hint,
      .line-note {
        margin: 0 0 12px;
        color: #616161;
        font-size: 13px;
        line-height: 1.5;
      }

      .error-banner,
      .issue-list {
        margin: 0 0 12px;
      }

      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 8px 12px;
        border-radius: 4px;
        background: #ffebee;
        color: #b71c1c;
      }

      .issue-list {
        padding-left: 20px;
        color: #b71c1c;
      }

      .period-row,
      .escalator-row,
      .metrics {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: flex-start;
      }

      .period-row {
        margin-bottom: 8px;
      }

      .metrics {
        margin-top: 12px;
      }

      .metric {
        min-width: 140px;
      }

      .label {
        display: block;
        font-size: 12px;
        color: #757575;
      }

      .preview-wrap {
        overflow-x: auto;
        margin-top: 8px;
      }

      table {
        width: 100%;
        border-collapse: collapse;
      }

      th,
      td {
        text-align: left;
        padding: 8px;
        border-bottom: 1px solid #e0e0e0;
        white-space: nowrap;
      }

      .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        border: 0;
      }

      @media (max-width: 768px) {
        .period-row,
        .escalator-row,
        .metrics {
          flex-direction: column;
        }
      }
    `,
  ],
})
export class QuoteRampEditorComponent {
  private readonly quoteService = inject(QuoteService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly quoteId = input<number | null>(null);
  readonly lines = input<QuoteLineItemDto[]>([]);
  readonly durationInMonths = input(12);
  readonly currency = input('USD');
  readonly editable = input(false);
  readonly tcv = input<number | null>(null);
  readonly acv = input<number | null>(null);
  readonly exitArr = input<number | null>(null);

  readonly priceBases = RAMP_PRICE_BASES;
  readonly priceBasisLabels = RAMP_PRICE_BASIS_LABELS;
  readonly escalatorModes = RAMP_ESCALATOR_MODES;
  readonly escalatorModeLabels = RAMP_ESCALATOR_MODE_LABELS;
  readonly maxPeriods = RAMP_MAX_PERIODS;

  readonly selectedLineId = signal<number | null>(null);
  readonly saving = signal(false);
  readonly expanding = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.group({
    steps: this.fb.array<PeriodGroup>([]),
    escalator: this.fb.group({
      startPrice: this.fb.control<number | null>(null),
      mode: this.fb.nonNullable.control<RampEscalatorMode>('PERCENT'),
      amount: this.fb.control<number | null>(null),
      everyMonths: this.fb.control<number | null>(12, {
        validators: [Validators.min(1)],
      }),
    }),
  });

  private readonly formRevision = signal(0);
  private appliedKey = '';
  private appliedLineId: number | null = null;

  readonly selectedLine = computed(() => {
    const id = this.selectedLineId();
    return this.lines().find((line) => line.id === id) ?? null;
  });

  readonly lineAllowed = computed(() => {
    const line = this.selectedLine();
    return line != null && rampLineAllowsSchedule(line);
  });

  readonly stepControls = computed(() => {
    this.formRevision();
    return [...this.form.controls.steps.controls];
  });

  readonly issues = computed(() => {
    this.formRevision();
    const scheduleIssues = validateRampSchedule(
      this.readSteps(),
      this.durationInMonths(),
      this.lineAllowed(),
    );
    return [...scheduleIssues, ...this.escalatorIssues()];
  });

  readonly fieldErrors = computed(() => {
    this.formRevision();
    return this.collectFieldErrors();
  });

  readonly lineTypeNote = computed(() => {
    const line = this.selectedLine();
    if (line == null || rampLineAllowsSchedule(line)) {
      return null;
    }
    return 'RAMP_NOT_ALLOWED_FOR_LINE_TYPE: a schedule is not allowed for this line.';
  });

  readonly previewRows = computed((): RampPreviewRow[] => {
    this.formRevision();
    const priced = this.selectedLine()?.pricedPeriods ?? [];
    return this.readSteps().map((step) => {
      const match = matchPricedPeriod(priced, step);
      return {
        sequence: step.sequence,
        months: match?.months ?? step.months,
        quantity: match?.quantity ?? step.quantity,
        netUnitPrice: match?.netUnitPrice,
        netMonthly: match?.netMonthly,
        periodAmount: match?.periodAmount,
      };
    });
  });

  readonly canSave = computed(() => {
    if (!this.editable() || this.saving() || this.quoteId() == null) {
      return false;
    }
    const line = this.selectedLine();
    if (line == null || line.listPrice == null) {
      return false;
    }
    if (this.form.invalid || this.issues().length > 0 || this.fieldErrors().length > 0) {
      return false;
    }
    const count = this.form.controls.steps.length;
    if (count === 0 && (line.schedule?.steps.length ?? 0) === 0) {
      return false;
    }
    return true;
  });

  readonly escalatorHint = computed(() => {
    this.formRevision();
    const everyMonths = finiteOrNull(this.form.controls.escalator.controls.everyMonths.value);
    if (everyMonths != null && (!Number.isInteger(everyMonths) || everyMonths < 1)) {
      return 'Escalator period length must be at least 1 whole month.';
    }
    return null;
  });

  readonly canExpand = computed(() => {
    this.formRevision();
    if (!this.editable() || this.expanding() || !this.lineAllowed() || this.quoteId() == null) {
      return false;
    }
    const escalator = this.readEscalator();
    if (escalator == null || escalator.startPrice < 0 || escalator.amount < 0) {
      return false;
    }
    return Number.isInteger(escalator.everyMonths) && escalator.everyMonths >= 1;
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.formRevision.update((revision) => revision + 1);
    });
  }

  private readonly followLines = effect(() => {
    const lines = this.lines();
    const current = this.selectedLineId();
    untracked(() => {
      if (lines.length === 0) {
        if (current !== null) {
          this.selectedLineId.set(null);
        }
        return;
      }
      if (current == null || !lines.some((line) => line.id === current)) {
        this.selectedLineId.set(lines[0].id);
      }
    });
  });

  private readonly lockWhenReadonly = effect(() => {
    const editable = this.editable();
    untracked(() => {
      this.syncLock(editable);
    });
  });

  private readonly syncSchedule = effect(() => {
    const line = this.selectedLine();
    const key = line == null ? '' : `${line.id}:${JSON.stringify(line.schedule ?? null)}`;
    untracked(() => {
      if (key === this.appliedKey) {
        return;
      }
      const lineId = line?.id ?? null;
      if (this.form.dirty && lineId != null && lineId === this.appliedLineId) {
        return;
      }
      this.appliedKey = key;
      this.appliedLineId = lineId;
      this.applyLine(line);
    });
  });

  onLineChange(lineId: number): void {
    this.selectedLineId.set(lineId);
  }

  addPeriod(partial: Partial<RampStep> = {}): void {
    if (!this.editable() || this.saving() || !this.lineAllowed()) {
      return;
    }
    const steps = this.form.controls.steps;
    if (steps.length >= RAMP_MAX_PERIODS) {
      return;
    }
    const last = steps.length > 0 ? steps.at(steps.length - 1).getRawValue() : null;
    const line = this.selectedLine();
    const inferredStart =
      last?.startMonth != null && last.months != null
        ? Number(last.startMonth) + Number(last.months)
        : 1;
    steps.push(
      this.periodGroup({
        sequence: steps.length + 1,
        startMonth: partial.startMonth ?? inferredStart,
        months: partial.months ?? 12,
        quantity: partial.quantity ?? line?.quantity ?? 1,
        priceBasis: partial.priceBasis ?? 'ABSOLUTE',
        unitPrice: partial.unitPrice !== undefined ? partial.unitPrice : line?.listPrice,
        discountFraction: partial.discountFraction,
      }),
    );
    steps.markAsDirty();
  }

  removePeriod(index: number): void {
    if (!this.editable() || this.saving()) {
      return;
    }
    this.form.controls.steps.removeAt(index);
    this.form.controls.steps.markAsDirty();
  }

  onExpand(): void {
    if (!this.canExpand()) {
      return;
    }
    const quoteId = this.quoteId();
    const escalator = this.readEscalator();
    if (quoteId == null || escalator == null) {
      return;
    }
    this.expanding.set(true);
    this.error.set(null);
    this.quoteService
      .expandRampSchedule(quoteId, {
        durationInMonths: this.durationInMonths(),
        escalator,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (steps) => {
          this.expanding.set(false);
          this.replaceSteps(Array.isArray(steps) ? steps : []);
        },
        error: (err: unknown) => {
          this.expanding.set(false);
          this.error.set(httpErrorMessage(err));
        },
      });
  }

  onSave(): void {
    if (!this.canSave()) {
      return;
    }
    const quoteId = this.quoteId();
    const line = this.selectedLine();
    if (quoteId == null || line == null || line.listPrice == null) {
      return;
    }
    const schedule = this.schedulePayload();
    if (schedule == null) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.quoteService
      .updateLineItem(quoteId, line.id, this.lineRequest(line, schedule))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.form.markAsPristine();
          this.appliedKey = '';
          this.quoteService.getById(quoteId);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(httpErrorMessage(err));
        },
      });
  }

  clearError(): void {
    this.error.set(null);
  }

  private schedulePayload(): RampSchedule | null {
    const steps = this.readSteps();
    if (steps.some((step) => !Number.isInteger(step.startMonth) || step.startMonth < 1)) {
      return null;
    }
    const schedule: RampSchedule = { steps };
    const escalator = this.readEscalator();
    if (
      escalator != null &&
      escalator.startPrice >= 0 &&
      escalator.amount >= 0 &&
      escalator.everyMonths >= 1
    ) {
      schedule.escalator = escalator;
    }
    return schedule;
  }

  private lineRequest(line: QuoteLineItemDto, schedule: RampSchedule): LineItemCreateRequest {
    const request: LineItemCreateRequest = {
      productExternalId: line.productExternalId,
      listPrice: line.listPrice ?? 0,
      quantity: line.quantity,
      schedule,
    };
    if (line.productName) {
      request.productName = line.productName;
    }
    if (line.productType) {
      request.productType = line.productType;
    }
    if (line.lineItemType) {
      request.lineItemType = line.lineItemType;
    }
    if (line.unitOfMeasure) {
      request.unitOfMeasure = line.unitOfMeasure;
    }
    if (line.billingFrequency) {
      request.billingFrequency = line.billingFrequency;
    }
    if (line.notes) {
      request.notes = line.notes;
    }
    if (line.sortOrder != null) {
      request.sortOrder = line.sortOrder;
    }
    return request;
  }

  private readSteps(): RampStep[] {
    const rows = this.form.controls.steps.getRawValue().map((row) => {
      const priceBasis = row.priceBasis;
      const step: RampStep = {
        sequence: 0,
        startMonth: finiteOrNull(row.startMonth) ?? Number.NaN,
        months: finiteOrNull(row.months) ?? Number.NaN,
        quantity: finiteOrNull(row.quantity) ?? Number.NaN,
        priceBasis,
      };
      if (priceBasis === 'ABSOLUTE') {
        const unitPrice = finiteOrNull(row.unitPrice);
        if (unitPrice != null) {
          step.unitPrice = unitPrice;
        }
      } else {
        const discountFraction = finiteOrNull(row.discountFraction);
        if (discountFraction != null) {
          step.discountFraction = discountFraction;
        }
      }
      return step;
    });
    rows.sort((a, b) => a.startMonth - b.startMonth);
    return rows.map((step, index) => ({ ...step, sequence: index + 1 }));
  }

  private readEscalator(): RampEscalator | null {
    const raw = this.form.controls.escalator.getRawValue();
    const startPrice = finiteOrNull(raw.startPrice);
    const amount = finiteOrNull(raw.amount);
    const everyMonths = finiteOrNull(raw.everyMonths);
    if (startPrice == null || amount == null || everyMonths == null) {
      return null;
    }
    return {
      startPrice,
      mode: raw.mode,
      amount,
      everyMonths,
    };
  }

  private escalatorIssues(): RampScheduleIssue[] {
    const raw = this.form.controls.escalator.getRawValue();
    const startPrice = finiteOrNull(raw.startPrice);
    const amount = finiteOrNull(raw.amount);
    if ((startPrice != null && startPrice < 0) || (amount != null && amount < 0)) {
      return [
        {
          code: 'RAMP_NEGATIVE_VALUE',
          message: 'RAMP_NEGATIVE_VALUE: escalator price or amount is negative.',
        },
      ];
    }
    return [];
  }

  private collectFieldErrors(): string[] {
    const errors: string[] = [];
    if (this.form.controls.steps.length > RAMP_MAX_PERIODS) {
      errors.push(`At most ${RAMP_MAX_PERIODS} periods.`);
    }
    this.form.controls.steps.getRawValue().forEach((row, index) => {
      const label = index + 1;
      const startMonth = row.startMonth == null ? Number.NaN : Number(row.startMonth);
      if (!Number.isInteger(startMonth) || startMonth < 1) {
        errors.push(`Period ${label} must start on month 1 or later.`);
      }
      if (
        row.priceBasis === 'ABSOLUTE' &&
        (row.unitPrice == null || !Number.isFinite(Number(row.unitPrice)))
      ) {
        errors.push(`Period ${label} needs a unit price.`);
      }
      if (
        row.priceBasis === 'DISCOUNT_FRACTION' &&
        (row.discountFraction == null || !Number.isFinite(Number(row.discountFraction)))
      ) {
        errors.push(`Period ${label} needs a discount fraction.`);
      }
      if (row.quantity == null || !Number.isFinite(Number(row.quantity))) {
        errors.push(`Period ${label} needs a quantity.`);
      } else if (!Number.isInteger(Number(row.quantity))) {
        errors.push(`Period ${label} quantity must be a whole number.`);
      }
      if (
        row.months != null &&
        Number.isFinite(Number(row.months)) &&
        Number(row.months) >= 1 &&
        !Number.isInteger(Number(row.months))
      ) {
        errors.push(`Period ${label} months must be a whole number.`);
      }
    });
    return errors;
  }

  private syncLock(editable = this.editable()): void {
    if (editable) {
      this.form.enable({ emitEvent: false });
    } else {
      this.form.disable({ emitEvent: false });
    }
  }

  private applyLine(line: QuoteLineItemDto | null): void {
    const steps = this.form.controls.steps;
    steps.clear({ emitEvent: false });
    for (const step of line?.schedule?.steps ?? []) {
      steps.push(this.periodGroup(step), { emitEvent: false });
    }
    const escalator = line?.schedule?.escalator;
    this.form.controls.escalator.reset(
      {
        startPrice: escalator?.startPrice ?? null,
        mode: escalator?.mode ?? 'PERCENT',
        amount: escalator?.amount ?? null,
        everyMonths: escalator?.everyMonths ?? 12,
      },
      { emitEvent: false },
    );
    this.form.markAsPristine();
    this.error.set(null);
    this.syncLock();
    this.formRevision.update((revision) => revision + 1);
  }

  private replaceSteps(steps: RampStep[]): void {
    const array = this.form.controls.steps;
    array.clear({ emitEvent: false });
    for (const step of steps) {
      array.push(this.periodGroup(step), { emitEvent: false });
    }
    array.markAsDirty();
    this.formRevision.update((revision) => revision + 1);
  }

  private periodGroup(step: Partial<RampStep> = {}): PeriodGroup {
    return this.fb.group({
      startMonth: this.fb.control<number | null>(step.startMonth ?? 1, {
        validators: [Validators.required],
      }),
      months: this.fb.control<number | null>(step.months ?? 12, {
        validators: [Validators.required],
      }),
      quantity: this.fb.control<number | null>(step.quantity ?? 1, {
        validators: [Validators.required],
      }),
      priceBasis: this.fb.nonNullable.control<RampPriceBasis>(step.priceBasis ?? 'ABSOLUTE'),
      unitPrice: this.fb.control<number | null>(step.unitPrice ?? null),
      discountFraction: this.fb.control<number | null>(step.discountFraction ?? null),
    });
  }
}

function matchPricedPeriod(
  priced: readonly RampPricedPeriod[],
  step: RampStep,
): RampPricedPeriod | undefined {
  return (
    priced.find((period) => period.sequence === step.sequence) ??
    priced.find((period) => period.startMonth != null && period.startMonth === step.startMonth)
  );
}
