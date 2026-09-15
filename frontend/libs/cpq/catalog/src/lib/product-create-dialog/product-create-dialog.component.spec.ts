import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { ProductCreateDialogComponent, ProductCreateFormModel } from './product-create-dialog.component';
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

  function fillForm(patch: Partial<ProductCreateFormModel>): void {
    component.productModel.update((current) => ({ ...current, ...patch }));
  }

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
    expect('status' in component.productModel()).toBe(false);
    expect(component.productForm().valid()).toBe(false);

    fillForm({
      sku: 'NEW-SKU',
      name: 'New Catalog Product',
      productType: 'HARDWARE',
      category: 'ADD_ON',
    });
    expect(component.productForm().valid()).toBe(true);
  });

  it('creates via ProductService and closes with the product', async () => {
    createSpy.mockReturnValue(of(createdProduct));
    fillForm({
      sku: '  NEW-SKU  ',
      name: ' New Catalog Product ',
      productType: 'HARDWARE',
      category: 'ADD_ON',
      listPrice: 99,
      currency: 'usd',
    });

    await component.onSubmit();

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

  it('omits optional listPrice and currency when empty', async () => {
    createSpy.mockReturnValue(of(createdProduct));
    fillForm({
      sku: 'SKU-1',
      name: 'Minimal',
      productType: 'CUSTOM',
      category: 'SERVICES',
      listPrice: null,
      currency: '',
    });

    await component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      sku: 'SKU-1',
      name: 'Minimal',
      productType: 'CUSTOM',
      category: 'SERVICES',
    });
  });

  it('shows error banner when create fails and keeps dialog open', async () => {
    createSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'SKU already exists' } })),
    );
    fillForm({
      sku: 'DUP',
      name: 'Dup',
      productType: 'CUSTOM',
      category: 'ADD_ON',
    });

    await component.onSubmit();
    fixture.detectChanges();

    expect(dialogClose).not.toHaveBeenCalled();
    expect(component.error()).toBe('SKU already exists');
    expect(component.saving()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'SKU already exists',
    );
  });

  it('does not submit when form is invalid', async () => {
    await component.onSubmit();
    expect(createSpy).not.toHaveBeenCalled();
  });
});
