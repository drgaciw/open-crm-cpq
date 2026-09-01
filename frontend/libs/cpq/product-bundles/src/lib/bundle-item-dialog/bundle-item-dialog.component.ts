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
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import {
  BundleItemCreateRequest,
  ProductBundleDto,
} from '../product-bundle.model';
import { ProductBundleService } from '../product-bundle.service';
import { bundleHttpErrorMessage } from '../bundle-http-error';

export interface BundleItemDialogData {
  bundleId: number;
}

/**
 * Add bundle item dialog matching BundleItemCreateRequest.
 * Required: productId. Optional: quantity (≥1), optional, sortOrder.
 */
@Component({
  selector: 'cpq-bundle-item-dialog',
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
    <h2 mat-dialog-title>Add bundle item</h2>
    <mat-dialog-content>
      @if (error()) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }
      <form [formGroup]="form" class="item-form" (ngSubmit)="onSubmit()">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Product ID</mat-label>
          <input
            matInput
            type="number"
            formControlName="productId"
            min="1"
            autocomplete="off"
          />
          @if (form.get('productId')?.hasError('required')) {
            <mat-error>Product ID is required</mat-error>
          }
          @if (form.get('productId')?.hasError('min')) {
            <mat-error>Product ID must be at least 1</mat-error>
          }
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Quantity</mat-label>
            <input
              matInput
              type="number"
              formControlName="quantity"
              min="1"
            />
            @if (form.get('quantity')?.hasError('min')) {
              <mat-error>Quantity must be at least 1</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Sort order</mat-label>
            <input matInput type="number" formControlName="sortOrder" min="0" />
          </mat-form-field>
        </div>

        <mat-checkbox formControlName="optional" color="primary">
          Optional item
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
          Add
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      min-width: min(480px, 90vw);
      padding-top: 8px;
    }

    .item-form {
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
export class BundleItemDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bundleService = inject(ProductBundleService);
  private readonly dialogRef = inject(
    MatDialogRef<BundleItemDialogComponent, ProductBundleDto>,
  );
  private readonly data = inject<BundleItemDialogData>(MAT_DIALOG_DATA);

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    productId: [null as number | null, [Validators.required, Validators.min(1)]],
    quantity: [1, [Validators.required, Validators.min(1)]],
    optional: [false],
    sortOrder: [0],
  });

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const productId = Number(value.productId);
    if (!Number.isFinite(productId) || productId <= 0) {
      this.error.set('Product ID must be a positive number');
      return;
    }

    const request: BundleItemCreateRequest = {
      productId,
      quantity: Number(value.quantity) || 1,
      optional: !!value.optional,
      sortOrder: Number(value.sortOrder) || 0,
    };

    this.error.set(null);
    this.saving.set(true);
    this.bundleService.addItem(this.data.bundleId, request).subscribe({
      next: (bundle) => {
        this.saving.set(false);
        this.dialogRef.close(bundle);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(
          bundleHttpErrorMessage(err, 'Failed to add bundle item'),
        );
      },
    });
  }
}
