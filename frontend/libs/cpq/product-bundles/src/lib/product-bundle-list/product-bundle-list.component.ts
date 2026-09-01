import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { PageEvent } from '@angular/material/paginator';
import {
  DataTableComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  TableColumn,
} from '@frontend/shared-ui';
import { ProductBundleDto } from '../product-bundle.model';
import { ProductBundleService } from '../product-bundle.service';
import { ProductBundleCreateDialogComponent } from '../product-bundle-create-dialog/product-bundle-create-dialog.component';

interface BundleTableRow {
  id: number;
  name: string;
  productId: string;
  discountType: string;
  discountValue: string;
  items: string;
  status: string;
}

@Component({
  selector: 'cpq-product-bundle-list',
  standalone: true,
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    PageHeaderComponent,
    DataTableComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Product Bundles"
      subtitle="Browse and manage product bundles from /api/v1/product-bundles"
    >
      <div actions>
        <button mat-stroked-button type="button" (click)="openCreateDialog()">
          <mat-icon>add</mat-icon>
          New Bundle
        </button>
      </div>
      <div search class="filters">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Product ID</mat-label>
          <input
            matInput
            type="number"
            [value]="productIdFilter()"
            (input)="onProductIdInput($event)"
            (keyup.enter)="applyProductIdFilter()"
            placeholder="e.g. 10"
            aria-label="Filter by product ID"
          />
        </mat-form-field>
        <button
          mat-stroked-button
          type="button"
          (click)="applyProductIdFilter()"
          aria-label="Apply product ID filter"
        >
          Filter
        </button>
        @if (appliedProductId() !== null) {
          <button mat-button type="button" (click)="clearProductIdFilter()">
            Clear
          </button>
        }
      </div>
    </app-page-header>

    @if (error()) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ error() }}</span>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
      </div>
    }

    @if (!loading() && isEmpty()) {
      <app-empty-state
        icon="inventory"
        [title]="
          hasActiveFilters() ? 'No matching bundles' : 'No product bundles yet'
        "
        [message]="
          hasActiveFilters()
            ? 'Try another product ID filter.'
            : 'Create a product bundle to get started.'
        "
        [actionLabel]="hasActiveFilters() ? '' : 'New Bundle'"
        (action)="openCreateDialog()"
      />
    } @else {
      <app-data-table
        [columns]="columns"
        [data]="tableRows()"
        [loading]="loading()"
        [pagination]="pagination()"
        (pageChange)="onPageChange($event)"
        (rowClick)="onRowClick($event)"
      />
    }
  `,
  styles: [
    `
      .filters {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-start;
        width: 100%;
      }

      .filter-field {
        width: 200px;
      }

      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 0 32px 16px;
        padding: 12px 16px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
      }

      @media (max-width: 768px) {
        .filters {
          flex-direction: column;
        }

        .filter-field {
          width: 100%;
        }

        .error-banner {
          margin: 0 16px 16px;
        }
      }
    `,
  ],
})
export class ProductBundleListComponent implements OnInit {
  readonly service = inject(ProductBundleService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Name', sortable: true },
    { key: 'productId', header: 'Product ID', sortable: true },
    { key: 'discountType', header: 'Discount', sortable: true },
    { key: 'discountValue', header: 'Value', sortable: true },
    { key: 'items', header: 'Items', sortable: true },
    { key: 'status', header: 'Status', type: 'badge', sortable: true },
  ];

  readonly productIdFilter = signal('');
  readonly appliedProductId = signal<number | null>(null);

  readonly loading = computed(() => this.service.loading());
  readonly error = computed(() => this.service.error());
  readonly pagination = computed(() => this.service.pagination());

  readonly tableRows = computed<BundleTableRow[]>(() =>
    this.service.bundles().map((b) => this.toRow(b)),
  );

  readonly isEmpty = computed(
    () => this.tableRows().length === 0 && !this.loading(),
  );

  ngOnInit(): void {
    this.reload();
  }

  hasActiveFilters(): boolean {
    return this.appliedProductId() !== null;
  }

  onProductIdInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value ?? '';
    this.productIdFilter.set(value);
  }

  applyProductIdFilter(): void {
    const raw = this.productIdFilter().trim();
    if (!raw) {
      this.clearProductIdFilter();
      return;
    }
    const id = Number(raw);
    if (!Number.isFinite(id) || id <= 0) {
      this.service.error.set('Product ID must be a positive number');
      return;
    }
    this.appliedProductId.set(id);
    this.reload(0);
  }

  clearProductIdFilter(): void {
    this.productIdFilter.set('');
    this.appliedProductId.set(null);
    this.reload(0);
  }

  onPageChange(event: PageEvent): void {
    if (this.appliedProductId() !== null) {
      return;
    }
    this.reload(event.pageIndex, event.pageSize);
  }

  onRowClick(row: BundleTableRow): void {
    void this.router.navigate(['/product-bundles', row.id]);
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(ProductBundleCreateDialogComponent, {
      width: '520px',
      disableClose: true,
    });
    ref.afterClosed().subscribe((created: ProductBundleDto | undefined) => {
      if (!created?.id) {
        return;
      }
      void this.router.navigate(['/product-bundles', created.id]);
    });
  }

  clearError(): void {
    this.service.error.set(null);
  }

  private reload(page = 0, size = this.service.pagination().size || 20): void {
    const productId = this.appliedProductId();
    if (productId !== null) {
      this.service.listByProductId(productId);
      return;
    }
    this.service.list(page, size, 'name,asc');
  }

  private toRow(b: ProductBundleDto): BundleTableRow {
    const itemCount = b.items?.length ?? 0;
    return {
      id: b.id,
      name: b.name,
      productId: String(b.productId),
      discountType: b.discountType ?? 'NONE',
      discountValue:
        b.discountValue != null && b.discountType && b.discountType !== 'NONE'
          ? String(b.discountValue)
          : '—',
      items: String(itemCount),
      status: b.status ?? '—',
    };
  }
}
