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
import { Subject, finalize, take, takeUntil } from 'rxjs';
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
  RampScheduleExtendResult,
  RampStep,
} from '@frontend/shared-models';
import { CurrencyFormatPipe } from '@frontend/shared-ui';
import { QuoteService } from '../quote.service';

/** Default of opencrm.pricing.ramp.max-periods. The server remains authoritative. */
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
  | 'RAMP_NOT_ALLOWED_FOR_LINE_TYPE'
  | 'RAMP_TOO_MANY_PERIODS'
  | 'RAMP_PRICE_BASIS';

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
    if (!Number.isFinite(step.months) || step.months < 1) {
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
    if (!rampStepMatchesBasis(step)) {
      issues.push({
        code: 'RAMP_PRICE_BASIS',
        message: `RAMP_PRICE_BASIS: period ${step.sequence} must set exactly one price field that matches its basis.`,
      });
    }
    if (!Number.isInteger(step.startMonth)) {
      issues.push({
        code: 'RAMP_GAP',
        message: `RAMP_GAP: period ${step.sequence} has no whole start month, so the schedule does not start at month 1.`,
      });
    }
  }

  if (steps.length > RAMP_MAX_PERIODS) {
    issues.push({
      code: 'RAMP_TOO_MANY_PERIODS',
      message: `RAMP_TOO_MANY_PERIODS: more than ${RAMP_MAX_PERIODS} periods.`,
    });
  }

  const comparable = steps
    .filter(
      (step) =>
        Number.isInteger(step.months) &&
        step.months >= 1 &&
        Number.isInteger(step.startMonth),
    )
    .sort((a, b) => a.startMonth - b.startMonth || a.sequence - b.sequence);

  if (comparable.length > 0 && comparable[0].startMonth !== 1) {
    issues.push({
      code: 'RAMP_GAP',
      message: 'RAMP_GAP: the schedule does not start at month 1.',
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

/** Exactly one price field, and it must be the field `priceBasis` names. */
export function rampStepMatchesBasis(
  step: Pick<RampStep, 'priceBasis' | 'unitPrice' | 'discountFraction'>,
): boolean {
  const hasUnit = step.unitPrice != null && Number.isFinite(step.unitPrice);
  const hasFraction = step.discountFraction != null && Number.isFinite(step.discountFraction);
  return step.priceBasis === 'ABSOLUTE' ? hasUnit && !hasFraction : hasFraction && !hasUnit;
}

function escalatorDividesTerm(everyMonths: number, durationInMonths: number): boolean {
  // Retained helper name for existing callers; a positive term may now have a short final period.
  return (
    Number.isInteger(everyMonths) &&
    everyMonths >= 1 &&
    Number.isInteger(durationInMonths) &&
    durationInMonths >= 1
  );
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
  readonly quoteVersion = input<number | null>(null);
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
  readonly extending = signal(false);
  readonly committingExtension = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.group({
    steps: this.fb.array<PeriodGroup>([]),
    escalator: this.fb.group({
      startPrice: this.fb.control<number | null>(null),
      mode: this.fb.nonNullable.control<RampEscalatorMode>('PERCENT'),
      compounding: this.fb.nonNullable.control(true),
      amount: this.fb.control<number | null>(null),
      everyMonths: this.fb.control<number | null>(12, {
        validators: [Validators.min(1)],
      }),
    }),
    extendDuration: this.fb.control<number | null>(null),
  });

  private readonly formRevision = signal(0);
  private readonly previewVersion = computed(() => ({
    quoteId: this.quoteId(),
    quoteVersion: this.quoteVersion(),
    lineId: this.selectedLineId(),
    duration: this.durationInMonths(),
    revision: this.formRevision(),
    editable: this.editable(),
  }));
  private activePreview: { context: ReturnType<QuoteRampEditorComponent['previewContext']>; cancel: Subject<void> } | null = null;
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
      this.readSteps(true),
      this.durationInMonths(),
      this.lineAllowed(),
    );
    return [...scheduleIssues, ...this.escalatorIssues()];
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
    return this.readSteps(false).map((step) => {
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
    if (!this.editable() || this.saving() || this.expanding() || this.extending() || this.quoteId() == null) {
      return false;
    }
    const line = this.selectedLine();
    if (line == null || line.listPrice == null) {
      return false;
    }
    if (this.form.invalid || this.issues().length > 0) {
      return false;
    }
    const count = this.form.controls.steps.length;
    if (count === 0 && (line.schedule?.steps.length ?? 0) === 0) {
      return false;
    }
    return true;
  });

  readonly canExpand = computed(() => {
    this.formRevision();
    if (!this.editable() || this.expanding() || this.extending() || !this.lineAllowed() || this.quoteId() == null) {
      return false;
    }
    const escalator = this.readEscalator();
    if (escalator == null || escalator.startPrice < 0 || escalator.amount < 0) {
      return false;
    }
    return escalatorDividesTerm(escalator.everyMonths, this.durationInMonths());
  });

  readonly extendIssue = computed((): string | null => {
    this.formRevision();
    const duration = finiteOrNull(this.form.controls.extendDuration.value);
    if (duration == null || this.form.controls.steps.length === 0) {
      return null;
    }
    const covered = this.coveredMonths();
    const escalator = this.readEscalator();
    if (!Number.isInteger(duration) || duration <= covered) {
      return 'RAMP_TERM_MISMATCH: the new term must be longer than the periods already shown.';
    }
    if (escalator != null && escalator.amount < 0) {
      return 'RAMP_NEGATIVE_VALUE: escalator amount is negative.';
    }
    return null;
  });

  readonly canExtend = computed(() => {
    this.formRevision();
    if (!this.editable() || this.extending() || this.expanding() || !this.lineAllowed() || this.quoteId() == null) {
      return false;
    }
    const duration = finiteOrNull(this.form.controls.extendDuration.value);
    if (
      this.form.controls.steps.length === 0 ||
      duration == null ||
      this.extendIssue() != null
    ) {
      return false;
    }
    const escalator = this.readEscalator();
    if (escalator == null || escalator.amount < 0 || !escalatorDividesTerm(escalator.everyMonths, duration)) {
      return false;
    }
    const covered = this.coveredMonths();
    return validateRampSchedule(this.readSteps(true), covered, true).length === 0;
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

  private readonly cancelStalePreview = effect(() => {
    const current = this.previewVersion();
    untracked(() => {
      const active = this.activePreview;
      if (active != null && active.context !== current) {
        this.reportDiscardedPreview(active.context);
        active.cancel.next();
        active.cancel.complete();
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
    if (this.committingExtension()) return;
    this.selectedLineId.set(lineId);
  }

  addPeriod(partial: Partial<RampStep> = {}): void {
    if (!this.editable() || this.saving() || this.committingExtension() || !this.lineAllowed()) {
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
    if (!this.editable() || this.saving() || this.committingExtension()) {
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
    const preview = this.previewContext();
    const active = { context: preview, cancel: new Subject<void>() };
    this.activePreview = active;
    this.expanding.set(true);
    this.error.set(null);
    this.quoteService
      .expandRampSchedule(quoteId, {
        durationInMonths: this.durationInMonths(),
        escalator,
      })
      .pipe(take(1), takeUntil(active.cancel), takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (this.activePreview === active) {
          this.activePreview = null;
          this.expanding.set(false);
        }
      }))
      .subscribe({
        next: (steps) => this.applyPreview(preview, steps),
        error: (err: unknown) => {
          if (this.isCurrentPreview(preview)) this.error.set(httpErrorMessage(err));
          else this.reportDiscardedPreview(preview);
        },
      });
  }

  onExtend(): void {
    if (!this.canExtend()) {
      return;
    }
    const quoteId = this.quoteId();
    const escalator = this.readEscalator();
    const duration = finiteOrNull(this.form.controls.extendDuration.value);
    if (quoteId == null || escalator == null || duration == null) {
      return;
    }
    const steps = this.readSteps(false);
    const preview = this.previewContext();
    const active = { context: preview, cancel: new Subject<void>() };
    const persist = this.quoteVersion() != null;
    // A committed extension must receive its response even if local context changes.
    // Only read-only previews participate in cancellation on form edits.
    if (!persist) this.activePreview = active;
    this.committingExtension.set(persist);
    this.syncLock();
    this.extending.set(true);
    this.error.set(null);
    this.quoteService
      .extendRampSchedule(quoteId, {
        steps,
        durationInMonths: duration,
        escalator,
        ...(this.quoteVersion() == null ? {} : {
          lineItemId: this.selectedLineId() ?? undefined,
          expectedQuoteVersion: this.quoteVersion() ?? undefined,
        }),
      })
      .pipe(take(1), takeUntil(active.cancel), takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (persist || this.activePreview === active) {
          this.activePreview = null;
          this.extending.set(false);
          this.committingExtension.set(false);
          this.syncLock();
        }
      }))
      .subscribe({
        next: (returned: RampScheduleExtendResult) => {
          const current = this.isCurrentPreview(preview);
          if (!current && !persist) {
            this.reportDiscardedPreview(preview);
            return;
          }
          if (current) this.replaceSteps(this.previewSteps(returned));
          if (returned.durationInMonths != null && returned.quoteVersion != null && preview.lineId != null) {
            if (current) this.form.markAsPristine();
            this.quoteService.applyRampExtension(quoteId, preview.lineId, returned);
          }
        },
        error: (err: unknown) => {
          if (this.isCurrentPreview(preview)) this.error.set(httpErrorMessage(err));
          else this.reportDiscardedPreview(preview);
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
    const steps = this.readSteps(false);
    const checked = this.readSteps(true);
    if (
      steps.some((step) => !Number.isInteger(step.startMonth) || step.startMonth < 1) ||
      checked.some((step) => !rampStepMatchesBasis(step))
    ) {
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

  private readSteps(keepBothPrices: boolean): RampStep[] {
    const rows = this.form.controls.steps.getRawValue().map((row) => {
      const priceBasis = row.priceBasis;
      const step: RampStep = {
        sequence: 0,
        startMonth: finiteOrNull(row.startMonth) ?? Number.NaN,
        months: finiteOrNull(row.months) ?? Number.NaN,
        quantity: finiteOrNull(row.quantity) ?? Number.NaN,
        priceBasis,
      };
      const unitPrice = finiteOrNull(row.unitPrice);
      const discountFraction = finiteOrNull(row.discountFraction);
      if ((keepBothPrices || priceBasis === 'ABSOLUTE') && unitPrice != null) {
        step.unitPrice = unitPrice;
      }
      if ((keepBothPrices || priceBasis === 'DISCOUNT_FRACTION') && discountFraction != null) {
        step.discountFraction = discountFraction;
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
      ...(raw.compounding ? {} : { compounding: false }),
    };
  }

  private escalatorIssues(): RampScheduleIssue[] {
    const issues: RampScheduleIssue[] = [];
    const raw = this.form.controls.escalator.getRawValue();
    const startPrice = finiteOrNull(raw.startPrice);
    const amount = finiteOrNull(raw.amount);
    if ((startPrice != null && startPrice < 0) || (amount != null && amount < 0)) {
      issues.push({
        code: 'RAMP_NEGATIVE_VALUE',
        message: 'RAMP_NEGATIVE_VALUE: escalator price or amount is negative.',
      });
    }
    const escalator = this.readEscalator();
    if (
      escalator != null &&
      !escalatorDividesTerm(escalator.everyMonths, this.durationInMonths())
    ) {
      issues.push({
        code: 'RAMP_TERM_MISMATCH',
        message: 'RAMP_TERM_MISMATCH: term and period length must be positive whole months.',
      });
    }
    return issues;
  }

  private syncLock(editable = this.editable()): void {
    if (editable && !this.committingExtension()) {
      this.form.enable({ emitEvent: false });
    } else {
      this.form.disable({ emitEvent: false });
    }
  }

  private applyLine(line: QuoteLineItemDto | null): void {
    this.form.setControl('steps', this.fb.array((line?.schedule?.steps ?? []).map((step) => this.periodGroup(step))),
      { emitEvent: false });
    const escalator = line?.schedule?.escalator;
    this.form.controls.escalator.reset(
      {
        startPrice: escalator?.startPrice ?? null,
        mode: escalator?.mode ?? 'PERCENT',
        amount: escalator?.amount ?? null,
        everyMonths: escalator?.everyMonths ?? 12,
        compounding: escalator?.compounding ?? true,
      },
      { emitEvent: false },
    );
    this.form.controls.extendDuration.reset(null, { emitEvent: false });
    this.form.markAsPristine();
    this.error.set(null);
    this.syncLock();
    this.formRevision.update((revision) => revision + 1);
  }

  private previewContext() {
    return this.previewVersion();
  }

  private isCurrentPreview(context: ReturnType<QuoteRampEditorComponent['previewContext']>): boolean {
    return context.editable && this.previewVersion() === context;
  }

  private reportDiscardedPreview(context: ReturnType<QuoteRampEditorComponent['previewContext']>): void {
    if (this.editable() && this.quoteId() === context.quoteId && this.selectedLineId() === context.lineId) {
      this.error.set('The schedule changed while the preview was running. Generate the preview again.');
    }
  }

  private applyPreview(context: ReturnType<QuoteRampEditorComponent['previewContext']>, result: unknown): void {
    if (this.isCurrentPreview(context)) this.replaceSteps(this.previewSteps(result));
    else this.reportDiscardedPreview(context);
  }

  private coveredMonths(): number {
    const steps = this.readSteps(false);
    if (steps.length === 0) {
      return 0;
    }
    const last = steps[steps.length - 1];
    if (!Number.isInteger(last.startMonth) || !Number.isInteger(last.months)) {
      return 0;
    }
    return last.startMonth + last.months - 1;
  }

  private previewSteps(body: unknown): RampStep[] {
    if (Array.isArray(body)) {
      return body;
    }
    if (
      body != null &&
      typeof body === 'object' &&
      'steps' in body &&
      Array.isArray((body as { steps: unknown }).steps)
    ) {
      return (body as { steps: RampStep[] }).steps;
    }
    return [];
  }

  private replaceSteps(steps: RampStep[]): void {
    const array = this.fb.array(steps.map((step) => this.periodGroup(step)));
    // Notify the root form that the controls were replaced so retained DOM rows
    // bind to the new controls even when the generated period count is unchanged.
    this.form.setControl('steps', array, { emitEvent: false });
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
