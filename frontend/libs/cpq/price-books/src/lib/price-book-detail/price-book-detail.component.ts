import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PageEvent } from '@angular/material/paginator';
import {
  DataTableComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  TableColumn,
} from '@frontend/shared-ui';
import { PriceBookDto, PriceBookEntryDto } from '@frontend/shared-models';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';

interface EntryTableRow {
  id: number;
  productExternalId: string;
  listPrice: number;
  floorPrice: number | string;
  currency: string;
}

@Component({
  selector: 'cpq-price-book-detail',
  standalone: true,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    EmptyStateComponent,
    DataTableComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading() && !book()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    } @else if (book(); as book) {
      <app-page-header [title]="book.name" [subtitle]="subtitle(book)">
        <div actions class="header-actions">
          <button mat-stroked-button type="button" (click)="goBack()">
            <mat-icon>arrow_back</mat-icon>
            Back to price books
          </button>
        </div>
      </app-page-header>

      @if (error()) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
          <button mat-button type="button" (click)="clearError()">
            Dismiss
          </button>
        </div>
      }

      <div class="detail-body">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Price book details</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Region</span>
                <span class="value">{{ book.region || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Currency</span>
                <span class="value">{{ book.currency || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Default</span>
                <span class="value">{{ book.isDefault ? 'Yes' : 'No' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Effective</span>
                <span class="value">{{ book.effectiveDate || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Expires</span>
                <span class="value">{{ book.expirationDate || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Updated</span>
                <span class="value">{{ book.updatedAt || '—' }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <section class="entries-section" aria-labelledby="entries-heading">
          <h3 id="entries-heading" class="entries-title">Entries</h3>

          @if (!entriesLoading() && entryRows().length === 0) {
            <app-empty-state
              icon="list_alt"
              title="No entries"
              message="This price book has no product entries yet."
            />
          } @else {
            <app-data-table
              [columns]="entryColumns"
              [data]="entryRows()"
              [loading]="entriesLoading()"
              [pagination]="entriesPagination()"
              (pageChange)="onEntriesPageChange($event)"
            />
          }
        </section>
      </div>
    } @else {
      <app-empty-state
        icon="search_off"
        title="Price book not found"
        [message]="
          error() ||
          'This price book could not be loaded from /api/v1/price-books.'
        "
        actionLabel="Back to price books"
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

      .entries-title {
        margin: 0 0 12px;
        font-size: 1.125rem;
        font-weight: 600;
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
export class PriceBookDetailComponent implements OnInit {
  private readonly router = inject(Router);
  readonly service = inject(PriceBookService);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly loading = computed(() => this.service.loading() && !this.book());
  readonly entriesLoading = computed(
    () => this.service.loading() && !!this.book(),
  );
  readonly error = computed(() => this.service.error());
  readonly entriesPagination = computed(() => this.service.entriesPagination());

  readonly entryColumns: TableColumn[] = [
    { key: 'productExternalId', header: 'Product', sortable: true },
    { key: 'listPrice', header: 'List price', type: 'currency', sortable: true },
    { key: 'floorPrice', header: 'Floor price', sortable: true },
    { key: 'currency', header: 'Currency', sortable: true },
  ];

  readonly book = computed<PriceBookDto | null>(() => {
    const selected = this.service.selectedPriceBook();
    if (!selected) {
      return null;
    }
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId) || selected.id !== numericId) {
      return null;
    }
    return selected;
  });

  readonly entryRows = computed<EntryTableRow[]>(() =>
    this.service.entries().map((e) => this.toEntryRow(e)),
  );

  ngOnInit(): void {
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId)) {
      this.service.selectedPriceBook.set(null);
      this.service.entries.set([]);
      this.service.error.set('Invalid price book id');
      this.service.loading.set(false);
      return;
    }
    this.service.getById(numericId);
    this.service.getEntries(numericId);
  }

  subtitle(book: PriceBookDto): string {
    const parts = [book.region, book.currency].filter(Boolean);
    return parts.length > 0 ? parts.join(' · ') : `ID ${book.id}`;
  }

  onEntriesPageChange(event: PageEvent): void {
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId)) {
      return;
    }
    this.service.getEntries(numericId, event.pageIndex, event.pageSize);
  }

  clearError(): void {
    this.service.error.set(null);
  }

  goBack(): void {
    void this.router.navigate(['/price-books']);
  }

  private toEntryRow(e: PriceBookEntryDto): EntryTableRow {
    return {
      id: e.id,
      productExternalId: e.productExternalId,
      listPrice: e.listPrice,
      floorPrice: e.floorPrice ?? '—',
      currency: e.currency ?? '—',
    };
  }
}
