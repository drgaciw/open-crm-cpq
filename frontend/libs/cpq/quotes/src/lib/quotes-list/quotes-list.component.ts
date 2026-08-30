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
  QuoteListDealType,
  QuoteListItem,
  QuoteListStatus,
} from '../quotes.model';
import { MOCK_QUOTES } from '../quotes.mock';

interface QuoteTableRow {
  id: number;
  quoteNumber: string;
  name: string;
  customerExternalId: string;
  status: string;
  dealType: string;
  totalAmount: number;
}

@Component({
  selector: 'cpq-quotes-list',
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
      title="Quotes"
      subtitle="Browse quotes (presentational stub — mock data)"
    >
      <div actions>
        <button mat-stroked-button type="button" disabled aria-disabled="true">
          <mat-icon>add</mat-icon>
          New Quote
        </button>
      </div>
      <div search class="filters">
        <app-search-input
          placeholder="Search by name, number, or customer..."
          (searchChange)="onSearch($event)"
        />
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
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Deal type</mat-label>
          <mat-select
            [value]="dealTypeFilter()"
            (selectionChange)="onDealTypeChange($event.value)"
            aria-label="Filter by deal type"
          >
            <mat-option value="">All deal types</mat-option>
            @for (t of dealTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
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
        icon="request_quote"
        [title]="hasActiveFilters() ? 'No matching quotes' : 'No quotes yet'"
        [message]="
          hasActiveFilters()
            ? 'Try another search, status, or deal type filter.'
            : 'Quotes will appear here once loaded.'
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
export class QuotesListComponent {
  readonly statuses: QuoteListStatus[] = [
    'DRAFT',
    'IN_REVIEW',
    'PENDING_APPROVAL',
    'APPROVED',
    'SENT',
    'ACCEPTED',
    'EXPIRED',
    'CANCELLED',
    'REJECTED',
  ];
  readonly dealTypes: QuoteListDealType[] = [
    'NEW',
    'AMENDMENT',
    'RENEWAL',
    'EXPANSION',
  ];

  readonly columns: TableColumn[] = [
    { key: 'quoteNumber', header: 'Quote #', sortable: true },
    { key: 'name', header: 'Name', sortable: true },
    { key: 'customerExternalId', header: 'Customer', sortable: true },
    { key: 'status', header: 'Status', type: 'badge', sortable: true },
    { key: 'dealType', header: 'Deal type', sortable: true },
    { key: 'totalAmount', header: 'Total', type: 'currency', sortable: true },
  ];

  private readonly allQuotes = signal<QuoteListItem[]>([...MOCK_QUOTES]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly searchTerm = signal('');
  readonly statusFilter = signal('');
  readonly dealTypeFilter = signal('');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(10);

  readonly filteredQuotes = computed(() => {
    const q = this.searchTerm().trim().toLowerCase();
    const status = this.statusFilter();
    const dealType = this.dealTypeFilter();

    return this.allQuotes().filter((quote) => {
      if (status && quote.status !== status) {
        return false;
      }
      if (dealType && quote.dealType !== dealType) {
        return false;
      }
      if (!q) {
        return true;
      }
      return (
        quote.name.toLowerCase().includes(q) ||
        quote.quoteNumber.toLowerCase().includes(q) ||
        quote.customerExternalId.toLowerCase().includes(q) ||
        (quote.description?.toLowerCase().includes(q) ?? false)
      );
    });
  });

  readonly tableRows = computed<QuoteTableRow[]>(() =>
    this.filteredQuotes().map((quote) => ({
      id: quote.id,
      quoteNumber: quote.quoteNumber,
      name: quote.name,
      customerExternalId: quote.customerExternalId,
      status: quote.status,
      dealType: quote.dealType,
      totalAmount: quote.totalAmount,
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
      this.statusFilter().length > 0 ||
      this.dealTypeFilter().length > 0
    );
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.pageIndex.set(0);
  }

  onStatusChange(value: string): void {
    this.statusFilter.set(value ?? '');
    this.pageIndex.set(0);
  }

  onDealTypeChange(value: string): void {
    this.dealTypeFilter.set(value ?? '');
    this.pageIndex.set(0);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  onRowClick(row: QuoteTableRow): void {
    void this.router.navigate(['/quotes', row.id]);
  }

  clearError(): void {
    this.error.set(null);
  }

  /** Test helper: replace quote rows (e.g. empty list). */
  setQuotesForTest(quotes: QuoteListItem[]): void {
    this.allQuotes.set(quotes);
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
