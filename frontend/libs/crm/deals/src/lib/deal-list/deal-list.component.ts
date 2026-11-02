import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { MatTabsModule } from '@angular/material/tabs';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { DealService } from '../deal.service';
import { DealDto, DealStage } from '@frontend/shared-models';
import {
  EmptyStateComponent,
  PageHeaderComponent,
  SearchInputComponent,
} from '@frontend/shared-ui';
import { DealLoseReasonDialogComponent } from '../lose-reason-dialog/lose-reason-dialog.component';
import { dealActionErrorMessage } from '../deal-action-error';
import {
  DEAL_STAGE_ORDER,
  canAdvanceDeal,
  canCloseDeal,
  dealStageColor,
  dealStageLabel,
} from '../deal-stage';

@Component({
  selector: 'app-deal-list',
  standalone: true,
  imports: [
    MatTableModule, MatPaginatorModule, MatSortModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule,
    MatMenuModule, MatTabsModule, MatChipsModule, CurrencyPipe, DatePipe,
    PageHeaderComponent, SearchInputComponent, EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Opportunities"
      subtitle="Track deals through the pipeline"
    >
      <div actions>
        <button mat-stroked-button (click)="router.navigate(['/deals/kanban'])">
          <mat-icon>view_kanban</mat-icon> Pipeline
        </button>
        <button mat-raised-button color="primary" (click)="onCreate()">
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

    <div class="deal-filters">
      <label>
        Organization
        <input
          data-testid="deal-organization-filter"
          type="text"
          [value]="organizationFilter()"
          (input)="onOrganizationFilter($any($event.target).value)"
          placeholder="Name or id"
        />
      </label>
      <label>
        Owner
        <input
          data-testid="deal-owner-filter"
          type="text"
          [value]="ownerFilter()"
          (input)="onOwnerFilter($any($event.target).value)"
          placeholder="Owner id"
        />
      </label>
    </div>

    @if (service.error()) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ service.error() }}</span>
        <button mat-button type="button" (click)="onRetry()">Retry</button>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
      </div>
    }

    @if (service.loading()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40"></mat-spinner>
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
    } @else if (!service.loading() && visibleDeals().length > 0) {
      <div class="table-container">
        <table mat-table [dataSource]="visibleDeals()" [trackBy]="trackById" matSort (matSortChange)="onSort($event)">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Opportunity</th>
            <td mat-cell *matCellDef="let d">
              <a class="link" (click)="onRowClick(d)">{{ d.name }}</a>
            </td>
          </ng-container>
          <ng-container matColumnDef="organization">
            <th mat-header-cell *matHeaderCellDef>Organization</th>
            <td mat-cell *matCellDef="let d">{{ d.organizationName || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="owner">
            <th mat-header-cell *matHeaderCellDef>Owner</th>
            <td mat-cell *matCellDef="let d">{{ d.ownerId || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Amount</th>
            <td mat-cell *matCellDef="let d">{{ d.amount | currency:(d.currency || 'USD'):'symbol':'1.0-0' }}</td>
          </ng-container>
          <ng-container matColumnDef="stage">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Stage</th>
            <td mat-cell *matCellDef="let d">
              <span class="stage-badge" [style.background]="stageColor(d.stage)">
                {{ stageLabel(d.stage) }}
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="probability">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Probability</th>
            <td mat-cell *matCellDef="let d">{{ d.probability ? d.probability + '%' : '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="expectedCloseDate">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Close Date</th>
            <td mat-cell *matCellDef="let d">{{ d.expectedCloseDate ? (d.expectedCloseDate | date:'mediumDate') : '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="contact">
            <th mat-header-cell *matHeaderCellDef>Contact</th>
            <td mat-cell *matCellDef="let d">{{ d.primaryContactName || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let d">
              <button mat-icon-button [matMenuTriggerFor]="menu" [disabled]="actionLoading()"
                      (click)="$event.stopPropagation()" aria-label="Deal actions">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item (click)="onEdit(d)"><mat-icon>edit</mat-icon><span>Edit</span></button>
                @if (canAdvance(d.stage)) {
                  <button mat-menu-item (click)="onAdvance(d)" [disabled]="actionLoading()">
                    <mat-icon>arrow_forward</mat-icon><span>Advance Stage</span>
                  </button>
                }
                @if (canClose(d.stage)) {
                  <button mat-menu-item (click)="onWin(d)" [disabled]="actionLoading()">
                    <mat-icon>emoji_events</mat-icon><span>Mark Won</span>
                  </button>
                  <button mat-menu-item (click)="onLose(d)" [disabled]="actionLoading()">
                    <mat-icon>cancel</mat-icon><span>Mark Lost</span>
                  </button>
                }
                <button mat-menu-item (click)="onDelete(d)" [disabled]="actionLoading()">
                  <mat-icon>delete</mat-icon><span>Delete</span>
                </button>
              </mat-menu>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns"
              class="clickable-row" (click)="onRowClick(row)"></tr>
        </table>
        <mat-paginator
          [length]="service.pagination().totalElements"
          [pageSize]="service.pagination().size"
          [pageIndex]="service.pagination().page"
          [pageSizeOptions]="[10, 20, 50]"
          (page)="onPageChange($event)"
          showFirstLastButtons>
        </mat-paginator>
      </div>
    }
  `,
  styles: `
    .stage-tabs { margin-bottom: 16px; }
    .deal-filters {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin: 0 0 16px;
    }
    .deal-filters label {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: 0.75rem;
      color: #555;
    }
    .deal-filters input {
      min-width: 180px;
      padding: 8px;
      border: 1px solid #ccc;
      border-radius: 4px;
      font-size: 0.95rem;
    }
    .table-container { background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); overflow: hidden; }
    table { width: 100%; }
    .link { color: #1976d2; cursor: pointer; font-weight: 500; }
    .link:hover { text-decoration: underline; }
    .clickable-row { cursor: pointer; }
    .clickable-row:hover { background: #f5f5f5; }
    .stage-badge { color: white; padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 500; }
    .loading { display: flex; justify-content: center; padding: 48px; }
    .error-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 16px;
      padding: 12px 16px;
      border-radius: 8px;
      background: #ffebee;
      color: #c62828;
    }
    @media (max-width: 768px) {
      .error-banner { margin: 0 0 16px; }
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

  displayedColumns = [
    'name', 'organization', 'owner', 'amount', 'stage',
    'probability', 'expectedCloseDate', 'contact', 'actions',
  ];
  stages: readonly DealStage[] = DEAL_STAGE_ORDER;
  private currentSort = 'createdAt,desc';

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

  trackById(index: number, item: DealDto): string | number {
    return item.id;
  }

  ngOnInit(): void {
    this.reload(0);
  }

  canAdvance(stage: DealStage): boolean {
    return canAdvanceDeal(stage);
  }

  canClose(stage: DealStage): boolean {
    return canCloseDeal(stage);
  }

  stageColor(stage: DealStage): string {
    return dealStageColor(stage);
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

  onSort(s: Sort): void {
    this.currentSort = s.active && s.direction ? `${s.active},${s.direction}` : 'createdAt,desc';
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

  onRowClick(d: DealDto): void {
    this.router.navigate(['/deals', d.id]);
  }

  onEdit(d: DealDto): void {
    this.router.navigate(['/deals', d.id, 'edit']);
  }

  onAdvance(d: DealDto): void {
    this.actionLoading.set(true);
    this.service.advanceStage(d.id).subscribe({
      next: (updated) => this.commitStage(updated),
      error: (err) => this.failStage(err, 'Could not advance this opportunity'),
    });
  }

  onWin(d: DealDto): void {
    if (!confirm(`Mark "${d.name}" as Won?`)) return;
    this.actionLoading.set(true);
    this.service.markWon(d.id).subscribe({
      next: (updated) => this.commitStage(updated),
      error: (err) => this.failStage(err, 'Could not mark this opportunity as Won'),
    });
  }

  onLose(d: DealDto): void {
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

  onDelete(d: DealDto): void {
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
