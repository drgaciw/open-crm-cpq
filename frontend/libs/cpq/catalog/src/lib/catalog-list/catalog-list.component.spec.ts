import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { computed, signal } from '@angular/core';
import { of } from 'rxjs';
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

    dialogOpen = vi.fn().mockReturnValue({
      afterClosed: () => of(undefined),
    });

    await TestBed.configureTestingModule({
      imports: [CatalogListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ProductService, useValue: serviceMock },
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
