import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  CurrencyFormatPipe,
  EmptyStateComponent,
  PageHeaderComponent,
  StatusBadgeComponent,
} from '@frontend/shared-ui';
import { LineItemCreateRequest } from '@frontend/shared-models';
import { QuoteService } from '../quote.service';

function httpErrorMessage(err: unknown): string {
  const e = err as { error?: { message?: string }; message?: string } | null;
  return e?.error?.message || e?.message || 'An error occurred';
}

@Component({
  selector: 'cpq-quotes-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    CurrencyFormatPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading() && !quote()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40" />
      </div>
    } @else if (error(); as err) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ err }}</span>
        <button mat-button type="button" (click)="retry()">Retry</button>
        <button mat-button type="button" (click)="goBack()">Back to quotes</button>
      </div>
    } @else if (quote(); as quote) {
      <app-page-header
        [title]="quote.name"
        [subtitle]="quote.quoteNumber ?? 'Quote ' + quote.id"
      >
        <div actions class="header-actions">
          <button mat-stroked-button type="button" (click)="goBack()">
            <mat-icon>arrow_back</mat-icon>
            Back to quotes
          </button>
          <button
            mat-stroked-button
            type="button"
            (click)="onCalculate()"
            [disabled]="!canCalculate() || actionBusy()"
            [title]="
              canCalculate()
                ? 'Recalculate pricing for this quote'
                : 'Calculate is available for DRAFT or REJECTED quotes'
            "
          >
            <mat-icon>calculate</mat-icon>
            Calculate
          </button>
          <button
            mat-raised-button
            color="primary"
            type="button"
            (click)="onSubmit()"
            [disabled]="!canSubmit() || actionBusy()"
            [title]="
              canSubmit()
                ? 'Submit quote for review'
                : 'Submit is available only for DRAFT quotes'
            "
          >
            <mat-icon>send</mat-icon>
            Submit
          </button>
        </div>
      </app-page-header>

      @if (actionError(); as actionErr) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ actionErr }}</span>
          <button mat-button type="button" (click)="clearActionError()">Dismiss</button>
        </div>
      }

      <div class="detail-body">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Quote details</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Status</span>
                <app-status-badge [status]="quote.status" type="quote" />
              </div>
              <div class="info-item">
                <span class="label">Deal type</span>
                <span class="value">{{ quote.dealType || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Customer</span>
                <span class="value">{{ quote.customerExternalId }}</span>
              </div>
              <div class="info-item">
                <span class="label">Deal</span>
                <span class="value">{{ quote.dealExternalId || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Total amount</span>
                <span class="value">{{
                  quote.totalAmount != null
                    ? (quote.totalAmount | currencyFormat: quote.currency || 'USD')
                    : '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">ARR</span>
                <span class="value">{{
                  quote.arr != null
                    ? (quote.arr | currencyFormat: quote.currency || 'USD')
                    : '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">Duration</span>
                <span class="value">{{ quote.durationInMonths }} months</span>
              </div>
              <div class="info-item">
                <span class="label">Region</span>
                <span class="value">{{ quote.region || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Line items</span>
                <span class="value">{{ lineItemCount() }}</span>
              </div>
              <div class="info-item full-width">
                <span class="label">Description</span>
                <span class="value">{{ quote.description || '—' }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        @if (canEditLines()) {
          <mat-card class="line-item-card">
            <mat-card-header>
              <mat-card-title>Add line item</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <form class="line-form" [formGroup]="lineForm" (ngSubmit)="onAddLineItem()">
                <mat-form-field appearance="outline">
                  <mat-label>Product external ID</mat-label>
                  <input matInput formControlName="productExternalId" autocomplete="off" />
                  @if (
                    lineForm.controls.productExternalId.hasError('required') &&
                    lineForm.controls.productExternalId.touched
                  ) {
                    <mat-error>Required</mat-error>
                  }
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>List price</mat-label>
                  <input matInput type="number" formControlName="listPrice" min="0" step="0.01" />
                  @if (
                    lineForm.controls.listPrice.hasError('required') &&
                    lineForm.controls.listPrice.touched
                  ) {
                    <mat-error>Required</mat-error>
                  }
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Quantity</mat-label>
                  <input matInput type="number" formControlName="quantity" min="1" />
                </mat-form-field>
                <button
                  mat-stroked-button
                  color="primary"
                  type="submit"
                  [disabled]="lineForm.invalid || actionBusy()"
                >
                  <mat-icon>add</mat-icon>
                  Add line
                </button>
              </form>
            </mat-card-content>
          </mat-card>
        }
      </div>
    } @else {
      <app-empty-state
        icon="search_off"
        title="Quote not found"
        message="This quote could not be loaded from the API."
        actionLabel="Back to quotes"
        (action)="goBack()"
      />
    }
  `,
  styles: [
    `
      .loading {
        display: flex;
        justify-content: center;
        padding: 48px;
      }

      .header-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
      }

      .error-banner {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px;
        margin: 24px 32px;
        padding: 12px 16px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
      }

      .detail-body {
        padding: 0 32px 32px;
        display: flex;
        flex-direction: column;
        gap: 16px;
      }

      .info-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 20px;
        margin-top: 16px;
      }

      .info-item {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }

      .info-item.full-width {
        grid-column: 1 / -1;
      }

      .label {
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.4px;
        color: #757575;
      }

      .value {
        font-size: 15px;
        color: #212121;
      }

      .line-form {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-start;
        margin-top: 8px;
      }

      .line-form mat-form-field {
        flex: 1 1 160px;
        min-width: 140px;
      }

      .line-item-card {
        margin-top: 0;
      }

      @media (max-width: 768px) {
        .error-banner {
          margin: 16px;
        }

        .detail-body {
          padding: 0 16px 24px;
        }

        .info-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class QuotesDetailComponent implements OnInit {
  readonly quoteService = inject(QuoteService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly loading = computed(() => this.quoteService.loading());
  readonly error = computed(() => this.quoteService.error());
  readonly quote = computed(() => this.quoteService.selectedQuote());
  readonly lineItemCount = computed(
    () => this.quote()?.lineItems?.length ?? 0,
  );

  readonly actionBusy = signal(false);
  readonly actionError = signal<string | null>(null);

  /** Submit only from DRAFT (matches quote-service). */
  readonly canSubmit = computed(() => this.quote()?.status === 'DRAFT');

  /** Calculate / add line when quote is editable. */
  readonly canEditLines = computed(() => {
    const status = this.quote()?.status;
    return status === 'DRAFT' || status === 'REJECTED';
  });

  readonly canCalculate = computed(() => this.canEditLines());

  readonly lineForm = this.fb.nonNullable.group({
    productExternalId: ['', Validators.required],
    listPrice: [null as number | null, Validators.required],
    quantity: [1 as number | null],
  });

  ngOnInit(): void {
    this.load();
  }

  goBack(): void {
    void this.router.navigate(['/quotes']);
  }

  retry(): void {
    this.load();
  }

  clearActionError(): void {
    this.actionError.set(null);
  }

  onCalculate(): void {
    if (!this.canCalculate() || this.actionBusy()) {
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.quoteService.calculate(numericId).subscribe({
      next: (updated) => {
        this.quoteService.selectedQuote.set(updated);
        this.actionBusy.set(false);
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(httpErrorMessage(err));
      },
    });
  }

  onSubmit(): void {
    if (!this.canSubmit() || this.actionBusy()) {
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.quoteService.submit(numericId).subscribe({
      next: (updated) => {
        this.quoteService.selectedQuote.set(updated);
        this.actionBusy.set(false);
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(httpErrorMessage(err));
      },
    });
  }

  onAddLineItem(): void {
    if (!this.canEditLines() || this.lineForm.invalid || this.actionBusy()) {
      this.lineForm.markAllAsTouched();
      return;
    }
    const numericId = this.numericId();
    if (numericId == null) {
      return;
    }
    const raw = this.lineForm.getRawValue();
    const request: LineItemCreateRequest = {
      productExternalId: raw.productExternalId.trim(),
      listPrice: Number(raw.listPrice),
      quantity:
        raw.quantity != null && Number.isFinite(raw.quantity)
          ? Number(raw.quantity)
          : 1,
    };
    this.actionBusy.set(true);
    this.actionError.set(null);
    this.quoteService.addLineItem(numericId, request).subscribe({
      next: () => {
        this.lineForm.reset({ productExternalId: '', listPrice: null, quantity: 1 });
        this.actionBusy.set(false);
        this.quoteService.getById(numericId);
      },
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(httpErrorMessage(err));
      },
    });
  }

  private numericId(): number | null {
    const numericId = Number(this.id());
    return Number.isFinite(numericId) ? numericId : null;
  }

  private load(): void {
    this.actionError.set(null);
    const numericId = this.numericId();
    if (numericId == null) {
      this.quoteService.selectedQuote.set(null);
      this.quoteService.error.set('Invalid quote id');
      this.quoteService.loading.set(false);
      return;
    }
    this.quoteService.selectedQuote.set(null);
    this.quoteService.getById(numericId);
  }
}
