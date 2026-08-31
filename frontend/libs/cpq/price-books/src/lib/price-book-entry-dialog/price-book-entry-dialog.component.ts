import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import {
  PriceBookEntryCreateRequest,
  PriceBookEntryDto,
  PriceBookEntryUpdateRequest,
} from '@frontend/shared-models';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';

export interface PriceBookEntryDialogData {
  bookId: number;
  /** Prefill currency on create from the parent price book. */
  defaultCurrency?: string;
  /** Present for edit mode. */
  entry?: PriceBookEntryDto;
}

function entryHttpErrorMessage(err: unknown, fallback: string): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || fallback;
  if (
    code === 'PRICE_BOOK_PUBLISHED_IMMUTABLE' ||
    code === 'DUPLICATE_PRICE_BOOK_ENTRY'
  ) {
    return `${message} [${code}]`;
  }
  return message;
}

/**
 * Create/update price-book entry dialog matching PriceBookEntryCreate/UpdateRequest.
 * Required: productExternalId, listPrice. Optional: floorPrice, currency.
 */
@Component({
  selector: 'cpq-price-book-entry-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit entry' : 'Add entry' }}</h2>
    <mat-dialog-content>
      @if (error()) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }
      <form [formGroup]="form" class="entry-form" (ngSubmit)="onSubmit()">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Product external ID</mat-label>
          <input
            matInput
            formControlName="productExternalId"
            maxlength="100"
            autocomplete="off"
            placeholder="e.g. CRM-SEAT-NAMED"
          />
          @if (form.get('productExternalId')?.hasError('required')) {
            <mat-error>Product external ID is required</mat-error>
          }
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>List price</mat-label>
            <input
              matInput
              type="number"
              formControlName="listPrice"
              min="0"
              step="0.01"
            />
            @if (form.get('listPrice')?.hasError('required')) {
              <mat-error>List price is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Floor price (optional)</mat-label>
            <input
              matInput
              type="number"
              formControlName="floorPrice"
              min="0"
              step="0.01"
            />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Currency (optional)</mat-label>
          <input
            matInput
            formControlName="currency"
            maxlength="3"
            placeholder="USD"
            autocomplete="off"
          />
          @if (form.get('currency')?.hasError('maxlength')) {
            <mat-error>Currency must be 3 characters</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="saving()" mat-dialog-close>
        Cancel
      </button>
      <button
        mat-raised-button
        color="primary"
        type="button"
        [disabled]="form.invalid || saving()"
        (click)="onSubmit()"
      >
        @if (saving()) {
          <mat-spinner diameter="20" />
        } @else {
          {{ isEdit ? 'Save' : 'Create' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      min-width: min(480px, 90vw);
      padding-top: 8px;
    }

    .entry-form {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .full-width {
      width: 100%;
    }

    .row {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }

    .half {
      flex: 1 1 180px;
    }

    .error-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
      padding: 10px 12px;
      border-radius: 8px;
      background: #ffebee;
      color: #c62828;
      font-size: 0.875rem;
    }
  `,
})
export class PriceBookEntryDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly priceBookService = inject(PriceBookService);
  private readonly dialogRef = inject(
    MatDialogRef<PriceBookEntryDialogComponent, PriceBookEntryDto>,
  );
  readonly data = inject<PriceBookEntryDialogData>(MAT_DIALOG_DATA);

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly isEdit = !!this.data.entry;

  readonly form = this.fb.nonNullable.group({
    productExternalId: [
      this.data.entry?.productExternalId ?? '',
      [Validators.required, Validators.maxLength(100)],
    ],
    listPrice: [
      this.data.entry?.listPrice ?? (null as number | null),
      [Validators.required],
    ],
    floorPrice: [this.data.entry?.floorPrice ?? (null as number | null)],
    currency: [
      this.data.entry?.currency ?? this.data.defaultCurrency ?? '',
      [Validators.maxLength(3)],
    ],
  });

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const productExternalId = value.productExternalId.trim();
    const listPrice = Number(value.listPrice);
    if (!productExternalId || Number.isNaN(listPrice)) {
      this.form.markAllAsTouched();
      return;
    }

    const currencyRaw = value.currency?.trim();
    const currency = currencyRaw ? currencyRaw.toUpperCase() : undefined;
    const floorRaw = value.floorPrice;
    const floorPrice =
      floorRaw != null && !Number.isNaN(Number(floorRaw))
        ? Number(floorRaw)
        : undefined;

    this.error.set(null);
    this.saving.set(true);

    if (this.isEdit && this.data.entry) {
      const request: PriceBookEntryUpdateRequest = {
        productExternalId,
        listPrice,
      };
      if (floorPrice !== undefined) {
        request.floorPrice = floorPrice;
      }
      if (currency) {
        request.currency = currency;
      }

      this.priceBookService
        .updateEntry(this.data.bookId, this.data.entry.id, request)
        .subscribe({
          next: (entry) => {
            this.saving.set(false);
            this.dialogRef.close(entry);
          },
          error: (err: unknown) => {
            this.saving.set(false);
            this.error.set(
              entryHttpErrorMessage(err, 'Failed to update price book entry'),
            );
          },
        });
      return;
    }

    const request: PriceBookEntryCreateRequest = {
      productExternalId,
      listPrice,
    };
    if (floorPrice !== undefined) {
      request.floorPrice = floorPrice;
    }
    if (currency) {
      request.currency = currency;
    }

    this.priceBookService.createEntry(this.data.bookId, request).subscribe({
      next: (entry) => {
        this.saving.set(false);
        this.dialogRef.close(entry);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(
          entryHttpErrorMessage(err, 'Failed to create price book entry'),
        );
      },
    });
  }
}
