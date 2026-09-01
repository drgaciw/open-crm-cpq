import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { computed, signal } from '@angular/core';
import { of } from 'rxjs';
import { ProductBundleListComponent } from './product-bundle-list.component';
import { ProductBundleCreateDialogComponent } from '../product-bundle-create-dialog/product-bundle-create-dialog.component';
import { MOCK_PRODUCT_BUNDLES } from '../product-bundles.mock';
import { ProductBundleService } from '../product-bundle.service';
import { PaginationMeta } from '@frontend/shared-models';
import { ProductBundleDto } from '../product-bundle.model';

describe('ProductBundleListComponent', () => {
  let fixture: ComponentFixture<ProductBundleListComponent>;
  let component: ProductBundleListComponent;
  let router: Router;
  let listSpy: ReturnType<typeof vi.fn>;
  let listByProductIdSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  const bundles = signal<ProductBundleDto[]>([...MOCK_PRODUCT_BUNDLES]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: MOCK_PRODUCT_BUNDLES.length,
    totalPages: 1,
  });

  beforeEach(async () => {
    listSpy = vi.fn(() => {
      bundles.set([...MOCK_PRODUCT_BUNDLES]);
      pagination.set({
        page: 0,
        size: 20,
        totalElements: MOCK_PRODUCT_BUNDLES.length,
        totalPages: 1,
      });
    });
    listByProductIdSpy = vi.fn((productId: number) => {
      const next = MOCK_PRODUCT_BUNDLES.filter((b) => b.productId === productId);
      bundles.set(next);
      pagination.set({
        page: 0,
        size: next.length || 20,
        totalElements: next.length,
        totalPages: 1,
      });
    });

    const serviceMock = {
      bundles,
      loading,
      error,
      pagination,
      isEmpty: computed(() => bundles().length === 0 && !loading()),
      list: listSpy,
      listByProductId: listByProductIdSpy,
      selectedBundle: signal<ProductBundleDto | null>(null),
    };

    dialogOpen = vi.fn().mockReturnValue({
      afterClosed: () => of(undefined),
    });

    await TestBed.configureTestingModule({
      imports: [ProductBundleListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ProductBundleService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductBundleListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    bundles.set([...MOCK_PRODUCT_BUNDLES]);
    loading.set(false);
    error.set(null);
  });

  it('loads bundles via ProductBundleService.list on init', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc');
    expect(compiled.textContent).toContain('Product Bundles');
    expect(component.tableRows().length).toBe(MOCK_PRODUCT_BUNDLES.length);
    expect(compiled.textContent).toContain('Starter Pack');
  });

  it('filters by product ID via listByProductId', () => {
    component.productIdFilter.set('10');
    component.applyProductIdFilter();
    fixture.detectChanges();

    expect(listByProductIdSpy).toHaveBeenCalledWith(10);
    expect(component.tableRows().every((r) => r.productId === '10')).toBe(true);
  });

  it('shows empty state when there are no bundles', () => {
    bundles.set([]);
    pagination.set({
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No product bundles yet');
  });

  it('shows error banner when service.error is set', () => {
    error.set('Failed to load bundles');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load bundles');
  });

  it('navigates to detail on row click', () => {
    component.onRowClick(component.tableRows()[0]);
    expect(router.navigate).toHaveBeenCalledWith(['/product-bundles', 1]);
  });

  it('opens create dialog and navigates on success', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of({ ...MOCK_PRODUCT_BUNDLES[0], id: 42 }),
    });

    component.openCreateDialog();

    expect(dialogOpen).toHaveBeenCalledWith(
      ProductBundleCreateDialogComponent,
      expect.objectContaining({ width: '520px', disableClose: true }),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/product-bundles', 42]);
  });
});
