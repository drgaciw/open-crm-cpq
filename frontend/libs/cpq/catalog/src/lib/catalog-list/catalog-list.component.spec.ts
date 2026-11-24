import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { computed, signal } from '@angular/core';
import { of } from 'rxjs';
import { NotificationService } from '@frontend/shared-data-access';
import { CatalogListComponent } from './catalog-list.component';
import { ProductService } from '../product.service';
import { ProductCreateDialogComponent } from '../product-create-dialog/product-create-dialog.component';
import { MOCK_CATALOG_PRODUCTS } from '../catalog.mock';
import {
  PaginationMeta,
  ProductCategory,
  ProductDto,
  ProductStatus,
} from '@frontend/shared-models';

function toProductDto(
  mock: (typeof MOCK_CATALOG_PRODUCTS)[number],
): ProductDto {
  return {
    id: mock.id,
    uuid: mock.uuid,
    sku: mock.sku,
    name: mock.name,
    description: mock.description,
    productType: mock.productType as ProductDto['productType'],
    category: mock.category,
    status: mock.status,
    listPrice: mock.listPrice,
    currency: mock.currency,
    configurable: false,
    bundle: false,
    createdAt: '',
    updatedAt: '',
  };
}

describe('CatalogListComponent', () => {
  let fixture: ComponentFixture<CatalogListComponent>;
  let component: CatalogListComponent;
  let router: Router;
  let listSpy: ReturnType<typeof vi.fn>;
  let searchSpy: ReturnType<typeof vi.fn>;
  let success: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  const products = signal<ProductDto[]>(
    MOCK_CATALOG_PRODUCTS.map(toProductDto),
  );
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 10,
    totalElements: MOCK_CATALOG_PRODUCTS.length,
    totalPages: 1,
  });

  beforeEach(async () => {
    listSpy = vi.fn(
      (
        _page?: number,
        _size?: number,
        _sort?: string,
        filters?: {
          category?: ProductCategory;
          status?: ProductStatus;
        },
      ) => {
        let next = MOCK_CATALOG_PRODUCTS.map(toProductDto);
        if (filters?.category) {
          next = next.filter((p) => p.category === filters.category);
        }
        if (filters?.status) {
          next = next.filter((p) => p.status === filters.status);
        }
        products.set(next);
        pagination.set({
          page: 0,
          size: 10,
          totalElements: next.length,
          totalPages: Math.max(1, Math.ceil(next.length / 10) || 1),
        });
      },
    );
    searchSpy = vi.fn((q: string) => {
      const term = q.toLowerCase();
      const next = MOCK_CATALOG_PRODUCTS.map(toProductDto).filter(
        (p) =>
          p.name.toLowerCase().includes(term) ||
          p.sku.toLowerCase().includes(term) ||
          (p.description?.toLowerCase().includes(term) ?? false),
      );
      products.set(next);
      pagination.set({
        page: 0,
        size: 10,
        totalElements: next.length,
        totalPages: Math.max(1, Math.ceil(next.length / 10) || 1),
      });
    });

    const serviceMock = {
      products,
      loading,
      error,
      pagination,
      isEmpty: computed(() => products().length === 0 && !loading()),
      list: listSpy,
      search: searchSpy,
      selectedProduct: signal<ProductDto | null>(null),
    };

    success = vi.fn();
    dialogOpen = vi.fn().mockReturnValue({
      afterClosed: () => of(undefined),
    });

    await TestBed.configureTestingModule({
      imports: [CatalogListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ProductService, useValue: serviceMock },
        { provide: NotificationService, useValue: { success } },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CatalogListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    products.set(MOCK_CATALOG_PRODUCTS.map(toProductDto));
    loading.set(false);
    error.set(null);
  });

  it('reports a successful create only when the dialog returns the saved product', () => {
    component.openCreateDialog();
    expect(success).not.toHaveBeenCalled();

    dialogOpen.mockReturnValueOnce({ afterClosed: () => of(toProductDto(MOCK_CATALOG_PRODUCTS[0])) });
    component.openCreateDialog();
    expect(success).toHaveBeenCalledExactlyOnceWith('Product created');
  });

  it('loads products via ProductService on init and removes stub subtitle', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(listSpy).toHaveBeenCalledWith(0, 10, 'name,asc', {
      category: undefined,
      status: undefined,
    });
    expect(compiled.textContent).toContain('Product Catalog');
    expect(compiled.textContent).toContain(
      'Browse sellable products from the product catalog API',
    );
    expect(compiled.textContent).not.toContain('presentational stub');
    expect(compiled.textContent).not.toContain('mock data');
    expect(component.tableRows().length).toBe(MOCK_CATALOG_PRODUCTS.length);
    expect(compiled.textContent).toContain('CRM-SEAT-NAMED');
  });

  it('renders the Status column as a product status badge with friendly labels (H-CPQ-P-003, H-CPQ-P-006)', () => {
    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('tr.mat-mdc-row'));
    const badges = rows.map((r) => r.querySelector('app-status-badge')?.textContent?.trim());
    expect(badges.every((b) => b !== undefined)).toBe(true);
    for (const b of badges) expect(['Draft', 'Active', 'Deprecated', 'Retired']).toContain(b);
  });

  it('labels the status filter options in words but filters by the enum (H-CPQ-P-006)', () => {
    expect(component.statusOptions.map((o) => o.label)).toEqual(['Draft', 'Active', 'Deprecated', 'Retired']);
    expect(component.statusOptions.map((o) => o.value)).toEqual(['DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED']);
  });

  describe('column sorting (H-CPQ-P-002)', () => {
    function emitSort(active: string, direction: 'asc' | 'desc' | '') {
      const table = fixture.debugElement.query(By.css('app-data-table'));
      table.triggerEventHandler('sortChange', { active, direction });
      fixture.detectChanges();
    }

    it('reloads page 0 from the API in the order the header asks for', () => {
      listSpy.mockClear();
      emitSort('sku', 'desc');

      expect(listSpy).toHaveBeenLastCalledWith(0, 10, 'sku,desc', {
        category: undefined,
        status: undefined,
      });
    });

    it('falls back to name order when the sort is cleared', () => {
      emitSort('listPrice', 'asc');
      listSpy.mockClear();
      emitSort('listPrice', '');

      expect(listSpy).toHaveBeenLastCalledWith(0, 10, 'name,asc', {
        category: undefined,
        status: undefined,
      });
    });

    it('keeps the chosen order while searching', () => {
      vi.useFakeTimers();
      emitSort('sku', 'asc');
      component.onSearch('SUP');
      vi.advanceTimersByTime(300);
      fixture.detectChanges();

      expect(searchSpy).toHaveBeenLastCalledWith('SUP', 0, 10, 'sku,asc');
      vi.useRealTimers();
    });
  });

  it('sends the category and status filters with a search instead of filtering page 1 (DRG-754)', () => {
    vi.useFakeTimers();
    component.onSearch('UAT');
    vi.advanceTimersByTime(300);
    searchSpy.mockClear();
    component.onCategoryChange('SUPPORT');
    expect(searchSpy).toHaveBeenLastCalledWith('UAT', 0, 10, undefined, { category: 'SUPPORT', status: undefined });

    component.onStatusChange('DRAFT');
    expect(searchSpy).toHaveBeenLastCalledWith('UAT', 0, 10, undefined, { category: 'SUPPORT', status: 'DRAFT' });
    vi.useRealTimers();
  });

  it('shows what the server returned for a filtered search, without a second client-side filter (DRG-754)', () => {
    vi.useFakeTimers();
    searchSpy.mockImplementation(() => {
      // The server already applied the filter across the whole result set.
      products.set([{ ...MOCK_CATALOG_PRODUCTS.map(toProductDto)[0], category: 'SUPPORT' }]);
    });
    component.onSearch('UAT');
    vi.advanceTimersByTime(300);
    component.onCategoryChange('LICENSE');
    fixture.detectChanges();
    expect(component.tableRows().length).toBe(1);
    vi.useRealTimers();
  });

  it('searches via ProductService.search', () => {
    vi.useFakeTimers();
    component.onSearch('SUP-PREM');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    expect(searchSpy).toHaveBeenCalledWith('SUP-PREM', 0, 10);
    expect(component.tableRows().length).toBe(1);
    expect(component.tableRows()[0].sku).toBe('SUP-PREM');
    vi.useRealTimers();
  });

  it('filters by category and status via ProductService.list', () => {
    component.onCategoryChange('SUBSCRIPTION');
    component.onStatusChange('ACTIVE');
    fixture.detectChanges();

    expect(listSpy).toHaveBeenCalledWith(0, 10, 'name,asc', {
      category: 'SUBSCRIPTION',
      status: 'ACTIVE',
    });
    expect(
      component.tableRows().every((r) => r.category === 'SUBSCRIPTION'),
    ).toBe(true);
    expect(component.tableRows().every((r) => r.status === 'ACTIVE')).toBe(
      true,
    );
    expect(component.tableRows().length).toBe(2);
  });

  it('shows empty state when filters match nothing', () => {
    vi.useFakeTimers();
    component.onSearch('zzz-no-match');
    vi.advanceTimersByTime(300);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(component.isEmpty()).toBe(true);
    expect(compiled.textContent).toContain('No matching products');
    vi.useRealTimers();
  });

  it('shows empty state when catalog has no products', () => {
    products.set([]);
    pagination.set({
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No products yet');
  });

  it('does not present a failed load as a successfully empty list', () => {
    products.set([]);
    error.set('Service unavailable');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain(
      'Service unavailable',
    );
    expect(compiled.querySelector('app-empty-state')).toBeNull();
    expect(compiled.textContent).not.toContain('No data available');
    expect(compiled.querySelector('app-data-table')).toBeNull();
    expect(compiled.textContent).not.toContain('No products yet');
  });

  it('keeps a failed first read out of the empty state until a new read succeeds', () => {
    products.set([]);
    loading.set(false);
    error.set('Scoped list unavailable');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const alert = el.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Scoped list unavailable');
    expect(el.querySelector('app-empty-state')).toBeNull();
    listSpy.mockClear();
    alert!.querySelector<HTMLButtonElement>('button')!.click();
    fixture.detectChanges();

    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelector('app-empty-state')).toBeNull();
    expect(el.querySelector('app-data-table')).toBeNull();
    expect(el.querySelector('h1')).toBeTruthy();
    expect(listSpy).not.toHaveBeenCalled();

    listSpy.mockImplementationOnce(() => loading.set(true));
    component.onCategoryChange('SUPPORT');
    fixture.detectChanges();
    expect(el.querySelector('app-empty-state')).toBeNull();

    products.set([]);
    loading.set(false);
    fixture.detectChanges();
    expect(el.querySelector('app-empty-state')?.textContent).toContain('No matching products');
    expect(el.querySelector('app-empty-state button')).toBeNull();
  });

  it('shows error banner when ProductService.error is set', () => {
    error.set('Failed to load catalog');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load catalog');

    component.clearError();
    fixture.detectChanges();
    expect(error()).toBeNull();
  });

  it('navigates to detail on row click', () => {
    component.onRowClick({
      id: 2,
      sku: 'CPQ-FLAT',
      name: 'CPQ Flat Rate',
      category: 'SUBSCRIPTION',
      status: 'ACTIVE',
      listPrice: 299,
    });

    expect(router.navigate).toHaveBeenCalledWith(['/catalog', 2]);
  });

  it('enables New Product and opens create dialog', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const button = Array.from(compiled.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('New Product'),
    ) as HTMLButtonElement | undefined;
    expect(button).toBeTruthy();
    expect(button?.disabled).toBe(false);
    expect(button?.getAttribute('title')).toBeNull();

    button?.click();
    expect(dialogOpen).toHaveBeenCalledWith(
      ProductCreateDialogComponent,
      expect.objectContaining({ width: '520px', disableClose: true }),
    );
  });

  it('navigates to detail after successful create dialog close', () => {
    dialogOpen.mockReturnValue({
      afterClosed: () =>
        of({
          id: 99,
          uuid: 'u-99',
          sku: 'CREATED',
          name: 'Created',
          productType: 'CUSTOM',
          category: 'ADD_ON',
          status: 'DRAFT',
          configurable: false,
          bundle: false,
          createdAt: '',
          updatedAt: '',
        } satisfies ProductDto),
    });

    component.openCreateDialog();

    expect(router.navigate).toHaveBeenCalledWith(['/catalog', 99]);
  });

  it('does not navigate when create dialog is cancelled', () => {
    dialogOpen.mockReturnValue({ afterClosed: () => of(undefined) });
    component.openCreateDialog();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('uses shared-ui data table (not inline MatTable happy path)', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-data-table')).toBeTruthy();
    expect(compiled.querySelector('app-page-header')).toBeTruthy();
    expect(compiled.querySelector('app-search-input')).toBeTruthy();
  });
});
