import { Component, inject, OnInit, ChangeDetectionStrategy, computed } from '@angular/core';
import { Router } from '@angular/router';
import { PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { ContactService } from '../contact.service';
import { ContactDto } from '@frontend/shared-models';
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

/** Flat row for DataTable; keeps identity fields for edit/delete. */
export interface ContactTableRow {
  id: number;
  name: string;
  email: string;
  phone: string;
  organization: string;
  title: string;
  firstName: string;
  lastName: string;
}

@Component({
  selector: 'app-contact-list',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    PageHeaderComponent,
    SearchInputComponent,
    EmptyStateComponent,
    DataTableComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Contacts"
      subtitle="Manage your contact database"
    >
      <div actions>
        <button mat-raised-button color="primary" type="button" (click)="onCreate()">
          <mat-icon>add</mat-icon> New Contact
        </button>
      </div>
      <div search>
        <app-search-input
          placeholder="Search contacts..."
          (searchChange)="onSearch($event)"
        />
      </div>
    </app-page-header>

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
        icon="people"
        title="No contacts yet"
        message="Add your first contact to get started."
        actionLabel="Create Contact"
        (action)="onCreate()"
      />
    } @else if (!service.error() && !service.isEmpty()) {
      <app-data-table
        [columns]="columns"
        [data]="tableRows()"
        [loading]="service.loading()"
        [pagination]="service.pagination()"
        [rowActions]="rowActions"
        rowActionsAriaLabel="Contact actions"
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
  `],
})
export class ContactListComponent implements OnInit {
  readonly service = inject(ContactService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Name', sortable: true },
    { key: 'email', header: 'Email', sortable: true },
    { key: 'phone', header: 'Phone', sortable: false },
    { key: 'organization', header: 'Organization', sortable: false },
    { key: 'title', header: 'Title', sortable: false },
  ];

  readonly rowActions: TableRowAction[] = [
    { action: 'edit', label: 'Edit', icon: 'edit' },
    { action: 'delete', label: 'Delete', icon: 'delete', danger: true },
  ];

  readonly tableRows = computed<ContactTableRow[]>(() =>
    this.service.contacts().map((c) => this.toRow(c)),
  );

  private currentSort = 'lastName,asc';

  ngOnInit(): void {
    this.service.list(0, 20, this.currentSort);
  }

  onSearch(q: string): void {
    if (q.trim()) {
      this.service.search(q.trim());
    } else {
      this.service.list(0, 20, this.currentSort);
    }
  }

  onPageChange(e: PageEvent): void {
    this.service.list(e.pageIndex, e.pageSize, this.currentSort);
  }

  onSort(s: SortChangeEvent): void {
    const field = s.active === 'name' ? 'lastName' : s.active;
    this.currentSort = field && s.direction ? `${field},${s.direction}` : 'lastName,asc';
    this.service.list(0, this.service.pagination().size, this.currentSort);
  }

  onCreate(): void {
    this.router.navigate(['/contacts', 'new']);
  }

  onRowClick(row: ContactTableRow): void {
    this.router.navigate(['/contacts', row.id]);
  }

  onEdit(row: ContactTableRow): void {
    this.router.navigate(['/contacts', row.id, 'edit']);
  }

  onRowAction(event: RowActionEvent<ContactTableRow>): void {
    if (event.action === 'edit') {
      this.onEdit(event.row);
      return;
    }
    if (event.action === 'delete') {
      this.onDelete(event.row);
    }
  }

  clearError(): void {
    this.service.error.set(null);
  }

  retry(): void {
    this.service.list(
      this.service.pagination().page,
      this.service.pagination().size || 20,
      this.currentSort,
    );
  }

  onDelete(c: ContactTableRow | ContactDto): void {
    const firstName = 'firstName' in c ? c.firstName : '';
    const lastName = 'lastName' in c ? c.lastName : '';
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete contact',
        message: `Delete "${firstName} ${lastName}"?`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });

    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) return;
      this.service.delete(c.id).subscribe(() => {
        this.service.list(this.service.pagination().page, this.service.pagination().size, this.currentSort);
      });
    });
  }

  private toRow(c: ContactDto): ContactTableRow {
    return {
      id: c.id,
      name: `${c.firstName} ${c.lastName}`.trim(),
      email: c.email || '—',
      phone: c.phone || '—',
      organization: c.organizationName || '—',
      title: c.jobTitle || '—',
      firstName: c.firstName,
      lastName: c.lastName,
    };
  }
}
