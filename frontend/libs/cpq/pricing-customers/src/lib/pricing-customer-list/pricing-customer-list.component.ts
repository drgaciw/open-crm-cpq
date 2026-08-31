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
import { MatSelectModule } from '@angular/material/select';
import { PageEvent } from '@angular/material/paginator';
import {
  DataTableComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  TableColumn,
} from '@frontend/shared-ui';
import {
  CUSTOMER_REGION_LABELS,
  CUSTOMER_SEGMENT_LABELS,
  CustomerSegment,
  PricingCustomerDto,
} from '@frontend/shared-models';
import { PricingCustomerService } from '@frontend/cpq-pricing-data-access';
import { PricingCustomerCreateDialogComponent } from '../pricing-customer-create-dialog/pricing-customer-create-dialog.component';

const SEGMENTS: CustomerSegment[] = [
  'STARTUP',
  'SMB',
  'MID_MARKET',
  'ENTERPRISE',
  'STRATEGIC',
];

interface PricingCustomerTableRow {
  id: number;
  name: string;
  externalId: string;
  segment: string;
  region: string;
  industry: string;
}

@Component({
  selector: 'cpq-pricing-customer-list',
  standalone: true,
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatSelectModule,
    PageHeaderComponent,
    DataTableComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Pricing customers"
      subtitle="Browse pricing-service customers at /api/v1/pricing-customers"
    >
      <div actions>
        <button mat-stroked-button type="button" (click)="openCreateDialog()">
          <mat-icon>add</mat-icon>
          New pricing customer
        </button>
      </div>
      <div search class="filters">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Segment</mat-label>
          <mat-select
            [value]="segmentFilter()"
            (selectionChange)="onSegmentChange($event.value)"
            aria-label="Filter by segment"
          >
            <mat-option value="">All segments</mat-option>
            @for (seg of segments; track seg) {
              <mat-option [value]="seg">{{ segmentLabels[seg] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (segmentFilter()) {
          <button mat-button type="button" (click)="clearSegmentFilter()">
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
        icon="groups"
        [title]="
          hasActiveFilters()
            ? 'No matching pricing customers'
            : 'No pricing customers yet'
        "
        [message]="
          hasActiveFilters()
            ? 'Try another segment filter.'
            : 'Create a pricing customer to get started.'
        "
        [actionLabel]="hasActiveFilters() ? '' : 'New pricing customer'"
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
        width: 220px;
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
export class PricingCustomerListComponent implements OnInit {
  readonly service = inject(PricingCustomerService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly segments = SEGMENTS;
  readonly segmentLabels = CUSTOMER_SEGMENT_LABELS;

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Name', sortable: true },
    { key: 'externalId', header: 'External ID', sortable: true },
    { key: 'segment', header: 'Segment', sortable: true },
    { key: 'region', header: 'Region', sortable: true },
    { key: 'industry', header: 'Industry', sortable: true },
  ];

  readonly segmentFilter = signal<CustomerSegment | ''>('');

  readonly loading = computed(() => this.service.loading());
  readonly error = computed(() => this.service.error());
  readonly pagination = computed(() => this.service.pagination());

  readonly tableRows = computed<PricingCustomerTableRow[]>(() =>
    this.service.customers().map((c) => this.toRow(c)),
  );

  readonly isEmpty = computed(
    () => this.tableRows().length === 0 && !this.loading(),
  );

  ngOnInit(): void {
    this.reload();
  }

  hasActiveFilters(): boolean {
    return this.segmentFilter() !== '';
  }

  onSegmentChange(value: CustomerSegment | ''): void {
    this.segmentFilter.set(value);
    this.reload(0);
  }

  clearSegmentFilter(): void {
    this.segmentFilter.set('');
    this.reload(0);
  }

  onPageChange(event: PageEvent): void {
    this.reload(event.pageIndex, event.pageSize);
  }

  onRowClick(row: PricingCustomerTableRow): void {
    void this.router.navigate(['/pricing-customers', row.id]);
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(PricingCustomerCreateDialogComponent, {
      width: '560px',
      disableClose: true,
    });
    ref.afterClosed().subscribe((created: PricingCustomerDto | undefined) => {
      if (!created?.id) {
        return;
      }
      void this.router.navigate(['/pricing-customers', created.id]);
    });
  }

  clearError(): void {
    this.service.error.set(null);
  }

  private reload(page = 0, size = this.service.pagination().size || 20): void {
    const segment = this.segmentFilter();
    if (segment) {
      this.service.listBySegment(segment, page, size);
      return;
    }
    this.service.list(page, size, 'name,asc');
  }

  private toRow(c: PricingCustomerDto): PricingCustomerTableRow {
    return {
      id: c.id,
      name: c.name,
      externalId: c.externalId,
      segment: c.segment ? CUSTOMER_SEGMENT_LABELS[c.segment] : '—',
      region: c.region ? CUSTOMER_REGION_LABELS[c.region] : '—',
      industry: c.industry ?? '—',
    };
  }
}
