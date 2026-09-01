import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { RuleDialogComponent } from './rule-dialog.component';
import { RuleService } from '@frontend/cpq-pricing-data-access';
import { MOCK_RULES } from '../rule-sets.mock';

describe('RuleDialogComponent', () => {
  let fixture: ComponentFixture<RuleDialogComponent>;
  let component: RuleDialogComponent;
  let createSpy: ReturnType<typeof vi.fn>;
  let updateSpy: ReturnType<typeof vi.fn>;
  let validateSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  async function setup(data: { ruleSetId: number; rule?: (typeof MOCK_RULES)[0] }) {
    createSpy = vi.fn().mockReturnValue(of(MOCK_RULES[0]));
    updateSpy = vi.fn().mockReturnValue(of(MOCK_RULES[0]));
    validateSpy = vi.fn().mockReturnValue(of(null));
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [RuleDialogComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: RuleService,
          useValue: {
            create: createSpy,
            update: updateSpy,
            validateSyntax: validateSpy,
          },
        },
        { provide: MatDialogRef, useValue: { close: dialogClose } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RuleDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it(
    'requires name, ruleType, priority, and DRL content on create',
    async () => {
      await setup({ ruleSetId: 1 });
      component.form.patchValue({ name: '', content: '' });
      component.onSubmit();
      expect(createSpy).not.toHaveBeenCalled();
      expect(component.form.invalid).toBe(true);
    },
    15000,
  );

  it('creates rule then calls validateSyntax after save', async () => {
    await setup({ ruleSetId: 1 });
    component.form.patchValue({
      name: 'Floor',
      ruleType: 'PRICING',
      priority: 100,
      content: 'rule "x"\nwhen\nthen\nend',
      description: 'desc',
    });
    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      ruleSetId: 1,
      name: 'Floor',
      ruleType: 'PRICING',
      priority: 100,
      content: 'rule "x"\nwhen\nthen\nend',
      description: 'desc',
    });
    expect(validateSpy).toHaveBeenCalledWith(MOCK_RULES[0].id);
    expect(dialogClose).toHaveBeenCalledWith(MOCK_RULES[0]);
  });

  it('edit validates before and after update', async () => {
    await setup({ ruleSetId: 1, rule: MOCK_RULES[0] });
    component.form.patchValue({
      name: 'Floor updated',
      content: 'rule "y"\nwhen\nthen\nend',
      priority: 150,
    });
    component.onSubmit();

    expect(validateSpy).toHaveBeenCalledWith(MOCK_RULES[0].id);
    expect(updateSpy).toHaveBeenCalledWith(
      MOCK_RULES[0].id,
      expect.objectContaining({
        name: 'Floor updated',
        content: 'rule "y"\nwhen\nthen\nend',
        priority: 150,
      }),
    );
    // before + after
    expect(validateSpy.mock.calls.length).toBeGreaterThanOrEqual(2);
    expect(dialogClose).toHaveBeenCalledWith(MOCK_RULES[0]);
  });

  it('surfaces post-save validate error and does not close', async () => {
    await setup({ ruleSetId: 1 });
    validateSpy.mockReturnValueOnce(
      throwError(() => ({
        error: {
          message: 'DRL parse error',
          errorCode: 'RULE_DRL_INVALID',
        },
      })),
    );
    component.form.patchValue({
      name: 'Bad',
      ruleType: 'PRICING',
      priority: 100,
      content: 'not valid',
    });
    component.onSubmit();
    fixture.detectChanges();

    expect(createSpy).toHaveBeenCalled();
    expect(component.error()).toBe('DRL parse error [RULE_DRL_INVALID]');
    expect(dialogClose).not.toHaveBeenCalled();
  });
});
