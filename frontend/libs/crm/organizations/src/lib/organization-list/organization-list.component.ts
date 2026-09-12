import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { OrganizationService } from '../organization.service';
import { OrganizationDto } from '@frontend/shared-models';
import {
  EmptyStateComponent,
  PageHeaderComponent,
  SearchInputComponent,
} from '@frontend/shared-ui';

@Component({
  selector: 'app-organization-list',
  standalone: true,
  imports: [
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    PageHeaderComponent,
    SearchInputComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Organizations"
      subtitle="Manage your business relationships"
    >
      <div actions>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon>
          New Organization
        </button>
      </div>
      <div search>
        <app-search-input
          placeholder="Search organizations..."
          (searchChange)="onSearch($event)"
        />
      </div>
    </app-page-header>

    @if (orgService.error()) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ orgService.error() }}</span>
        <button mat-button type="button" (click)="onRetry()">Retry</button>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
      </div>
    }

    @if (orgService.loading()) {
      <div class="loading-overlay" role="status" aria-live="polite">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    }

    @if (!orgService.loading() && !orgService.error() && orgService.isEmpty()) {
      <app-empty-state
        icon="business"
        title="No organizations yet"
        message="Create your first organization to get started."
        actionLabel="Create Organization"
        (action)="onCreate()"
      />
    } @else if (!orgService.loading() && !orgService.isEmpty()) {
      <div class="table-container">
        <table mat-table [dataSource]="orgService.organizations()" [trackBy]="trackById" matSort
               (matSortChange)="onSort($event)">

          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Name</th>
            <td mat-cell *matCellDef="let org">
              <a class="link" (click)="onRowClick(org)">{{ org.name }}</a>
            </td>
          </ng-container>

          <ng-container matColumnDef="industry">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Industry</th>
            <td mat-cell *matCellDef="let org">{{ org.industry || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="city">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>City</th>
            <td mat-cell *matCellDef="let org">{{ org.city || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="phone">
            <th mat-header-cell *matHeaderCellDef>Phone</th>
            <td mat-cell *matCellDef="let org">{{ org.phone || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let org">
              <button mat-icon-button [matMenuTriggerFor]="menu">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item (click)="onEdit(org)">
                  <mat-icon>edit</mat-icon>
                  <span>Edit</span>
                </button>
                <button mat-menu-item (click)="onDelete(org)" class="delete-action">
                  <mat-icon>delete</mat-icon>
                  <span>Delete</span>
                </button>
              </mat-menu>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns"
              class="clickable-row" (click)="onRowClick(row)"></tr>
        </table>

        <mat-paginator
          [length]="orgService.pagination().totalElements"
          [pageSize]="orgService.pagination().size"
          [pageIndex]="orgService.pagination().page"
          [pageSizeOptions]="[10, 20, 50]"
          (page)="onPageChange($event)"
          showFirstLastButtons>
        </mat-paginator>
      </div>
    }
  `,
  styles: [`
    .table-container { background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); overflow: hidden; }
    table { width: 100%; }
    .link { color: #1976d2; cursor: pointer; text-decoration: none; font-weight: 500; }
    .link:hover { text-decoration: underline; }
    .clickable-row { cursor: pointer; }
    .clickable-row:hover { background-color: #f5f5f5; }
    .loading-overlay { display: flex; justify-content: center; padding: 48px; }
    .delete-action { color: #d32f2f; }
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
  `],
})
export class OrganizationListComponent implements OnInit {
  readonly orgService = inject(OrganizationService);
  private readonly router = inject(Router);

  displayedColumns = ['name', 'industry', 'city', 'phone', 'actions'];
  private currentSort = 'name,asc';
  private lastSearchQuery = '';

  trackById(index: number, item: OrganizationDto): string | number {
    return item.id;
  }

  ngOnInit(): void {
    this.orgService.list(0, 20, this.currentSort);
  }

  onSearch(query: string): void {
    const trimmed = query.trim();
    this.lastSearchQuery = trimmed;
    if (trimmed) {
      this.orgService.search(trimmed);
    } else {
      this.orgService.list(0, 20, this.currentSort);
    }
  }

  clearError(): void {
    this.orgService.error.set(null);
  }

  onRetry(): void {
    if (this.lastSearchQuery) {
      this.orgService.search(this.lastSearchQuery);
    } else {
      this.orgService.list(
        this.orgService.pagination().page,
        this.orgService.pagination().size,
        this.currentSort,
      );
    }
  }

  onPageChange(event: PageEvent): void {
    this.orgService.list(event.pageIndex, event.pageSize, this.currentSort);
  }

  onSort(sort: Sort): void {
    this.currentSort = sort.active && sort.direction
      ? `${sort.active},${sort.direction}`
      : 'name,asc';
    this.orgService.list(0, this.orgService.pagination().size, this.currentSort);
  }

  onCreate(): void {
    this.router.navigate(['/organizations', 'new']);
  }

  onRowClick(org: OrganizationDto): void {
    this.router.navigate(['/organizations', org.id]);
  }

  onEdit(org: OrganizationDto): void {
    this.router.navigate(['/organizations', org.id, 'edit']);
  }

  onDelete(org: OrganizationDto): void {
    if (confirm(`Delete "${org.name}"? This action cannot be undone.`)) {
      this.orgService.delete(org.id).subscribe(() => {
        this.orgService.list(
          this.orgService.pagination().page,
          this.orgService.pagination().size,
          this.currentSort
        );
      });
    }
  }
}
