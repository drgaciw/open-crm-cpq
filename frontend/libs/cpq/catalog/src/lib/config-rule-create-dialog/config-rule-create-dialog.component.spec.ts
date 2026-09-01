import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import {
  ConfigRuleCreateDialogComponent,
  ConfigRuleCreateDialogData,
} from './config-rule-create-dialog.component';
import { ConfigurationRuleService } from '../configuration-rule.service';
import { ConfigurationRuleDto } from '../configuration-rule.model';

describe('ConfigRuleCreateDialogComponent', () => {
  let fixture: ComponentFixture<ConfigRuleCreateDialogComponent>;
  let component: ConfigRuleCreateDialogComponent;
  let createSpy: ReturnType<typeof vi.fn>;
  let dialogClose: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    createSpy = vi.fn();
    dialogClose = vi.fn();

    await TestBed.configureTestingModule({
      imports: [ConfigRuleCreateDialogComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: { productId: 12 } satisfies ConfigRuleCreateDialogData,
        },
        {
          provide: MatDialogRef,
          useValue: { close: dialogClose },
        },
        {
          provide: ConfigurationRuleService,
          useValue: { create: createSpy },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfigRuleCreateDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('POSTs ConfigRuleCreateRequest with sourceProductId from dialog data', () => {
    const created: ConfigurationRuleDto = {
      id: 3,
      uuid: 'cr-3',
      name: 'Requires addon',
      ruleType: 'REQUIRES',
      sourceProductId: 12,
      targetProductId: 99,
      conditionExpression: 'attr:tier == "pro"',
      priority: 1,
    };
    createSpy.mockReturnValue(of(created));

    component.form.setValue({
      name: '  Requires addon  ',
      ruleType: 'REQUIRES',
      targetProductId: 99,
      conditionExpression: '  attr:tier == "pro"  ',
      description: '',
      priority: 1,
    });
    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      name: 'Requires addon',
      ruleType: 'REQUIRES',
      sourceProductId: 12,
      targetProductId: 99,
      conditionExpression: 'attr:tier == "pro"',
      priority: 1,
    });
    expect(dialogClose).toHaveBeenCalledWith(created);
  });

  it('does not submit when form is invalid', () => {
    component.onSubmit();
    expect(createSpy).not.toHaveBeenCalled();
  });

  it('shows error on create failure', () => {
    createSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'Duplicate rule' } })),
    );
    component.form.patchValue({
      name: 'Dup',
      ruleType: 'EXCLUDES',
    });
    component.onSubmit();
    fixture.detectChanges();

    expect(component.error()).toBe('Duplicate rule');
    expect(dialogClose).not.toHaveBeenCalled();
  });

  it('cancel closes without create', () => {
    component.onCancel();
    expect(dialogClose).toHaveBeenCalledWith(null);
    expect(createSpy).not.toHaveBeenCalled();
  });
});
