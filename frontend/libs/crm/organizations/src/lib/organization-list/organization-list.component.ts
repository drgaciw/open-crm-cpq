import { Component, inject, OnInit, ChangeDetectionStrategy, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { OrganizationService } from '../organization.service';
import { ACCOUNT_INDUSTRIES } from '../account-industries';
import { OrganizationDto } from '@frontend/shared-models';
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

export interface OrganizationTableRow {
  id: number;
  name: string;
  industry: string;
  territory: string;
  owner: string;
  ownerId: string;
  city: string;
  phone: string;
}

@Component({
  selector: 'app-organization-list',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    PageHeaderComponent,
    SearchInputComponent,
    EmptyStateComponent,
    DataTableComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Accounts"
      subtitle="Manage customer accounts"
    >
      <div actions>
        <button mat-raised-button color="primary" type="button" (click)="onCreate()">
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

    <div class="account-filters" role="search" aria-label="Account filters">
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Industry</mat-label>
        <mat-select
          data-testid="account-industry-filter"
          [value]="industryFilter()"
          (selectionChange)="onIndustryFilter($event.value)"
        >
          <mat-option value="">All industries</mat-option>
          @for (ind of industries; track ind) {
            <mat-option [value]="ind">{{ ind }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Territory</mat-label>
        <input
          matInput
          data-testid="account-territory-filter"
          [value]="territoryFilter()"
          (input)="onTerritoryFilter($any($event.target).value)"
          placeholder="Territory"
        />
      </mat-form-field>

      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Owner</mat-label>
        <input
          matInput
          data-testid="account-owner-filter"
          [value]="ownerFilter()"
          (input)="onOwnerFilter($any($event.target).value)"
          placeholder="Owner"
        />
      </mat-form-field>
    </div>

    @if (orgService.error()) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ orgService.error() }}</span>
        <button mat-button type="button" (click)="onRetry()">Retry</button>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
      </div>
    }

    @if (!orgService.loading() && !orgService.error() && orgService.isEmpty()) {
      <app-empty-state
        icon="business"
        [title]="hasActiveCriteria() ? 'No matching accounts' : 'No accounts yet'"
        [message]="hasActiveCriteria() ? 'Try changing your search or filters.' : 'Create your first account to get started.'"
        [actionLabel]="hasActiveCriteria() ? '' : 'Create Account'"
        (action)="onCreate()"
      />
    } @else if (!orgService.isEmpty()) {
      <app-data-table
        [columns]="columns"
        [data]="tableRows()"
        [loading]="orgService.loading()"
        [pagination]="orgService.pagination()"
        [rowActions]="rowActions"
        rowActionsAriaLabel="Account actions"
        [pageSizeOptions]="[10, 20, 50]"
        (pageChange)="onPageChange($event)"
        (sortChange)="onSort($event)"
        (rowClick)="onRowClick($event)"
        (rowAction)="onRowAction($event)"
      />
    }
  `,
  styles: [`
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
    .account-filters {
      display: flex;
      flex-wrap: wrap;
      gap: var(--crm-space-md, 16px);
      margin: 0 0 var(--crm-space-md, 16px);
      align-items: flex-start;
    }
    .account-filters mat-form-field {
      min-width: 180px;
      flex: 1 1 180px;
      max-width: 280px;
    }
  `],
})
export class OrganizationListComponent implements OnInit {
  readonly orgService = inject(OrganizationService);
  private readonly router = inject(Router);

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Name', sortable: true },
    { key: 'industry', header: 'Industry', sortable: true },
    { key: 'territory', header: 'Territory', sortable: true },
    { key: 'owner', header: 'Owner', sortable: true },
    { key: 'city', header: 'City', sortable: true },
    { key: 'phone', header: 'Phone', sortable: false },
  ];

  readonly rowActions: TableRowAction[] = [
    { action: 'edit', label: 'Edit', icon: 'edit' },
    { action: 'archive', label: 'Archive', icon: 'archive', danger: true },
  ];

  readonly industries = ACCOUNT_INDUSTRIES;
  readonly industryFilter = signal('');
  readonly territoryFilter = signal('');
  readonly ownerFilter = signal('');
  readonly searchQuery = signal('');
  readonly hasActiveCriteria = computed(
    () =>
      !!(
        this.searchQuery() ||
        this.industryFilter().trim() ||
        this.territoryFilter().trim() ||
        this.ownerFilter().trim()
      ),
  );

  readonly tableRows = computed<OrganizationTableRow[]>(() =>
    this.orgService.organizations().map((org) => this.toRow(org)),
  );

  private currentSort = 'name,asc';

  ngOnInit(): void {
    this.orgService.list(0, 20, this.currentSort);
  }

  onSearch(query: string): void {
    this.searchQuery.set(query.trim());
    this.reload(0);
  }

  clearError(): void {
    this.orgService.error.set(null);
  }

  onIndustryFilter(value: string): void {
    this.industryFilter.set(value ?? '');
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

  onSort(sort: SortChangeEvent): void {
    const field = sort.active === 'owner' ? 'ownerId' : sort.active;
    this.currentSort = field && sort.direction ? `${field},${sort.direction}` : 'name,asc';
    this.reload(0);
  }

  onCreate(): void {
    this.router.navigate(['/organizations', 'new']);
  }

  onRowClick(row: OrganizationTableRow): void {
    this.router.navigate(['/organizations', row.id]);
  }

  onEdit(row: OrganizationTableRow): void {
    this.router.navigate(['/organizations', row.id, 'edit']);
  }

  onRowAction(event: RowActionEvent<OrganizationTableRow>): void {
    if (event.action === 'edit') {
      this.onEdit(event.row);
      return;
    }
    if (event.action === 'archive') {
      this.onDelete(event.row);
    }
  }

  onDelete(org: OrganizationTableRow | OrganizationDto): void {
    const name = org.name;
    if (confirm(`Archive "${name}"? The account will be hidden from lists.`)) {
      this.orgService.delete(org.id).subscribe(() => {
        this.reload(this.orgService.pagination().page);
      });
    }
  }

  private toRow(org: OrganizationDto): OrganizationTableRow {
    return {
      id: org.id,
      name: org.name,
      industry: org.industry || '—',
      territory: org.territory || '—',
      owner: org.ownerId || '—',
      ownerId: org.ownerId || '',
      city: org.city || '—',
      phone: org.phone || '—',
    };
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
    if (this.searchQuery()) {
      if (filters) {
        this.orgService.search(this.searchQuery(), page, size, this.currentSort, filters);
      } else {
        this.orgService.search(this.searchQuery(), page, size, this.currentSort);
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
