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
import { TextFieldModule } from '@angular/cdk/text-field';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { finalize } from 'rxjs';
import {
  DiscountType,
  ProductBundleUpdateRequest,
  ProductBundleDto,
} from '../product-bundle.model';
import { ProductBundleService } from '../product-bundle.service';
import { bundleHttpErrorMessage } from '../bundle-http-error';

/**
 * New Product Bundle dialog matching ProductBundleCreateRequest.
 * Required: productId, name. Optional: description, discountType,
 * discountValue, minItems, maxItems.
 */
export interface ProductBundleDialogData {
  bundle?: ProductBundleDto;
}

@Component({
  selector: 'cpq-product-bundle-create-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    TextFieldModule,
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
    <h2 mat-dialog-title>{{ existing ? 'Edit Product Bundle' : 'New Product Bundle' }}</h2>
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
          @if (form.get('name')?.hasError('required') || form.get('name')?.hasError('pattern')) {
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
            cdkTextareaAutosize
            cdkAutosizeMinRows="3"
            cdkAutosizeMaxRows="8"
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
        [attr.aria-label]="saving() ? (existing ? 'Save' : 'Create') : null"
        (click)="onSubmit()"
      >
        @if (saving()) {
          <mat-spinner diameter="20" aria-label="Saving product bundle" />
        } @else {
          {{ existing ? 'Save' : 'Create' }}
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

  readonly existing = inject<ProductBundleDialogData>(MAT_DIALOG_DATA, { optional: true })?.bundle;

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    productId: [{ value: this.existing?.productId ?? null, disabled: !!this.existing }, [Validators.required, Validators.min(1)]],
    name: [this.existing?.name ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
    description: [this.existing?.description ?? ''],
    discountType: [this.existing?.discountType ?? 'NONE' as DiscountType],
    discountValue: [this.existing?.discountValue ?? null, Validators.min(0)],
    minItems: [this.existing?.minItems ?? null, Validators.min(0)],
    maxItems: [this.existing?.maxItems ?? null, Validators.min(0)],
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

    const request: ProductBundleUpdateRequest = {
      name: value.name.trim(),
      discountType: value.discountType,
      ...(value.description.trim() ? { description: value.description.trim() } : {}),
      ...(value.discountValue == null ? {} : { discountValue: value.discountValue }),
      ...(value.minItems == null ? {} : { minItems: value.minItems }),
      ...(value.maxItems == null ? {} : { maxItems: value.maxItems }),
    };

    this.error.set(null);
    this.saving.set(true);
    const save = this.existing
      ? this.bundleService.update(this.existing.id, request)
      : this.bundleService.create({ ...request, productId });
    this.form.disable({ emitEvent: false });
    save.pipe(finalize(() => {
      this.form.enable({ emitEvent: false });
      if (this.existing) {
        this.form.controls.productId.disable({ emitEvent: false });
      }
      this.saving.set(false);
    })).subscribe({
      next: (bundle) => {
        this.dialogRef.close(bundle);
      },
      error: (err: unknown) => {
        this.error.set(
          bundleHttpErrorMessage(err, this.existing ? 'Failed to save product bundle' : 'Failed to create product bundle'),
        );
      },
    });
  }
}
