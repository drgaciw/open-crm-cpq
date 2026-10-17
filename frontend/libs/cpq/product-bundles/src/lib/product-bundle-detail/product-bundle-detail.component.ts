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
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  ConfirmDialogComponent,
  EmptyStateComponent,
  PageHeaderComponent,
} from '@frontend/shared-ui';
import { ProductBundleDto, BundleItemDto } from '../product-bundle.model';
import { ProductBundleService } from '../product-bundle.service';
import { bundleHttpErrorMessage } from '../bundle-http-error';
import {
  BundleItemDialogComponent,
  BundleItemDialogData,
} from '../bundle-item-dialog/bundle-item-dialog.component';

interface ItemTableRow {
  id: number;
  productId: number;
  quantity: number;
  optional: string;
  sortOrder: number;
}

@Component({
  selector: 'cpq-product-bundle-detail',
  standalone: true,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading() && !bundle()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    } @else if (bundle(); as bundle) {
      <app-page-header [title]="bundle.name" [subtitle]="subtitle(bundle)">
        <div actions class="header-actions">
          <button
            mat-stroked-button
            color="warn"
            type="button"
            class="delete-bundle-btn"
            [disabled]="mutating()"
            (click)="confirmDeleteBundle()"
          >
            <mat-icon>delete</mat-icon>
            Delete
          </button>
          <button mat-stroked-button type="button" (click)="goBack()">
            <mat-icon>arrow_back</mat-icon>
            Back to bundles
          </button>
        </div>
      </app-page-header>

      @if (bannerError(); as err) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ err }}</span>
          <button mat-button type="button" (click)="clearError()">
            Dismiss
          </button>
        </div>
      }

      <div class="detail-body">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Bundle details</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Product ID</span>
                <span class="value">{{ bundle.productId }}</span>
              </div>
              <div class="info-item">
                <span class="label">Status</span>
                <span class="value">{{ bundle.status || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Discount type</span>
                <span class="value">{{ bundle.discountType || 'NONE' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Discount value</span>
                <span class="value">{{
                  bundle.discountValue ?? '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">Min items</span>
                <span class="value">{{
                  bundle.minItems ?? '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">Max items</span>
                <span class="value">{{
                  bundle.maxItems ?? '—'
                }}</span>
              </div>
              <div class="info-item full">
                <span class="label">Description</span>
                <span class="value">{{ bundle.description || '—' }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <section class="items-section" aria-labelledby="items-heading">
          <div class="items-header">
            <h3 id="items-heading" class="items-title">Items</h3>
            <button
              mat-stroked-button
              type="button"
              class="add-item-btn"
              [disabled]="mutating()"
              (click)="openAddItem()"
            >
              <mat-icon>add</mat-icon>
              Add item
            </button>
          </div>

          @if (itemRows().length === 0) {
            <app-empty-state
              icon="list_alt"
              title="No items"
              message="This bundle has no items yet."
            />
          } @else {
            <div class="items-table-wrap">
              <table class="items-table">
                <thead>
                  <tr>
                    <th scope="col">Product ID</th>
                    <th scope="col">Quantity</th>
                    <th scope="col">Optional</th>
                    <th scope="col">Sort</th>
                    <th scope="col" class="actions-col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of itemRows(); track row.id) {
                    <tr>
                      <td>{{ row.productId }}</td>
                      <td>{{ row.quantity }}</td>
                      <td>{{ row.optional }}</td>
                      <td>{{ row.sortOrder }}</td>
                      <td class="actions-col">
                        <button
                          mat-button
                          color="warn"
                          type="button"
                          class="row-remove-btn"
                          [disabled]="mutating()"
                          (click)="confirmRemoveItem(row)"
                        >
                          Remove
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
      </div>
    } @else {
      <app-empty-state
        icon="search_off"
        title="Bundle not found"
        [message]="
          bannerError() ||
          'This bundle could not be loaded from /api/v1/product-bundles.'
        "
        actionLabel="Back to bundles"
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

      .info-item.full {
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

      .items-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        margin-bottom: 12px;
        flex-wrap: wrap;
      }

      .items-title {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 600;
      }

      .items-table-wrap {
        border: 1px solid #e0e0e0;
        border-radius: 8px;
        overflow: auto;
        background: #fff;
      }

      .items-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.875rem;
      }

      .items-table th,
      .items-table td {
        padding: 12px 16px;
        text-align: left;
        border-bottom: 1px solid #eee;
      }

      .items-table th {
        font-weight: 600;
        color: #424242;
        background: #fafafa;
      }

      .actions-col {
        white-space: nowrap;
        text-align: right;
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
export class ProductBundleDetailComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  readonly service = inject(ProductBundleService);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly mutating = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly loading = computed(() => this.service.loading() && !this.bundle());
  readonly bannerError = computed(
    () => this.actionError() ?? this.service.error(),
  );

  readonly bundle = computed<ProductBundleDto | null>(() => {
    const selected = this.service.selectedBundle();
    if (!selected) {
      return null;
    }
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId) || selected.id !== numericId) {
      return null;
    }
    return selected;
  });

  readonly itemRows = computed<ItemTableRow[]>(() =>
    (this.bundle()?.items ?? []).map((item) => this.toItemRow(item)),
  );

  ngOnInit(): void {
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId)) {
      this.service.selectedBundle.set(null);
      this.service.error.set('Invalid bundle id');
      this.service.loading.set(false);
      return;
    }
    this.service.getById(numericId);
  }

  subtitle(bundle: ProductBundleDto): string {
    const parts = [
      `Product ${bundle.productId}`,
      bundle.status,
      bundle.minItems != null || bundle.maxItems != null
        ? `items ${bundle.minItems ?? '—'}-${bundle.maxItems ?? '—'}`
        : null,
    ].filter(Boolean);
    return parts.length > 0 ? parts.join(' · ') : `ID ${bundle.id}`;
  }

  clearError(): void {
    this.actionError.set(null);
    this.service.error.set(null);
  }

  goBack(): void {
    void this.router.navigate(['/product-bundles']);
  }

  openAddItem(): void {
    if (this.mutating()) {
      return;
    }
    const bundle = this.bundle();
    if (!bundle) {
      return;
    }

    const data: BundleItemDialogData = { bundleId: bundle.id };
    const ref = this.dialog.open(BundleItemDialogComponent, {
      width: '520px',
      disableClose: true,
      data,
    });

    ref.afterClosed().subscribe((saved: ProductBundleDto | undefined) => {
      if (!saved) {
        return;
      }
      this.actionError.set(null);
      this.service.selectedBundle.set(saved);
    });
  }

  confirmRemoveItem(row: ItemTableRow): void {
    if (this.mutating()) {
      return;
    }
    const bundle = this.bundle();
    if (!bundle) {
      return;
    }

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Remove item',
        message: `Remove product ${row.productId} from this bundle?`,
        confirmText: 'Remove',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.actionError.set(null);
      this.mutating.set(true);
      this.service.removeItem(bundle.id, row.id).subscribe({
        next: () => {
          this.mutating.set(false);
          this.service.getById(bundle.id);
        },
        error: (err: unknown) => {
          this.mutating.set(false);
          this.actionError.set(
            bundleHttpErrorMessage(err, 'Failed to remove bundle item'),
          );
        },
      });
    });
  }

  confirmDeleteBundle(): void {
    if (this.mutating()) {
      return;
    }
    const bundle = this.bundle();
    if (!bundle) {
      return;
    }

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete bundle',
        message: `Soft-delete "${bundle.name}"? It will be removed from the active bundle list.`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.actionError.set(null);
      this.mutating.set(true);
      this.service.delete(bundle.id).subscribe({
        next: () => {
          this.mutating.set(false);
          this.service.selectedBundle.set(null);
          void this.router.navigate(['/product-bundles']);
        },
        error: (err: unknown) => {
          this.mutating.set(false);
          this.actionError.set(
            bundleHttpErrorMessage(err, 'Failed to delete product bundle'),
          );
        },
      });
    });
  }

  private toItemRow(item: BundleItemDto): ItemTableRow {
    return {
      id: item.id,
      productId: item.productId,
      quantity: item.quantity,
      optional: item.optional ? 'Yes' : 'No',
      sortOrder: item.sortOrder,
    };
  }
}
