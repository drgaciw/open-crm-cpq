import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';
import { BundleItemDialogComponent } from './bundle-item-dialog.component';
import { ProductBundleService } from '../product-bundle.service';
import { MOCK_PRODUCT_BUNDLES } from '../product-bundles.mock';

describe('BundleItemDialogComponent', () => {
  let fixture: ComponentFixture<BundleItemDialogComponent>;
  let component: BundleItemDialogComponent;
  let addItemSpy: ReturnType<typeof vi.fn>;
  let updateItemSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    addItemSpy = vi.fn().mockReturnValue(of(MOCK_PRODUCT_BUNDLES[0]));
    updateItemSpy = vi.fn().mockReturnValue(of(MOCK_PRODUCT_BUNDLES[0]));
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [BundleItemDialogComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: ProductBundleService,
          useValue: { addItem: addItemSpy, updateItem: updateItemSpy },
        },
        {
          provide: MatDialogRef,
          useValue: { close: dialogClose },
        },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { bundleId: 1 },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BundleItemDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('requires productId', () => {
    expect(component.form.invalid).toBe(true);
    component.form.patchValue({ productId: 20 });
    expect(component.form.valid).toBe(true);
  });

  it('adds item and closes dialog on success', () => {
    component.form.patchValue({
      productId: 20,
      quantity: 2,
      optional: true,
      sortOrder: 3,
    });

    component.onSubmit();

    expect(addItemSpy).toHaveBeenCalledWith(1, {
      productId: 20,
      quantity: 2,
      optional: true,
      sortOrder: 3,
    });
    expect(dialogClose).toHaveBeenCalledWith(MOCK_PRODUCT_BUNDLES[0]);
  });

  it('prefills the existing item and saves only its editable fields', () => {
    TestBed.inject(MAT_DIALOG_DATA).item = {
      id: 8, productId: 20, quantity: 2, optional: false, sortOrder: 3,
    };
    fixture.destroy();
    fixture = TestBed.createComponent(BundleItemDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.form.getRawValue()).toEqual({ productId: 20, quantity: 2, optional: false, sortOrder: 3 });
    expect(component.form.controls.productId.disabled).toBe(true);
    component.form.patchValue({ quantity: 4, optional: true, sortOrder: 5 });
    component.onSubmit();

    expect(updateItemSpy).toHaveBeenCalledWith(1, 8, { quantity: 4, optional: true, sortOrder: 5 });
    expect(addItemSpy).not.toHaveBeenCalled();
    expect(dialogClose).toHaveBeenCalledWith(MOCK_PRODUCT_BUNDLES[0]);
  });

  it.each(['Add', 'Save'] as const)('keeps the pending %s button and progressbar named', (action) => {
    const pending = new Subject<(typeof MOCK_PRODUCT_BUNDLES)[number]>();
    if (action === 'Save') {
      TestBed.inject(MAT_DIALOG_DATA).item = { id: 8, productId: 20, quantity: 2, optional: false, sortOrder: 3 };
      fixture.destroy();
      fixture = TestBed.createComponent(BundleItemDialogComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      updateItemSpy.mockReturnValueOnce(pending);
    } else {
      component.form.patchValue({ productId: 20 });
      addItemSpy.mockReturnValueOnce(pending);
    }

    component.onSubmit();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const submit = host.querySelector<HTMLButtonElement>('mat-dialog-actions button:last-child');
    expect(submit?.disabled).toBe(true);
    expect(submit?.getAttribute('aria-label')).toBe(action);
    expect(submit?.querySelector('[role="progressbar"]')?.getAttribute('aria-label')).toBe('Saving bundle item');
  });

  it('surfaces BUNDLE_MAX_ITEMS_EXCEEDED on add failure', () => {
    addItemSpy.mockReturnValueOnce(
      throwError(() => ({
        message: 'Bundle has reached maximum items (5)',
        errorCode: 'BUNDLE_MAX_ITEMS_EXCEEDED',
      })),
    );
    component.form.patchValue({ productId: 20 });
    component.onSubmit();
    fixture.detectChanges();

    expect(component.error()).toContain('BUNDLE_MAX_ITEMS_EXCEEDED');
    expect(dialogClose).not.toHaveBeenCalled();
  });
});
