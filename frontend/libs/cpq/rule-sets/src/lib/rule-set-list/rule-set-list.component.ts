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
  RULE_SET_STATUS_LABELS,
  RuleSetDto,
  RuleSetStatus,
} from '@frontend/shared-models';
import { RuleSetService } from '@frontend/cpq-pricing-data-access';
import { RuleSetCreateDialogComponent } from '../rule-set-create-dialog/rule-set-create-dialog.component';

const STATUSES: RuleSetStatus[] = [
  'DRAFT',
  'ACTIVE',
  'DEPRECATED',
  'ARCHIVED',
];

interface RuleSetTableRow {
  id: number;
  name: string;
  status: string;
  engineType: string;
  version: string;
  effectiveDate: string;
}

@Component({
  selector: 'cpq-rule-set-list',
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
      title="Rule sets"
      subtitle="Browse pricing rule sets at /api/v1/rule-sets"
    >
      <div actions>
        <button mat-stroked-button type="button" (click)="openCreateDialog()">
          <mat-icon>add</mat-icon>
          New rule set
        </button>
      </div>
      <div search class="filters">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Status</mat-label>
          <mat-select
            [value]="statusFilter()"
            (selectionChange)="onStatusChange($event.value)"
            aria-label="Filter by status"
          >
            <mat-option value="">All statuses</mat-option>
            @for (st of statuses; track st) {
              <mat-option [value]="st">{{ statusLabels[st] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (statusFilter()) {
          <button mat-button type="button" (click)="clearStatusFilter()">
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
        icon="rule"
        [title]="
          hasActiveFilters() ? 'No matching rule sets' : 'No rule sets yet'
        "
        [message]="
          hasActiveFilters()
            ? 'Try another status filter.'
            : 'Create a rule set to get started.'
        "
        [actionLabel]="hasActiveFilters() ? '' : 'New rule set'"
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
export class RuleSetListComponent implements OnInit {
  readonly service = inject(RuleSetService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly statuses = STATUSES;
  readonly statusLabels = RULE_SET_STATUS_LABELS;

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Name', sortable: true },
    { key: 'status', header: 'Status', sortable: true },
    { key: 'engineType', header: 'Engine', sortable: true },
    { key: 'version', header: 'Version', sortable: true },
    { key: 'effectiveDate', header: 'Effective', sortable: true },
  ];

  readonly statusFilter = signal<RuleSetStatus | ''>('');

  readonly loading = computed(() => this.service.loading());
  readonly error = computed(() => this.service.error());
  readonly pagination = computed(() => this.service.pagination());

  readonly tableRows = computed<RuleSetTableRow[]>(() =>
    this.service.ruleSets().map((r) => this.toRow(r)),
  );

  readonly isEmpty = computed(
    () => this.tableRows().length === 0 && !this.loading(),
  );

  ngOnInit(): void {
    this.reload();
  }

  hasActiveFilters(): boolean {
    return this.statusFilter() !== '';
  }

  onStatusChange(value: RuleSetStatus | ''): void {
    this.statusFilter.set(value);
    this.reload(0);
  }

  clearStatusFilter(): void {
    this.statusFilter.set('');
    this.reload(0);
  }

  onPageChange(event: PageEvent): void {
    this.reload(event.pageIndex, event.pageSize);
  }

  onRowClick(row: RuleSetTableRow): void {
    void this.router.navigate(['/rule-sets', row.id]);
  }

  openCreateDialog(): void {
    const ref = this.dialog.open(RuleSetCreateDialogComponent, {
      width: '560px',
      disableClose: true,
    });
    ref.afterClosed().subscribe((created: RuleSetDto | undefined) => {
      if (!created?.id) {
        return;
      }
      void this.router.navigate(['/rule-sets', created.id]);
    });
  }

  clearError(): void {
    this.service.error.set(null);
  }

  private reload(page = 0, size = this.service.pagination().size || 20): void {
    const status = this.statusFilter();
    if (status) {
      this.service.listByStatus(status, page, size);
      return;
    }
    this.service.list(page, size, 'name,asc');
  }

  private toRow(r: RuleSetDto): RuleSetTableRow {
    return {
      id: r.id,
      name: r.name,
      status: RULE_SET_STATUS_LABELS[r.status] ?? r.status,
      engineType: r.engineType ?? 'DROOLS',
      version:
        r.ruleSetVersion != null ? String(r.ruleSetVersion) : '—',
      effectiveDate: r.effectiveDate ?? '—',
    };
  }
}
