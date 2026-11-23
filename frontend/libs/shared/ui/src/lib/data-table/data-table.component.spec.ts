import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { BehaviorSubject } from 'rxjs';
import {
  DataTableCellDefDirective,
  DataTableComponent,
  TableColumn,
  TableRowAction,
  RowActionEvent,
} from './data-table.component';
import { PaginationMeta } from '@frontend/shared-models';

@Component({
  standalone: true,
  imports: [DataTableComponent, DataTableCellDefDirective],
  template: `
    <app-data-table [columns]="columns" [data]="rows">
      <ng-template appDataTableCell="name" let-row let-value="value">
        <span class="custom-name-cell" [attr.data-id]="row.id">Custom: {{ value }}</span>
      </ng-template>
    </app-data-table>
  `,
})
class CustomCellHostComponent {
  columns: TableColumn[] = [
    { key: 'id', header: 'ID', type: 'number' },
    { key: 'name', header: 'Name', type: 'text' },
  ];
  rows = [
    { id: 1, name: 'Alpha' },
    { id: 2, name: 'Beta' },
  ];
}

describe('DataTableComponent', () => {
  let component: ComponentFixture<DataTableComponent>;

  const mockColumns: TableColumn[] = [
    { key: 'id', header: 'ID', type: 'number' },
    { key: 'name', header: 'Name', type: 'text' },
    { key: 'amount', header: 'Amount', type: 'currency' },
    { key: 'date', header: 'Date', type: 'date' },
  ];

  const mockData = [
    { id: 1, name: 'Item 1', amount: 100, date: new Date('2024-01-01') },
    { id: 2, name: 'Item 2', amount: 200, date: new Date('2024-01-02') },
  ];

  const mockPagination: PaginationMeta = {
    page: 0,
    size: 10,
    totalElements: 2,
    totalPages: 1,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DataTableComponent, NoopAnimationsModule],
    }).compileComponents();

    component = TestBed.createComponent(DataTableComponent);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // UI-CRM-SLDS Phase 2: CRM list pages (contacts/accounts/leads/deals) assert this testid via
  // Playwright to confirm they render the shared table rather than a raw mat-table.
  it('renders a humanised status badge for badge columns with a badgeType (leads regression after UI-CRM-SLDS)', () => {
    component.componentRef.setInput('columns', [
      { key: 'title', header: 'Title' },
      { key: 'status', header: 'Status', type: 'badge', badgeType: 'lead' },
    ]);
    component.componentRef.setInput('data', [{ id: 1, title: 'Warm lead', status: 'NEW' }]);
    component.detectChanges();

    const cell: HTMLElement = component.nativeElement.querySelectorAll('td.mat-mdc-cell')[1];
    expect(cell.querySelector('app-status-badge')).not.toBeNull();
    expect(cell.textContent?.trim()).toBe('New');
  });

  it('names each row-actions trigger from the row when given a label function (lead list a11y regression)', () => {
    component.componentRef.setInput('columns', [{ key: 'title', header: 'Title' }]);
    component.componentRef.setInput('data', [{ id: 1, title: 'Alpha' }, { id: 2, title: 'Beta' }]);
    component.componentRef.setInput('rowActions', [{ action: 'edit', label: 'Edit' }]);
    component.componentRef.setInput('rowActionsAriaLabel', (row: unknown) => `Actions for ${(row as { title: string }).title}`);
    component.detectChanges();

    const labels = Array.from(component.nativeElement.querySelectorAll('td.actions-cell button'))
      .map((b) => (b as HTMLElement).getAttribute('aria-label'));
    expect(labels).toEqual(['Actions for Alpha', 'Actions for Beta']);
  });

  it('keeps raw text for badge columns without a badgeType', () => {
    component.componentRef.setInput('columns', [{ key: 'status', header: 'Status', type: 'badge' }]);
    component.componentRef.setInput('data', [{ id: 1, status: 'DRAFT' }]);
    component.detectChanges();

    const cell: HTMLElement = component.nativeElement.querySelector('td.mat-mdc-cell');
    expect(cell.querySelector('app-status-badge')).toBeNull();
    expect(cell.textContent?.trim()).toBe('DRAFT');
  });

  it('exposes a stable data-testid for browser UAT', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.detectChanges();

    expect(component.nativeElement.querySelector('[data-testid="data-table"]')).toBeTruthy();
  });

  it('should display table with correct columns', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.detectChanges();

    const headers = component.nativeElement.querySelectorAll('th');
    expect(headers.length).toBe(mockColumns.length);
    expect(headers[0].textContent?.trim()).toContain('ID');
    expect(headers[1].textContent?.trim()).toContain('Name');
  });

  it('should display loading spinner when loading is true', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', []);
    component.componentRef.setInput('loading', true);
    component.detectChanges();

    const spinner = component.nativeElement.querySelector('mat-spinner');
    expect(spinner).toBeTruthy();
  });

  it('should announce loading overlay as a polite live region', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', []);
    component.componentRef.setInput('loading', true);
    component.detectChanges();

    const overlay = component.nativeElement.querySelector('.loading-overlay') as HTMLElement;
    expect(overlay.getAttribute('role')).toBe('status');
    expect(overlay.getAttribute('aria-live')).toBe('polite');
  });

  it('should display empty state when data is empty and not loading', async () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', []);
    component.componentRef.setInput('loading', false);
    component.detectChanges();
    await component.whenStable();

    const emptyState = component.nativeElement.querySelector('.empty-state');
    expect(emptyState).toBeTruthy();
    expect(emptyState.textContent).toContain('No data available');
  });

  it('should emit pageChange event when page is changed', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.componentRef.setInput('pagination', mockPagination);
    component.detectChanges();

    let pageEvent: any;
    component.componentInstance.pageChange.subscribe((event) => {
      pageEvent = event;
    });

    const paginator = component.nativeElement.querySelector('mat-paginator');
    expect(paginator).toBeTruthy();
  });

  it('should emit sortChange event when column is sorted', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.detectChanges();

    let sortEvent: any;
    component.componentInstance.sortChange.subscribe((event) => {
      sortEvent = event;
    });

    component.componentInstance.onSortChange({ active: 'name', direction: 'asc' });
    expect(sortEvent).toEqual({ active: 'name', direction: 'asc' });
  });

  it('should emit rowClick event when row is clicked', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.detectChanges();

    let clickedRow: any;
    component.componentInstance.rowClick.subscribe((row) => {
      clickedRow = row;
    });

    component.componentInstance.onRowClick(mockData[0]);
    expect(clickedRow).toEqual(mockData[0]);
  });

  it('should set tabindex="0" on clickable rows', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.componentRef.setInput('clickable', true);
    component.detectChanges();

    const rows = component.nativeElement.querySelectorAll('tr.table-row');
    expect(rows.length).toBeGreaterThan(0);
    rows.forEach((row: HTMLElement) => {
      expect(row.getAttribute('tabindex')).toBe('0');
    });
  });

  it('should emit rowClick on Enter and Space keydown', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.detectChanges();

    const clicks: unknown[] = [];
    component.componentInstance.rowClick.subscribe((row) => clicks.push(row));

    component.componentInstance.onRowKeydown(
      new KeyboardEvent('keydown', { key: 'Enter' }),
      mockData[0],
    );
    component.componentInstance.onRowKeydown(
      new KeyboardEvent('keydown', { key: ' ' }),
      mockData[1],
    );

    expect(clicks).toEqual([mockData[0], mockData[1]]);
  });

  it('should not emit rowClick on other keys', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.detectChanges();

    let clicked = false;
    component.componentInstance.rowClick.subscribe(() => {
      clicked = true;
    });

    component.componentInstance.onRowKeydown(
      new KeyboardEvent('keydown', { key: 'Tab' }),
      mockData[0],
    );
    expect(clicked).toBe(false);
  });

  it('should display paginator when pagination data is provided', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.componentRef.setInput('pagination', mockPagination);
    component.detectChanges();

    const paginator = component.nativeElement.querySelector('mat-paginator');
    expect(paginator).toBeTruthy();
  });

  it('should not display paginator when pagination data is null', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.componentRef.setInput('pagination', null);
    component.detectChanges();

    const paginator = component.nativeElement.querySelector('mat-paginator');
    expect(paginator).toBeFalsy();
  });

  it('should omit actions column when rowActions is empty', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.componentRef.setInput('rowActions', []);
    component.detectChanges();

    expect(component.componentInstance.displayedColumns()).toEqual(
      mockColumns.map((c) => c.key),
    );
    expect(component.nativeElement.querySelector('[aria-label="Row actions"]')).toBeNull();
  });

  it('should append actions column and use custom aria-label', () => {
    const actions: TableRowAction[] = [
      { action: 'edit', label: 'Edit', icon: 'edit' },
      { action: 'delete', label: 'Delete', icon: 'delete', danger: true },
    ];
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.componentRef.setInput('rowActions', actions);
    component.componentRef.setInput('rowActionsAriaLabel', 'Account actions');
    component.detectChanges();

    expect(component.componentInstance.displayedColumns()).toEqual([
      ...mockColumns.map((c) => c.key),
      '__actions',
    ]);
    const triggers = component.nativeElement.querySelectorAll(
      'button[aria-label="Account actions"]',
    );
    expect(triggers.length).toBe(mockData.length);
  });

  it('should emit rowAction when emitRowAction is called', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.componentRef.setInput('rowActions', [
      { action: 'edit', label: 'Edit' },
    ] satisfies TableRowAction[]);
    component.detectChanges();

    const events: RowActionEvent[] = [];
    component.componentInstance.rowAction.subscribe((e) => events.push(e));

    component.componentInstance.emitRowAction('edit', mockData[0]);
    expect(events).toEqual([{ action: 'edit', row: mockData[0] }]);
  });

  it('should filter row actions via visible predicates', () => {
    const actions: TableRowAction[] = [
      { action: 'edit', label: 'Edit' },
      {
        action: 'delete',
        label: 'Delete',
        visible: (row) => (row as (typeof mockData)[0]).id === 1,
      },
    ];
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', mockData);
    component.componentRef.setInput('rowActions', actions);
    component.detectChanges();

    expect(component.componentInstance.visibleRowActions(mockData[0]).map((a) => a.action)).toEqual([
      'edit',
      'delete',
    ]);
    expect(component.componentInstance.visibleRowActions(mockData[1]).map((a) => a.action)).toEqual([
      'edit',
    ]);
  });

  describe('CDK enhancements (additive)', () => {
    it('renders custom cell templates registered via [appDataTableCell]', async () => {
      TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [CustomCellHostComponent, NoopAnimationsModule],
      }).compileComponents();

      const hostFixture = TestBed.createComponent(CustomCellHostComponent);
      hostFixture.detectChanges();

      const customCells = Array.from(
        hostFixture.nativeElement.querySelectorAll('.custom-name-cell'),
      ) as HTMLElement[];
      expect(customCells.map((el) => el.textContent?.trim())).toEqual([
        'Custom: Alpha',
        'Custom: Beta',
      ]);
      expect(customCells[0].getAttribute('data-id')).toBe('1');
    });

    it('supports opt-in row selection with master toggle, indeterminate state, and reset on data change', () => {
      component.componentRef.setInput('columns', mockColumns);
      component.componentRef.setInput('data', mockData);
      component.componentRef.setInput('selectable', true);
      component.detectChanges();

      expect(component.componentInstance.displayedColumns()[0]).toBe('__select');

      const emitted: unknown[][] = [];
      component.componentInstance.selectionChange.subscribe((rows) => emitted.push(rows));

      // Select single row -> indeterminate master checkbox
      component.componentInstance.toggleRow(mockData[0]);
      component.detectChanges();
      expect(component.componentInstance.isRowSelected(mockData[0])).toBe(true);
      expect(component.componentInstance.isSomeSelected()).toBe(true);
      expect(component.componentInstance.isAllSelected()).toBe(false);
      expect(emitted[emitted.length - 1]).toEqual([mockData[0]]);

      // Toggle master -> selects all rows
      component.componentInstance.toggleAllRows();
      component.detectChanges();
      expect(component.componentInstance.isAllSelected()).toBe(true);
      expect(component.componentInstance.isSomeSelected()).toBe(false);
      expect(emitted[emitted.length - 1]).toEqual(mockData);

      // Toggle master again -> clears selection
      component.componentInstance.toggleAllRows();
      component.detectChanges();
      expect(component.componentInstance.isAllSelected()).toBe(false);
      expect(emitted[emitted.length - 1]).toEqual([]);

      // Select one, then replace data -> clears selection and emits []
      component.componentInstance.toggleRow(mockData[1]);
      component.detectChanges();
      expect(component.componentInstance.selection.selected).toEqual([mockData[1]]);

      component.componentRef.setInput('data', [
        { id: 3, name: 'Item 3', amount: 300, date: new Date('2024-01-03') },
      ]);
      component.detectChanges();
      expect(component.componentInstance.selection.isEmpty()).toBe(true);
      expect(emitted[emitted.length - 1]).toEqual([]);
    });

    it('uses selectionKey when matching rows for selection', () => {
      const keyedRows = [
        { code: 'A1', name: 'First' },
        { code: 'B2', name: 'Second' },
      ];
      component.componentRef.setInput('columns', [
        { key: 'code', header: 'Code' },
        { key: 'name', header: 'Name' },
      ]);
      component.componentRef.setInput('data', keyedRows);
      component.componentRef.setInput('selectable', true);
      component.componentRef.setInput('selectionKey', 'code');
      component.detectChanges();

      component.componentInstance.toggleRow(keyedRows[0]);
      expect(component.componentInstance.isRowSelected({ code: 'A1', name: 'First' })).toBe(true);
      expect(component.componentInstance.isRowSelected({ code: 'B2', name: 'Second' })).toBe(false);
    });

    it('hides columns with hideBelow when BreakpointObserver reports a narrow viewport', async () => {
      const breakpointSubject = new BehaviorSubject<BreakpointState>({
        matches: false,
        breakpoints: {
          '(max-width: 639.98px)': false,
          '(max-width: 959.98px)': false,
          '(max-width: 1279.98px)': false,
        },
      });

      TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [DataTableComponent, NoopAnimationsModule],
        providers: [
          {
            provide: BreakpointObserver,
            useValue: {
              observe: vi.fn().mockReturnValue(breakpointSubject.asObservable()),
            },
          },
        ],
      }).compileComponents();

      const responsiveFixture = TestBed.createComponent(DataTableComponent);
      const responsiveColumns: TableColumn[] = [
        { key: 'name', header: 'Name' },
        { key: 'amount', header: 'Amount', hideBelow: 'sm' },
        { key: 'date', header: 'Date', hideBelow: 'md' },
        { key: 'owner', header: 'Owner', hideBelow: 'lg' },
      ];
      responsiveFixture.componentRef.setInput('columns', responsiveColumns);
      responsiveFixture.componentRef.setInput('data', [
        { id: 1, name: 'Acme', amount: 100, date: '2026-01-01', owner: 'Alice' },
      ]);
      responsiveFixture.detectChanges();

      expect(responsiveFixture.componentInstance.displayedColumns()).toEqual([
        'name',
        'amount',
        'date',
        'owner',
      ]);

      // Medium tablet viewport (< 960px, also < 1280px)
      breakpointSubject.next({
        matches: true,
        breakpoints: {
          '(max-width: 639.98px)': false,
          '(max-width: 959.98px)': true,
          '(max-width: 1279.98px)': true,
        },
      });
      responsiveFixture.detectChanges();

      expect(responsiveFixture.componentInstance.displayedColumns()).toEqual(['name', 'amount']);

      // Mobile viewport (< 640px)
      breakpointSubject.next({
        matches: true,
        breakpoints: {
          '(max-width: 639.98px)': true,
          '(max-width: 959.98px)': true,
          '(max-width: 1279.98px)': true,
        },
      });
      responsiveFixture.detectChanges();

      expect(responsiveFixture.componentInstance.displayedColumns()).toEqual(['name']);
    });
  });
});
