import {
  ChangeDetectionStrategy,
  Component,
  computed,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { PageEvent } from '@angular/material/paginator';
import {
  DataTableComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SearchInputComponent,
  TableColumn,
} from '@frontend/shared-ui';
import { PaginationMeta } from '@frontend/shared-models';
import {
  CatalogProduct,
  CatalogProductCategory,
  CatalogProductStatus,
} from '../catalog.model';
import { MOCK_CATALOG_PRODUCTS } from '../catalog.mock';

interface CatalogTableRow {
  id: number;
  sku: string;
  name: string;
  category: string;
  status: string;
  listPrice: number;
}

@Component({
  selector: 'cpq-catalog-list',
  standalone: true,
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    PageHeaderComponent,
    SearchInputComponent,
    DataTableComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Product Catalog"
      subtitle="Browse sellable products (presentational stub — mock data)"
    >
      <div actions>
        <button mat-stroked-button type="button" disabled aria-disabled="true">
          <mat-icon>add</mat-icon>
          New Product
        </button>
      </div>
      <div search class="filters">
        <app-search-input
          placeholder="Search by name or SKU..."
          (searchChange)="onSearch($event)"
        />
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Category</mat-label>
          <mat-select
            [value]="categoryFilter()"
            (selectionChange)="onCategoryChange($event.value)"
            aria-label="Filter by category"
          >
            <mat-option value="">All categories</mat-option>
            @for (c of categories; track c) {
              <mat-option [value]="c">{{ c }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Status</mat-label>
          <mat-select
            [value]="statusFilter()"
            (selectionChange)="onStatusChange($event.value)"
            aria-label="Filter by status"
          >
            <mat-option value="">All statuses</mat-option>
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
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
        icon="inventory_2"
        [title]="hasActiveFilters() ? 'No matching products' : 'No products yet'"
        [message]="
          hasActiveFilters()
            ? 'Try another search, category, or status filter.'
            : 'Catalog is empty. Products will appear here once loaded.'
        "
      />
    } @else {
      <app-data-table
        [columns]="columns"
        [data]="pagedRows()"
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
        gap: 16px;
        align-items: flex-start;
        width: 100%;
      }

      .filter-field {
        width: 180px;
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
export class CatalogListComponent {
  readonly categories: CatalogProductCategory[] = [
    'SUBSCRIPTION',
    'LICENSE',
    'SUPPORT',
    'SERVICES',
    'ADD_ON',
  ];
  readonly statuses: CatalogProductStatus[] = [
    'DRAFT',
    'ACTIVE',
    'DEPRECATED',
    'RETIRED',
  ];

  readonly columns: TableColumn[] = [
    { key: 'sku', header: 'SKU', sortable: true },
    { key: 'name', header: 'Name', sortable: true },
    { key: 'category', header: 'Category', sortable: true },
    { key: 'status', header: 'Status', type: 'badge', sortable: true },
    { key: 'listPrice', header: 'List Price', type: 'currency', sortable: true },
  ];

  private readonly allProducts = signal<CatalogProduct[]>([...MOCK_CATALOG_PRODUCTS]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly searchTerm = signal('');
  readonly categoryFilter = signal('');
  readonly statusFilter = signal('');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);

  readonly filteredProducts = computed(() => {
    const q = this.searchTerm().trim().toLowerCase();
    const category = this.categoryFilter();
    const status = this.statusFilter();

    return this.allProducts().filter((p) => {
      if (category && p.category !== category) {
        return false;
      }
      if (status && p.status !== status) {
        return false;
      }
      if (!q) {
        return true;
      }
      return (
        p.name.toLowerCase().includes(q) ||
        p.sku.toLowerCase().includes(q) ||
        (p.description?.toLowerCase().includes(q) ?? false)
      );
    });
  });

  readonly tableRows = computed<CatalogTableRow[]>(() =>
    this.filteredProducts().map((p) => ({
      id: p.id,
      sku: p.sku,
      name: p.name,
      category: p.category,
      status: p.status,
      listPrice: p.listPrice,
    })),
  );

  readonly pagination = computed<PaginationMeta>(() => {
    const total = this.tableRows().length;
    const size = this.pageSize();
    return {
      page: this.pageIndex(),
      size,
      totalElements: total,
      totalPages: Math.max(1, Math.ceil(total / size) || 1),
    };
  });

  readonly pagedRows = computed(() => {
    const start = this.pageIndex() * this.pageSize();
    return this.tableRows().slice(start, start + this.pageSize());
  });

  readonly isEmpty = computed(
    () => this.tableRows().length === 0 && !this.loading(),
  );

  constructor(private readonly router: Router) {}

  hasActiveFilters(): boolean {
    return (
      this.searchTerm().trim().length > 0 ||
      this.categoryFilter().length > 0 ||
      this.statusFilter().length > 0
    );
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.pageIndex.set(0);
  }

  onCategoryChange(value: string): void {
    this.categoryFilter.set(value ?? '');
    this.pageIndex.set(0);
  }

  onStatusChange(value: string): void {
    this.statusFilter.set(value ?? '');
    this.pageIndex.set(0);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  onRowClick(row: CatalogTableRow): void {
    void this.router.navigate(['/catalog', row.id]);
  }

  clearError(): void {
    this.error.set(null);
  }

  /** Test helper: replace catalog rows (e.g. empty list). */
  setProductsForTest(products: CatalogProduct[]): void {
    this.allProducts.set(products);
    this.pageIndex.set(0);
  }

  /** Test helper: surface the error banner. */
  setErrorForTest(message: string | null): void {
    this.error.set(message);
  }

  /** Test helper: toggle loading overlay. */
  setLoadingForTest(value: boolean): void {
    this.loading.set(value);
  }
}
