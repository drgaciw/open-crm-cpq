import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { ContactService } from '../contact.service';
import { ContactDto } from '@frontend/shared-models';
import {
  ConfirmDialogComponent,
  EmptyStateComponent,
  PageHeaderComponent,
  SearchInputComponent,
} from '@frontend/shared-ui';

@Component({
  selector: 'app-contact-list',
  standalone: true,
  imports: [
    MatTableModule, MatPaginatorModule, MatSortModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, MatMenuModule,
    PageHeaderComponent, SearchInputComponent, EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Contacts"
      subtitle="Manage your contact database"
    >
      <div actions>
        <button mat-raised-button color="primary" (click)="onCreate()">
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

    @if (service.loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    }

    @if (!service.loading() && service.isEmpty()) {
      <app-empty-state
        icon="people"
        title="No contacts yet"
        message="Add your first contact to get started."
        actionLabel="Create Contact"
        (action)="onCreate()"
      />
    } @else if (!service.loading()) {
      <div class="table-container">
        <table mat-table [dataSource]="service.contacts()" [trackBy]="trackById" matSort (matSortChange)="onSort($event)">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef mat-sort-header="lastName">Name</th>
            <td mat-cell *matCellDef="let c">
              <a class="link" (click)="onRowClick(c)">{{ c.firstName }} {{ c.lastName }}</a>
            </td>
          </ng-container>
          <ng-container matColumnDef="email">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Email</th>
            <td mat-cell *matCellDef="let c">{{ c.email }}</td>
          </ng-container>
          <ng-container matColumnDef="phone">
            <th mat-header-cell *matHeaderCellDef>Phone</th>
            <td mat-cell *matCellDef="let c">{{ c.phone || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="organization">
            <th mat-header-cell *matHeaderCellDef>Organization</th>
            <td mat-cell *matCellDef="let c">{{ c.organizationName || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="title">
            <th mat-header-cell *matHeaderCellDef>Title</th>
            <td mat-cell *matCellDef="let c">{{ c.jobTitle || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let c">
              <button mat-icon-button [matMenuTriggerFor]="menu">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item (click)="onEdit(c)"><mat-icon>edit</mat-icon><span>Edit</span></button>
                <button mat-menu-item (click)="onDelete(c)"><mat-icon>delete</mat-icon><span>Delete</span></button>
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
    .table-container { background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); overflow: hidden; }
    table { width: 100%; }
    .link { color: #1976d2; cursor: pointer; font-weight: 500; }
    .link:hover { text-decoration: underline; }
    .clickable-row { cursor: pointer; }
    .clickable-row:hover { background: #f5f5f5; }
    .loading { display: flex; justify-content: center; padding: 48px; }
  `],
})
export class ContactListComponent implements OnInit {
  readonly service = inject(ContactService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  displayedColumns = ['name', 'email', 'phone', 'organization', 'title', 'actions'];
  private currentSort = 'lastName,asc';

  trackById(index: number, item: ContactDto): string | number {
    return item.id;
  }

  ngOnInit(): void { this.service.list(0, 20, this.currentSort); }

  onSearch(q: string): void {
    if (q.trim()) {
      this.service.search(q.trim());
    } else {
      this.service.list(0, 20, this.currentSort);
    }
  }

  onPageChange(e: PageEvent): void { this.service.list(e.pageIndex, e.pageSize, this.currentSort); }
  onSort(s: Sort): void {
    this.currentSort = s.active && s.direction ? `${s.active},${s.direction}` : 'lastName,asc';
    this.service.list(0, this.service.pagination().size, this.currentSort);
  }
  onCreate(): void { this.router.navigate(['/contacts', 'new']); }
  onRowClick(c: ContactDto): void { this.router.navigate(['/contacts', c.id]); }
  onEdit(c: ContactDto): void { this.router.navigate(['/contacts', c.id, 'edit']); }
  onDelete(c: ContactDto): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete contact',
        message: `Delete "${c.firstName} ${c.lastName}"?`,
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
}
