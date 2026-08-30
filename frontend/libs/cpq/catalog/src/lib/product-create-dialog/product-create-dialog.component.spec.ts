import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { ProductCreateDialogComponent } from './product-create-dialog.component';
import { ProductService } from '../product.service';
import { ProductCreateRequest, ProductDto } from '@frontend/shared-models';

describe('ProductCreateDialogComponent', () => {
  let fixture: ComponentFixture<ProductCreateDialogComponent>;
  let component: ProductCreateDialogComponent;
  let createSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  const createdProduct: ProductDto = {
    id: 42,
    uuid: 'u-42',
    sku: 'NEW-SKU',
    name: 'New Catalog Product',
    productType: 'HARDWARE',
    category: 'ADD_ON',
    status: 'DRAFT',
    listPrice: 99,
    currency: 'USD',
    configurable: false,
    bundle: false,
    createdAt: '',
    updatedAt: '',
  };

  beforeEach(async () => {
    createSpy = vi.fn();
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [ProductCreateDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: ProductService, useValue: { create: createSpy } },
        {
          provide: MatDialogRef,
          useValue: { close: dialogClose },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductCreateDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requires sku, name, productType, and category (no status control)', () => {
    expect(component.form.contains('status')).toBe(false);
    expect(component.form.valid).toBe(false);

    component.form.patchValue({
      sku: 'NEW-SKU',
      name: 'New Catalog Product',
      productType: 'HARDWARE',
      category: 'ADD_ON',
    });
    expect(component.form.valid).toBe(true);
  });

  it('creates via ProductService and closes with the product', () => {
    createSpy.mockReturnValue(of(createdProduct));
    component.form.patchValue({
      sku: '  NEW-SKU  ',
      name: ' New Catalog Product ',
      productType: 'HARDWARE',
      category: 'ADD_ON',
      listPrice: 99,
      currency: 'usd',
    });

    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      sku: 'NEW-SKU',
      name: 'New Catalog Product',
      productType: 'HARDWARE',
      category: 'ADD_ON',
      listPrice: 99,
      currency: 'USD',
    } satisfies ProductCreateRequest);
    expect(dialogClose).toHaveBeenCalledWith(createdProduct);
    expect(component.saving()).toBe(false);
    expect(component.error()).toBeNull();
  });

  it('omits optional listPrice and currency when empty', () => {
    createSpy.mockReturnValue(of(createdProduct));
    component.form.patchValue({
      sku: 'SKU-1',
      name: 'Minimal',
      productType: 'CUSTOM',
      category: 'SERVICES',
      listPrice: null,
      currency: '',
    });

    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      sku: 'SKU-1',
      name: 'Minimal',
      productType: 'CUSTOM',
      category: 'SERVICES',
    });
  });

  it('shows error banner when create fails and keeps dialog open', () => {
    createSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'SKU already exists' } })),
    );
    component.form.patchValue({
      sku: 'DUP',
      name: 'Dup',
      productType: 'CUSTOM',
      category: 'ADD_ON',
    });

    component.onSubmit();
    fixture.detectChanges();

    expect(dialogClose).not.toHaveBeenCalled();
    expect(component.error()).toBe('SKU already exists');
    expect(component.saving()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'SKU already exists',
    );
  });

  it('does not submit when form is invalid', () => {
    component.onSubmit();
    expect(createSpy).not.toHaveBeenCalled();
  });
});
