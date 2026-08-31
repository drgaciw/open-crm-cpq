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
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import {
  ConfirmDialogComponent,
  CurrencyFormatPipe,
  EmptyStateComponent,
  PageHeaderComponent,
  StatusBadgeComponent,
} from '@frontend/shared-ui';
import {
  CATALOG_PRODUCT_TYPES,
  CatalogProductType,
  ProductCategory,
  ProductDto,
  ProductStatus,
  ProductUpdateRequest,
} from '@frontend/shared-models';
import { ProductService } from '../product.service';

function httpErrorMessage(
  err: unknown,
  fallback = 'Failed to update product',
): string {
  const e = err as { error?: { message?: string }; message?: string } | null;
  return e?.error?.message || e?.message || fallback;
}

@Component({
  selector: 'cpq-catalog-detail',
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
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    CurrencyFormatPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    } @else if (product(); as product) {
      <app-page-header [title]="product.name" [subtitle]="product.sku">
        <div actions class="header-actions">
          <button
            mat-stroked-button
            type="button"
            (click)="goBack()"
            [disabled]="saving() || deleting()"
          >
            <mat-icon>arrow_back</mat-icon>
            Back to catalog
          </button>
          @if (!editing()) {
            <button
              mat-raised-button
              color="primary"
              type="button"
              (click)="startEdit()"
              [disabled]="deleting()"
            >
              <mat-icon>edit</mat-icon>
              Edit
            </button>
            <button
              mat-stroked-button
              color="warn"
              type="button"
              (click)="onDelete()"
              [disabled]="deleting()"
            >
              @if (deleting()) {
                <mat-spinner diameter="20" />
              } @else {
                <mat-icon>delete</mat-icon>
                Delete
              }
            </button>
          } @else {
            <button mat-button type="button" (click)="cancelEdit()" [disabled]="saving()">
              Cancel
            </button>
            <button
              mat-raised-button
              color="primary"
              type="button"
              (click)="onSave()"
              [disabled]="form.invalid || saving()"
            >
              @if (saving()) {
                <mat-spinner diameter="20" />
              } @else {
                <mat-icon>save</mat-icon>
                Save
              }
            </button>
          }
        </div>
      </app-page-header>

      @if (saveError(); as err) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ err }}</span>
          <button mat-button type="button" (click)="clearSaveError()">Dismiss</button>
        </div>
      }

      <div class="detail-body">
        <mat-card>
          <mat-card-header>
            <mat-card-title>
              {{ editing() ? 'Edit product' : 'Product details' }}
            </mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (!editing()) {
              <div class="info-grid">
                <div class="info-item">
                  <span class="label">Status</span>
                  <app-status-badge [status]="product.status" type="product" />
                </div>
                <div class="info-item">
                  <span class="label">Category</span>
                  <span class="value">{{ product.category }}</span>
                </div>
                <div class="info-item">
                  <span class="label">Product type</span>
                  <span class="value">{{ product.productType }}</span>
                </div>
                <div class="info-item">
                  <span class="label">List price</span>
                  <span class="value">{{
                    (product.listPrice ?? 0)
                      | currencyFormat: product.currency || 'USD'
                  }}</span>
                </div>
                <div class="info-item full-width">
                  <span class="label">Description</span>
                  <span class="value">{{ product.description || '—' }}</span>
                </div>
              </div>
            } @else {
              <form [formGroup]="form" class="edit-form" (ngSubmit)="onSave()">
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
                  <mat-label>Description</mat-label>
                  <textarea
                    matInput
                    formControlName="description"
                    rows="3"
                  ></textarea>
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
                    <mat-label>Status</mat-label>
                    <mat-select formControlName="status" aria-label="Status">
                      @for (s of statuses; track s) {
                        <mat-option [value]="s">{{ s }}</mat-option>
                      }
                    </mat-select>
                    @if (form.get('status')?.hasError('required')) {
                      <mat-error>Status is required</mat-error>
                    }
                  </mat-form-field>

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
                </div>

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
              </form>
            }
          </mat-card-content>
        </mat-card>
      </div>
    } @else {
      <app-empty-state
        icon="search_off"
        title="Product not found"
        [message]="
          error() ||
          'This catalog item could not be loaded from the product catalog API.'
        "
        actionLabel="Back to catalog"
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
        gap: 8px;
        margin: 0 32px 16px;
        padding: 10px 12px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
        font-size: 0.875rem;
      }

      .detail-body {
        padding: 0 32px 32px;
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

      .edit-form {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin-top: 16px;
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

      @media (max-width: 768px) {
        .detail-body {
          padding: 0 16px 24px;
        }

        .error-banner {
          margin: 0 16px 16px;
        }

        .info-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class CatalogDetailComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);
  readonly service = inject(ProductService);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly loading = computed(() => this.service.loading());
  readonly error = computed(() => this.service.error());

  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly deleting = signal(false);
  readonly saveError = signal<string | null>(null);

  readonly productTypes: CatalogProductType[] = [...CATALOG_PRODUCT_TYPES];
  readonly categories: ProductCategory[] = [
    'SUBSCRIPTION',
    'LICENSE',
    'SUPPORT',
    'SERVICES',
    'ADD_ON',
  ];
  readonly statuses: ProductStatus[] = [
    'DRAFT',
    'ACTIVE',
    'DEPRECATED',
    'RETIRED',
  ];

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    productType: this.fb.nonNullable.control<CatalogProductType | ''>('', {
      validators: [Validators.required],
    }),
    category: this.fb.nonNullable.control<ProductCategory | ''>('', {
      validators: [Validators.required],
    }),
    status: this.fb.nonNullable.control<ProductStatus | ''>('', {
      validators: [Validators.required],
    }),
    listPrice: this.fb.control<number | null>(null),
    currency: ['', [Validators.maxLength(3)]],
  });

  readonly product = computed<ProductDto | null>(() => {
    const selected = this.service.selectedProduct();
    if (!selected) {
      return null;
    }
    const raw = this.id();
    const numericId = Number(raw);
    if (!Number.isFinite(numericId) || selected.id !== numericId) {
      return null;
    }
    return selected;
  });

  ngOnInit(): void {
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId)) {
      this.service.selectedProduct.set(null);
      this.service.error.set('Invalid product id');
      this.service.loading.set(false);
      return;
    }
    this.service.getById(numericId);
  }

  startEdit(): void {
    const product = this.product();
    if (!product) {
      return;
    }
    this.saveError.set(null);
    this.form.reset({
      name: product.name,
      description: product.description ?? '',
      productType: product.productType,
      category: product.category,
      status: product.status,
      listPrice: product.listPrice ?? null,
      currency: product.currency ?? '',
    });
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
    this.saveError.set(null);
    this.saving.set(false);
  }

  clearSaveError(): void {
    this.saveError.set(null);
  }

  onSave(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const product = this.product();
    if (!product) {
      return;
    }

    const value = this.form.getRawValue();
    const request: ProductUpdateRequest = {
      name: value.name.trim(),
      description: value.description?.trim() || undefined,
      productType: value.productType as CatalogProductType,
      category: value.category as ProductCategory,
      status: value.status as ProductStatus,
    };

    if (value.listPrice != null && !Number.isNaN(Number(value.listPrice))) {
      request.listPrice = Number(value.listPrice);
    }
    const currency = value.currency?.trim();
    if (currency) {
      request.currency = currency.toUpperCase();
    }

    this.saveError.set(null);
    this.saving.set(true);
    this.service.update(product.id, request).subscribe({
      next: (updated) => {
        this.service.selectedProduct.set(updated);
        this.saving.set(false);
        this.editing.set(false);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.saveError.set(httpErrorMessage(err));
      },
    });
  }

  onDelete(): void {
    const product = this.product();
    if (!product || this.deleting() || this.editing()) {
      return;
    }

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete product',
        message: `Soft-delete "${product.name}" (${product.sku})? It will be removed from the active catalog.`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.saveError.set(null);
      this.deleting.set(true);
      this.service.delete(product.id).subscribe({
        next: () => {
          this.deleting.set(false);
          this.service.selectedProduct.set(null);
          void this.router.navigate(['/catalog']);
        },
        error: (err: unknown) => {
          this.deleting.set(false);
          this.saveError.set(
            httpErrorMessage(err, 'Failed to delete product'),
          );
        },
      });
    });
  }

  goBack(): void {
    void this.router.navigate(['/catalog']);
  }
}
