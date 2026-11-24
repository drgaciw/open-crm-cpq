import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { CdkTree, CdkTreeNodeToggle } from '@angular/cdk/tree';
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
  const detailReadFailure = signal<{ id: number; status: number; message: string } | null>(null);

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
      detailReadFailure,
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
    detailReadFailure.set(null);
  });

  it('loads bundle via ProductBundleService.getById on init', () => {
    expect(getByIdSpy).toHaveBeenCalledWith(selected.id);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Starter Pack');
    expect(compiled.textContent).toContain('Items');
    expect(compiled.textContent).toContain('Add item');
  });

  it('names the indicator while waiting for the bundle detail', () => {
    selectedBundle.set(null);
    loading.set(true);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="progressbar"]')?.getAttribute('aria-label')).toBe('Loading bundle details');
    expect(host.querySelector('app-empty-state')).toBeNull();
  });

  it('offers Retry for a transient read failure without saying the bundle is missing', () => {
    selectedBundle.set(null);
    detailReadFailure.set({ id: selected.id, status: 503, message: 'Bundle service unavailable' });
    error.set('Bundle service unavailable');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const alert = host.querySelector('[role="alert"]');
    expect(host.textContent).not.toContain('Bundle not found');
    expect(alert?.textContent).toContain('Bundle service unavailable');
    getByIdSpy.mockClear();
    const retry = [...(alert?.querySelectorAll('button') ?? [])].find(button => button.textContent?.trim() === 'Retry');
    expect(retry).toBeTruthy();
    retry?.click();
    expect(getByIdSpy).toHaveBeenCalledOnce();
    expect(getByIdSpy).toHaveBeenCalledWith(selected.id);
  });

  it('keeps the existing not-found card for an actual 404', () => {
    selectedBundle.set(null);
    detailReadFailure.set({ id: selected.id, status: 404, message: 'Bundle missing' });
    error.set('Bundle missing');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('app-empty-state')?.textContent).toContain('Bundle not found');
    expect(host.querySelector('[role="alert"]')).toBeNull();
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

  describe('CdkTree bundle hierarchy (Phase 5.3)', () => {
    it('renders CdkTree with role=tree and Bundle -> Option Groups -> Options hierarchy', () => {
      selectedBundle.set({
        ...selected,
        items: [
          { id: 101, productId: 20, quantity: 1, optional: false, sortOrder: 0 },
          { id: 102, productId: 21, quantity: 2, optional: true, sortOrder: 1 },
        ],
      });
      fixture.detectChanges();

      const treeDe = fixture.debugElement.query(By.directive(CdkTree));
      expect(treeDe).toBeTruthy();

      const host = fixture.nativeElement as HTMLElement;
      const treeEl = host.querySelector('[data-testid="bundle-tree"]');
      expect(treeEl?.getAttribute('role')).toBe('tree');

      const treeItems = host.querySelectorAll('[role="treeitem"]');
      expect(treeItems.length).toBeGreaterThanOrEqual(1);

      const data = component.treeData();
      expect(data).toHaveLength(1);
      expect(data[0].kind).toBe('bundle');
      expect(data[0].label).toBe('Starter Pack');
      expect(data[0].children?.map((g) => g.label)).toEqual([
        'Required options',
        'Optional options',
      ]);
      expect(data[0].children?.[0].children?.[0].label).toBe('Product #20');
      expect(data[0].children?.[1].children?.[0].label).toBe('Product #21');
    });

    it('refreshes nested item views after edit and retains existing expansions', () => {
      const moved = { id: 101, productId: 20, quantity: 1, optional: false, sortOrder: 0 };
      const required = { id: 102, productId: 21, quantity: 2, optional: false, sortOrder: 1 };
      const optional = { id: 103, productId: 22, quantity: 3, optional: true, sortOrder: 2 };
      const original = { ...selected, items: [moved, required, optional] };
      fixture.destroy();
      getByIdSpy.mockImplementationOnce(() => selectedBundle.set(original));
      fixture = TestBed.createComponent(ProductBundleDetailComponent);
      fixture.componentRef.setInput('id', String(original.id));
      component = fixture.componentInstance;
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      const toggle = (label: string) => host.querySelector<HTMLButtonElement>(`[aria-label="Toggle ${label}"]`)!;
      const branch = (label: string) => toggle(label).closest('[role="treeitem"]')!;
      for (const label of [original.name, 'Required options', 'Optional options']) {
        toggle(label).click();
        fixture.detectChanges();
        expect(branch(label).getAttribute('aria-expanded')).toBe('true');
      }

      const updated = { ...original, items: [{ ...moved, quantity: 4, optional: true, sortOrder: 7 }, required, optional] };
      dialogOpen.mockReturnValueOnce({ afterClosed: () => of(updated) });
      component.openEditItem(component.itemRows()[0]);
      fixture.detectChanges();

      expect(branch(original.name).getAttribute('aria-expanded')).toBe('true');
      expect(branch('Required options').getAttribute('aria-expanded')).toBe('true');
      expect(branch('Optional options').getAttribute('aria-expanded')).toBe('true');
      const requiredBranch = branch('Required options');
      const optionalBranch = branch('Optional options');
      expect(requiredBranch.textContent).toContain('Product #21');
      expect(requiredBranch.textContent).not.toContain('Product #20');
      expect(optionalBranch.textContent).toContain('Product #20');
      expect(optionalBranch.textContent).toContain('Qty 4 · Sort 7');
    });

    it('expands and collapses branch nodes using cdkTreeNodeToggle', () => {
      fixture.detectChanges();

      const toggleDe = fixture.debugElement.query(By.directive(CdkTreeNodeToggle));
      expect(toggleDe).toBeTruthy();

      const tree = fixture.debugElement.query(By.directive(CdkTree)).componentInstance as CdkTree<unknown>;
      const rootNode = component.treeData()[0];

      expect(tree.isExpanded(rootNode)).toBe(false);
      (toggleDe.nativeElement as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(tree.isExpanded(rootNode)).toBe(true);

      (toggleDe.nativeElement as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(tree.isExpanded(rootNode)).toBe(false);
    });
  });
});
