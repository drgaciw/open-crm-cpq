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
import {
  ProductCategory,
  ProductDto,
  ProductStatus,
} from '@frontend/shared-models';
import { ProductService } from '../product.service';

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
      subtitle="Browse sellable products from the product catalog API"
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
export class CatalogListComponent implements OnInit {
  readonly service = inject(ProductService);
  private readonly router = inject(Router);

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

  readonly columns: TableColumn[] = [
    { key: 'sku', header: 'SKU', sortable: true },
    { key: 'name', header: 'Name', sortable: true },
    { key: 'category', header: 'Category', sortable: true },
    { key: 'status', header: 'Status', type: 'badge', sortable: true },
    { key: 'listPrice', header: 'List Price', type: 'currency', sortable: true },
  ];

  readonly searchTerm = signal('');
  readonly categoryFilter = signal('');
  readonly statusFilter = signal('');

  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  readonly loading = computed(() => this.service.loading());
  readonly error = computed(() => this.service.error());
  readonly pagination = computed(() => this.service.pagination());

  /**
   * When a text search is active, category/status are refined client-side on the
   * current search page (search API has no category/status params).
   */
  readonly tableRows = computed<CatalogTableRow[]>(() => {
    const category = this.categoryFilter();
    const status = this.statusFilter();
    const searching = this.searchTerm().trim().length > 0;

    return this.service
      .products()
      .filter((p) => {
        if (!searching) {
          return true;
        }
        if (category && p.category !== category) {
          return false;
        }
        if (status && p.status !== status) {
          return false;
        }
        return true;
      })
      .map((p) => this.toRow(p));
  });

  readonly isEmpty = computed(
    () => this.tableRows().length === 0 && !this.loading(),
  );

  ngOnInit(): void {
    this.reload();
  }

  hasActiveFilters(): boolean {
    return (
      this.searchTerm().trim().length > 0 ||
      this.categoryFilter().length > 0 ||
      this.statusFilter().length > 0
    );
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    if (this.searchTimeout) {
      clearTimeout(this.searchTimeout);
    }
    this.searchTimeout = setTimeout(() => this.reload(0), 300);
  }

  onCategoryChange(value: string): void {
    this.categoryFilter.set(value ?? '');
    this.reload(0);
  }

  onStatusChange(value: string): void {
    this.statusFilter.set(value ?? '');
    this.reload(0);
  }

  onPageChange(event: PageEvent): void {
    this.reload(event.pageIndex, event.pageSize);
  }

  onRowClick(row: CatalogTableRow): void {
    void this.router.navigate(['/catalog', row.id]);
  }

  clearError(): void {
    this.service.error.set(null);
  }

  private reload(page = 0, size = this.service.pagination().size || 10): void {
    const q = this.searchTerm().trim();
    if (q) {
      this.service.search(q, page, size);
      return;
    }

    const category = this.categoryFilter();
    const status = this.statusFilter();
    this.service.list(page, size, 'name,asc', {
      category: (category || undefined) as ProductCategory | undefined,
      status: (status || undefined) as ProductStatus | undefined,
    });
  }

  private toRow(p: ProductDto): CatalogTableRow {
    return {
      id: p.id,
      sku: p.sku,
      name: p.name,
      category: p.category,
      status: p.status,
      listPrice: p.listPrice ?? 0,
    };
  }
}
