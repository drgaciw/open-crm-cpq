import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Subject, of, throwError } from 'rxjs';
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

  async function setup(data: {
    ruleSetId: number;
    rule?: (typeof MOCK_RULES)[0];
  }) {
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

  // First TestBed compile can exceed 15s under parallel nx load; use project testTimeout.
  it('requires name, ruleType, priority, and DRL content on create', async () => {
    await setup({ ruleSetId: 1 });
    component.form.patchValue({ name: '', content: '' });
    component.onSubmit();
    expect(createSpy).not.toHaveBeenCalled();
    expect(component.form.invalid).toBe(true);
  });

  it.each(['name', 'content'] as const)(
    'blocks a whitespace-only %s on create',
    async (field) => {
      await setup({ ruleSetId: 1 });
      component.form.patchValue({
        name: 'Floor',
        content: 'rule "x" when then end',
        [field]: ' \t\n ',
      });
      fixture.detectChanges();

      component.onSubmit();

      const submit = fixture.debugElement.query(
        By.css('button[mat-raised-button]'),
      ).nativeElement as HTMLButtonElement;
      expect(submit.disabled).toBe(true);
      expect(createSpy).not.toHaveBeenCalled();
      expect(validateSpy).not.toHaveBeenCalled();
    },
  );

  it.each(['name', 'content'] as const)(
    'blocks a whitespace-only %s on edit',
    async (field) => {
      await setup({ ruleSetId: 1, rule: MOCK_RULES[0] });
      component.form.patchValue({
        name: 'Floor',
        content: 'rule "x" when then end',
        [field]: ' \t\n ',
      });
      fixture.detectChanges();

      component.onSubmit();

      const submit = fixture.debugElement.query(
        By.css('button[mat-raised-button]'),
      ).nativeElement as HTMLButtonElement;
      expect(submit.disabled).toBe(true);
      expect(updateSpy).not.toHaveBeenCalled();
      expect(validateSpy).not.toHaveBeenCalled();
    },
  );

  it.each(['Create', 'Save'] as const)(
    'keeps %s and progress named through persistence and validation',
    async (action) => {
      const editing = action === 'Save';
      await setup({ ruleSetId: 1, rule: editing ? MOCK_RULES[0] : undefined });
      component.form.patchValue({
        name: 'Floor',
        content: 'rule "x" when then end',
      });
      const save = new Subject<(typeof MOCK_RULES)[number]>();
      const validation = new Subject<null>();
      if (editing) {
        updateSpy.mockReturnValue(save);
        validateSpy.mockReturnValueOnce(of(null));
      } else {
        createSpy.mockReturnValue(save);
      }
      validateSpy.mockReturnValue(validation);

      const expectNamedPendingAction = () => {
        fixture.detectChanges();
        const actions = (fixture.nativeElement as HTMLElement).querySelector(
          'mat-dialog-actions',
        );
        const submit = actions?.querySelector<HTMLButtonElement>(
          'button[mat-raised-button]',
        );
        const cancel = actions?.querySelector<HTMLButtonElement>(
          'button[mat-dialog-close]',
        );
        expect(submit?.getAttribute('aria-label')).toBe(action);
        expect(submit?.disabled).toBe(true);
        expect(cancel?.disabled).toBe(true);
        expect(
          actions
            ?.querySelector('[role="progressbar"]')
            ?.getAttribute('aria-label'),
        ).toBe('Saving rule');
        expect(dialogClose).not.toHaveBeenCalled();
      };

      component.onSubmit();
      expectNamedPendingAction();

      save.next(MOCK_RULES[0]);
      save.complete();
      expectNamedPendingAction();

      validation.next(null);
      validation.complete();
      expect(dialogClose).toHaveBeenCalledWith(MOCK_RULES[0]);
    },
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

  it('applies cdkTextareaAutosize to description and DRL content textareas', async () => {
    await setup({ ruleSetId: 1 });
    const textareas = fixture.debugElement.queryAll(By.directive(CdkTextareaAutosize));
    expect(textareas).toHaveLength(2);
    const [descAutosize, drlAutosize] = textareas.map((de) =>
      de.injector.get(CdkTextareaAutosize),
    );
    expect(descAutosize.minRows).toBe(2);
    expect(descAutosize.maxRows).toBe(6);
    expect(drlAutosize.minRows).toBe(8);
    expect(drlAutosize.maxRows).toBe(24);
  });
});
