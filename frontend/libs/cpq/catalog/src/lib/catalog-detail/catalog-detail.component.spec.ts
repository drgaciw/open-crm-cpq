import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { CatalogDetailComponent } from './catalog-detail.component';
import { ProductService } from '../product.service';
import { ProductOptionService } from '../product-option.service';
import { ProductAttributeService } from '../product-attribute.service';
import { ConfigurationRuleService } from '../configuration-rule.service';
import { MOCK_CATALOG_PRODUCTS } from '../catalog.mock';
import {
  AttributeValueDto,
  ProductAttributeDto,
  ProductDto,
  ProductOptionGroupDto,
  ProductUpdateRequest,
  SpringPageResponse,
} from '@frontend/shared-models';
import { ConfirmDialogComponent } from '@frontend/shared-ui';
import { OptionGroupCreateDialogComponent } from '../option-group-create-dialog/option-group-create-dialog.component';
import { OptionCreateDialogComponent } from '../option-create-dialog/option-create-dialog.component';

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

const SAMPLE_GROUP: ProductOptionGroupDto = {
  id: 50,
  uuid: 'g-50',
  productId: 1,
  name: 'Tier features',
  selectionType: 'MULTIPLE',
  minSelections: 0,
  maxSelections: 3,
  sortOrder: 0,
  options: [
    {
      id: 501,
      uuid: 'o-501',
      productId: 1,
      name: 'SSO',
      defaultOption: false,
      sortOrder: 0,
      priceAdjustment: 10,
      adjustmentType: 'ABSOLUTE',
    },
  ],
};

const SAMPLE_VALUE: AttributeValueDto = {
  id: 70,
  uuid: 'v-70',
  productId: 1,
  attributeId: 9,
  attributeName: 'region',
  value: 'US',
};

const SAMPLE_DEF: ProductAttributeDto = {
  id: 9,
  uuid: 'a-9',
  name: 'region',
  displayName: 'Region',
  dataType: 'STRING',
  required: false,
  sortOrder: 0,
};

