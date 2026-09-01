import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { BundleItemDialogComponent } from './bundle-item-dialog.component';
import { ProductBundleService } from '../product-bundle.service';
import { MOCK_PRODUCT_BUNDLES } from '../product-bundles.mock';

describe('BundleItemDialogComponent', () => {
  let fixture: ComponentFixture<BundleItemDialogComponent>;
  let component: BundleItemDialogComponent;
  let addItemSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    addItemSpy = vi.fn().mockReturnValue(of(MOCK_PRODUCT_BUNDLES[0]));
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [BundleItemDialogComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: ProductBundleService,
          useValue: { addItem: addItemSpy },
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
