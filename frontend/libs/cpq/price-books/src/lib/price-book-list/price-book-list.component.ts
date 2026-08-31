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
import { PriceBookDto } from '@frontend/shared-models';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';
import { PriceBookCreateDialogComponent } from '../price-book-create-dialog/price-book-create-dialog.component';

interface PriceBookTableRow {
  id: number;
  name: string;
  region: string;
  currency: string;
  isDefault: string;
  effectiveDate: string;
  expirationDate: string;
}

@Component({
  selector: 'cpq-price-book-list',
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
      title="Price Books"
      subtitle="Browse price books from the pricebook-service API"
    >
      <div actions>
        <button mat-stroked-button type="button" (click)="openCreateDialog()">
          <mat-icon>add</mat-icon>
          New Price Book
        </button>
      </div>
      <div search class="filters">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Region</mat-label>
          <input
            matInput
            [value]="regionFilter()"
            (input)="onRegionInput($event)"
            (keyup.enter)="applyRegionFilter()"
            placeholder="e.g. NA, EMEA"
            aria-label="Filter by region"
          />
        </mat-form-field>
        <button
          mat-stroked-button
          type="button"
          (click)="applyRegionFilter()"
          aria-label="Apply region filter"
        >
          Filter
        </button>
        @if (regionFilter()) {
          <button mat-button type="button" (click)="clearRegionFilter()">
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
        icon="menu_book"
        [title]="
          hasActiveFilters() ? 'No matching price books' : 'No price books yet'
        "
        [message]="
          hasActiveFilters()
            ? 'Try another region filter.'
            : 'Create a price book to get started.'
        "
        [actionLabel]="hasActiveFilters() ? '' : 'New Price Book'"
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
export class PriceBookListComponent implements OnInit {
  readonly service = inject(PriceBookService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Name', sortable: true },
    { key: 'region', header: 'Region', sortable: true },
    { key: 'currency', header: 'Currency', sortable: true },
    { key: 'isDefault', header: 'Default', type: 'badge', sortable: true },
    { key: 'effectiveDate', header: 'Effective', sortable: true },
    { key: 'expirationDate', header: 'Expires', sortable: true },
  ];

  readonly regionFilter = signal('');
  readonly appliedRegion = signal('');

  readonly loading = computed(() => this.service.loading());
  readonly error = computed(() => this.service.error());
  readonly pagination = computed(() => this.service.pagination());

  readonly tableRows = computed<PriceBookTableRow[]>(() =>
    this.service.priceBooks().map((b) => this.toRow(b)),
  );

  readonly isEmpty = computed(
    () => this.tableRows().length === 0 && !this.loading(),
  );

  ngOnInit(): void {
    this.reload();
  }

  hasActiveFilters(): boolean {
    return this.appliedRegion().trim().length > 0;
  }

  onRegionInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value ?? '';
    this.regionFilter.set(value);
  }

  applyRegionFilter(): void {
    this.appliedRegion.set(this.regionFilter().trim());
    this.reload(0);
  }

  clearRegionFilter(): void {
    this.regionFilter.set('');
    this.appliedRegion.set('');
    this.reload(0);
  }

  onPageChange(event: PageEvent): void {
    this.reload(event.pageIndex, event.pageSize);
  }

  onRowClick(row: PriceBookTableRow): void {
    void this.router.navigate(['/price-books', row.id]);
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(PriceBookCreateDialogComponent, {
      width: '520px',
      disableClose: true,
    });
    ref.afterClosed().subscribe((created: PriceBookDto | undefined) => {
      if (!created?.id) {
        return;
      }
      void this.router.navigate(['/price-books', created.id]);
    });
  }

  clearError(): void {
    this.service.error.set(null);
  }

  private reload(page = 0, size = this.service.pagination().size || 20): void {
    const region = this.appliedRegion().trim();
    if (region) {
      this.service.listByRegion(region, page, size);
      return;
    }
    this.service.list(page, size, 'name,asc');
  }

  private toRow(b: PriceBookDto): PriceBookTableRow {
    return {
      id: b.id,
      name: b.name,
      region: b.region ?? '—',
      currency: b.currency ?? '—',
      isDefault: b.isDefault ? 'YES' : 'NO',
      effectiveDate: b.effectiveDate ?? '—',
      expirationDate: b.expirationDate ?? '—',
    };
  }
}
