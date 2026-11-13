import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
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

/** Menu item for the optional trailing actions column (UI-CRM-SLDS Phase 2). */
export interface TableRowAction {
  action: string;
  label: string;
  icon?: string;
  /** When true, menu item uses warn styling. */
  danger?: boolean;
  /** When set, item is shown only if this returns true for the row. */
  visible?: (row: unknown) => boolean;
}

const ACTIONS_COLUMN_KEY = '__actions';

/**
 * Generic reusable data table component wrapping Angular Material table.
 *
 * Optional `rowActions` appends a menu column and emits `rowAction` — additive;
 * existing CPQ callers omit it and behave as before.
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
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="data-table-container" data-testid="data-table">
      @if (loading()) {
        <div class="loading-overlay" role="status" aria-live="polite" aria-label="Loading">
          <mat-spinner diameter="50"></mat-spinner>
        </div>
      }

      <div class="table-wrapper">
        <table mat-table [dataSource]="data()" [trackBy]="trackById" matSort (matSortChange)="onSortChange($event)">
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
              <td mat-cell *matCellDef="let row">
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

          @if (hasRowActions()) {
            <ng-container [matColumnDef]="actionsColumnKey">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let row" class="actions-cell" (click)="$event.stopPropagation()" (keydown)="$event.stopPropagation()">
                @if (visibleRowActions(row); as actions) {
                  @if (actions.length > 0) {
                    <button
                      mat-icon-button
                      type="button"
                      [matMenuTriggerFor]="rowMenu"
                      [disabled]="rowActionsDisabled()"
                      [attr.aria-label]="rowActionsAriaLabel()"
                    >
                      <mat-icon>more_vert</mat-icon>
                    </button>
                    <mat-menu #rowMenu="matMenu">
                      @for (action of actions; track action.action) {
                        <button
                          mat-menu-item
                          type="button"
                          [class.danger-action]="action.danger"
                          [disabled]="rowActionsDisabled()"
                          (click)="emitRowAction(action.action, row)"
                        >
                          @if (action.icon) {
                            <mat-icon>{{ action.icon }}</mat-icon>
                          }
                          <span>{{ action.label }}</span>
                        </button>
                      }
                    </mat-menu>
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
            [class.clickable]="clickable()"
            [attr.tabindex]="clickable() ? 0 : null"
            (click)="onRowClick(row)"
            (keydown)="onRowKeydown($event, row)"
          ></tr>

          <tr *matNoDataRow class="mat-row">
            <td [attr.colspan]="displayedColumns().length" class="empty-state-cell">
              @if (!loading()) {
                <div class="empty-state">
                  <p class="empty-state-text">No data available</p>
                </div>
              }
            </td>
          </tr>
        </table>
      </div>

      @if (pagination() && data().length > 0) {
        <mat-paginator
          [length]="pagination()!.totalElements"
          [pageSize]="pagination()!.size"
          [pageIndex]="pagination()!.page"
          [pageSizeOptions]="pageSizeOptions()"
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
      background: var(--crm-color-surface, #fff);
      border-radius: var(--crm-radius-sm, 8px);
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      overflow: hidden;
    }

    .loading-overlay {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: color-mix(in srgb, var(--crm-color-surface, #fff) 80%, transparent);
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
      background-color: var(--crm-color-track, #f5f5f5);
    }

    .table-row.clickable {
      cursor: pointer;
    }

    .table-row.clickable:focus-visible {
      outline: 3px solid var(--crm-focus-ring, #ffb747);
      outline-offset: -2px;
    }

    .actions-cell {
      width: 56px;
      text-align: right;
    }

    .danger-action {
      color: var(--crm-color-danger, #c62828);
    }

    .empty-state-cell {
      text-align: center;
      padding: var(--crm-space-xl, 48px) var(--crm-space-lg, 24px);
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
    }

    .empty-state-text {
      color: var(--crm-color-muted, #757575);
      font-size: 14px;
      margin: 0;
    }

    mat-header-cell {
      font-weight: 600;
      color: var(--crm-color-ink, #424242);
    }

    mat-cell {
      color: var(--crm-color-muted, #616161);
    }

    mat-paginator {
      border-top: 1px solid var(--crm-color-line, #e0e0e0);
    }
  `],
})
export class DataTableComponent<T = unknown> {
  readonly actionsColumnKey = ACTIONS_COLUMN_KEY;

  columns = input.required<TableColumn[]>();
  data = input.required<T[]>();
  loading = input<boolean>(false);
  pagination = input<PaginationMeta | null>(null);
  /** When true, rows show pointer cursor (OutputEmitterRef has no EventEmitter.observed). */
  clickable = input<boolean>(true);
  /** Optional trailing menu actions; empty/omitted keeps CPQ lists unchanged. */
  rowActions = input<TableRowAction[]>([]);
  /** Accessible name for the row actions trigger (CRM lists customize per entity). */
  rowActionsAriaLabel = input<string>('Row actions');
  /** Disables the actions trigger (e.g. while a lifecycle mutation is in flight). */
  rowActionsDisabled = input<boolean>(false);
  pageSizeOptions = input<number[]>([10, 25, 50, 100]);

  pageChange = output<PageEvent>();
  sortChange = output<SortChangeEvent>();
  rowClick = output<T>();
  rowAction = output<RowActionEvent<T>>();

  hasRowActions = computed(() => this.rowActions().length > 0);

  displayedColumns = computed(() => {
    const keys = this.columns().map((col) => col.key);
    return this.hasRowActions() ? [...keys, ACTIONS_COLUMN_KEY] : keys;
  });

  trackById(index: number, item: any): string | number {
    return item?.id ?? index;
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
    if (!this.clickable()) {
      return;
    }
    this.rowClick.emit(row);
  }

  onRowKeydown(event: KeyboardEvent, row: T): void {
    if (!this.clickable()) {
      return;
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.onRowClick(row);
    }
  }

  visibleRowActions(row: T): TableRowAction[] {
    return this.rowActions().filter((action) => !action.visible || action.visible(row));
  }

  emitRowAction(action: string, row: T): void {
    this.rowAction.emit({ action, row });
  }
}
