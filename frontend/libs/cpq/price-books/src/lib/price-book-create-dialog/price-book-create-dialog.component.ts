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
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import {
  PriceBookCreateRequest,
  PriceBookDto,
} from '@frontend/shared-models';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';

/**
 * New Price Book dialog matching PriceBookCreateRequest.
 * Required: name. Optional: region, currency, isDefault, effectiveDate, expirationDate.
 */
@Component({
  selector: 'cpq-price-book-create-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>New Price Book</h2>
    <mat-dialog-content>
      @if (error()) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }
      <form [formGroup]="form" class="create-form" (ngSubmit)="onSubmit()">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Name</mat-label>
          <input
            matInput
            formControlName="name"
            maxlength="255"
            autocomplete="off"
          />
          @if (form.get('name')?.hasError('required')) {
            <mat-error>Name is required</mat-error>
          }
          @if (form.get('name')?.hasError('maxlength')) {
            <mat-error>Name must be at most 255 characters</mat-error>
          }
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Region</mat-label>
            <input
              matInput
              formControlName="region"
              maxlength="50"
              placeholder="e.g. NA"
              autocomplete="off"
            />
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Currency</mat-label>
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
        </div>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Effective date</mat-label>
            <input matInput type="date" formControlName="effectiveDate" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Expiration date</mat-label>
            <input matInput type="date" formControlName="expirationDate" />
          </mat-form-field>
        </div>

        <mat-checkbox formControlName="isDefault" color="primary">
          Default price book
        </mat-checkbox>
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
          Create
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      min-width: min(480px, 90vw);
      padding-top: 8px;
    }

    .create-form {
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
export class PriceBookCreateDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly priceBookService = inject(PriceBookService);
  private readonly dialogRef = inject(
    MatDialogRef<PriceBookCreateDialogComponent, PriceBookDto>,
  );

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    region: [''],
    currency: ['', [Validators.maxLength(3)]],
    isDefault: [false],
    effectiveDate: [''],
    expirationDate: [''],
  });

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: PriceBookCreateRequest = {
      name: value.name.trim(),
    };

    const region = value.region?.trim();
    if (region) {
      request.region = region;
    }
    const currency = value.currency?.trim();
    if (currency) {
      request.currency = currency.toUpperCase();
    }
    if (value.isDefault) {
      request.isDefault = true;
    }
    if (value.effectiveDate) {
      request.effectiveDate = value.effectiveDate;
    }
    if (value.expirationDate) {
      request.expirationDate = value.expirationDate;
    }

    this.error.set(null);
    this.saving.set(true);
    this.priceBookService.create(request).subscribe({
      next: (book) => {
        this.saving.set(false);
        this.dialogRef.close(book);
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.saving.set(false);
        this.error.set(
          err?.error?.message || err?.message || 'Failed to create price book',
        );
      },
    });
  }
}
