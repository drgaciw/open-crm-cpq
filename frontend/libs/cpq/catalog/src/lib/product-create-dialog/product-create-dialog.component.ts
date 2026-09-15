import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  form,
  FormField,
  FormRoot,
  maxLength,
  min,
  required,
  submit,
} from '@angular/forms/signals';
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

export interface ProductCreateFormModel {
  sku: string;
  name: string;
  productType: CatalogProductType | '';
  category: ProductCategory | '';
  listPrice: number | null;
  currency: string;
}

/**
 * Minimal New Product dialog matching ProductCreateRequest.
 * Required: sku, name, productType, category. Optional: listPrice, currency.
 * No status field on create DTO.
 * NG3: Signal Form (`@angular/forms/signals`) — the one v22 form conversion.
 */
@Component({
  selector: 'cpq-product-create-dialog',
  imports: [
    FormField,
    FormRoot,
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
      <form [formRoot]="productForm" class="create-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>SKU</mat-label>
          <input matInput [formField]="productForm.sku" autocomplete="off" />
          @if (productForm.sku().touched() && productForm.sku().invalid()) {
            @for (err of productForm.sku().errors(); track err.kind) {
              <mat-error>{{ err.message }}</mat-error>
            }
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Name</mat-label>
          <input matInput [formField]="productForm.name" autocomplete="off" />
          @if (productForm.name().touched() && productForm.name().invalid()) {
            @for (err of productForm.name().errors(); track err.kind) {
              <mat-error>{{ err.message }}</mat-error>
            }
          }
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Product type</mat-label>
            <mat-select [formField]="productForm.productType" aria-label="Product type">
              @for (t of productTypes; track t) {
                <mat-option [value]="t">{{ t }}</mat-option>
              }
            </mat-select>
            @if (productForm.productType().touched() && productForm.productType().invalid()) {
              @for (err of productForm.productType().errors(); track err.kind) {
                <mat-error>{{ err.message }}</mat-error>
              }
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Category</mat-label>
            <mat-select [formField]="productForm.category" aria-label="Category">
              @for (c of categories; track c) {
                <mat-option [value]="c">{{ c }}</mat-option>
              }
            </mat-select>
            @if (productForm.category().touched() && productForm.category().invalid()) {
              @for (err of productForm.category().errors(); track err.kind) {
                <mat-error>{{ err.message }}</mat-error>
              }
            }
          </mat-form-field>
        </div>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>List price</mat-label>
            <input
              matInput
              type="number"
              [formField]="productForm.listPrice"
              step="0.01"
            />
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Currency</mat-label>
            <input
              matInput
              [formField]="productForm.currency"
              placeholder="USD"
              autocomplete="off"
            />
            @if (productForm.currency().touched() && productForm.currency().invalid()) {
              @for (err of productForm.currency().errors(); track err.kind) {
                <mat-error>{{ err.message }}</mat-error>
              }
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
        [disabled]="productForm().invalid() || saving()"
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

  readonly error = signal<string | null>(null);

  readonly productModel = signal<ProductCreateFormModel>({
    sku: '',
    name: '',
    productType: '',
    category: '',
    listPrice: null,
    currency: '',
  });

  readonly productForm = form(
    this.productModel,
    (path) => {
      required(path.sku, { message: 'SKU is required' });
      maxLength(path.sku, 100, { message: 'SKU must be at most 100 characters' });
      required(path.name, { message: 'Name is required' });
      maxLength(path.name, 255, { message: 'Name must be at most 255 characters' });
      required(path.productType, { message: 'Product type is required' });
      required(path.category, { message: 'Category is required' });
      min(path.listPrice, 0, { message: 'List price must be at least 0' });
      maxLength(path.currency, 3, { message: 'Currency must be 3 characters' });
    },
    {
      submission: {
        action: (field) => this.createProduct(field().value()),
      },
    },
  );

  readonly saving = computed(() => this.productForm().submitting());

  async onSubmit(): Promise<void> {
    this.error.set(null);
    await submit(this.productForm, () => this.createProduct(this.productForm().value()));
  }

  private async createProduct(value: ProductCreateFormModel) {
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

    try {
      const product = await firstValueFrom(this.productService.create(request));
      this.dialogRef.close(product);
      return;
    } catch (err: unknown) {
      const e = err as { error?: { message?: string }; message?: string };
      const message = e?.error?.message || e?.message || 'Failed to create product';
      this.error.set(message);
      return { kind: 'serverError', message };
    }
  }
}
