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
});
