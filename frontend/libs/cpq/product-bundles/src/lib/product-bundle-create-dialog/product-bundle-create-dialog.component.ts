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
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import {
  DiscountType,
  ProductBundleCreateRequest,
  ProductBundleDto,
} from '../product-bundle.model';
import { ProductBundleService } from '../product-bundle.service';
import { bundleHttpErrorMessage } from '../bundle-http-error';

/**
 * New Product Bundle dialog matching ProductBundleCreateRequest.
 * Required: productId, name. Optional: description, discountType,
 * discountValue, minItems, maxItems.
 */
@Component({
  selector: 'cpq-product-bundle-create-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>New Product Bundle</h2>
    <mat-dialog-content>
      @if (error()) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }
      <form [formGroup]="form" class="create-form" (ngSubmit)="onSubmit()">
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

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description (optional)</mat-label>
          <textarea
            matInput
            formControlName="description"
            rows="3"
            maxlength="2000"
          ></textarea>
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Discount type</mat-label>
            <mat-select formControlName="discountType">
              <mat-option value="NONE">NONE</mat-option>
              <mat-option value="PERCENTAGE">PERCENTAGE</mat-option>
              <mat-option value="FIXED_AMOUNT">FIXED_AMOUNT</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Discount value</mat-label>
            <input
              matInput
              type="number"
              formControlName="discountValue"
              min="0"
              step="0.01"
            />
          </mat-form-field>
        </div>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Min items</mat-label>
            <input matInput type="number" formControlName="minItems" min="0" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Max items</mat-label>
            <input matInput type="number" formControlName="maxItems" min="0" />
          </mat-form-field>
        </div>
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
export class ProductBundleCreateDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bundleService = inject(ProductBundleService);
  private readonly dialogRef = inject(
    MatDialogRef<ProductBundleCreateDialogComponent, ProductBundleDto>,
  );

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    productId: [null as number | null, [Validators.required, Validators.min(1)]],
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    discountType: ['NONE' as DiscountType],
    discountValue: [null as number | null],
    minItems: [null as number | null],
    maxItems: [null as number | null],
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

    const request: ProductBundleCreateRequest = {
      productId,
      name: value.name.trim(),
    };

    const description = value.description?.trim();
    if (description) {
      request.description = description;
    }
    if (value.discountType && value.discountType !== 'NONE') {
      request.discountType = value.discountType;
    } else if (value.discountType === 'NONE') {
      request.discountType = 'NONE';
    }
    if (value.discountValue != null && value.discountValue !== ('' as unknown)) {
      const dv = Number(value.discountValue);
      if (Number.isFinite(dv)) {
        request.discountValue = dv;
      }
    }
    if (value.minItems != null && value.minItems !== ('' as unknown)) {
      const min = Number(value.minItems);
      if (Number.isFinite(min)) {
        request.minItems = min;
      }
    }
    if (value.maxItems != null && value.maxItems !== ('' as unknown)) {
      const max = Number(value.maxItems);
      if (Number.isFinite(max)) {
        request.maxItems = max;
      }
    }

    this.error.set(null);
    this.saving.set(true);
    this.bundleService.create(request).subscribe({
      next: (bundle) => {
        this.saving.set(false);
        this.dialogRef.close(bundle);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(
          bundleHttpErrorMessage(err, 'Failed to create product bundle'),
        );
      },
    });
  }
}