describe('CatalogDetailComponent', () => {
  let fixture: ComponentFixture<CatalogDetailComponent>;
  let component: CatalogDetailComponent;
  let router: Router;
  let getByIdSpy: ReturnType<typeof vi.fn>;
  let updateSpy: ReturnType<typeof vi.fn>;
  let deleteSpy: ReturnType<typeof vi.fn>;
  let listGroupsSpy: ReturnType<typeof vi.fn>;
  let deleteGroupSpy: ReturnType<typeof vi.fn>;
  let listValuesSpy: ReturnType<typeof vi.fn>;
  let listDefinitionsSpy: ReturnType<typeof vi.fn>;
  let setValueSpy: ReturnType<typeof vi.fn>;
  let listConfigRulesSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;
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
    deleteSpy = vi.fn(() => of(undefined));
    listGroupsSpy = vi.fn(() => of([SAMPLE_GROUP]));
    deleteGroupSpy = vi.fn(() => of(undefined));
    listValuesSpy = vi.fn(() => of([SAMPLE_VALUE]));
    listDefinitionsSpy = vi.fn(() =>
      of({
        content: [SAMPLE_DEF],
        number: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      } satisfies SpringPageResponse<ProductAttributeDto>),
    );
    setValueSpy = vi.fn();
    listConfigRulesSpy = vi.fn(() => of([]));
    dialogOpen = vi.fn(() => ({
      afterClosed: () => of(true),
    }));

    const serviceMock = {
      selectedProduct,
      loading,
      error,
      products: signal<ProductDto[]>([]),
      getById: getByIdSpy,
      update: updateSpy,
      delete: deleteSpy,
    };

    const optionServiceMock = {
      listGroupsByProduct: listGroupsSpy,
      deleteGroup: deleteGroupSpy,
      createGroup: vi.fn(),
      addOption: vi.fn(),
    };

    const attributeServiceMock = {
      listValues: listValuesSpy,
      listDefinitions: listDefinitionsSpy,
      setValue: setValueSpy,
    };

    const configRuleServiceMock = {
      listByProduct: listConfigRulesSpy,
      create: vi.fn(),
      delete: vi.fn(() => of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [CatalogDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ProductService, useValue: serviceMock },
        { provide: ProductOptionService, useValue: optionServiceMock },
        { provide: ProductAttributeService, useValue: attributeServiceMock },
        { provide: ConfigurationRuleService, useValue: configRuleServiceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
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

  it('loads option groups and attribute values for the product', () => {
    expect(listGroupsSpy).toHaveBeenCalledWith(1);
    expect(listValuesSpy).toHaveBeenCalledWith(1);
    expect(listDefinitionsSpy).toHaveBeenCalled();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Option groups');
    expect(compiled.textContent).toContain('Tier features');
    expect(compiled.textContent).toContain('SSO');
    expect(compiled.textContent).toContain('Attribute values');
    expect(compiled.textContent).toContain('region');
    expect(compiled.textContent).toContain('US');
  });

  it('hosts extracted config-rules panel for the product', () => {
    expect(listConfigRulesSpy).toHaveBeenCalledWith(1);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('cpq-config-rules-panel')).toBeTruthy();
    expect(compiled.textContent).toContain('Configuration rules');
    expect(compiled.textContent?.toLowerCase()).not.toContain('validate configuration');
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
      throwError(() => ({
        error: {
          message: 'Name already taken',
          errorCode: 'PRODUCT_NAME_CONFLICT',
        },
      })),
    );

    component.startEdit();
    component.form.patchValue({ name: 'Dup Name' });
    component.onSave();
    fixture.detectChanges();

    expect(component.editing()).toBe(true);
    expect(component.saving()).toBe(false);
    expect(component.saveError()).toBe(
      'Name already taken [PRODUCT_NAME_CONFLICT]',
    );
    expect(navigateSpy).not.toHaveBeenCalled();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain(
      'Name already taken [PRODUCT_NAME_CONFLICT]',
    );
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

  it('renders Delete button with icon in view mode and deletes on click', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const deleteBtn = Array.from(
      compiled.querySelectorAll('button'),
    ).find((btn) => btn.textContent?.includes('Delete'));

    expect(deleteBtn).toBeTruthy();
    expect(deleteBtn?.querySelector('mat-icon')?.textContent?.trim()).toBe(
      'delete',
    );
    expect(compiled.textContent).not.toContain('Save');

    deleteBtn?.click();
    fixture.detectChanges();

    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          title: 'Delete product',
          confirmText: 'Delete',
          confirmColor: 'warn',
        }),
      }),
    );
    expect(deleteSpy).toHaveBeenCalledWith(1);
    expect(selectedProduct()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/catalog']);
    expect(component.deleting()).toBe(false);
    expect(component.saveError()).toBeNull();
  });

  it('renders Save button with icon in edit mode and saves on click', () => {
    const updated: ProductDto = {
      ...toProductDto(MOCK_CATALOG_PRODUCTS[0]),
      name: 'CRM Named Seat Saved',
    };
    updateSpy.mockReturnValue(of(updated));

    component.startEdit();
    component.form.patchValue({ name: 'CRM Named Seat Saved' });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const saveBtn = Array.from(compiled.querySelectorAll('button')).find(
      (btn) => btn.textContent?.includes('Save'),
    );

    expect(saveBtn).toBeTruthy();
    expect(saveBtn?.querySelector('mat-icon')?.textContent?.trim()).toBe(
      'save',
    );
    const headerDeleteBtn = Array.from(
      compiled.querySelectorAll('.header-actions button'),
    ).find((btn) => btn.textContent?.includes('Delete'));
    expect(headerDeleteBtn).toBeUndefined();

    saveBtn?.click();
    fixture.detectChanges();

    expect(updateSpy).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ name: 'CRM Named Seat Saved' }),
    );
    expect(component.editing()).toBe(false);
    expect(component.saving()).toBe(false);
    expect(selectedProduct()?.name).toBe('CRM Named Seat Saved');
  });

  it('confirms then soft-deletes via ProductService.delete and navigates to list', () => {
    component.onDelete();

    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          title: 'Delete product',
          confirmText: 'Delete',
          confirmColor: 'warn',
        }),
      }),
    );
    expect(deleteSpy).toHaveBeenCalledWith(1);
    expect(selectedProduct()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/catalog']);
    expect(component.deleting()).toBe(false);
    expect(component.saveError()).toBeNull();
  });

  it('does not delete when ConfirmDialog is cancelled', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(false),
    });

    component.onDelete();

    expect(dialogOpen).toHaveBeenCalled();
    expect(deleteSpy).not.toHaveBeenCalled();
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('shows error banner on delete failure and stays on detail', () => {
    deleteSpy.mockReturnValueOnce(
      throwError(() => ({
        error: {
          message: 'Cannot delete linked product',
          errorCode: 'PRODUCT_IN_USE',
        },
      })),
    );

    component.onDelete();
    fixture.detectChanges();

    expect(deleteSpy).toHaveBeenCalledWith(1);
    expect(component.deleting()).toBe(false);
    expect(component.saveError()).toBe(
      'Cannot delete linked product [PRODUCT_IN_USE]',
    );
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(selectedProduct()?.id).toBe(1);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain(
      'Cannot delete linked product [PRODUCT_IN_USE]',
    );
  });

  it('does not open delete dialog while editing', () => {
    component.startEdit();
    component.onDelete();

    expect(dialogOpen).not.toHaveBeenCalled();
    expect(deleteSpy).not.toHaveBeenCalled();
  });

  it('opens create-group dialog and appends created group', () => {
    const created: ProductOptionGroupDto = {
      id: 99,
      uuid: 'g-99',
      productId: 1,
      name: 'Hardware',
      selectionType: 'SINGLE',
      minSelections: 1,
      sortOrder: 1,
      options: [],
    };
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(created),
    });

    component.openCreateGroup();
    fixture.detectChanges();

    expect(dialogOpen).toHaveBeenCalledWith(
      OptionGroupCreateDialogComponent,
      expect.objectContaining({
        data: { productId: 1 },
      }),
    );
    expect(component.optionGroups().some((g) => g.id === 99)).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Hardware',
    );
  });

  it('opens add-option dialog and replaces the updated group', () => {
    const updated: ProductOptionGroupDto = {
      ...SAMPLE_GROUP,
      options: [
        ...(SAMPLE_GROUP.options ?? []),
        {
          id: 502,
          uuid: 'o-502',
          productId: 1,
          name: 'Analytics',
          defaultOption: false,
          sortOrder: 1,
        },
      ],
    };
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(updated),
    });

    component.openAddOption(SAMPLE_GROUP);
    fixture.detectChanges();

    expect(dialogOpen).toHaveBeenCalledWith(
      OptionCreateDialogComponent,
      expect.objectContaining({
        data: {
          groupId: 50,
          groupName: 'Tier features',
          productId: 1,
        },
      }),
    );
    expect(
      component.optionGroups()[0].options?.some((o) => o.name === 'Analytics'),
    ).toBe(true);
  });

  it('confirms then deletes an option group', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(true),
    });

    component.confirmDeleteGroup(SAMPLE_GROUP);
    fixture.detectChanges();

    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          title: 'Delete option group',
          confirmColor: 'warn',
        }),
      }),
    );
    expect(deleteGroupSpy).toHaveBeenCalledWith(50);
    expect(component.optionGroups().find((g) => g.id === 50)).toBeUndefined();
  });

  it('does not delete option group when ConfirmDialog is cancelled', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(false),
    });

    component.confirmDeleteGroup(SAMPLE_GROUP);

    expect(deleteGroupSpy).not.toHaveBeenCalled();
    expect(component.optionGroups().some((g) => g.id === 50)).toBe(true);
  });

  it('sets attribute value via ProductAttributeService.setValue', () => {
    const saved: AttributeValueDto = {
      id: 71,
      uuid: 'v-71',
      productId: 1,
      attributeId: 9,
      attributeName: 'region',
      value: 'EU',
    };
    setValueSpy.mockReturnValue(of(saved));

    component.attrForm.setValue({ attributeId: 9, value: '  EU  ' });
    component.onSetAttributeValue();
    fixture.detectChanges();

    expect(setValueSpy).toHaveBeenCalledWith(1, {
      attributeId: 9,
      value: 'EU',
    });
    expect(
      component.attributeValues().find((v) => v.attributeId === 9)?.value,
    ).toBe('EU');
  });

  it('shows attribute error when setValue fails', () => {
    setValueSpy.mockReturnValue(
      throwError(() => ({
        error: {
          message: 'Invalid attribute value',
          errorCode: 'ATTR_VALUE_INVALID',
        },
      })),
    );

    component.attrForm.setValue({ attributeId: 9, value: 'bad' });
    component.onSetAttributeValue();
    fixture.detectChanges();

    expect(component.attrsError()).toBe(
      'Invalid attribute value [ATTR_VALUE_INVALID]',
    );
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Invalid attribute value [ATTR_VALUE_INVALID]',
    );
  });
});
