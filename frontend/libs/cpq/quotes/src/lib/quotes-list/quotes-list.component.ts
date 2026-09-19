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
  QuoteDealType,
  QuoteDto,
  QuoteStatus,
} from '@frontend/shared-models';
import { QuoteService } from '../quote.service';

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
      subtitle="Browse quotes from quote-service via gateway"
    >
      <div actions>
        <button
          mat-stroked-button
          type="button"
          (click)="onNewQuote()"
          title="Create a new quote"
        >
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

    @if (!loading() && !error() && isEmpty()) {
      <app-empty-state
        icon="request_quote"
        [title]="hasActiveFilters() ? 'No matching quotes' : 'No quotes yet'"
        [message]="
          hasActiveFilters()
            ? 'Try another search, status, or deal type filter.'
            : 'Quotes will appear here once loaded.'
        "
        [actionLabel]="hasActiveFilters() ? '' : 'New Quote'"
        (action)="onNewQuote()"
      />
    } @else if (!error() || tableRows().length > 0) {
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
export class QuotesListComponent implements OnInit {
  readonly quoteService = inject(QuoteService);
  private readonly router = inject(Router);

  readonly statuses: QuoteStatus[] = [
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
  readonly dealTypes: QuoteDealType[] = [
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

  readonly searchTerm = signal('');
  readonly statusFilter = signal('');
  readonly dealTypeFilter = signal('');

  readonly loading = computed(() => this.quoteService.loading());
  readonly error = computed(() => this.quoteService.error());
  readonly pagination = computed(() => this.quoteService.pagination());

  /** Client-side text filter on the current API page (status/dealType are server-side). */
  readonly filteredQuotes = computed(() => {
    const q = this.searchTerm().trim().toLowerCase();
    const quotes = this.quoteService.quotes();
    if (!q) {
      return quotes;
    }
    return quotes.filter((quote) => this.matchesSearch(quote, q));
  });

  readonly tableRows = computed<QuoteTableRow[]>(() =>
    this.filteredQuotes().map((quote) => ({
      id: quote.id,
      quoteNumber: quote.quoteNumber ?? `Q-${quote.id}`,
      name: quote.name,
      customerExternalId: quote.customerExternalId,
      status: quote.status,
      dealType: quote.dealType ?? '—',
      totalAmount: quote.totalAmount ?? 0,
    })),
  );

  readonly isEmpty = computed(
    () => this.tableRows().length === 0 && !this.loading(),
  );

  ngOnInit(): void {
    this.reload(0, this.pagination().size || 10);
  }

  hasActiveFilters(): boolean {
    return (
      this.searchTerm().trim().length > 0 ||
      this.statusFilter().length > 0 ||
      this.dealTypeFilter().length > 0
    );
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
  }

  onStatusChange(value: string): void {
    this.statusFilter.set(value ?? '');
    this.reload(0, this.pagination().size || 10);
  }

  onDealTypeChange(value: string): void {
    this.dealTypeFilter.set(value ?? '');
    this.reload(0, this.pagination().size || 10);
  }

  onPageChange(event: PageEvent): void {
    this.reload(event.pageIndex, event.pageSize);
  }

  onRowClick(row: QuoteTableRow): void {
    void this.router.navigate(['/quotes', row.id]);
  }

  onNewQuote(): void {
    void this.router.navigate(['/quotes', 'new']);
  }

  clearError(): void {
    this.quoteService.error.set(null);
  }

  private reload(page: number, size: number): void {
    const status = this.statusFilter();
    const dealType = this.dealTypeFilter();
    this.quoteService.list(page, size, 'createdAt,desc', {
      status: status ? (status as QuoteStatus) : undefined,
      dealType: dealType ? (dealType as QuoteDealType) : undefined,
    });
  }

  private matchesSearch(quote: QuoteDto, q: string): boolean {
    return (
      quote.name.toLowerCase().includes(q) ||
      (quote.quoteNumber?.toLowerCase().includes(q) ?? false) ||
      quote.customerExternalId.toLowerCase().includes(q) ||
      (quote.description?.toLowerCase().includes(q) ?? false)
    );
  }
}
