import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PaginationMeta } from '@frontend/shared-models';

export interface TableColumn {
  key: string;
  header: string;
  type?: 'text' | 'number' | 'date' | 'currency' | 'badge';
  sortable?: boolean;
}

export interface SortChangeEvent {
  active: string;
  direction: 'asc' | 'desc' | '';
}

export interface RowActionEvent<T = unknown> {
  action: string;
  row: T;
}

/**
 * Generic reusable data table component wrapping Angular Material table
 *
 * @example
 * <app-data-table
 *   [columns]="columns"
 *   [data]="tableData"
 *   [loading]="isLoading"
 *   [pagination]="paginationMeta"
 *   (pageChange)="onPageChange($event)"
 *   (sortChange)="onSortChange($event)"
 *   (rowClick)="onRowClick($event)"
 * />
 */
@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="data-table-container">
      <!-- Loading Overlay -->
      @if (loading()) {
        <div class="loading-overlay">
          <mat-spinner diameter="50"></mat-spinner>
        </div>
      }

      <!-- Table -->
      <div class="table-wrapper">
        <table mat-table [dataSource]="data()" [trackBy]="trackById" matSort (matSortChange)="onSortChange($event)">
          <!-- Dynamic Columns -->
          @for (column of columns(); track column.key) {
            <ng-container [matColumnDef]="column.key">
              <th
                mat-header-cell
                *matHeaderCellDef
                [mat-sort-header]="column.sortable !== false ? column.key : ''"
                [disabled]="column.sortable === false"
              >
                {{ column.header }}
              </th>
              <td mat-cell *matCellDef="let row" (click)="onRowClick(row)">
                @switch (column.type) {
                  @case ('number') {
                    {{ row[column.key] | number }}
                  }
                  @case ('date') {
                    {{ row[column.key] | date: 'short' }}
                  }
                  @case ('currency') {
                    {{ row[column.key] | currency }}
                  }
                  @default {
                    {{ row[column.key] }}
                  }
                }
              </td>
            </ng-container>
          }

          <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
          <tr
            mat-row
            *matRowDef="let row; columns: displayedColumns();"
            class="table-row"
            [class.clickable]="rowClick.observed"
          ></tr>

          <!-- Empty State Row -->
          @if (data().length === 0 && !loading()) {
            <tr class="mat-row">
              <td [attr.colspan]="displayedColumns().length" class="empty-state-cell">
                <div class="empty-state">
                  <p class="empty-state-text">No data available</p>
                </div>
              </td>
            </tr>
          }
        </table>
      </div>

      <!-- Paginator -->
      @if (pagination() && data().length > 0) {
        <mat-paginator
          [length]="pagination()!.totalElements"
          [pageSize]="pagination()!.size"
          [pageIndex]="pagination()!.page"
          [pageSizeOptions]="[10, 25, 50, 100]"
          (page)="onPageChange($event)"
          showFirstLastButtons
        >
        </mat-paginator>
      }
    </div>
  `,
  styles: [`
    .data-table-container {
      position: relative;
      width: 100%;
      background: white;
      border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      overflow: hidden;
    }

    .loading-overlay {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(255, 255, 255, 0.8);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10;
    }

    .table-wrapper {
      overflow-x: auto;
      max-width: 100%;
    }

    table {
      width: 100%;
    }

    .table-row {
      transition: background-color 0.2s ease;
    }

    .table-row:hover {
      background-color: #f5f5f5;
    }

    .table-row.clickable {
      cursor: pointer;
    }

    .empty-state-cell {
      text-align: center;
      padding: 48px 24px;
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
    }

    .empty-state-text {
      color: #757575;
      font-size: 14px;
      margin: 0;
    }

    mat-header-cell {
      font-weight: 600;
      color: #424242;
    }

    mat-cell {
      color: #616161;
    }

    mat-paginator {
      border-top: 1px solid #e0e0e0;
    }
  `],
})
export class DataTableComponent<T = unknown> {
  // Inputs using signal-based API
  columns = input.required<TableColumn[]>();
  data = input.required<T[]>();
  loading = input<boolean>(false);
  pagination = input<PaginationMeta | null>(null);

  // Outputs using signal-based API
  pageChange = output<PageEvent>();
  sortChange = output<SortChangeEvent>();
  rowClick = output<T>();
  rowAction = output<RowActionEvent<T>>();

  // Computed signals
  displayedColumns = computed(() => this.columns().map(col => col.key));

  trackById(index: number, item: any): any {
    return item.id ?? index;
  }

  onSortChange(sort: Sort): void {
    this.sortChange.emit({
      active: sort.active,
      direction: sort.direction as 'asc' | 'desc' | '',
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageChange.emit(event);
  }

  onRowClick(row: T): void {
    this.rowClick.emit(row);
  }

  emitRowAction(action: string, row: T): void {
    this.rowAction.emit({ action, row });
  }
}
