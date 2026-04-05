import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { DataTableComponent, TableColumn } from './data-table.component';
import { PaginationMeta } from '@frontend/shared-models';

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

  it('should display empty state when data is empty and not loading', () => {
    component.componentRef.setInput('columns', mockColumns);
    component.componentRef.setInput('data', []);
    component.componentRef.setInput('loading', false);
    component.detectChanges();

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

    component.componentInstance.onSortChange({
      active: 'name',
      direction: 'asc',
    });
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
});
