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
import { CurrencyPipe, DatePipe } from '@angular/common';
import { DealService } from '../deal.service';
import { DealDto, DealStage, STAGE_COLORS, STAGE_LABELS } from '@frontend/shared-models';

@Component({
  selector: 'app-deal-list',
  standalone: true,
  imports: [
    MatTableModule, MatPaginatorModule, MatSortModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, MatFormFieldModule, MatInputModule,
    MatMenuModule, MatTabsModule, MatChipsModule, CurrencyPipe, DatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="header-left">
        <h1>Deals</h1>
        <p class="subtitle">Track your sales pipeline</p>
      </div>
      <div class="header-actions">
        <mat-form-field appearance="outline" class="search-field">
          <mat-icon matPrefix>search</mat-icon>
          <input matInput placeholder="Search deals..." #searchInput
                 (input)="onSearch(searchInput.value)"
                 aria-label="Search deals">
        </mat-form-field>
        <button mat-stroked-button (click)="router.navigate(['/deals/kanban'])">
          <mat-icon>view_kanban</mat-icon> Kanban
        </button>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon> New Deal
        </button>
      </div>
    </div>

    <mat-tab-group (selectedTabChange)="onTabChange($event.index)" class="stage-tabs">
      <mat-tab label="All"></mat-tab>
      @for (stage of stages; track stage) {
        <mat-tab [label]="stageLabel(stage)"></mat-tab>
      }
    </mat-tab-group>

    @if (service.loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    }

    @if (!service.loading() && service.isEmpty()) {
      <div class="empty-state">
        <mat-icon class="empty-icon">handshake</mat-icon>
        <h3>No deals yet</h3>
        <p>Create your first deal or convert a lead.</p>
        <button mat-raised-button color="primary" (click)="onCreate()">
          <mat-icon>add</mat-icon> Create Deal
        </button>
      </div>
    } @else {
      <div class="table-container">
        <table mat-table [dataSource]="service.deals()" matSort (matSortChange)="onSort($event)">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Deal Name</th>
            <td mat-cell *matCellDef="let d">
              <a class="link" (click)="onRowClick(d)">{{ d.name }}</a>
            </td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef mat-sort-header>Amount</th>
            <td mat-cell *matCellDef="let d">{{ d.amount | currency:'USD':'symbol':'1.0-0' }}</td>
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
              <button mat-icon-button [matMenuTriggerFor]="menu">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item (click)="onEdit(d)"><mat-icon>edit</mat-icon><span>Edit</span></button>
                @if (d.stage !== 'WON' && d.stage !== 'LOST') {
                  <button mat-menu-item (click)="onAdvance(d)"><mat-icon>arrow_forward</mat-icon><span>Advance Stage</span></button>
                  <button mat-menu-item (click)="onWin(d)"><mat-icon>emoji_events</mat-icon><span>Mark Won</span></button>
                  <button mat-menu-item (click)="onLose(d)"><mat-icon>cancel</mat-icon><span>Mark Lost</span></button>
                }
                <button mat-menu-item (click)="onDelete(d)"><mat-icon>delete</mat-icon><span>Delete</span></button>
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
    .stage-tabs { margin-bottom: 16px; }
    .table-container { background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); overflow: hidden; }
    table { width: 100%; }
    .link { color: #1976d2; cursor: pointer; font-weight: 500; }
    .link:hover { text-decoration: underline; }
    .clickable-row { cursor: pointer; }
    .clickable-row:hover { background: #f5f5f5; }
    .stage-badge { color: white; padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 500; }
    .loading { display: flex; justify-content: center; padding: 48px; }
    .empty-state { display: flex; flex-direction: column; align-items: center; padding: 64px 24px; text-align: center; background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); }
    .empty-icon { font-size: 64px; width: 64px; height: 64px; color: #ccc; margin-bottom: 16px; }
    .empty-state h3 { margin: 0 0 8px; }
    .empty-state p { color: #666; margin: 0 0 24px; }
  `],
})
export class DealListComponent implements OnInit {
  readonly service = inject(DealService);
  readonly router = inject(Router);

  displayedColumns = ['name', 'amount', 'stage', 'probability', 'expectedCloseDate', 'contact', 'actions'];
  stages: DealStage[] = ['PROSPECTING', 'QUALIFICATION', 'NEEDS_ANALYSIS', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'];
  private currentSort = 'createdAt,desc';
  private currentStage?: DealStage;
  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void { this.service.list(0, 20, this.currentSort); }

  stageColor(stage: string): string { return STAGE_COLORS[stage] ?? '#666'; }
  stageLabel(stage: string): string { return STAGE_LABELS[stage] ?? stage; }

  onTabChange(index: number): void {
    this.currentStage = index === 0 ? undefined : this.stages[index - 1];
    this.service.list(0, 20, this.currentSort, this.currentStage);
  }

  onSearch(q: string): void {
    if (this.searchTimeout) clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => {
      q.trim() ? this.service.search(q.trim()) : this.service.list(0, 20, this.currentSort, this.currentStage);
    }, 300);
  }

  onPageChange(e: PageEvent): void { this.service.list(e.pageIndex, e.pageSize, this.currentSort, this.currentStage); }
  onSort(s: Sort): void {
    this.currentSort = s.active && s.direction ? `${s.active},${s.direction}` : 'createdAt,desc';
    this.service.list(0, this.service.pagination().size, this.currentSort, this.currentStage);
  }
  onCreate(): void { this.router.navigate(['/deals', 'new']); }
  onRowClick(d: DealDto): void { this.router.navigate(['/deals', d.id]); }
  onEdit(d: DealDto): void { this.router.navigate(['/deals', d.id, 'edit']); }

  onAdvance(d: DealDto): void {
    this.service.advanceStage(d.id).subscribe({
      next: () => this.refreshList(),
      error: () => {}
    });
  }
  onWin(d: DealDto): void {
    if (confirm(`Mark "${d.name}" as Won?`)) {
      this.service.markWon(d.id).subscribe({
        next: () => this.refreshList(),
        error: () => {}
      });
    }
  }
  onLose(d: DealDto): void {
    const reason = prompt(`Why is "${d.name}" lost?`);
    if (reason) {
      this.service.markLost(d.id, reason).subscribe({
        next: () => this.refreshList(),
        error: () => {}
      });
    }
  }
  onDelete(d: DealDto): void {
    if (confirm(`Delete "${d.name}"?`)) {
      this.service.delete(d.id).subscribe({
        next: () => this.refreshList(),
        error: () => {}
      });
    }
  }

  private refreshList(): void {
    this.service.list(this.service.pagination().page, this.service.pagination().size, this.currentSort, this.currentStage);
  }
}
