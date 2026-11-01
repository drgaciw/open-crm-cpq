import { Component, inject, OnInit, ChangeDetectionStrategy, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { OrganizationService } from '../organization.service';
import { ACCOUNT_INDUSTRIES } from '../account-industries';
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
      title="Accounts"
      subtitle="Manage customer accounts"
    >
      <div actions>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon>
          New Account
        </button>
      </div>
      <div search>
        <app-search-input
          placeholder="Search accounts..."
          (searchChange)="onSearch($event)"
        />
      </div>
    </app-page-header>

    <div class="account-filters">
      <label>
        Industry
        <select
          data-testid="account-industry-filter"
          [value]="industryFilter()"
          (change)="onIndustryFilter($any($event.target).value)"
        >
          <option value="">All industries</option>
          @for (ind of industries; track ind) {
            <option [value]="ind">{{ ind }}</option>
          }
        </select>
      </label>
      <label>
        Territory
        <input
          data-testid="account-territory-filter"
          type="text"
          [value]="territoryFilter()"
          (input)="onTerritoryFilter($any($event.target).value)"
          placeholder="Territory"
        />
      </label>
      <label>
        Owner
        <input
          data-testid="account-owner-filter"
          type="text"
          [value]="ownerFilter()"
          (input)="onOwnerFilter($any($event.target).value)"
          placeholder="Owner"
        />
      </label>
    </div>

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
        title="No accounts yet"
        message="Create your first account to get started."
        actionLabel="Create Account"
        (action)="onCreate()"
      />
    } @else if (!orgService.loading() && !orgService.isEmpty()) {
      <div class="table-container">
        <table mat-table [dataSource]="visibleOrganizations()" [trackBy]="trackById" matSort
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

          <ng-container matColumnDef="territory">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Territory</th>
            <td mat-cell *matCellDef="let org">{{ org.territory || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="owner">
            <th mat-header-cell *matHeaderCellDef mat-sort-header="ownerId">Owner</th>
            <td mat-cell *matCellDef="let org">{{ org.ownerId || '—' }}</td>
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
            <td mat-cell *matCellDef="let org" (click)="$event.stopPropagation()">
              <button mat-icon-button [matMenuTriggerFor]="menu" aria-label="Account actions">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item (click)="onEdit(org)">
                  <mat-icon>edit</mat-icon>
                  <span>Edit</span>
                </button>
                <button mat-menu-item (click)="onDelete(org)" class="delete-action">
                  <mat-icon>archive</mat-icon>
                  <span>Archive</span>
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
    .account-filters {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin: 0 0 16px;
    }
    .account-filters label {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: 0.75rem;
      color: #555;
    }
    .account-filters select,
    .account-filters input {
      min-width: 180px;
      padding: 8px;
      border: 1px solid #ccc;
      border-radius: 4px;
      font-size: 0.95rem;
    }
  `],
})
export class OrganizationListComponent implements OnInit {
  readonly orgService = inject(OrganizationService);
  private readonly router = inject(Router);

  displayedColumns = ['name', 'industry', 'territory', 'owner', 'city', 'phone', 'actions'];
  readonly industries = ACCOUNT_INDUSTRIES;
  readonly industryFilter = signal('');
  readonly territoryFilter = signal('');
  readonly ownerFilter = signal('');
  readonly visibleOrganizations = computed(() => {
    const industry = this.industryFilter().trim().toLowerCase();
    const territory = this.territoryFilter().trim().toLowerCase();
    const owner = this.ownerFilter().trim().toLowerCase();
    return this.orgService.organizations().filter((org) => {
      if (industry && (org.industry ?? '').toLowerCase() !== industry) {
        return false;
      }
      if (territory && !(org.territory ?? '').toLowerCase().includes(territory)) {
        return false;
      }
      if (owner && !(org.ownerId ?? '').toLowerCase().includes(owner)) {
        return false;
      }
      return true;
    });
  });
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
      const filters = this.currentFilters();
      if (filters) {
        this.orgService.search(trimmed, 0, this.orgService.pagination().size, this.currentSort, filters);
      } else {
        this.orgService.search(trimmed);
      }
    } else {
      this.reload(0);
    }
  }

  clearError(): void {
    this.orgService.error.set(null);
  }

  onIndustryFilter(value: string): void {
    this.industryFilter.set(value);
    this.reload(0);
  }

  onTerritoryFilter(value: string): void {
    this.territoryFilter.set(value);
    this.reload(0);
  }

  onOwnerFilter(value: string): void {
    this.ownerFilter.set(value);
    this.reload(0);
  }

  onRetry(): void {
    this.reload(this.orgService.pagination().page);
  }

  onPageChange(event: PageEvent): void {
    this.reload(event.pageIndex, event.pageSize);
  }

  onSort(sort: Sort): void {
    this.currentSort = sort.active && sort.direction
      ? `${sort.active},${sort.direction}`
      : 'name,asc';
    this.reload(0);
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
    if (confirm(`Archive "${org.name}"? The account will be hidden from lists.`)) {
      this.orgService.delete(org.id).subscribe(() => {
        this.reload(this.orgService.pagination().page);
      });
    }
  }

  private currentFilters():
    | { industry: string; territory: string; ownerId: string }
    | undefined {
    const industry = this.industryFilter().trim();
    const territory = this.territoryFilter().trim();
    const ownerId = this.ownerFilter().trim();
    if (!industry && !territory && !ownerId) {
      return undefined;
    }
    return { industry, territory, ownerId };
  }

  private reload(page: number, size = this.orgService.pagination().size): void {
    const filters = this.currentFilters();
    if (this.lastSearchQuery) {
      if (filters) {
        this.orgService.search(this.lastSearchQuery, page, size, this.currentSort, filters);
      } else {
        this.orgService.search(this.lastSearchQuery, page, size, this.currentSort);
      }
      return;
    }
    if (filters) {
      this.orgService.list(page, size, this.currentSort, filters);
      return;
    }
    this.orgService.list(page, size, this.currentSort);
  }
}
