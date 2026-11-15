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

  it('shows listPrice min mat-error when value is below 0', () => {
    fillForm({
      sku: 'SKU-1',
      name: 'Priced',
      productType: 'HARDWARE',
      category: 'ADD_ON',
      listPrice: -1,
    });
    component.productForm.listPrice().markAsTouched();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('List price must be at least 0');
  });

  // H-CPQ-P-015: mat-error nested in @if > @for is projected into the input's infix (clipped)
  // instead of mat-form-field's error slot, so the message is in the DOM but never visible.
  it('projects the SKU maxLength error into the form-field error slot (H-CPQ-P-015)', () => {
    fillForm({ sku: 'S'.repeat(101), name: 'Long', productType: 'HARDWARE', category: 'ADD_ON' });
    component.productForm.sku().markAsTouched();
    fixture.detectChanges();

    const slot = (fixture.nativeElement as HTMLElement).querySelector('.mat-mdc-form-field-error-wrapper');
    expect(slot?.textContent).toContain('SKU must be at most 100 characters');
  });

  it('projects required errors into the error slot too', () => {
    component.productForm.name().markAsTouched();
    fixture.detectChanges();

    const slots = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.mat-mdc-form-field-error-wrapper'));
    expect(slots.map((s) => s.textContent ?? '').join(' ')).toContain('Name is required');
  });

  // H-CPQ-P-014: required() accepts whitespace, so Create was enabled for "   ".
  it('treats a whitespace-only SKU or Name as missing (H-CPQ-P-014)', async () => {
    fillForm({ sku: '   ', name: '  ', productType: 'HARDWARE', category: 'ADD_ON' });
    fixture.detectChanges();

    expect(component.productForm().invalid()).toBe(true);
    expect(component.productForm.sku().errors().map((e) => e.message)).toContain('SKU is required');
    expect(component.productForm.name().errors().map((e) => e.message)).toContain('Name is required');
    await component.onSubmit();
    expect(createSpy).not.toHaveBeenCalled();
  });

  // Found by the Jev + Playwright MCP UAT (2026-11-15): Product type listed SAAS_FLAT_RATE,
  // ONPREM_PERPETUAL, ... and Category the raw enum too, although label maps exist in shared-models.
  async function optionTexts(label: string): Promise<string[]> {
    const trigger = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(`mat-select[aria-label="${label}"]`);
    trigger!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const texts = Array.from(document.querySelectorAll('mat-option')).map((o) => o.textContent!.trim());
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    (document.querySelector('.cdk-overlay-backdrop') as HTMLElement | null)?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    return texts;
  }

  it('lists product types and categories by their labels, not the raw enum', async () => {
    fixture.detectChanges();
    const types = await optionTexts('Product type');
    expect(types).toContain('SaaS Flat Rate');
    expect(types).not.toContain('SAAS_FLAT_RATE');
    const categories = await optionTexts('Category');
    expect(categories.length).toBeGreaterThan(0);
    expect(categories.every((c) => !/^[A-Z_]+$/.test(c))).toBe(true);
  });
});
