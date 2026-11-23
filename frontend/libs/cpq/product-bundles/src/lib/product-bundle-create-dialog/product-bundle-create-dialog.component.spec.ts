import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { ProductBundleCreateDialogComponent } from './product-bundle-create-dialog.component';
import { ProductBundleService } from '../product-bundle.service';
import { MOCK_PRODUCT_BUNDLES } from '../product-bundles.mock';

describe('ProductBundleCreateDialogComponent', () => {
  let fixture: ComponentFixture<ProductBundleCreateDialogComponent>;
  let component: ProductBundleCreateDialogComponent;
  let createSpy: ReturnType<typeof vi.fn>;
  let updateSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    createSpy = vi.fn().mockReturnValue(of(MOCK_PRODUCT_BUNDLES[0]));
    updateSpy = vi.fn().mockReturnValue(of(MOCK_PRODUCT_BUNDLES[0]));
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [ProductBundleCreateDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: MAT_DIALOG_DATA, useValue: {} },
        {
          provide: ProductBundleService,
          useValue: { create: createSpy, update: updateSpy },
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

  it('edits bundle details without submitting its parent product identity', () => {
    TestBed.inject(MAT_DIALOG_DATA).bundle = {
      id: 7, productId: 45, name: 'Pack', description: 'Old details',
      discountType: 'NONE', minItems: 0, maxItems: 3,
    };
    fixture.destroy();
    fixture = TestBed.createComponent(ProductBundleCreateDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.form.controls.productId.disabled).toBe(true);
    expect(component.form.controls.name.value).toBe('Pack');
    component.form.patchValue({ name: 'Edited pack', description: '' });
    component.onSubmit();

    expect(updateSpy).toHaveBeenCalledWith(7, {
      name: 'Edited pack', discountType: 'NONE', minItems: 0, maxItems: 3,
    });
    expect(createSpy).not.toHaveBeenCalled();
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

  it('applies cdkTextareaAutosize with min/max rows to the description textarea', () => {
    const textareaDe = fixture.debugElement.query(By.directive(CdkTextareaAutosize));
    expect(textareaDe).toBeTruthy();
    const autosize = textareaDe.injector.get(CdkTextareaAutosize);
    expect(autosize.minRows).toBe(3);
    expect(autosize.maxRows).toBe(8);
  });
});
