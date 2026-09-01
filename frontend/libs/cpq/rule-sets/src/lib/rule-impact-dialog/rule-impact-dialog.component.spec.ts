import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { RuleImpactDto } from '@frontend/shared-models';
import { RuleService } from '@frontend/cpq-pricing-data-access';
import { RuleImpactDialogComponent } from './rule-impact-dialog.component';

const IMPACT: RuleImpactDto = {
  ruleId: 10,
  ruleName: 'List price floor',
  ruleType: 'PRICING',
  ruleStatus: 'ACTIVE',
  priority: 100,
  ruleSetId: 1,
  ruleSetStatus: 'ACTIVE',
  activeRuleSet: true,
  totalRulesInRuleSet: 3,
  activeRulesInRuleSet: 2,
  pricingExecutionsWithRuleSet: 42,
  pricingExecutionsWithRuleSetLast30Days: 7,
};

describe('RuleImpactDialogComponent', () => {
  let fixture: ComponentFixture<RuleImpactDialogComponent>;
  let component: RuleImpactDialogComponent;
  let analyzeImpactSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  async function setup(data: { ruleId: number; ruleName?: string }) {
    analyzeImpactSpy = vi.fn().mockReturnValue(of(IMPACT));
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [RuleImpactDialogComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: RuleService,
          useValue: { analyzeImpact: analyzeImpactSpy },
        },
        { provide: MatDialogRef, useValue: { close: dialogClose } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RuleImpactDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('loads impact via RuleService.analyzeImpact on init', async () => {
    await setup({ ruleId: 10, ruleName: 'List price floor' });
    expect(analyzeImpactSpy).toHaveBeenCalledWith(10);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('List price floor');
    expect(compiled.textContent).toContain('Pricing executions (all-time)');
    expect(compiled.textContent).toContain('42');
    expect(compiled.textContent).toContain('Pricing executions (last 30d)');
    expect(compiled.textContent).toContain('7');
    expect(compiled.textContent).toContain('Active rule set');
    expect(compiled.textContent).toContain('Yes');
    expect(compiled.querySelector('[data-testid="rule-impact-fields"]')).toBeTruthy();
  });

  it('surfaces analyzeImpact errors and allows retry', async () => {
    await setup({ ruleId: 10 });
    analyzeImpactSpy.mockReturnValueOnce(
      throwError(() => ({
        error: {
          message: 'Impact unavailable',
          errorCode: 'RULE_IMPACT_UNAVAILABLE',
        },
      })),
    );
    component.load();
    fixture.detectChanges();
    expect(component.error()).toBe(
      'Impact unavailable [RULE_IMPACT_UNAVAILABLE]',
    );
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain(
      'Impact unavailable [RULE_IMPACT_UNAVAILABLE]',
    );
    expect(compiled.querySelector('.retry-btn')).toBeTruthy();

    analyzeImpactSpy.mockReturnValueOnce(of(IMPACT));
    component.load();
    fixture.detectChanges();
    expect(component.error()).toBeNull();
    expect(component.impact()?.ruleId).toBe(10);
  });

  it('closes the dialog', async () => {
    await setup({ ruleId: 10 });
    component.close();
    expect(dialogClose).toHaveBeenCalled();
  });
});
