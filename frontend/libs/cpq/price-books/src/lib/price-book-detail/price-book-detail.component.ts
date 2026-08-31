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
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { CurrencyPipe } from '@angular/common';
import {
  ConfirmDialogComponent,
  EmptyStateComponent,
  PageHeaderComponent,
} from '@frontend/shared-ui';
import { PriceBookDto, PriceBookEntryDto } from '@frontend/shared-models';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';
import {
  PriceBookEntryDialogComponent,
  PriceBookEntryDialogData,
} from '../price-book-entry-dialog/price-book-entry-dialog.component';

interface EntryTableRow {
  id: number;
  productExternalId: string;
  listPrice: number;
  floorPrice: number | null;
  currency: string | null;
}

function entryHttpErrorMessage(err: unknown, fallback: string): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || fallback;
  if (
    code === 'PRICE_BOOK_PUBLISHED_IMMUTABLE' ||
    code === 'DUPLICATE_PRICE_BOOK_ENTRY'
  ) {
    return `${message} [${code}]`;
  }
  return message;
}

/**
 * Mirror BE PriceBookService.ensureNotPublished:
 * effectiveDate present AND effectiveDate <= today → published / immutable.
 */
export function isPriceBookPublished(
  book: Pick<PriceBookDto, 'effectiveDate'> | null | undefined,
  today: Date = new Date(),
): boolean {
  if (!book?.effectiveDate) {
    return false;
  }
  const effective = book.effectiveDate.slice(0, 10);
  const y = today.getFullYear();
  const m = String(today.getMonth() + 1).padStart(2, '0');
  const d = String(today.getDate()).padStart(2, '0');
  const todayIso = `${y}-${m}-${d}`;
  return effective <= todayIso;
}

