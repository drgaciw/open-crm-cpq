import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatTabsModule } from '@angular/material/tabs';
import { MatChipsModule } from '@angular/material/chips';
import { LeadService } from '../lead.service';
import { LeadDto, LeadStatus } from '@frontend/shared-models';
import { CurrencyPipe } from '@angular/common';

@Component({
  selector: 'lib-lead-list',
  standalone: true,
  imports: [
    MatTableModule, MatPaginatorModule, MatSortModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, MatFormFieldModule,
    MatInputModule, MatMenuModule, MatTabsModule, MatChipsModule, CurrencyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="header-left">
        <h1>Leads</h1>
        <p class="subtitle">Manage your lead pipeline</p>
      </div>
      <div class="header-actions">
        <mat-form-field appearance="outline" class="search-field">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Search leads..." #searchInput
                 (input)="onSearch(searchInput.value)"
                 aria-label="Search leads">
        </mat-form-field>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon> New Lead
        </button>
      </div>
    </div>

    <mat-tab-group [(selectedIndex)]="selectedTabIndex" (selectedIndexChange)="onTabChange($event)" class="status-tabs">
      <mat-tab label="All"></mat-tab>
      <mat-tab label="New"></mat-tab>
      <mat-tab label="Contacted"></mat-tab>
      <mat-tab label="Qualified"></mat-tab>
      <mat-tab label="Converted"></mat-tab>
      <mat-tab label="Lost"></mat-tab>
    </mat-tab-group>

    @if (service.loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    }

    @if (!service.loading() && service.isEmpty()) {
      <div class="empty-state">
        <mat-icon class="empty-icon">trending_up</mat-icon>
        <h3>No leads yet</h3>
        <p>Add your first lead to start tracking opportunities.</p>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon> Create Lead
        </button>
      </div>
    } @else {
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
              <mat-chip [class]="getStatusClass(lead.status)">{{ lead.status }}</mat-chip>
            </td>
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
              <button mat-icon-button [matMenuTriggerFor]="menu">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item (click)="onEdit(lead)">
                  <mat-icon>edit</mat-icon><span>Edit</span>
                </button>
                <button mat-menu-item (click)="onDelete(lead)">
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
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .header-left h1 { margin: 0; font-size: 1.75rem; font-weight: 600; }
    .subtitle { margin: 4px 0 0; color: #666; }
    .header-actions { display: flex; gap: 12px; align-items: center; }
    .search-field { width: 280px; }
    .search-field .mat-mdc-form-field-subscript-wrapper { display: none; }
    .status-tabs { margin-bottom: 24px; }
    .table-container { background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); overflow: hidden; }
    table { width: 100%; }
    .link { color: #1976d2; cursor: pointer; font-weight: 500; }
    .link:hover { text-decoration: underline; }
    .clickable-row { cursor: pointer; }
    .clickable-row:hover { background: #f5f5f5; }
    .loading { display: flex; justify-content: center; padding: 48px; }
    .empty-state { display: flex; flex-direction: column; align-items: center; padding: 64px 24px; text-align: center; background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); }
    .empty-icon { font-size: 64px; width: 64px; height: 64px; color: #ccc; margin-bottom: 16px; }
    .empty-state h3 { margin: 0 0 8px; }
    .empty-state p { color: #666; margin: 0 0 24px; }

    mat-chip { font-size: 0.75rem; font-weight: 500; min-height: 24px; padding: 4px 8px; }
    .status-new { background-color: #2196f3 !important; color: white !important; }
    .status-contacted { background-color: #ff9800 !important; color: white !important; }
    .status-qualified { background-color: #4caf50 !important; color: white !important; }
    .status-converted { background-color: #9c27b0 !important; color: white !important; }
    .status-lost { background-color: #f44336 !important; color: white !important; }
  `],
})
export class LeadListComponent implements OnInit {
  readonly service = inject(LeadService);
  private readonly router = inject(Router);

  displayedColumns = ['title', 'status', 'source', 'estimatedValue', 'contact', 'organization', 'actions'];
  selectedTabIndex = 0;
  private currentSort = 'createdAt,desc';
  private currentStatus?: LeadStatus;
  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  trackById(index: number, item: LeadDto): string | number {
    return item.id;
  }

  ngOnInit(): void {
    this.service.list(0, 20, this.currentSort);
  }

  
  onTabChange(index: number): void {
    const statusMap: (LeadStatus | undefined)[] = [
      undefined, 'NEW', 'CONTACTED', 'QUALIFIED', 'CONVERTED', 'LOST'
    ];
    this.currentStatus = statusMap[index];
    this.service.list(0, 20, this.currentSort, this.currentStatus);
  }

  onSearch(q: string): void {
    if (this.searchTimeout) clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => {
      if (q.trim()) { this.service.search(q.trim()); } else { this.service.list(0, 20, this.currentSort, this.currentStatus); }
    }, 300);
  }

  onPageChange(e: PageEvent): void {
    this.service.list(e.pageIndex, e.pageSize, this.currentSort, this.currentStatus);
  }

  onSort(s: Sort): void {
    this.currentSort = s.active && s.direction ? `${s.active},${s.direction}` : 'createdAt,desc';
    this.service.list(0, this.service.pagination().size, this.currentSort, this.currentStatus);
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

  onDelete(lead: LeadDto): void {
    if (confirm(`Delete lead "${lead.title}"?`)) {
      this.service.delete(lead.id).subscribe(() => {
        this.service.list(this.service.pagination().page, this.service.pagination().size, this.currentSort, this.currentStatus);
      });
    }
  }

  getStatusClass(status: LeadStatus): string {
    return `status-${status.toLowerCase()}`;
  }
}
