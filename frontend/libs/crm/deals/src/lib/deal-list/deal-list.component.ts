import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTabsModule } from '@angular/material/tabs';
import { MatDialog } from '@angular/material/dialog';
import { DealService } from '../deal.service';
import { DealDto, DealStage } from '@frontend/shared-models';
import {
  DataTableComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SearchInputComponent,
  SortChangeEvent,
  TableColumn,
  TableRowAction,
  RowActionEvent,
} from '@frontend/shared-ui';
import { DealLoseReasonDialogComponent } from '../lose-reason-dialog/lose-reason-dialog.component';
import { dealActionErrorMessage } from '../deal-action-error';
import {
  DEAL_STAGE_ORDER,
  canAdvanceDeal,
  canCloseDeal,
  dealStageLabel,
} from '../deal-stage';

export interface DealTableRow {
  id: number;
  name: string;
  organization: string;
  owner: string;
  amount: string;
  stage: DealStage;
  stageLabel: string;
  probability: string;
  expectedCloseDate: string;
  contact: string;
}

@Component({
  selector: 'app-deal-list',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatTabsModule,
    PageHeaderComponent,
    SearchInputComponent,
    EmptyStateComponent,
    DataTableComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Opportunities"
      subtitle="Track deals through the pipeline"
    >
      <div actions>
        <button mat-stroked-button type="button" (click)="router.navigate(['/deals/kanban'])">
          <mat-icon>view_kanban</mat-icon> Pipeline
        </button>
        <button mat-raised-button color="primary" type="button" (click)="onCreate()">
          <mat-icon>add</mat-icon> New opportunity
        </button>
      </div>
      <div search>
        <app-search-input
          placeholder="Search opportunities..."
          (searchChange)="onSearch($event)"
        />
      </div>
    </app-page-header>

    <mat-tab-group (selectedTabChange)="onTabChange($event.index)" class="stage-tabs">
      <mat-tab label="All"></mat-tab>
      @for (stage of stages; track stage) {
        <mat-tab [label]="stageLabel(stage)"></mat-tab>
      }
    </mat-tab-group>

    <div class="deal-filters" role="search" aria-label="Opportunity filters">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Organization</mat-label>
        <input
          matInput
          data-testid="deal-organization-filter"
          [value]="organizationFilter()"
          (input)="onOrganizationFilter($any($event.target).value)"
          placeholder="Name or id"
        />
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Owner</mat-label>
        <input
          matInput
          data-testid="deal-owner-filter"
          [value]="ownerFilter()"
          (input)="onOwnerFilter($any($event.target).value)"
          placeholder="Owner id"
        />
      </mat-form-field>
    </div>

    @if (service.error()) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ service.error() }}</span>
        <button mat-button type="button" (click)="onRetry()">Retry</button>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
      </div>
    }

    @if (!service.loading() && !service.error() && visibleDeals().length === 0) {
      <app-empty-state
        icon="handshake"
        [title]="hasActiveFilters() ? 'No matching opportunities' : 'No opportunities yet'"
        [message]="hasActiveFilters()
          ? 'Try another stage, organization, owner, or search.'
          : 'Create your first opportunity or convert a lead.'"
        [actionLabel]="hasActiveFilters() ? '' : 'Create opportunity'"
        (action)="onCreate()"
      />
    } @else if (!service.error() && (service.loading() || visibleDeals().length > 0)) {
      <app-data-table
        [columns]="columns"
        [data]="tableRows()"
        [loading]="service.loading()"
        [pagination]="service.pagination()"
        [rowActions]="rowActions"
        rowActionsAriaLabel="Deal actions"
        [rowActionsDisabled]="actionLoading()"
        [pageSizeOptions]="[10, 20, 50]"
        (pageChange)="onPageChange($event)"
        (sortChange)="onSort($event)"
        (rowClick)="onRowClick($event)"
        (rowAction)="onRowAction($event)"
      />
    }
  `,
  styles: `
    .stage-tabs { margin-bottom: var(--crm-space-md, 16px); }
    .deal-filters {
      display: flex;
      flex-wrap: wrap;
      gap: var(--crm-space-md, 16px);
      margin: 0 0 var(--crm-space-md, 16px);
      align-items: flex-start;
    }
    .deal-filters mat-form-field {
      min-width: 180px;
      flex: 1 1 180px;
      max-width: 280px;
    }
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
  `,
})
export class DealListComponent implements OnInit {
  readonly service = inject(DealService);
  readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly actionLoading = signal(false);
  readonly organizationFilter = signal('');
  readonly ownerFilter = signal('');
  readonly searchQuery = signal('');
  readonly stageFilter = signal<DealStage | undefined>(undefined);

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Opportunity', sortable: true },
    { key: 'organization', header: 'Organization', sortable: false },
    { key: 'owner', header: 'Owner', sortable: false },
    { key: 'amount', header: 'Amount', sortable: true },
    { key: 'stageLabel', header: 'Stage', sortable: true },
    { key: 'probability', header: 'Probability', sortable: true },
    { key: 'expectedCloseDate', header: 'Close Date', sortable: true },
    { key: 'contact', header: 'Contact', sortable: false },
  ];

  readonly rowActions: TableRowAction[] = [
    { action: 'edit', label: 'Edit', icon: 'edit' },
    {
      action: 'advance',
      label: 'Advance Stage',
      icon: 'arrow_forward',
      visible: (row) => this.canAdvance((row as DealTableRow).stage),
    },
    {
      action: 'win',
      label: 'Mark Won',
      icon: 'emoji_events',
      visible: (row) => this.canClose((row as DealTableRow).stage),
    },
    {
      action: 'lose',
      label: 'Mark Lost',
      icon: 'cancel',
      visible: (row) => this.canClose((row as DealTableRow).stage),
    },
    { action: 'delete', label: 'Delete', icon: 'delete', danger: true },
  ];

  stages: readonly DealStage[] = DEAL_STAGE_ORDER;
  private currentSort = 'createdAt,desc';
  private readonly money = new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  });
  private readonly dates = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' });

  readonly visibleDeals = computed(() => {
    let rows = this.service.deals();
    const owner = this.ownerFilter().trim().toLowerCase();
    const org = this.organizationFilter().trim().toLowerCase();
    const stage = this.stageFilter();
    const searching = this.searchQuery().trim().length > 0;

    if (searching && stage) {
      rows = rows.filter((row) => row.stage === stage);
    }
    if (org) {
      if (/^\d+$/.test(org)) {
        if (searching) {
          const id = Number(org);
          rows = rows.filter((row) => row.organizationId === id);
        }
      } else {
        rows = rows.filter((row) => (row.organizationName ?? '').toLowerCase().includes(org));
      }
    }
    if (owner) {
      rows = rows.filter((row) => (row.ownerId ?? '').toLowerCase().includes(owner));
    }
    return rows;
  });

  readonly tableRows = computed<DealTableRow[]>(() =>
    this.visibleDeals().map((d) => this.toRow(d)),
  );

  ngOnInit(): void {
    this.reload(0);
  }

  canAdvance(stage: DealStage): boolean {
    return canAdvanceDeal(stage);
  }

  canClose(stage: DealStage): boolean {
    return canCloseDeal(stage);
  }

  stageLabel(stage: DealStage): string {
    return dealStageLabel(stage);
  }

  onTabChange(index: number): void {
    this.stageFilter.set(index === 0 ? undefined : this.stages[index - 1]);
    this.reload(0);
  }

  onSearch(q: string): void {
    this.searchQuery.set(q.trim());
    this.reload(0);
  }

  onOrganizationFilter(value: string): void {
    const previous = this.serverOrganizationId();
    this.organizationFilter.set(value);
    if (previous !== this.serverOrganizationId()) {
      this.reload(0);
    }
  }

  onOwnerFilter(value: string): void {
    this.ownerFilter.set(value);
  }

  clearError(): void {
    this.service.error.set(null);
  }

  onRetry(): void {
    const page = this.searchQuery().trim() ? 0 : this.service.pagination().page;
    this.reload(page);
  }

  onPageChange(e: PageEvent): void {
    this.reload(e.pageIndex, e.pageSize);
  }

  onSort(s: SortChangeEvent): void {
    const field = s.active === 'stageLabel' ? 'stage' : s.active;
    this.currentSort = field && s.direction ? `${field},${s.direction}` : 'createdAt,desc';
    this.reload(0);
  }

  hasActiveFilters(): boolean {
    return this.stageFilter() !== undefined
      || this.ownerFilter().trim().length > 0
      || this.organizationFilter().trim().length > 0
      || this.searchQuery().trim().length > 0;
  }

  onCreate(): void {
    this.router.navigate(['/deals', 'new']);
  }

  onRowClick(row: DealTableRow): void {
    this.router.navigate(['/deals', row.id]);
  }

  onRowAction(event: RowActionEvent<DealTableRow>): void {
    if (event.action === 'edit') {
      this.onEdit(event.row);
      return;
    }
    if (event.action === 'advance') {
      this.onAdvance(event.row);
      return;
    }
    if (event.action === 'win') {
      this.onWin(event.row);
      return;
    }
    if (event.action === 'lose') {
      this.onLose(event.row);
      return;
    }
    if (event.action === 'delete') {
      this.onDelete(event.row);
    }
  }

  onEdit(d: DealTableRow | DealDto): void {
    this.router.navigate(['/deals', d.id, 'edit']);
  }

  onAdvance(d: DealTableRow | DealDto): void {
    this.actionLoading.set(true);
    this.service.advanceStage(d.id).subscribe({
      next: (updated) => this.commitStage(updated),
      error: (err) => this.failStage(err, 'Could not advance this opportunity'),
    });
  }

  onWin(d: DealTableRow | DealDto): void {
    if (!confirm(`Mark "${d.name}" as Won?`)) return;
    this.actionLoading.set(true);
    this.service.markWon(d.id).subscribe({
      next: (updated) => this.commitStage(updated),
      error: (err) => this.failStage(err, 'Could not mark this opportunity as Won'),
    });
  }

  onLose(d: DealTableRow | DealDto): void {
    const dialogRef = this.dialog.open(DealLoseReasonDialogComponent, { width: '500px' });
    dialogRef.afterClosed().subscribe((reason: string | undefined) => {
      if (!reason) return;
      this.actionLoading.set(true);
      this.service.markLost(d.id, reason).subscribe({
        next: (updated) => this.commitStage(updated),
        error: (err) => this.failStage(err, 'Could not mark this opportunity as Lost'),
      });
    });
  }

  onDelete(d: DealTableRow | DealDto): void {
    if (!confirm(`Delete "${d.name}"?`)) return;
    this.actionLoading.set(true);
    this.service.delete(d.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.reload(this.service.pagination().page);
      },
      error: (err) => this.failStage(err, 'Could not delete this opportunity'),
    });
  }

  private toRow(d: DealDto): DealTableRow {
    const currency = d.currency || 'USD';
    const amountFormatter =
      currency === 'USD'
        ? this.money
        : new Intl.NumberFormat(undefined, {
            style: 'currency',
            currency,
            maximumFractionDigits: 0,
          });
    return {
      id: d.id,
      name: d.name,
      organization: d.organizationName || '—',
      owner: d.ownerId || '—',
      amount: d.amount != null ? amountFormatter.format(d.amount) : '—',
      stage: d.stage,
      stageLabel: dealStageLabel(d.stage),
      probability: d.probability != null ? `${d.probability}%` : '—',
      expectedCloseDate: d.expectedCloseDate
        ? this.dates.format(new Date(d.expectedCloseDate))
        : '—',
      contact: d.primaryContactName || '—',
    };
  }

  private serverOrganizationId(): number | undefined {
    const raw = this.organizationFilter().trim();
    if (!/^\d+$/.test(raw)) {
      return undefined;
    }
    const id = Number(raw);
    return id > 0 ? id : undefined;
  }

  private reload(page: number, size = this.service.pagination().size || 20): void {
    const query = this.searchQuery().trim();
    if (query) {
      this.service.search(query);
      return;
    }
    const orgId = this.serverOrganizationId();
    const stage = this.stageFilter();
    if (orgId != null) {
      this.service.list(page, size, this.currentSort, stage, orgId);
      return;
    }
    if (stage) {
      this.service.list(page, size, this.currentSort, stage);
      return;
    }
    this.service.list(page, size, this.currentSort);
  }

  private commitStage(updated: DealDto): void {
    this.service.applyStageChange(updated);
    this.actionLoading.set(false);
    this.reload(this.service.pagination().page);
  }

  private failStage(err: unknown, fallback: string): void {
    this.actionLoading.set(false);
    this.service.error.set(dealActionErrorMessage(err, fallback));
  }
}
