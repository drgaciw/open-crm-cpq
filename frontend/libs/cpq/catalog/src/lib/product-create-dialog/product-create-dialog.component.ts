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
  CATALOG_PRODUCT_TYPES,
  CatalogProductType,
  ProductCategory,
  ProductCreateRequest,
  ProductDto,
} from '@frontend/shared-models';
import { ProductService } from '../product.service';

/**
 * Minimal New Product dialog matching ProductCreateRequest.
 * Required: sku, name, productType, category. Optional: listPrice, currency.
 * No status field on create DTO.
 */
@Component({
  selector: 'cpq-product-create-dialog',
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
    <h2 mat-dialog-title>New Product</h2>
    <mat-dialog-content>
      @if (error()) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }
      <form [formGroup]="form" class="create-form" (ngSubmit)="onSubmit()">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>SKU</mat-label>
          <input matInput formControlName="sku" maxlength="100" autocomplete="off" />
          @if (form.get('sku')?.hasError('required')) {
            <mat-error>SKU is required</mat-error>
          }
          @if (form.get('sku')?.hasError('maxlength')) {
            <mat-error>SKU must be at most 100 characters</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="255" autocomplete="off" />
          @if (form.get('name')?.hasError('required')) {
            <mat-error>Name is required</mat-error>
          }
          @if (form.get('name')?.hasError('maxlength')) {
            <mat-error>Name must be at most 255 characters</mat-error>
          }
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Product type</mat-label>
            <mat-select formControlName="productType" aria-label="Product type">
              @for (t of productTypes; track t) {
                <mat-option [value]="t">{{ t }}</mat-option>
              }
            </mat-select>
            @if (form.get('productType')?.hasError('required')) {
              <mat-error>Product type is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Category</mat-label>
            <mat-select formControlName="category" aria-label="Category">
              @for (c of categories; track c) {
                <mat-option [value]="c">{{ c }}</mat-option>
              }
            </mat-select>
            @if (form.get('category')?.hasError('required')) {
              <mat-error>Category is required</mat-error>
            }
          </mat-form-field>
        </div>

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
export class ProductCreateDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly productService = inject(ProductService);
  private readonly dialogRef = inject(MatDialogRef<ProductCreateDialogComponent, ProductDto>);

  readonly productTypes: CatalogProductType[] = [...CATALOG_PRODUCT_TYPES];
  readonly categories: ProductCategory[] = [
    'SUBSCRIPTION',
    'LICENSE',
    'SUPPORT',
    'SERVICES',
    'ADD_ON',
  ];

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    sku: ['', [Validators.required, Validators.maxLength(100)]],
    name: ['', [Validators.required, Validators.maxLength(255)]],
    productType: this.fb.nonNullable.control<CatalogProductType | ''>('', {
      validators: [Validators.required],
    }),
    category: this.fb.nonNullable.control<ProductCategory | ''>('', {
      validators: [Validators.required],
    }),
    listPrice: this.fb.control<number | null>(null),
    currency: ['', [Validators.maxLength(3)]],
  });

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: ProductCreateRequest = {
      sku: value.sku.trim(),
      name: value.name.trim(),
      productType: value.productType as CatalogProductType,
      category: value.category as ProductCategory,
    };

    if (value.listPrice != null && !Number.isNaN(Number(value.listPrice))) {
      request.listPrice = Number(value.listPrice);
    }
    const currency = value.currency?.trim();
    if (currency) {
      request.currency = currency.toUpperCase();
    }

    this.error.set(null);
    this.saving.set(true);
    this.productService.create(request).subscribe({
      next: (product) => {
        this.saving.set(false);
        this.dialogRef.close(product);
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.saving.set(false);
        this.error.set(
          err?.error?.message || err?.message || 'Failed to create product',
        );
      },
    });
  }
}
