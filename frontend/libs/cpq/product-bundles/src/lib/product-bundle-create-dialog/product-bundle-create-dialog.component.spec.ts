import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { ProductBundleCreateDialogComponent } from './product-bundle-create-dialog.component';
import { ProductBundleService } from '../product-bundle.service';
import { MOCK_PRODUCT_BUNDLES } from '../product-bundles.mock';

describe('ProductBundleCreateDialogComponent', () => {
  let fixture: ComponentFixture<ProductBundleCreateDialogComponent>;
  let component: ProductBundleCreateDialogComponent;
  let createSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    createSpy = vi.fn().mockReturnValue(of(MOCK_PRODUCT_BUNDLES[0]));
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [ProductBundleCreateDialogComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: ProductBundleService,
          useValue: { create: createSpy },
        },
        {
          provide: MatDialogRef,
          useValue: { close: dialogClose },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductBundleCreateDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('requires productId and name', () => {
    expect(component.form.invalid).toBe(true);
    component.form.patchValue({ productId: 10, name: 'Pack' });
    expect(component.form.valid).toBe(true);
  });

  it('creates bundle and closes dialog on success', () => {
    component.form.patchValue({
      productId: 10,
      name: 'Pack',
      description: 'desc',
      discountType: 'PERCENTAGE',
      discountValue: 5,
      minItems: 1,
      maxItems: 3,
    });

    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      productId: 10,
      name: 'Pack',
      description: 'desc',
      discountType: 'PERCENTAGE',
      discountValue: 5,
      minItems: 1,
      maxItems: 3,
    });
    expect(dialogClose).toHaveBeenCalledWith(MOCK_PRODUCT_BUNDLES[0]);
  });

  it('surfaces error banner on create failure', () => {
    createSpy.mockReturnValueOnce(
      throwError(() => ({
        message: 'Product not found',
        errorCode: 'RESOURCE_NOT_FOUND',
      })),
    );
    component.form.patchValue({ productId: 10, name: 'Pack' });
    component.onSubmit();
    fixture.detectChanges();

    expect(component.error()).toBe('Product not found [RESOURCE_NOT_FOUND]');
    expect(dialogClose).not.toHaveBeenCalled();
  });
});
