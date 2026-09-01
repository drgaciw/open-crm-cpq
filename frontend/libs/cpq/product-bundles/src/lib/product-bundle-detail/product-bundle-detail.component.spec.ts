import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ProductBundleDetailComponent } from './product-bundle-detail.component';
import {
  MOCK_PRODUCT_BUNDLES,
  findMockProductBundle,
} from '../product-bundles.mock';
import { ProductBundleService } from '../product-bundle.service';
import { ProductBundleDto } from '../product-bundle.model';
import { ConfirmDialogComponent } from '@frontend/shared-ui';
import { BundleItemDialogComponent } from '../bundle-item-dialog/bundle-item-dialog.component';

describe('ProductBundleDetailComponent', () => {
  let fixture: ComponentFixture<ProductBundleDetailComponent>;
  let component: ProductBundleDetailComponent;
  let router: Router;
  let getByIdSpy: ReturnType<typeof vi.fn>;
  let deleteSpy: ReturnType<typeof vi.fn>;
  let removeItemSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  const selected = MOCK_PRODUCT_BUNDLES[0];
  const selectedBundle = signal<ProductBundleDto | null>(selected);
  const loading = signal(false);
  const error = signal<string | null>(null);

  beforeEach(async () => {
    getByIdSpy = vi.fn((id: number) => {
      selectedBundle.set(findMockProductBundle(id) ?? null);
    });
    deleteSpy = vi.fn().mockReturnValue(of(void 0));
    removeItemSpy = vi.fn().mockReturnValue(of(void 0));
    dialogOpen = vi.fn().mockReturnValue({
      afterClosed: () => of(undefined),
    });

    const serviceMock = {
      selectedBundle,
      loading,
      error,
      getById: getByIdSpy,
      delete: deleteSpy,
      removeItem: removeItemSpy,
    };

    await TestBed.configureTestingModule({
      imports: [ProductBundleDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ProductBundleService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductBundleDetailComponent);
    fixture.componentRef.setInput('id', String(selected.id));
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    selectedBundle.set(selected);
    loading.set(false);
    error.set(null);
  });

  it('loads bundle via ProductBundleService.getById on init', () => {
    expect(getByIdSpy).toHaveBeenCalledWith(selected.id);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Starter Pack');
    expect(compiled.textContent).toContain('Items');
    expect(compiled.textContent).toContain('Add item');
  });

  it('renders items table rows from selected bundle', () => {
    expect(component.itemRows().length).toBe(selected.items?.length ?? 0);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('20');
  });

  it('opens add-item dialog and updates selectedBundle on save', () => {
    const updated: ProductBundleDto = {
      ...selected,
      items: [
        ...(selected.items ?? []),
        {
          id: 999,
          productId: 99,
          quantity: 1,
          optional: false,
          sortOrder: 2,
        },
      ],
    };
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(updated),
    });

    component.openAddItem();

    expect(dialogOpen).toHaveBeenCalledWith(
      BundleItemDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({ bundleId: selected.id }),
      }),
    );
    expect(selectedBundle()?.items?.length).toBe(2);
  });

  it('removes item after ConfirmDialog and reloads getById', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(true),
    });
    getByIdSpy.mockClear();

    component.confirmRemoveItem({
      id: 101,
      productId: 20,
      quantity: 1,
      optional: 'No',
      sortOrder: 0,
    });

    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          title: 'Remove item',
          confirmColor: 'warn',
        }),
      }),
    );
    expect(removeItemSpy).toHaveBeenCalledWith(selected.id, 101);
    expect(getByIdSpy).toHaveBeenCalledWith(selected.id);
  });

  it('surfaces BUNDLE_MIN_ITEMS_REQUIRED on remove failure', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(true),
    });
    removeItemSpy.mockReturnValueOnce(
      throwError(() => ({
        message: 'Bundle requires minimum 1 items',
        errorCode: 'BUNDLE_MIN_ITEMS_REQUIRED',
      })),
    );

    component.confirmRemoveItem({
      id: 101,
      productId: 20,
      quantity: 1,
      optional: 'No',
      sortOrder: 0,
    });
    fixture.detectChanges();

    expect(component.bannerError()).toContain('BUNDLE_MIN_ITEMS_REQUIRED');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('BUNDLE_MIN_ITEMS_REQUIRED');
  });

  it('deletes bundle after ConfirmDialog and navigates to list', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(true),
    });

    component.confirmDeleteBundle();

    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          title: 'Delete bundle',
          confirmText: 'Delete',
          confirmColor: 'warn',
        }),
      }),
    );
    expect(deleteSpy).toHaveBeenCalledWith(selected.id);
    expect(router.navigate).toHaveBeenCalledWith(['/product-bundles']);
  });

  it('does not delete when ConfirmDialog is cancelled', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(false),
    });

    component.confirmDeleteBundle();

    expect(deleteSpy).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
