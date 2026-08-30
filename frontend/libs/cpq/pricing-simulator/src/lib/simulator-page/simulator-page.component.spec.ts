import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { PricingExecutionService } from '@frontend/cpq-pricing-data-access';
import { PricingContext, PricingResult } from '@frontend/shared-models';
import { SimulatorPageComponent } from './simulator-page.component';
import {
  MOCK_PRICING_EXECUTE_OK,
  mockExecuteOk,
} from '../pricing-simulator.mock';

describe('SimulatorPageComponent', () => {
  let fixture: ComponentFixture<SimulatorPageComponent>;
  let component: SimulatorPageComponent;
  let executeMock: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    executeMock = vi.fn();
    await TestBed.configureTestingModule({
      imports: [SimulatorPageComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: PricingExecutionService,
          useValue: {
            execute: executeMock,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SimulatorPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders page header and empty result state', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Pricing Simulator');
    expect(compiled.textContent).toContain('/api/v1/pricing/execute');
    expect(compiled.querySelector('app-page-header')).toBeTruthy();
    expect(compiled.textContent).toContain('No simulation yet');
    expect(component.result()).toBeNull();
    expect(component.error()).toBeNull();
    expect(executeMock).not.toHaveBeenCalled();
  });

  it('calls PricingExecutionService.execute and shows success panel', () => {
    const ok = mockExecuteOk({
      customerExternalId: 'CUST-GOLD-001',
      quoteExternalId: 'Q-SIM-001',
      region: 'NA',
      currency: 'USD',
      durationInMonths: 12,
      subscriptionType: 'NEW',
      lineItems: [
        {
          productExternalId: 'PROD-001',
          lineItemType: 'SUBSCRIPTION',
          quantity: 5,
        },
      ],
    });
    executeMock.mockReturnValue(of(ok));

    component.onSimulate();
    fixture.detectChanges();

    expect(executeMock).toHaveBeenCalledTimes(1);
    const ctx = executeMock.mock.calls[0][0] as PricingContext;
    expect(ctx.customerExternalId).toBe('CUST-GOLD-001');
    expect(ctx.lineItems[0]?.productExternalId).toBe('PROD-001');

    const result = component.result();
    expect(result).not.toBeNull();
    expect(result!.success).toBe(true);
    expect(result!.approvalState).toBe(MOCK_PRICING_EXECUTE_OK.approvalState);
    expect(component.error()).toBeNull();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Execute OK');
    expect(compiled.textContent).toContain('PROD-001');
    expect(compiled.querySelector('.success-banner')).toBeTruthy();
  });

  it('passes quantity and list override into execute context', () => {
    executeMock.mockReturnValue(
      of(
        mockExecuteOk({
          customerExternalId: 'CUST-GOLD-001',
          quoteExternalId: 'Q-SIM-001',
          region: 'NA',
          currency: 'USD',
          durationInMonths: 12,
          subscriptionType: 'NEW',
          lineItems: [
            {
              productExternalId: 'PROD-OVERRIDE',
              lineItemType: 'SUBSCRIPTION',
              quantity: 10,
              listPriceOverride: 50,
            },
          ],
        }),
      ),
    );

    component.form.patchValue({
      quantity: 10,
      listPriceOverride: 50,
      productExternalId: 'PROD-OVERRIDE',
    });
    component.onSimulate();
    fixture.detectChanges();

    const ctx = executeMock.mock.calls[0][0] as PricingContext;
    expect(ctx.lineItems[0]?.productExternalId).toBe('PROD-OVERRIDE');
    expect(ctx.lineItems[0]?.quantity).toBe(10);
    expect(ctx.lineItems[0]?.listPriceOverride).toBe(50);

    const line = component.result()!.lineResults![0];
    expect(line.productExternalId).toBe('PROD-OVERRIDE');
    expect(line.quantity).toBe(10);
    expect(line.listPrice).toBe(50);
    expect(line.finalPrice).toBe(500);
  });

  it('shows error empty-state when execute HTTP fails', () => {
    executeMock.mockReturnValue(
      throwError(() => ({ message: 'Gateway unreachable' })),
    );

    component.onSimulate();
    fixture.detectChanges();

    expect(component.result()).toBeNull();
    expect(component.error()).toBe('Gateway unreachable');
    expect(component.loading()).toBe(false);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Simulation failed');
    expect(compiled.textContent).toContain('Gateway unreachable');
    expect(compiled.querySelector('app-empty-state')).toBeTruthy();
    expect(compiled.querySelector('.success-banner')).toBeFalsy();
  });

  it('shows error empty-state when execute returns success=false', () => {
    const failed: PricingResult = {
      success: false,
      errorMessage: 'No matching price book',
    };
    executeMock.mockReturnValue(of(failed));

    component.onSimulate();
    fixture.detectChanges();

    expect(component.result()).toBeNull();
    expect(component.error()).toBe('No matching price book');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Simulation failed');
    expect(compiled.textContent).toContain('No matching price book');
  });

  it('dismisses error empty-state', () => {
    component.setErrorForTest('Boom');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Boom');

    component.clearError();
    fixture.detectChanges();
    expect(component.error()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('No simulation yet');
  });

  it('does not simulate when form is invalid', () => {
    component.form.patchValue({ customerExternalId: '' });
    component.onSimulate();
    fixture.detectChanges();

    expect(executeMock).not.toHaveBeenCalled();
    expect(component.result()).toBeNull();
    expect(component.form.invalid).toBe(true);
  });
});
