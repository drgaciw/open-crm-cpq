import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { SimulatorPageComponent } from './simulator-page.component';
import { MOCK_PRICING_EXECUTE_OK } from '../pricing-simulator.mock';

describe('SimulatorPageComponent', () => {
  let fixture: ComponentFixture<SimulatorPageComponent>;
  let component: SimulatorPageComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SimulatorPageComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(SimulatorPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders page header and empty result state', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Pricing Simulator');
    expect(compiled.querySelector('app-page-header')).toBeTruthy();
    expect(compiled.textContent).toContain('No simulation yet');
    expect(component.result()).toBeNull();
  });

  it('mock execute returns success OK and shows result panel', () => {
    component.onSimulate();
    fixture.detectChanges();

    const result = component.result();
    expect(result).not.toBeNull();
    expect(result!.success).toBe(true);
    expect(result!.approvalState).toBe(MOCK_PRICING_EXECUTE_OK.approvalState);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Execute OK');
    expect(compiled.textContent).toContain('PROD-001');
    expect(compiled.querySelector('.success-banner')).toBeTruthy();
  });

  it('adjusts final price from quantity and list override', () => {
    component.form.patchValue({
      quantity: 10,
      listPriceOverride: 50,
      productExternalId: 'PROD-OVERRIDE',
    });
    component.onSimulate();
    fixture.detectChanges();

    const line = component.result()!.lineResults![0];
    expect(line.productExternalId).toBe('PROD-OVERRIDE');
    expect(line.quantity).toBe(10);
    expect(line.listPrice).toBe(50);
    expect(line.finalPrice).toBe(500);
  });

  it('does not simulate when form is invalid', () => {
    component.form.patchValue({ customerExternalId: '' });
    component.onSimulate();
    fixture.detectChanges();

    expect(component.result()).toBeNull();
    expect(component.form.invalid).toBe(true);
  });
});
