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

  it('rejects a fractional product ID before starting a lookup', () => {
    component.productIdFilter.set('2.5');
    component.applyProductIdFilter();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect.soft(listByProductIdSpy).not.toHaveBeenCalled();
    expect(host.querySelector('[role="alert"]')?.textContent ?? '')
      .toContain('Product ID must be a positive whole number');
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
    const card = compiled.querySelector('app-empty-state');
    expect(card?.textContent).toContain('No product bundles yet');
    expect(card?.querySelector('button')?.textContent?.trim()).toBe('New Bundle');
  });

  it('shows a confirmed product no-match without the first-bundle action', () => {
    component.productIdFilter.set('999');
    component.applyProductIdFilter();
    fixture.detectChanges();

    const card = (fixture.nativeElement as HTMLElement).querySelector('app-empty-state');
    expect(card?.textContent).toContain('No matching bundles');
    expect(card?.querySelector('button')).toBeNull();
  });

  it('shows the initial list error without claiming that there are no bundles', () => {
    bundles.set([]);
    error.set('Unable to load bundles');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Unable to load bundles');
    expect(host.querySelector('app-empty-state')).toBeNull();
  });

  it('dismisses a failed first read without creating an empty state or reloading', () => {
    bundles.set([]);
    error.set('Unable to load bundles');
    listSpy.mockClear();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const dismiss = host.querySelector<HTMLButtonElement>('[role="alert"] button');
    expect(dismiss?.textContent?.trim()).toBe('Dismiss');
    dismiss?.click();
    fixture.detectChanges();

    expect(host.querySelector('[role="alert"]')).toBeNull();
    expect(host.querySelector('app-empty-state')).toBeNull();
    expect(host.querySelector('app-data-table')).toBeNull();
    expect(host.querySelector('app-page-header')?.textContent).toContain('New Bundle');
    expect(listSpy).not.toHaveBeenCalled();
  });

  it('keeps the confirmed first-bundle action after dismissing invalid filter input', () => {
    bundles.set([]);
    component.productIdFilter.set('-1');
    component.applyProductIdFilter();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Product ID must be a positive whole number');
    component.clearError();
    fixture.detectChanges();

    expect(host.querySelector('app-empty-state button')?.textContent?.trim()).toBe('New Bundle');
    expect(listByProductIdSpy).not.toHaveBeenCalled();
  });

  it('keeps rows already loaded after dismissing a refresh error', () => {
    error.set('Unable to refresh bundles');
    fixture.detectChanges();
    component.clearError();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="alert"]')).toBeNull();
    expect(host.querySelector('app-data-table')?.textContent).toContain('Starter Pack');
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
