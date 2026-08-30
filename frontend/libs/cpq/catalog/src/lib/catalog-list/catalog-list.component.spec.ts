import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CatalogListComponent } from './catalog-list.component';
import { MOCK_CATALOG_PRODUCTS } from '../catalog.mock';

describe('CatalogListComponent', () => {
  let fixture: ComponentFixture<CatalogListComponent>;
  let component: CatalogListComponent;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CatalogListComponent],
      providers: [provideRouter([]), provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(CatalogListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders page header and mock catalog rows', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Product Catalog');
    expect(component.tableRows().length).toBe(MOCK_CATALOG_PRODUCTS.length);
    expect(compiled.textContent).toContain('CRM-SEAT-NAMED');
  });

  it('filters by search term (name/SKU)', () => {
    component.onSearch('SUP-PREM');
    fixture.detectChanges();

    expect(component.tableRows().length).toBe(1);
    expect(component.tableRows()[0].sku).toBe('SUP-PREM');
  });

  it('filters by category and status', () => {
    component.onCategoryChange('SUBSCRIPTION');
    component.onStatusChange('ACTIVE');
    fixture.detectChanges();

    expect(component.tableRows().every((r) => r.category === 'SUBSCRIPTION')).toBe(
      true,
    );
    expect(component.tableRows().every((r) => r.status === 'ACTIVE')).toBe(true);
    expect(component.tableRows().length).toBe(2);
  });

  it('shows empty state when filters match nothing', () => {
    component.onSearch('zzz-no-match');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(component.isEmpty()).toBe(true);
    expect(compiled.textContent).toContain('No matching products');
  });

  it('shows empty state when catalog has no products', () => {
    component.setProductsForTest([]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No products yet');
  });

  it('shows error banner when error is set', () => {
    component.setErrorForTest('Failed to load catalog');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load catalog');

    component.clearError();
    fixture.detectChanges();
    expect(component.error()).toBeNull();
  });

  it('navigates to detail on row click', () => {
    component.onRowClick({
      id: 2,
      sku: 'CPQ-FLAT',
      name: 'CPQ Flat Rate',
      category: 'SUBSCRIPTION',
      status: 'ACTIVE',
      listPrice: 299,
    });

    expect(router.navigate).toHaveBeenCalledWith(['/catalog', 2]);
  });

  it('uses shared-ui data table (not inline MatTable happy path)', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-data-table')).toBeTruthy();
    expect(compiled.querySelector('app-page-header')).toBeTruthy();
    expect(compiled.querySelector('app-search-input')).toBeTruthy();
  });
});
