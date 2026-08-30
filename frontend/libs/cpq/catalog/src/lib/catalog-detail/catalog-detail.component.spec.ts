import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { CatalogDetailComponent } from './catalog-detail.component';
import { ProductService } from '../product.service';
import { MOCK_CATALOG_PRODUCTS } from '../catalog.mock';
import { ProductDto } from '@frontend/shared-models';

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

    const serviceMock = {
      selectedProduct,
      loading,
      error,
      products: signal<ProductDto[]>([]),
      getById: getByIdSpy,
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
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
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
});
