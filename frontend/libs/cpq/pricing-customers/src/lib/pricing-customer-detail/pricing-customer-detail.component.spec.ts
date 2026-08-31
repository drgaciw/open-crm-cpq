import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ConfirmDialogComponent } from '@frontend/shared-ui';
import { PricingCustomerDetailComponent } from './pricing-customer-detail.component';
import { PricingCustomerService } from '@frontend/cpq-pricing-data-access';
import { PricingCustomerDto } from '@frontend/shared-models';
import {
  MOCK_PRICING_CUSTOMERS,
  findMockPricingCustomer,
} from '../pricing-customers.mock';

describe('PricingCustomerDetailComponent', () => {
  let fixture: ComponentFixture<PricingCustomerDetailComponent>;
  let component: PricingCustomerDetailComponent;
  let router: Router;
  let getByIdSpy: ReturnType<typeof vi.fn>;
  let updateSpy: ReturnType<typeof vi.fn>;
  let deleteSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;
  let navigateSpy: ReturnType<typeof vi.fn>;

  const selectedCustomer = signal<PricingCustomerDto | null>(
    MOCK_PRICING_CUSTOMERS[0],
  );
  const loading = signal(false);
  const error = signal<string | null>(null);

  beforeEach(async () => {
    getByIdSpy = vi.fn((id: number) => {
      selectedCustomer.set(findMockPricingCustomer(id) ?? null);
      loading.set(false);
    });
    updateSpy = vi.fn().mockImplementation((_id: number, req: Partial<PricingCustomerDto>) =>
      of({
        ...MOCK_PRICING_CUSTOMERS[0],
        ...req,
        name: req.name ?? MOCK_PRICING_CUSTOMERS[0].name,
      }),
    );
    deleteSpy = vi.fn().mockReturnValue(of(void 0));

    dialogOpen = vi.fn().mockReturnValue({
      afterClosed: () => of(true),
    });

    const serviceMock = {
      selectedCustomer,
      loading,
      error,
      getById: getByIdSpy,
      update: updateSpy,
      delete: deleteSpy,
    };

    await TestBed.configureTestingModule({
      imports: [PricingCustomerDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: PricingCustomerService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PricingCustomerDetailComponent);
    fixture.componentRef.setInput('id', '1');
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    selectedCustomer.set(MOCK_PRICING_CUSTOMERS[0]);
    loading.set(false);
    error.set(null);
  });

  it('loads customer via PricingCustomerService.getById on init', () => {
    expect(getByIdSpy).toHaveBeenCalledWith(1);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Acme Corp');
    expect(compiled.textContent).toContain('CUST-ACME');
    expect(compiled.textContent).toContain('Enterprise');
  });

  it('enters edit mode and saves via PricingCustomerService.update', () => {
    component.startEdit();
    component.form.patchValue({ name: 'Acme Updated', segment: 'STRATEGIC' });
    component.onSave();
    fixture.detectChanges();

    expect(updateSpy).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        name: 'Acme Updated',
        segment: 'STRATEGIC',
      }),
    );
    expect(component.editing()).toBe(false);
    expect(selectedCustomer()?.name).toBe('Acme Updated');
  });

  it('shows error banner on update failure and stays in edit mode', () => {
    updateSpy.mockReturnValueOnce(
      throwError(() => ({ error: { message: 'Name already taken' } })),
    );
    component.startEdit();
    component.form.patchValue({ name: 'Taken' });
    component.onSave();
    fixture.detectChanges();

    expect(component.editing()).toBe(true);
    expect(component.actionError()).toBe('Name already taken');
    expect(navigateSpy).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Name already taken',
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
      'Pricing customer details',
    );
  });

  it('renders Delete in view mode', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Delete');
  });

  it('confirms then soft-deletes via PricingCustomerService.delete and navigates to list', () => {
    component.onDelete();

    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          title: 'Delete pricing customer',
          confirmText: 'Delete',
          confirmColor: 'warn',
        }),
      }),
    );
    expect(deleteSpy).toHaveBeenCalledWith(1);
    expect(selectedCustomer()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/pricing-customers']);
    expect(component.deleting()).toBe(false);
    expect(component.actionError()).toBeNull();
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
        error: { message: 'Cannot delete linked pricing customer' },
      })),
    );

    component.onDelete();
    fixture.detectChanges();

    expect(deleteSpy).toHaveBeenCalledWith(1);
    expect(component.deleting()).toBe(false);
    expect(component.actionError()).toBe(
      'Cannot delete linked pricing customer',
    );
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(selectedCustomer()?.id).toBe(1);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Cannot delete linked pricing customer',
    );
  });

  it('does not open delete dialog while editing', () => {
    component.startEdit();
    component.onDelete();

    expect(dialogOpen).not.toHaveBeenCalled();
    expect(deleteSpy).not.toHaveBeenCalled();
  });

  it('navigates back to pricing-customers list', () => {
    component.goBack();
    expect(navigateSpy).toHaveBeenCalledWith(['/pricing-customers']);
  });
});
