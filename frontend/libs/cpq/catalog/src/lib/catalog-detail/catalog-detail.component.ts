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
  AttributeValueDto,
  AttributeValueSetRequest,
  CATALOG_PRODUCT_TYPES,
  CatalogProductType,
  ProductAttributeDto,
  ProductCategory,
  ProductDto,
  ProductOptionGroupDto,
  ProductStatus,
  ProductUpdateRequest,
} from '@frontend/shared-models';
import { ProductService } from '../product.service';
import { ProductOptionService } from '../product-option.service';
import { ProductAttributeService } from '../product-attribute.service';
import {
  OptionGroupCreateDialogComponent,
  OptionGroupCreateDialogData,
} from '../option-group-create-dialog/option-group-create-dialog.component';
import {
  OptionCreateDialogComponent,
  OptionCreateDialogData,
} from '../option-create-dialog/option-create-dialog.component';
import { ConfigRulesPanelComponent } from '../config-rules-panel/config-rules-panel.component';

function httpErrorMessage(
  err: unknown,
  fallback = 'Failed to update product',
): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || fallback;
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
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
    ConfigRulesPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading() && !product()) {
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
                <ng-container>
                  <mat-icon>delete</mat-icon>
                  Delete
                </ng-container>
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
                <ng-container>
                  <mat-icon>save</mat-icon>
                  Save
                </ng-container>
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

        <section class="nested-section" aria-labelledby="options-heading">
          <div class="section-header">
            <h3 id="options-heading" class="section-title">Option groups</h3>
            <button
              mat-stroked-button
              type="button"
              class="create-group-btn"
              [disabled]="optionsBusy()"
              (click)="openCreateGroup()"
            >
              <mat-icon>add</mat-icon>
              Create group
            </button>
          </div>

          @if (optionsError(); as err) {
            <div class="section-error" role="alert">{{ err }}</div>
          }

          @if (optionsLoading()) {
            <div class="section-loading" role="status">
              <mat-spinner diameter="32"></mat-spinner>
            </div>
          } @else if (optionGroups().length === 0) {
            <app-empty-state
              icon="tune"
              title="No option groups"
              message="This product has no option groups yet."
            />
          } @else {
            <div class="groups-list">
              @for (group of optionGroups(); track group.id) {
                <mat-card class="group-card">
                  <mat-card-header>
                    <mat-card-title>{{ group.name }}</mat-card-title>
                    <mat-card-subtitle>
                      {{ group.selectionType }} · min {{ group.minSelections
                      }}{{
                        group.maxSelections !== null && group.maxSelections !== undefined
                          ? ' · max ' + group.maxSelections
                          : ''
                      }}
                    </mat-card-subtitle>
                  </mat-card-header>
                  <mat-card-content>
                    @if ((group.options ?? []).length === 0) {
                      <p class="muted">No options in this group.</p>
                    } @else {
                      <ul class="option-list">
                        @for (opt of group.options ?? []; track opt.id) {
                          <li>
                            <span>{{ opt.name }}</span>
                            @if (opt.defaultOption) {
                              <span class="badge">default</span>
                            }
                            @if (opt.priceAdjustment !== null && opt.priceAdjustment !== undefined) {
                              <span class="muted">
                                {{ opt.priceAdjustment
                                }}{{
                                  opt.adjustmentType === 'PERCENTAGE' ? '%' : ''
                                }}
                              </span>
                            }
                          </li>
                        }
                      </ul>
                    }
                  </mat-card-content>
                  <mat-card-actions align="end">
                    <button
                      mat-button
                      type="button"
                      class="add-option-btn"
                      [disabled]="optionsBusy()"
                      (click)="openAddOption(group)"
                    >
                      <mat-icon>add</mat-icon>
                      Add option
                    </button>
                    <button
                      mat-button
                      color="warn"
                      type="button"
                      class="delete-group-btn"
                      [disabled]="optionsBusy()"
                      (click)="confirmDeleteGroup(group)"
                    >
                      <mat-icon>delete</mat-icon>
                      Delete group
                    </button>
                  </mat-card-actions>
                </mat-card>
              }
            </div>
          }
        </section>

        <section class="nested-section" aria-labelledby="attrs-heading">
          <div class="section-header">
            <h3 id="attrs-heading" class="section-title">Attribute values</h3>
          </div>

          @if (attrsError(); as err) {
            <div class="section-error" role="alert">{{ err }}</div>
          }

          @if (attrsLoading()) {
            <div class="section-loading" role="status">
              <mat-spinner diameter="32"></mat-spinner>
            </div>
          } @else {
            @if (attributeValues().length === 0) {
              <app-empty-state
                icon="label"
                title="No attribute values"
                message="Set a value using the form below."
              />
            } @else {
              <table class="attrs-table">
                <thead>
                  <tr>
                    <th scope="col">Attribute</th>
                    <th scope="col">Value</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of attributeValues(); track row.id) {
                    <tr>
                      <td>{{ row.attributeName || row.attributeId }}</td>
                      <td>{{ row.value || '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }

            <form
              [formGroup]="attrForm"
              class="attr-form"
              (ngSubmit)="onSetAttributeValue()"
            >
              <mat-form-field appearance="outline" class="attr-field">
                <mat-label>Attribute</mat-label>
                <mat-select
                  formControlName="attributeId"
                  aria-label="Attribute definition"
                >
                  @for (def of attributeDefinitions(); track def.id) {
                    <mat-option [value]="def.id">
                      {{ def.displayName || def.name }}
                      ({{ def.dataType }})
                    </mat-option>
                  }
                </mat-select>
                @if (attrForm.get('attributeId')?.hasError('required')) {
                  <mat-error>Attribute is required</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline" class="attr-field">
                <mat-label>Value</mat-label>
                <input matInput formControlName="value" autocomplete="off" />
              </mat-form-field>
              <button
                mat-raised-button
                color="primary"
                type="submit"
                class="set-attr-btn"
                [disabled]="attrForm.invalid || attrsBusy()"
              >
                @if (attrsBusy()) {
                  <mat-spinner diameter="20" />
                } @else {
                  Set value
                }
              </button>
            </form>
          }
        </section>

        <cpq-config-rules-panel [productId]="product.id" />
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
        display: flex;
        flex-direction: column;
        gap: 24px;
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

      .nested-section {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }

      .section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        flex-wrap: wrap;
      }

      .section-title {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 600;
      }

      .section-loading {
        display: flex;
        justify-content: center;
        padding: 24px;
      }

      .section-error {
        padding: 8px 10px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
        font-size: 0.875rem;
      }

      .groups-list {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }

      .group-card {
        width: 100%;
      }

      .option-list {
        margin: 0;
        padding-left: 18px;
        display: flex;
        flex-direction: column;
        gap: 6px;
      }

      .option-list li {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
      }

      .badge {
        font-size: 11px;
        font-weight: 600;
        text-transform: uppercase;
        padding: 2px 6px;
        border-radius: 4px;
        background: #e3f2fd;
        color: #1565c0;
      }

      .muted {
        color: #757575;
        font-size: 0.875rem;
      }

      .attrs-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.9375rem;
      }

      .attrs-table th,
      .attrs-table td {
        text-align: left;
        padding: 10px 12px;
        border-bottom: 1px solid #e0e0e0;
      }

      .attrs-table th {
        font-size: 12px;
        text-transform: uppercase;
        letter-spacing: 0.4px;
        color: #757575;
      }

      .attr-form {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-start;
        margin-top: 8px;
      }

      .attr-field {
        flex: 1 1 180px;
      }

      .set-attr-btn {
        margin-top: 4px;
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

        .header-actions {
          flex-direction: column;
          align-items: stretch;
        }

        .header-actions button {
          width: 100%;
        }

        .section-header {
          flex-direction: column;
          align-items: stretch;
        }

        .section-header button {
          width: 100%;
        }

        .row {
          flex-direction: column;
        }

        .attr-form {
          flex-direction: column;
        }

        .attr-field {
          flex: 1 1 auto;
          width: 100%;
        }

        .set-attr-btn {
          width: 100%;
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
  private readonly optionService = inject(ProductOptionService);
  private readonly attributeService = inject(ProductAttributeService);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly loading = computed(() => this.service.loading());
  readonly error = computed(() => this.service.error());

  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly deleting = signal(false);
  readonly saveError = signal<string | null>(null);

  readonly optionGroups = signal<ProductOptionGroupDto[]>([]);
  readonly optionsLoading = signal(false);
  readonly optionsBusy = signal(false);
  readonly optionsError = signal<string | null>(null);

  readonly attributeValues = signal<AttributeValueDto[]>([]);
  readonly attributeDefinitions = signal<ProductAttributeDto[]>([]);
  readonly attrsLoading = signal(false);
  readonly attrsBusy = signal(false);
  readonly attrsError = signal<string | null>(null);

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

  readonly attrForm = this.fb.nonNullable.group({
    attributeId: this.fb.control<number | null>(null, {
      validators: [Validators.required],
    }),
    value: [''],
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
    this.loadOptionGroups(numericId);
    this.loadAttributeValues(numericId);
    this.loadAttributeDefinitions();
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

  openCreateGroup(): void {
    const product = this.product();
    if (!product || this.optionsBusy()) {
      return;
    }
    const ref = this.dialog.open(OptionGroupCreateDialogComponent, {
      width: '480px',
      data: { productId: product.id } satisfies OptionGroupCreateDialogData,
    });
    ref.afterClosed().subscribe((created: ProductOptionGroupDto | null) => {
      if (!created) {
        return;
      }
      this.optionGroups.update((groups) => [...groups, created]);
      this.optionsError.set(null);
    });
  }

  openAddOption(group: ProductOptionGroupDto): void {
    const product = this.product();
    if (!product || this.optionsBusy()) {
      return;
    }
    const ref = this.dialog.open(OptionCreateDialogComponent, {
      width: '480px',
      data: {
        groupId: group.id,
        groupName: group.name,
        productId: product.id,
      } satisfies OptionCreateDialogData,
    });
    ref.afterClosed().subscribe((updated: ProductOptionGroupDto | null) => {
      if (!updated) {
        return;
      }
      this.optionGroups.update((groups) =>
        groups.map((g) => (g.id === updated.id ? updated : g)),
      );
      this.optionsError.set(null);
    });
  }

  confirmDeleteGroup(group: ProductOptionGroupDto): void {
    if (this.optionsBusy()) {
      return;
    }
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete option group',
        message: `Delete option group "${group.name}"? Options in the group will be removed.`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.optionsError.set(null);
      this.optionsBusy.set(true);
      this.optionService.deleteGroup(group.id).subscribe({
        next: () => {
          this.optionsBusy.set(false);
          this.optionGroups.update((groups) =>
            groups.filter((g) => g.id !== group.id),
          );
        },
        error: (err: unknown) => {
          this.optionsBusy.set(false);
          this.optionsError.set(
            httpErrorMessage(err, 'Failed to delete option group'),
          );
        },
      });
    });
  }

  onSetAttributeValue(): void {
    if (this.attrForm.invalid || this.attrsBusy()) {
      this.attrForm.markAllAsTouched();
      return;
    }
    const product = this.product();
    if (!product) {
      return;
    }
    const value = this.attrForm.getRawValue();
    const request: AttributeValueSetRequest = {
      attributeId: Number(value.attributeId),
      value: value.value?.trim() || undefined,
    };
    this.attrsError.set(null);
    this.attrsBusy.set(true);
    this.attributeService.setValue(product.id, request).subscribe({
      next: (saved) => {
        this.attrsBusy.set(false);
        this.attributeValues.update((rows) => {
          const without = rows.filter(
            (r) => r.attributeId !== saved.attributeId,
          );
          return [...without, saved];
        });
        this.attrForm.patchValue({ value: '' });
      },
      error: (err: unknown) => {
        this.attrsBusy.set(false);
        this.attrsError.set(
          httpErrorMessage(err, 'Failed to set attribute value'),
        );
      },
    });
  }

  goBack(): void {
    void this.router.navigate(['/catalog']);
  }

  private loadOptionGroups(productId: number): void {
    this.optionsLoading.set(true);
    this.optionsError.set(null);
    this.optionService.listGroupsByProduct(productId).subscribe({
      next: (groups) => {
        this.optionGroups.set(groups ?? []);
        this.optionsLoading.set(false);
      },
      error: (err: unknown) => {
        this.optionsLoading.set(false);
        this.optionsError.set(
          httpErrorMessage(err, 'Failed to load option groups'),
        );
      },
    });
  }

  private loadAttributeValues(productId: number): void {
    this.attrsLoading.set(true);
    this.attrsError.set(null);
    this.attributeService.listValues(productId).subscribe({
      next: (values) => {
        this.attributeValues.set(values ?? []);
        this.attrsLoading.set(false);
      },
      error: (err: unknown) => {
        this.attrsLoading.set(false);
        this.attrsError.set(
          httpErrorMessage(err, 'Failed to load attribute values'),
        );
      },
    });
  }

  private loadAttributeDefinitions(): void {
    this.attributeService.listDefinitions(0, 50).subscribe({
      next: (page) => {
        this.attributeDefinitions.set(page?.content ?? []);
      },
      error: (err: unknown) => {
        this.attrsError.set(
          httpErrorMessage(err, 'Failed to load attribute definitions'),
        );
      },
    });
  }
}
