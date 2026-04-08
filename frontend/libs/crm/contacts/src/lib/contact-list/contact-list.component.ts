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
import { ContactService } from '../contact.service';
import { ContactDto } from '@frontend/shared-models';

@Component({
  selector: 'app-contact-list',
  standalone: true,
  imports: [
    MatTableModule, MatPaginatorModule, MatSortModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, MatFormFieldModule,
    MatInputModule, MatMenuModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="header-left">
        <h1>Contacts</h1>
        <p class="subtitle">Manage your contact database</p>
      </div>
      <div class="header-actions">
        <mat-form-field appearance="outline" class="search-field">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Search contacts..." #searchInput
                 (input)="onSearch(searchInput.value)"
                 aria-label="Search contacts">
        </mat-form-field>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon> New Contact
        </button>
      </div>
    </div>

    @if (service.loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    }

    @if (!service.loading() && service.isEmpty()) {
      <div class="empty-state">
        <mat-icon class="empty-icon">people</mat-icon>
        <h3>No contacts yet</h3>
        <p>Add your first contact to get started.</p>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon> Create Contact
        </button>
      </div>
    } @else {
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
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
    .header-left h1 { margin: 0; font-size: 1.75rem; font-weight: 600; }
    .subtitle { margin: 4px 0 0; color: #666; }
    .header-actions { display: flex; gap: 12px; align-items: center; }
    .search-field { width: 280px; }
    .search-field .mat-mdc-form-field-subscript-wrapper { display: none; }
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
  `],
})
export class ContactListComponent implements OnInit {
  readonly service = inject(ContactService);
  private readonly router = inject(Router);

  displayedColumns = ['name', 'email', 'phone', 'organization', 'title', 'actions'];
  private currentSort = 'lastName,asc';
  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  trackById(index: number, item: ContactDto): string | number {
    return item.id;
  }

  ngOnInit(): void { this.service.list(0, 20, this.currentSort); }

  onSearch(q: string): void {
    if (this.searchTimeout) clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => {
      q.trim() ? this.service.search(q.trim()) : this.service.list(0, 20, this.currentSort);
    }, 300);
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
    if (confirm(`Delete "${c.firstName} ${c.lastName}"?`)) {
      this.service.delete(c.id).subscribe(() => {
        this.service.list(this.service.pagination().page, this.service.pagination().size, this.currentSort);
      });
    }
  }
}
