import {
  Component,
  ChangeDetectionStrategy,
  Directive,
  TemplateRef,
  input,
  output,
  computed,
  contentChildren,
  effect,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { BreakpointObserver } from '@angular/cdk/layout';
import { SelectionModel } from '@angular/cdk/collections';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { PaginationMeta } from '@frontend/shared-models';

export type TableColumnBreakpoint = 'sm' | 'md' | 'lg';

export interface TableColumn {
  key: string;
  header: string;
  type?: 'text' | 'number' | 'date' | 'currency' | 'badge';
  sortable?: boolean;
  /** Hides this column when the viewport is narrower than the named breakpoint. */
  hideBelow?: TableColumnBreakpoint;
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

const SELECT_COLUMN_KEY = '__select';
const ACTIONS_COLUMN_KEY = '__actions';

const BREAKPOINT_QUERIES: Record<TableColumnBreakpoint, string> = {
  sm: '(max-width: 639.98px)',
  md: '(max-width: 959.98px)',
  lg: '(max-width: 1279.98px)',
};

/**
 * Custom cell template directive for `DataTableComponent`.
 *
 * @example
 * <app-data-table [columns]="columns" [data]="rows">
 *   <ng-template appDataTableCell="status" let-row let-value="value">
 *     <app-status-badge [status]="value" />
 *   </ng-template>
 * </app-data-table>
 */
@Directive({
  selector: 'ng-template[appDataTableCell]',
  standalone: true,
})
export class DataTableCellDefDirective {
  readonly appDataTableCell = input.required<string>();
  readonly templateRef = inject<TemplateRef<unknown>>(TemplateRef);
}

/**
 * Generic reusable data table component wrapping Angular Material table.
 *
 * Optional `rowActions` appends a menu column and emits `rowAction`; optional
 * `selectable` prepends a selection checkbox column backed by CDK `SelectionModel`;
 * optional `ng-template[appDataTableCell]` overrides rendering for a column key;
 * optional `column.hideBelow` hides columns on narrower viewports — all strictly
 * additive so existing CPQ/CRM callers behave as before.
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
    MatCheckboxModule,
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
          @if (selectable()) {
            <ng-container [matColumnDef]="selectColumnKey">
              <th mat-header-cell *matHeaderCellDef class="select-cell">
                <mat-checkbox
                  [checked]="isAllSelected()"
                  [indeterminate]="isSomeSelected()"
                  [disabled]="data().length === 0"
                  aria-label="Select all rows"
                  (change)="toggleAllRows()"
                />
              </th>
              <td
                mat-cell
                *matCellDef="let row; let i = index"
                class="select-cell"
                (click)="$event.stopPropagation()"
                (keydown)="$event.stopPropagation()"
              >
                <mat-checkbox
                  [checked]="isRowSelected(row)"
                  [attr.aria-label]="'Select row ' + (i + 1)"
                  (change)="toggleRow(row)"
                />
              </td>
            </ng-container>
          }

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
                @if (cellTemplateMap().get(column.key); as customTemplate) {
                  <ng-container
                    *ngTemplateOutlet="
                      customTemplate;
                      context: { $implicit: row, row: row, value: row[column.key], column: column }
                    "
                  />
                } @else {
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

    .select-cell {
      width: 48px;
      padding-right: 0;
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
  private readonly breakpointObserver = inject(BreakpointObserver);

  readonly selectColumnKey = SELECT_COLUMN_KEY;
  readonly actionsColumnKey = ACTIONS_COLUMN_KEY;

  columns = input.required<TableColumn[]>();
  data = input.required<T[]>();
  loading = input<boolean>(false);
  pagination = input<PaginationMeta | null>(null);
  /** When true, rows show pointer cursor (OutputEmitterRef has no EventEmitter.observed). */
  clickable = input<boolean>(true);
  /** Opt-in row selection powered by CDK SelectionModel. */
  selectable = input<boolean>(false);
  /** Property name used to compare rows in the selection model. */
  selectionKey = input<string>('id');
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
  selectionChange = output<T[]>();

  private readonly cellTemplates = contentChildren(DataTableCellDefDirective);
  private readonly hiddenBreakpoints = signal<Record<TableColumnBreakpoint, boolean>>({
    sm: false,
    md: false,
    lg: false,
  });
  private readonly selectionVersion = signal(0);

  readonly selection = new SelectionModel<T>(true, [], true, (a, b) => this.compareRows(a, b));

  protected readonly cellTemplateMap = computed(() => {
    const map = new Map<string, TemplateRef<unknown>>();
    for (const def of this.cellTemplates()) {
      map.set(def.appDataTableCell(), def.templateRef);
    }
    return map;
  });

  hasRowActions = computed(() => this.rowActions().length > 0);

  displayedColumns = computed(() => {
    const hidden = this.hiddenBreakpoints();
    const keys = this.columns()
      .filter((col) => !col.hideBelow || !hidden[col.hideBelow])
      .map((col) => col.key);

    if (this.selectable()) {
      keys.unshift(SELECT_COLUMN_KEY);
    }
    if (this.hasRowActions()) {
      keys.push(ACTIONS_COLUMN_KEY);
    }
    return keys;
  });

  readonly isAllSelected = computed(() => {
    this.selectionVersion();
    const rows = this.data();
    return rows.length > 0 && rows.every((row) => this.selection.isSelected(row));
  });

  readonly isSomeSelected = computed(() => {
    this.selectionVersion();
    const rows = this.data();
    if (rows.length === 0) {
      return false;
    }
    const selectedCount = rows.filter((row) => this.selection.isSelected(row)).length;
    return selectedCount > 0 && selectedCount < rows.length;
  });

  constructor() {
    this.breakpointObserver
      .observe([BREAKPOINT_QUERIES.sm, BREAKPOINT_QUERIES.md, BREAKPOINT_QUERIES.lg])
      .pipe(takeUntilDestroyed())
      .subscribe((state) => {
        this.hiddenBreakpoints.set({
          sm: Boolean(state.breakpoints[BREAKPOINT_QUERIES.sm]),
          md: Boolean(state.breakpoints[BREAKPOINT_QUERIES.md]),
          lg: Boolean(state.breakpoints[BREAKPOINT_QUERIES.lg]),
        });
      });

    effect(() => {
      // Reset selection whenever the backing data array changes.
      this.data();
      if (this.selection.hasValue()) {
        this.selection.clear();
        this.selectionVersion.update((v) => v + 1);
        this.selectionChange.emit([]);
      }
    });
  }

  trackById = (index: number, item: T): string | number => {
    const key = this.selectionKey();
    if (item && typeof item === 'object' && key in (item as Record<string, unknown>)) {
      const value = (item as Record<string, unknown>)[key];
      if (typeof value === 'string' || typeof value === 'number') {
        return value;
      }
    }
    return index;
  };

  isRowSelected(row: T): boolean {
    this.selectionVersion();
    return this.selection.isSelected(row);
  }

  toggleRow(row: T): void {
    this.selection.toggle(row);
    this.selectionVersion.update((v) => v + 1);
    this.selectionChange.emit([...this.selection.selected]);
  }

  toggleAllRows(): void {
    if (this.isAllSelected()) {
      this.selection.clear();
    } else {
      this.selection.setSelection(...this.data());
    }
    this.selectionVersion.update((v) => v + 1);
    this.selectionChange.emit([...this.selection.selected]);
  }

  private compareRows(a: T, b: T): boolean {
    if (a === b) {
      return true;
    }
    const key = this.selectionKey();
    if (
      a &&
      b &&
      typeof a === 'object' &&
      typeof b === 'object' &&
      key in (a as Record<string, unknown>) &&
      key in (b as Record<string, unknown>)
    ) {
      return (a as Record<string, unknown>)[key] === (b as Record<string, unknown>)[key];
    }
    return false;
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
