import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { CatalogDetailComponent } from './catalog-detail.component';
import { ProductService } from '../product.service';
import { MOCK_CATALOG_PRODUCTS } from '../catalog.mock';
import { ProductDto, ProductUpdateRequest } from '@frontend/shared-models';

function toProductDto(
  mock: (typeof MOCK_CATALOG_PRODUCTS)[number],
): ProductDto {
  return {
    id: mock.id,
    uuid: mock.uuid,
    sku: mock.sku,
    name: mock.name,
    description: mock.description,
    productType: mock.productType as ProductDto['productType'],
    category: mock.category,
    status: mock.status,
    listPrice: mock.listPrice,
    currency: mock.currency,
    configurable: false,
    bundle: false,
    createdAt: '',
    updatedAt: '',
  };
}

describe('CatalogDetailComponent', () => {
  let fixture: ComponentFixture<CatalogDetailComponent>;
  let component: CatalogDetailComponent;
  let router: Router;
  let getByIdSpy: ReturnType<typeof vi.fn>;
  let updateSpy: ReturnType<typeof vi.fn>;
  let navigateSpy: ReturnType<typeof vi.fn>;

  const selectedProduct = signal<ProductDto | null>(null);
  const loading = signal(false);
  const error = signal<string | null>(null);

  beforeEach(async () => {
    getByIdSpy = vi.fn((id: number) => {
      const found = MOCK_CATALOG_PRODUCTS.find((p) => p.id === id);
      selectedProduct.set(found ? toProductDto(found) : null);
      if (!found) {
        error.set('Product not found');
      }
      loading.set(false);
    });
    updateSpy = vi.fn();

    const serviceMock = {
      selectedProduct,
      loading,
      error,
      products: signal<ProductDto[]>([]),
      getById: getByIdSpy,
      update: updateSpy,
    };

    await TestBed.configureTestingModule({
      imports: [CatalogDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ProductService, useValue: serviceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CatalogDetailComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentRef.setInput('id', '1');
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    selectedProduct.set(null);
    loading.set(false);
    error.set(null);
  });

  it('loads product via ProductService.getById', () => {
    expect(getByIdSpy).toHaveBeenCalledWith(1);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('CRM Named Seat');
    expect(compiled.textContent).toContain('CRM-SEAT-NAMED');
    expect(compiled.textContent).not.toContain('mock data');
  });

  it('shows empty state when product is missing', () => {
    fixture.componentRef.setInput('id', '999');
    getByIdSpy(999);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Product not found');
    expect(compiled.textContent).not.toContain('mock data set');
  });

  it('navigates back to catalog', () => {
    component.goBack();
    expect(router.navigate).toHaveBeenCalledWith(['/catalog']);
  });

  it('enters edit mode with ProductUpdateRequest fields populated', () => {
    component.startEdit();
    fixture.detectChanges();

    expect(component.editing()).toBe(true);
    expect(component.form.value).toMatchObject({
      name: 'CRM Named Seat',
      description: 'Per-user named seat subscription for the CRM portal.',
      productType: 'SAAS_SEAT_NAMED',
      category: 'SUBSCRIPTION',
      status: 'ACTIVE',
      listPrice: 49,
      currency: 'USD',
    });
    expect(component.form.contains('sku')).toBe(false);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Edit product');
    expect(compiled.textContent).toContain('Save');
  });

  it('saves via ProductService.update, stays on detail, and exits edit mode', () => {
    const updated: ProductDto = {
      ...toProductDto(MOCK_CATALOG_PRODUCTS[0]),
      name: 'CRM Named Seat Pro',
      listPrice: 59,
      status: 'DEPRECATED',
    };
    updateSpy.mockReturnValue(of(updated));

    component.startEdit();
    component.form.patchValue({
      name: '  CRM Named Seat Pro  ',
      description: 'Updated description',
      productType: 'SAAS_SEAT_NAMED',
      category: 'SUBSCRIPTION',
      status: 'DEPRECATED',
      listPrice: 59,
      currency: 'usd',
    });

    component.onSave();
    fixture.detectChanges();

    expect(updateSpy).toHaveBeenCalledWith(1, {
      name: 'CRM Named Seat Pro',
      description: 'Updated description',
      productType: 'SAAS_SEAT_NAMED',
      category: 'SUBSCRIPTION',
      status: 'DEPRECATED',
      listPrice: 59,
      currency: 'USD',
    } satisfies ProductUpdateRequest);
    expect(selectedProduct()?.name).toBe('CRM Named Seat Pro');
    expect(component.editing()).toBe(false);
    expect(component.saving()).toBe(false);
    expect(component.saveError()).toBeNull();
    expect(navigateSpy).not.toHaveBeenCalled();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('CRM Named Seat Pro');
    expect(compiled.textContent).toContain('Product details');
  });

  it('omits empty description and currency from update payload', () => {
    updateSpy.mockReturnValue(
      of({
        ...toProductDto(MOCK_CATALOG_PRODUCTS[0]),
        name: 'Minimal Update',
        description: undefined,
        currency: undefined,
      }),
    );

    component.startEdit();
    component.form.patchValue({
      name: 'Minimal Update',
      description: '   ',
      listPrice: null,
      currency: '',
    });

    component.onSave();

    expect(updateSpy).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        name: 'Minimal Update',
        productType: 'SAAS_SEAT_NAMED',
        category: 'SUBSCRIPTION',
        status: 'ACTIVE',
      }),
    );
    const payload = updateSpy.mock.calls[0][1] as ProductUpdateRequest;
    expect(payload.description).toBeUndefined();
    expect(payload.currency).toBeUndefined();
    expect(payload.listPrice).toBeUndefined();
  });

  it('shows error banner on save failure and stays in edit mode', () => {
    updateSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'Name already taken' } })),
    );

    component.startEdit();
    component.form.patchValue({ name: 'Dup Name' });
    component.onSave();
    fixture.detectChanges();

    expect(component.editing()).toBe(true);
    expect(component.saving()).toBe(false);
    expect(component.saveError()).toBe('Name already taken');
    expect(navigateSpy).not.toHaveBeenCalled();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Name already taken');
  });

  it('does not save when form is invalid', () => {
    component.startEdit();
    component.form.patchValue({ name: '' });
    component.onSave();
    expect(updateSpy).not.toHaveBeenCalled();
  });

  it('cancelEdit exits edit mode without calling update', () => {
    component.startEdit();
    component.form.patchValue({ name: 'Changed' });
    component.cancelEdit();
    fixture.detectChanges();

    expect(component.editing()).toBe(false);
    expect(updateSpy).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Product details',
    );
  });
});
