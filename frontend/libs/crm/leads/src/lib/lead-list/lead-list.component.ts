import { Component, inject, OnInit, ChangeDetectionStrategy, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatMenuModule } from '@angular/material/menu';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog } from '@angular/material/dialog';
import { LeadService } from '../lead.service';
import { LeadDto, LeadPriority, LeadStatus } from '@frontend/shared-models';
import { CurrencyPipe } from '@angular/common';
import {
  ConfirmDialogComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SearchInputComponent,
  StatusBadgeComponent,
} from '@frontend/shared-ui';
import { LoseReasonDialogComponent } from '../lead-detail/lose-reason-dialog.component';

@Component({
  selector: 'lib-lead-list',
  standalone: true,
  imports: [
    MatTableModule, MatPaginatorModule, MatSortModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, MatFormFieldModule,
    MatMenuModule, MatTabsModule, MatSelectModule, CurrencyPipe,
    PageHeaderComponent, SearchInputComponent, EmptyStateComponent, StatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Leads"
      subtitle="Manage your lead pipeline"
    >
      <div actions>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon> New Lead
        </button>
      </div>
      <div search class="list-filters">
        <app-search-input
          placeholder="Search leads..."
          (searchChange)="onSearch($event)"
        />
        <mat-form-field appearance="outline" class="priority-field">
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

    @if (service.loading()) {
      <div class="loading" role="status" aria-live="polite"><mat-spinner diameter="40"></mat-spinner></div>
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
    } @else if (!service.loading() && !service.error()) {
      <div class="table-container">
        <table mat-table [dataSource]="service.leads()" [trackBy]="trackById" matSort (matSortChange)="onSort($event)">
          <ng-container matColumnDef="title">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Title</th>
            <td mat-cell *matCellDef="let lead">
              <a class="link" (click)="onRowClick(lead)" (keyup.enter)="onRowClick(lead)" tabindex="0">{{ lead.title }}</a>
            </td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Status</th>
            <td mat-cell *matCellDef="let lead">
              <app-status-badge [status]="lead.status" type="lead" />
            </td>
          </ng-container>

          <ng-container matColumnDef="priority">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Priority</th>
            <td mat-cell *matCellDef="let lead">{{ lead.priority || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="source">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Source</th>
            <td mat-cell *matCellDef="let lead">{{ lead.source || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="estimatedValue">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Estimated Value</th>
            <td mat-cell *matCellDef="let lead">
              {{ lead.estimatedValue ? (lead.estimatedValue | currency) : '—' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="contact">
            <th mat-header-cell *matHeaderCellDef>Contact</th>
            <td mat-cell *matCellDef="let lead">
              {{ lead.contactName || '—' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="organization">
            <th mat-header-cell *matHeaderCellDef>Organization</th>
            <td mat-cell *matCellDef="let lead">{{ lead.organizationName || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let lead" (click)="$event.stopPropagation()">
              <button mat-icon-button [matMenuTriggerFor]="menu" [disabled]="actionLoading()"
                      [attr.aria-label]="'Actions for ' + lead.title">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                @if (canEdit(lead.status)) {
                  <button mat-menu-item (click)="onEdit(lead)">
                    <mat-icon>edit</mat-icon><span>Edit</span>
                  </button>
                }
                @if (lead.status === 'NEW') {
                  <button mat-menu-item (click)="onMarkContacted(lead)">
                    <mat-icon>phone</mat-icon><span>Mark Contacted</span>
                  </button>
                }
                @if (lead.status === 'CONTACTED') {
                  <button mat-menu-item (click)="onQualify(lead)">
                    <mat-icon>check_circle</mat-icon><span>Qualify</span>
                  </button>
                }
                @if (lead.status === 'QUALIFIED') {
                  <button mat-menu-item (click)="onConvert(lead)">
                    <mat-icon>arrow_forward</mat-icon><span>Convert to Deal</span>
                  </button>
                }
                @if (canMarkLost(lead.status)) {
                  <button mat-menu-item (click)="onMarkLost(lead)">
                    <mat-icon>close</mat-icon><span>Mark Lost</span>
                  </button>
                }
                @if (canDelete(lead.status)) {
                  <button mat-menu-item (click)="onDelete(lead)">
                    <mat-icon>delete</mat-icon><span>Delete</span>
                  </button>
                }
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
  styles: [`
    .list-filters { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-start; width: 100%; }
    .priority-field { width: 160px; }
    .priority-field .mat-mdc-form-field-subscript-wrapper { display: none; }
    .status-tabs { margin-bottom: 24px; }
    .table-container { background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); overflow: hidden; }
    table { width: 100%; }
    .link { color: #1976d2; cursor: pointer; font-weight: 500; }
    .link:hover { text-decoration: underline; }
    .clickable-row { cursor: pointer; }
    .clickable-row:hover { background: #f5f5f5; }
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
      .list-filters { flex-direction: column; }
      .priority-field { width: 100%; }
    }
  `],
})
export class LeadListComponent implements OnInit {
  readonly service = inject(LeadService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly priorities: LeadPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
  readonly actionLoading = signal(false);
  readonly currentPriority = signal<LeadPriority | undefined>(undefined);

  displayedColumns = ['title', 'status', 'priority', 'source', 'estimatedValue', 'contact', 'organization', 'actions'];
  selectedTabIndex = 0;
  private currentSort = 'createdAt,desc';
  private currentStatus?: LeadStatus;

  /** Tab order: All, New, Contacted, Qualified, Unqualified, Converted, Lost */
  private readonly statusMap: (LeadStatus | undefined)[] = [
    undefined, 'NEW', 'CONTACTED', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED', 'LOST',
  ];

  trackById(index: number, item: LeadDto): string | number {
    return item.id;
  }

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

  onSort(s: Sort): void {
    this.currentSort = s.active && s.direction ? `${s.active},${s.direction}` : 'createdAt,desc';
    this.refreshList(0, this.service.pagination().size);
  }

  onCreate(): void {
    this.router.navigate(['/leads', 'new']);
  }

  onRowClick(lead: LeadDto): void {
    this.router.navigate(['/leads', lead.id]);
  }

  onEdit(lead: LeadDto): void {
    this.router.navigate(['/leads', lead.id, 'edit']);
  }

  onMarkContacted(lead: LeadDto): void {
    this.actionLoading.set(true);
    this.service.markContacted(lead.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.refreshList(this.service.pagination().page, this.service.pagination().size);
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onQualify(lead: LeadDto): void {
    this.actionLoading.set(true);
    this.service.qualify(lead.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.refreshList(this.service.pagination().page, this.service.pagination().size);
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onConvert(lead: LeadDto): void {
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

  onMarkLost(lead: LeadDto): void {
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

  onDelete(lead: LeadDto): void {
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

  private refreshList(page: number, size: number): void {
    this.service.list(page, size, this.currentSort, this.currentStatus, this.currentPriority());
  }
}
