import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { PricingCustomerCreateDialogComponent } from './pricing-customer-create-dialog.component';
import { PricingCustomerService } from '@frontend/cpq-pricing-data-access';
import { MOCK_PRICING_CUSTOMERS } from '../pricing-customers.mock';

describe('PricingCustomerCreateDialogComponent', () => {
  let fixture: ComponentFixture<PricingCustomerCreateDialogComponent>;
  let component: PricingCustomerCreateDialogComponent;
  let createSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    createSpy = vi.fn().mockReturnValue(of(MOCK_PRICING_CUSTOMERS[0]));
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [PricingCustomerCreateDialogComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: PricingCustomerService,
          useValue: { create: createSpy },
        },
        {
          provide: MatDialogRef,
          useValue: { close: dialogClose },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PricingCustomerCreateDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requires externalId and name', () => {
    component.onSubmit();
    expect(createSpy).not.toHaveBeenCalled();
    expect(component.form.invalid).toBe(true);
  });

  it('creates via PricingCustomerService.create and closes with result', () => {
    component.form.patchValue({
      externalId: 'CUST-NEW',
      name: 'New Customer',
      segment: 'SMB',
      region: 'NA',
      industry: 'Tech',
      churnScore: 5,
      annualRevenue: 1000,
    });
    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      externalId: 'CUST-NEW',
      name: 'New Customer',
      segment: 'SMB',
      region: 'NA',
      industry: 'Tech',
      churnScore: 5,
      annualRevenue: 1000,
    });
    expect(dialogClose).toHaveBeenCalledWith(MOCK_PRICING_CUSTOMERS[0]);
    expect(component.saving()).toBe(false);
  });

  it('surfaces create error in dialog banner', () => {
    createSpy.mockReturnValueOnce(
      throwError(() => ({ error: { message: 'Duplicate externalId' } })),
    );
    component.form.patchValue({
      externalId: 'CUST-DUP',
      name: 'Dup',
    });
    component.onSubmit();
    fixture.detectChanges();

    expect(component.error()).toBe('Duplicate externalId');
    expect(dialogClose).not.toHaveBeenCalled();
    expect(
      (fixture.nativeElement as HTMLElement).textContent,
    ).toContain('Duplicate externalId');
  });

  it.each([
    ['externalId', 'External ID is required'],
    ['name', 'Name is required'],
  ] as const)('rejects whitespace-only %s with the required message', (field, message) => {
    component.form.patchValue({ externalId: 'CUST-NEW', name: 'New Customer' });
    component.form.controls[field].setValue(' \t ');
    component.form.controls[field].markAsTouched();
    component.onSubmit();
    fixture.detectChanges();

    expect(createSpy).not.toHaveBeenCalled();
    expect(component.form.invalid).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(message);
  });

  it('requires Type to fit the 30-character API contract', () => {
    component.form.patchValue({ externalId: 'CUST-NEW', name: 'New Customer', type: 'T'.repeat(31) });
    component.onSubmit();

    expect(createSpy).not.toHaveBeenCalled();
    expect(component.form.controls.type.hasError('maxlength')).toBe(true);
    expect(fixture.nativeElement.querySelector('input[formControlName="type"]').maxLength).toBe(30);

    const allowedType = 'T'.repeat(30);
    component.form.controls.type.setValue(allowedType);
    component.onSubmit();
    expect(createSpy).toHaveBeenCalledWith({ externalId: 'CUST-NEW', name: 'New Customer', type: allowedType });
  });

  it('trims valid required fields and omits blank optionals without losing zeros', () => {
    component.form.patchValue({
      externalId: '  CUST-NEW  ',
      name: '  New Customer  ',
      type: '   ',
      industry: '   ',
      churnScore: 0,
      annualRevenue: 0,
    });
    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      externalId: 'CUST-NEW',
      name: 'New Customer',
      churnScore: 0,
      annualRevenue: 0,
    });
  });

});
