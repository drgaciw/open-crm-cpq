import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
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
import { DealDto, DealStage, STAGE_COLORS, STAGE_LABELS } from '@frontend/shared-models';
import {
  EmptyStateComponent,
  PageHeaderComponent,
  SearchInputComponent,
} from '@frontend/shared-ui';
import { DealLoseReasonDialogComponent } from '../lose-reason-dialog/lose-reason-dialog.component';

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
      title="Deals"
      subtitle="Track your sales pipeline"
    >
      <div actions>
        <button mat-stroked-button (click)="router.navigate(['/deals/kanban'])">
          <mat-icon>view_kanban</mat-icon> Kanban
        </button>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon> New Deal
        </button>
      </div>
      <div search>
        <app-search-input
          placeholder="Search deals..."
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

    @if (!service.loading() && !service.error() && service.isEmpty()) {
      <app-empty-state
        icon="handshake"
        [title]="hasActiveFilters() ? 'No matching deals' : 'No deals yet'"
        [message]="hasActiveFilters()
          ? 'Try another stage filter, or clear filters to see all deals.'
          : 'Create your first deal or convert a lead.'"
        [actionLabel]="hasActiveFilters() ? '' : 'Create Deal'"
        (action)="onCreate()"
      />
    } @else if (!service.loading() && !service.isEmpty()) {
      <div class="table-container">
        <table mat-table [dataSource]="service.deals()" [trackBy]="trackById" matSort (matSortChange)="onSort($event)">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Deal Name</th>
            <td mat-cell *matCellDef="let d">
              <a class="link" (click)="onRowClick(d)">{{ d.name }}</a>
            </td>
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

  displayedColumns = ['name', 'amount', 'stage', 'probability', 'expectedCloseDate', 'contact', 'actions'];
  stages: DealStage[] = ['PROSPECTING', 'QUALIFICATION', 'NEEDS_ANALYSIS', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'];
  private currentSort = 'createdAt,desc';
  private currentStage?: DealStage;
  private lastSearchQuery = '';

  trackById(index: number, item: DealDto): string | number {
    return item.id;
  }

  ngOnInit(): void {
    this.service.list(0, 20, this.currentSort);
  }

  /** API advances one step; NEGOTIATION must use win/lose. */
  canAdvance(stage: DealStage): boolean {
    return stage !== 'WON' && stage !== 'LOST' && stage !== 'NEGOTIATION';
  }

  canClose(stage: DealStage): boolean {
    return stage !== 'WON' && stage !== 'LOST';
  }

  stageColor(stage: string): string {
    return STAGE_COLORS[stage] ?? '#666';
  }

  stageLabel(stage: string): string {
    return STAGE_LABELS[stage] ?? stage;
  }

  onTabChange(index: number): void {
    this.currentStage = index === 0 ? undefined : this.stages[index - 1];
    this.service.list(0, 20, this.currentSort, this.currentStage);
  }

  onSearch(q: string): void {
    const trimmed = q.trim();
    this.lastSearchQuery = trimmed;
    if (trimmed) {
      this.service.search(trimmed);
    } else {
      this.service.list(0, 20, this.currentSort, this.currentStage);
    }
  }

  clearError(): void {
    this.service.error.set(null);
  }

  onRetry(): void {
    if (this.lastSearchQuery) {
      this.service.search(this.lastSearchQuery);
    } else {
      this.service.list(
        this.service.pagination().page,
        this.service.pagination().size,
        this.currentSort,
        this.currentStage,
      );
    }
  }

  onPageChange(e: PageEvent): void {
    this.service.list(e.pageIndex, e.pageSize, this.currentSort, this.currentStage);
  }

  onSort(s: Sort): void {
    this.currentSort = s.active && s.direction ? `${s.active},${s.direction}` : 'createdAt,desc';
    this.service.list(0, this.service.pagination().size, this.currentSort, this.currentStage);
  }

  hasActiveFilters(): boolean {
    return this.currentStage !== undefined;
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
      next: () => {
        this.actionLoading.set(false);
        this.refreshList();
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onWin(d: DealDto): void {
    if (!confirm(`Mark "${d.name}" as Won?`)) return;
    this.actionLoading.set(true);
    this.service.markWon(d.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.refreshList();
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onLose(d: DealDto): void {
    const dialogRef = this.dialog.open(DealLoseReasonDialogComponent, { width: '500px' });
    dialogRef.afterClosed().subscribe((reason: string | undefined) => {
      if (!reason) return;
      this.actionLoading.set(true);
      this.service.markLost(d.id, reason).subscribe({
        next: () => {
          this.actionLoading.set(false);
          this.refreshList();
        },
        error: () => this.actionLoading.set(false),
      });
    });
  }

  onDelete(d: DealDto): void {
    if (!confirm(`Delete "${d.name}"?`)) return;
    this.actionLoading.set(true);
    this.service.delete(d.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.refreshList();
      },
      error: () => this.actionLoading.set(false),
    });
  }

  private refreshList(): void {
    this.service.list(
      this.service.pagination().page,
      this.service.pagination().size,
      this.currentSort,
      this.currentStage,
    );
  }
}