@Component({
  selector: 'cpq-price-book-detail',
  standalone: true,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    CurrencyPipe,
    PageHeaderComponent,
    EmptyStateComponent,
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
              <div class="info-item">
                <span class="label">Status</span>
                <span class="value">{{
                  canEditEntries() ? 'Draft (editable)' : 'Published (read-only)'
                }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <section class="entries-section" aria-labelledby="entries-heading">
          <div class="entries-header">
            <h3 id="entries-heading" class="entries-title">Entries</h3>
            <button
              mat-stroked-button
              type="button"
              class="add-entry-btn"
              [disabled]="!canEditEntries() || mutating()"
              (click)="openCreateEntry()"
            >
              <mat-icon>add</mat-icon>
              Add entry
            </button>
          </div>

          @if (!canEditEntries()) {
            <p class="published-note" role="status">
              Published price books are immutable. Entry create, edit, and
              delete are disabled.
            </p>
          }

          @if (!entriesLoading() && entryRows().length === 0) {
            <app-empty-state
              icon="list_alt"
              title="No entries"
              message="This price book has no product entries yet."
            />
          } @else {
            <div class="entries-table-wrap">
              @if (entriesLoading()) {
                <div class="entries-loading" role="status">
                  <mat-spinner diameter="32"></mat-spinner>
                </div>
              }
              <table class="entries-table">
                <thead>
                  <tr>
                    <th scope="col">Product</th>
                    <th scope="col">List price</th>
                    <th scope="col">Floor price</th>
                    <th scope="col">Currency</th>
                    @if (canEditEntries()) {
                      <th scope="col" class="actions-col">Actions</th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (row of entryRows(); track row.id) {
                    <tr>
                      <td>{{ row.productExternalId }}</td>
                      <td>
                        {{
                          row.listPrice
                            | currency: row.currency || book.currency || 'USD'
                        }}
                      </td>
                      <td>
                        @if (row.floorPrice != null) {
                          {{
                            row.floorPrice
                              | currency: row.currency || book.currency || 'USD'
                          }}
                        } @else {
                          —
                        }
                      </td>
                      <td>{{ row.currency || '—' }}</td>
                      @if (canEditEntries()) {
                        <td class="actions-col">
                          <button
                            mat-button
                            type="button"
                            class="row-edit-btn"
                            [disabled]="mutating()"
                            (click)="openEditEntry(row)"
                          >
                            Edit
                          </button>
                          <button
                            mat-button
                            color="warn"
                            type="button"
                            class="row-delete-btn"
                            [disabled]="mutating()"
                            (click)="confirmDeleteEntry(row)"
                          >
                            Delete
                          </button>
                        </td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
              @if (entriesPagination().totalElements > 0) {
                <mat-paginator
                  [length]="entriesPagination().totalElements"
                  [pageSize]="entriesPagination().size"
                  [pageIndex]="entriesPagination().page"
                  [pageSizeOptions]="[10, 20, 50, 100]"
                  (page)="onEntriesPageChange($event)"
                />
              }
            </div>
          }
        </section>
      </div>
    } @else {
      <app-empty-state
        icon="search_off"
        title="Price book not found"
        [message]="
          bannerError() ||
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

      .entries-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        margin-bottom: 12px;
        flex-wrap: wrap;
      }

      .entries-title {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 600;
      }

      .published-note {
        margin: 0 0 12px;
        font-size: 0.875rem;
        color: #757575;
      }

      .entries-table-wrap {
        position: relative;
        border: 1px solid #e0e0e0;
        border-radius: 8px;
        overflow: auto;
        background: #fff;
      }

      .entries-loading {
        position: absolute;
        inset: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(255, 255, 255, 0.7);
        z-index: 1;
      }

      .entries-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.875rem;
      }

      .entries-table th,
      .entries-table td {
        padding: 12px 16px;
        text-align: left;
        border-bottom: 1px solid #eee;
      }

      .entries-table th {
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
export class PriceBookDetailComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  readonly service = inject(PriceBookService);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly mutating = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly loading = computed(() => this.service.loading() && !this.book());
  readonly entriesLoading = computed(
    () => this.service.loading() && !!this.book(),
  );
  readonly bannerError = computed(
    () => this.actionError() ?? this.service.error(),
  );
  readonly entriesPagination = computed(() => this.service.entriesPagination());

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

  /** Disable Add/Edit/Delete when published (effectiveDate <= today). */
  readonly canEditEntries = computed(
    () => !!this.book() && !isPriceBookPublished(this.book()),
  );

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
    this.actionError.set(null);
    this.service.error.set(null);
  }

  goBack(): void {
    void this.router.navigate(['/price-books']);
  }

  openCreateEntry(): void {
    if (!this.canEditEntries() || this.mutating()) {
      return;
    }
    const book = this.book();
    if (!book) {
      return;
    }
    this.openEntryDialog({
      bookId: book.id,
      defaultCurrency: book.currency,
    });
  }

  openEditEntry(row: EntryTableRow): void {
    if (!this.canEditEntries() || this.mutating()) {
      return;
    }
    const book = this.book();
    const entry = this.service.entries().find((e) => e.id === row.id);
    if (!book || !entry) {
      return;
    }
    this.openEntryDialog({
      bookId: book.id,
      defaultCurrency: book.currency,
      entry,
    });
  }

  confirmDeleteEntry(row: EntryTableRow): void {
    if (!this.canEditEntries() || this.mutating()) {
      return;
    }
    const book = this.book();
    if (!book) {
      return;
    }

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete entry',
        message: `Remove product "${row.productExternalId}" from this price book?`,
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
      this.service.deleteEntry(book.id, row.id).subscribe({
        next: () => {
          this.mutating.set(false);
          this.reloadEntries(book.id);
        },
        error: (err: unknown) => {
          this.mutating.set(false);
          this.actionError.set(
            entryHttpErrorMessage(err, 'Failed to delete price book entry'),
          );
        },
      });
    });
  }

  private openEntryDialog(data: PriceBookEntryDialogData): void {
    const ref = this.dialog.open(PriceBookEntryDialogComponent, {
      width: '520px',
      disableClose: true,
      data,
    });
    ref.afterClosed().subscribe((saved: PriceBookEntryDto | undefined) => {
      if (!saved) {
        return;
      }
      this.actionError.set(null);
      this.reloadEntries(data.bookId);
    });
  }

  private reloadEntries(bookId: number): void {
    const page = this.service.entriesPagination();
    this.service.getEntries(bookId, page.page, page.size);
  }

  private toEntryRow(e: PriceBookEntryDto): EntryTableRow {
    return {
      id: e.id,
      productExternalId: e.productExternalId,
      listPrice: e.listPrice,
      floorPrice: e.floorPrice ?? null,
      currency: e.currency ?? null,
    };
  }
}
