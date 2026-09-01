import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { ConfigRulesPanelComponent } from './config-rules-panel.component';
import { ConfigurationRuleService } from '../configuration-rule.service';
import { ConfigurationRuleDto } from '../configuration-rule.model';
import { ConfirmDialogComponent } from '@frontend/shared-ui';
import { ConfigRuleCreateDialogComponent } from '../config-rule-create-dialog/config-rule-create-dialog.component';

const SAMPLE_RULE: ConfigurationRuleDto = {
  id: 80,
  uuid: 'cr-80',
  name: 'Requires storage',
  ruleType: 'REQUIRES',
  sourceProductId: 1,
  targetProductId: 22,
  conditionExpression: 'attr:tier == "pro"',
  priority: 5,
};

describe('ConfigRulesPanelComponent', () => {
  let fixture: ComponentFixture<ConfigRulesPanelComponent>;
  let component: ConfigRulesPanelComponent;
  let listByProductSpy: ReturnType<typeof vi.fn>;
  let deleteSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    listByProductSpy = vi.fn(() => of([SAMPLE_RULE]));
    deleteSpy = vi.fn(() => of(undefined));
    dialogOpen = vi.fn(() => ({
      afterClosed: () => of(true),
    }));

    await TestBed.configureTestingModule({
      imports: [ConfigRulesPanelComponent],
      providers: [
        provideNoopAnimations(),
        {
          provide: ConfigurationRuleService,
          useValue: {
            listByProduct: listByProductSpy,
            create: vi.fn(),
            delete: deleteSpy,
          },
        },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfigRulesPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('productId', 1);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists rules via GET product-scoped configuration/rules', () => {
    expect(listByProductSpy).toHaveBeenCalledWith(1);
    expect(component.rules()).toEqual([SAMPLE_RULE]);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Configuration rules');
    expect(el.textContent).toContain('Requires storage');
    expect(el.textContent).toContain('REQUIRES');
    expect(el.textContent).toContain('22');
  });

  it('shows empty state when no rules', () => {
    listByProductSpy.mockReturnValue(of([]));
    fixture.componentRef.setInput('productId', 2);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('No configuration rules');
  });

  it('opens create dialog and appends created rule', () => {
    const created: ConfigurationRuleDto = {
      id: 81,
      uuid: 'cr-81',
      name: 'Exclude rival',
      ruleType: 'EXCLUDES',
      sourceProductId: 1,
      targetProductId: 33,
      priority: 0,
    };
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(created),
    });

    component.openCreate();
    fixture.detectChanges();

    expect(dialogOpen).toHaveBeenCalledWith(
      ConfigRuleCreateDialogComponent,
      expect.objectContaining({
        data: { productId: 1 },
      }),
    );
    expect(component.rules().some((r) => r.id === 81)).toBe(true);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Exclude rival',
    );
  });

  it('confirms then deletes a rule', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(true),
    });

    component.confirmDelete(SAMPLE_RULE);
    fixture.detectChanges();

    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          title: 'Delete configuration rule',
          confirmColor: 'warn',
        }),
      }),
    );
    expect(deleteSpy).toHaveBeenCalledWith(80);
    expect(component.rules().find((r) => r.id === 80)).toBeUndefined();
  });

  it('does not delete when ConfirmDialog is cancelled', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(false),
    });

    component.confirmDelete(SAMPLE_RULE);

    expect(deleteSpy).not.toHaveBeenCalled();
    expect(component.rules().some((r) => r.id === 80)).toBe(true);
  });

  it('shows error when list fails', () => {
    listByProductSpy.mockReturnValue(
      throwError(() => ({
        error: {
          message: 'Rules unavailable',
          errorCode: 'CONFIG_RULES_UNAVAILABLE',
        },
      })),
    );
    fixture.componentRef.setInput('productId', 9);
    fixture.detectChanges();

    expect(component.error()).toBe(
      'Rules unavailable [CONFIG_RULES_UNAVAILABLE]',
    );
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Rules unavailable [CONFIG_RULES_UNAVAILABLE]',
    );
  });

  it('never exposes validate UI (product POST /validate)', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent?.toLowerCase()).not.toContain('validate configuration');
    expect(el.querySelector('[data-validate], .validate-btn')).toBeNull();
    expect(el.querySelector('button.validate-btn')).toBeNull();
  });
});
