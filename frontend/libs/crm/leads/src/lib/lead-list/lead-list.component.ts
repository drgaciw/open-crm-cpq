import { Component, inject, OnInit, ChangeDetectionStrategy, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog } from '@angular/material/dialog';
import { LeadService } from '../lead.service';
import { LeadDto, LeadPriority, LeadStatus } from '@frontend/shared-models';
import {
  ConfirmDialogComponent,
  DataTableComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SearchInputComponent,
  SortChangeEvent,
  TableColumn,
  TableRowAction,
  RowActionEvent,
} from '@frontend/shared-ui';
import { LoseReasonDialogComponent } from '../lead-detail/lose-reason-dialog.component';

export interface LeadTableRow {
  id: number;
  title: string;
  status: LeadStatus;
  priority: string;
  source: string;
  estimatedValue: string;
  score: string;
  contact: string;
  organization: string;
}

@Component({
  selector: 'lib-lead-list',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatTabsModule,
    MatSelectModule,
    PageHeaderComponent,
    SearchInputComponent,
    EmptyStateComponent,
    DataTableComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Leads"
      subtitle="Manage your lead pipeline"
    >
      <div actions>
        <button mat-raised-button color="primary" type="button" (click)="onCreate()">
          <mat-icon>add</mat-icon> New Lead
        </button>
      </div>
      <div search class="list-filters">
        <app-search-input
          placeholder="Search leads..."
          (searchChange)="onSearch($event)"
        />
        <mat-form-field appearance="outline" class="priority-field" subscriptSizing="dynamic">
          <mat-label>Priority</mat-label>
          <mat-select [value]="currentPriority() ?? ''" (selectionChange)="onPriorityChange($event.value)"
                      aria-label="Filter by priority">
            <mat-option value="">All priorities</mat-option>
            @for (p of priorities; track p) {
              <mat-option [value]="p">{{ p }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>
    </app-page-header>

    <mat-tab-group [(selectedIndex)]="selectedTabIndex" (selectedIndexChange)="onTabChange($event)" class="status-tabs">
      <mat-tab label="All"></mat-tab>
      <mat-tab label="New"></mat-tab>
      <mat-tab label="Contacted"></mat-tab>
      <mat-tab label="Qualified"></mat-tab>
      <mat-tab label="Unqualified"></mat-tab>
      <mat-tab label="Converted"></mat-tab>
      <mat-tab label="Lost"></mat-tab>
    </mat-tab-group>

    @if (service.error(); as err) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ err }}</span>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
        <button mat-button type="button" (click)="retry()">Retry</button>
      </div>
    }

    @if (!service.loading() && !service.error() && service.isEmpty()) {
      <app-empty-state
        icon="trending_up"
        [title]="hasActiveFilters() ? 'No matching leads' : 'No leads yet'"
        [message]="hasActiveFilters()
          ? 'Try another status or priority filter, or clear filters to see all leads.'
          : 'Add your first lead to start tracking opportunities.'"
        [actionLabel]="hasActiveFilters() ? '' : 'Create Lead'"
        (action)="onCreate()"
      />
    } @else if (!service.error() && (service.loading() || !service.isEmpty())) {
      <app-data-table
        [columns]="columns"
        [data]="tableRows()"
        [loading]="service.loading()"
        [pagination]="service.pagination()"
        [rowActions]="rowActions"
        rowActionsAriaLabel="Lead actions"
        [rowActionsDisabled]="actionLoading()"
        [pageSizeOptions]="[10, 20, 50]"
        (pageChange)="onPageChange($event)"
        (sortChange)="onSort($event)"
        (rowClick)="onRowClick($event)"
        (rowAction)="onRowAction($event)"
      />
    }
  `,
  styles: [`
    .list-filters { display: flex; flex-wrap: wrap; gap: var(--crm-space-md, 16px); align-items: flex-start; width: 100%; }
    .priority-field { width: 160px; }
    .status-tabs { margin-bottom: var(--crm-space-lg, 24px); }
    .error-banner {
      display: flex;
      align-items: center;
      gap: var(--crm-space-sm, 8px);
      margin: 0 0 var(--crm-space-md, 16px);
      padding: 12px var(--crm-space-md, 16px);
      border-radius: var(--crm-radius-sm, 8px);
      background: var(--crm-color-danger-container, #ffebee);
      color: var(--crm-color-danger, #c62828);
    }

    @media (max-width: 768px) {
      .list-filters { flex-direction: column; }
      .priority-field { width: 100%; }
    }
  `],
})
export class LeadListComponent implements OnInit {
  readonly service = inject(LeadService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly money = new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: 'USD',
  });

  readonly priorities: LeadPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
  readonly actionLoading = signal(false);
  readonly currentPriority = signal<LeadPriority | undefined>(undefined);

  readonly columns: TableColumn[] = [
    { key: 'title', header: 'Title', sortable: true },
    { key: 'status', header: 'Status', sortable: true },
    { key: 'priority', header: 'Priority', sortable: true },
    { key: 'source', header: 'Source', sortable: true },
    { key: 'estimatedValue', header: 'Estimated Value', sortable: true },
    { key: 'score', header: 'Score (advisory)', sortable: false },
    { key: 'contact', header: 'Contact', sortable: false },
    { key: 'organization', header: 'Organization', sortable: false },
  ];

  readonly rowActions: TableRowAction[] = [
    {
      action: 'edit',
      label: 'Edit',
      icon: 'edit',
      visible: (row) => this.canEdit((row as LeadTableRow).status),
    },
    {
      action: 'markContacted',
      label: 'Mark Contacted',
      icon: 'phone',
      visible: (row) => (row as LeadTableRow).status === 'NEW',
    },
    {
      action: 'qualify',
      label: 'Qualify',
      icon: 'check_circle',
      visible: (row) => (row as LeadTableRow).status === 'CONTACTED',
    },
    {
      action: 'convert',
      label: 'Convert to Deal',
      icon: 'arrow_forward',
      visible: (row) => (row as LeadTableRow).status === 'QUALIFIED',
    },
    {
      action: 'markLost',
      label: 'Mark Lost',
      icon: 'close',
      visible: (row) => this.canMarkLost((row as LeadTableRow).status),
    },
    {
      action: 'delete',
      label: 'Delete',
      icon: 'delete',
      danger: true,
      visible: (row) => this.canDelete((row as LeadTableRow).status),
    },
  ];

  readonly tableRows = computed<LeadTableRow[]>(() =>
    this.service.leads().map((lead) => this.toRow(lead)),
  );

  selectedTabIndex = 0;
  private currentSort = 'createdAt,desc';
  private currentStatus?: LeadStatus;

  /** Tab order: All, New, Contacted, Qualified, Unqualified, Converted, Lost */
  private readonly statusMap: (LeadStatus | undefined)[] = [
    undefined, 'NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED', 'LOST',
  ];

  ngOnInit(): void {
    this.refreshList(0, 20);
  }

  onTabChange(index: number): void {
    this.currentStatus = this.statusMap[index];
    this.refreshList(0, 20);
  }

  onPriorityChange(value: string): void {
    this.currentPriority.set(value ? (value as LeadPriority) : undefined);
    this.refreshList(0, this.service.pagination().size || 20);
  }

  onSearch(q: string): void {
    if (q.trim()) {
      this.service.search(q.trim());
    } else {
      this.refreshList(0, 20);
    }
  }

  onPageChange(e: PageEvent): void {
    this.refreshList(e.pageIndex, e.pageSize);
  }

  onSort(s: SortChangeEvent): void {
    this.currentSort = s.active && s.direction ? `${s.active},${s.direction}` : 'createdAt,desc';
    this.refreshList(0, this.service.pagination().size);
  }

  onCreate(): void {
    this.router.navigate(['/leads', 'new']);
  }

  onRowClick(row: LeadTableRow): void {
    this.router.navigate(['/leads', row.id]);
  }

  onRowAction(event: RowActionEvent<LeadTableRow>): void {
    const row = event.row;
    if (event.action === 'edit') {
      this.onEdit(row);
      return;
    }
    if (event.action === 'markContacted') {
      this.onMarkContacted(row);
      return;
    }
    if (event.action === 'qualify') {
      this.onQualify(row);
      return;
    }
    if (event.action === 'convert') {
      this.onConvert(row);
      return;
    }
    if (event.action === 'markLost') {
      this.onMarkLost(row);
      return;
    }
    if (event.action === 'delete') {
      this.onDelete(row);
    }
  }

  onEdit(lead: LeadTableRow | LeadDto): void {
    this.router.navigate(['/leads', lead.id, 'edit']);
  }

  onMarkContacted(lead: LeadTableRow | LeadDto): void {
    this.actionLoading.set(true);
    this.service.markContacted(lead.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.refreshList(this.service.pagination().page, this.service.pagination().size);
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onQualify(lead: LeadTableRow | LeadDto): void {
    this.actionLoading.set(true);
    this.service.qualify(lead.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.refreshList(this.service.pagination().page, this.service.pagination().size);
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onConvert(lead: LeadTableRow | LeadDto): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Convert lead',
        message: `Convert "${lead.title}" to a deal?`,
        confirmText: 'Convert',
        cancelText: 'Cancel',
        confirmColor: 'primary' as const,
      },
    });

    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) return;

      this.actionLoading.set(true);
      this.service.convertToDeal(lead.id).subscribe({
        next: (convertedLead) => {
          this.actionLoading.set(false);
          const dealId = convertedLead.convertedDealId;
          if (dealId != null) {
            this.router.navigate(['/deals', dealId]);
          } else {
            this.refreshList(this.service.pagination().page, this.service.pagination().size);
          }
        },
        error: () => this.actionLoading.set(false),
      });
    });
  }

  onMarkLost(lead: LeadTableRow | LeadDto): void {
    const dialogRef = this.dialog.open(LoseReasonDialogComponent, {
      width: '500px',
    });

    dialogRef.afterClosed().subscribe((reason: string) => {
      if (!reason) return;
      this.actionLoading.set(true);
      this.service.markLost(lead.id, reason).subscribe({
        next: () => {
          this.actionLoading.set(false);
          this.refreshList(this.service.pagination().page, this.service.pagination().size);
        },
        error: () => this.actionLoading.set(false),
      });
    });
  }

  onDelete(lead: LeadTableRow | LeadDto): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete lead',
        message: `Delete lead "${lead.title}"?`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });

    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) return;
      this.service.delete(lead.id).subscribe(() => {
        this.refreshList(this.service.pagination().page, this.service.pagination().size);
      });
    });
  }

  canEdit(status: LeadStatus): boolean {
    return status !== 'CONVERTED' && status !== 'LOST';
  }

  canDelete(status: LeadStatus): boolean {
    return status !== 'CONVERTED';
  }

  canMarkLost(status: LeadStatus): boolean {
    return status === 'NEW' || status === 'CONTACTED' || status === 'QUALIFIED';
  }

  hasActiveFilters(): boolean {
    return this.currentStatus !== undefined || this.currentPriority() !== undefined;
  }

  clearError(): void {
    this.service.error.set(null);
  }

  retry(): void {
    this.refreshList(this.service.pagination().page, this.service.pagination().size || 20);
  }

  private toRow(lead: LeadDto): LeadTableRow {
    return {
      id: lead.id,
      title: lead.title,
      status: lead.status,
      priority: lead.priority || '—',
      source: lead.source || '—',
      estimatedValue:
        lead.estimatedValue != null ? this.money.format(lead.estimatedValue) : '—',
      score: lead.scoreSuggestion?.value != null ? String(lead.scoreSuggestion.value) : '—',
      contact: lead.contactName || '—',
      organization: lead.organizationName || '—',
    };
  }

  private refreshList(page: number, size: number): void {
    this.service.list(page, size, this.currentSort, this.currentStatus, this.currentPriority());
  }
}
