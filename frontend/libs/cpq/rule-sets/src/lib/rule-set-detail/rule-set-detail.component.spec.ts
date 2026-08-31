import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ConfirmDialogComponent } from '@frontend/shared-ui';
import { RuleSetDetailComponent } from './rule-set-detail.component';
import {
  RuleService,
  RuleSetService,
} from '@frontend/cpq-pricing-data-access';
import { PaginationMeta, RuleDto, RuleSetDto } from '@frontend/shared-models';
import {
  MOCK_RULE_SETS,
  MOCK_RULES,
  findMockRuleSet,
  findMockRulesForSet,
} from '../rule-sets.mock';
import { RuleDialogComponent } from '../rule-dialog/rule-dialog.component';

describe('RuleSetDetailComponent', () => {
  let fixture: ComponentFixture<RuleSetDetailComponent>;
  let component: RuleSetDetailComponent;
  let router: Router;
  let getByIdSpy: ReturnType<typeof vi.fn>;
  let listByRuleSetSpy: ReturnType<typeof vi.fn>;
  let activateSpy: ReturnType<typeof vi.fn>;
  let deprecateSpy: ReturnType<typeof vi.fn>;
  let deleteRuleSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;
  let navigateSpy: ReturnType<typeof vi.fn>;

  const selectedRuleSet = signal<RuleSetDto | null>(MOCK_RULE_SETS[0]);
  const ruleSetLoading = signal(false);
  const ruleSetError = signal<string | null>(null);

  const rules = signal<RuleDto[]>(findMockRulesForSet(1));
  const rulesLoading = signal(false);
  const rulesError = signal<string | null>(null);
  const rulesPagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: findMockRulesForSet(1).length,
    totalPages: 1,
  });

  beforeEach(async () => {
    getByIdSpy = vi.fn((id: number) => {
      selectedRuleSet.set(findMockRuleSet(id) ?? null);
      ruleSetLoading.set(false);
    });
    listByRuleSetSpy = vi.fn((ruleSetId: number) => {
      const next = findMockRulesForSet(ruleSetId);
      rules.set(next);
      rulesPagination.set({
        page: 0,
        size: 20,
        totalElements: next.length,
        totalPages: 1,
      });
      rulesLoading.set(false);
    });
    activateSpy = vi.fn().mockReturnValue(
      of({ ...MOCK_RULE_SETS[0], status: 'ACTIVE' as const }),
    );
    deprecateSpy = vi.fn().mockReturnValue(
      of({ ...MOCK_RULE_SETS[1], status: 'DEPRECATED' as const }),
    );
    deleteRuleSpy = vi.fn().mockReturnValue(of(void 0));

    dialogOpen = vi.fn().mockReturnValue({
      afterClosed: () => of(true),
    });

    const ruleSetServiceMock = {
      selectedRuleSet,
      loading: ruleSetLoading,
      error: ruleSetError,
      getById: getByIdSpy,
      activate: activateSpy,
      deprecate: deprecateSpy,
    };

    const ruleServiceMock = {
      rules,
      loading: rulesLoading,
      error: rulesError,
      pagination: rulesPagination,
      listByRuleSet: listByRuleSetSpy,
      delete: deleteRuleSpy,
    };

    await TestBed.configureTestingModule({
      imports: [RuleSetDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: RuleSetService, useValue: ruleSetServiceMock },
        { provide: RuleService, useValue: ruleServiceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RuleSetDetailComponent);
    fixture.componentRef.setInput('id', '1');
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    selectedRuleSet.set(MOCK_RULE_SETS[0]);
    rules.set(findMockRulesForSet(1));
    ruleSetLoading.set(false);
    rulesLoading.set(false);
    ruleSetError.set(null);
    rulesError.set(null);
  });

  it('loads rule set and nested rules via listByRuleSet on init', () => {
    expect(getByIdSpy).toHaveBeenCalledWith(1);
    expect(listByRuleSetSpy).toHaveBeenCalledWith(1);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Standard Pricing');
    expect(compiled.textContent).toContain('List price floor');
    expect(compiled.textContent).toContain('Activate');
  });

  it('activates DRAFT rule set via ConfirmDialog + RuleSetService.activate', () => {
    component.confirmActivate();
    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({ confirmText: 'Activate' }),
      }),
    );
    expect(activateSpy).toHaveBeenCalledWith(1);
    expect(selectedRuleSet()?.status).toBe('ACTIVE');
  });

  it('shows Deprecate for ACTIVE rule sets and calls deprecate', () => {
    // Keep route id in sync with selectedRuleSet.id (computed ruleSet() gates on match).
    const activeOnRoute: RuleSetDto = {
      ...MOCK_RULE_SETS[1],
      id: 1,
    };
    selectedRuleSet.set(activeOnRoute);
    fixture.detectChanges();
    expect(component.canDeprecate()).toBe(true);
    expect(component.canActivate()).toBe(false);

    component.confirmDeprecate();
    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({ confirmText: 'Deprecate' }),
      }),
    );
    expect(deprecateSpy).toHaveBeenCalledWith(1);
  });

  it('surfaces activate failure in banner', () => {
    activateSpy.mockReturnValueOnce(
      throwError(() => ({ error: { message: 'Only DRAFT can activate' } })),
    );
    component.confirmActivate();
    fixture.detectChanges();
    expect(component.actionError()).toBe('Only DRAFT can activate');
  });

  it('opens create rule dialog and reloads listByRuleSet', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(MOCK_RULES[0]),
    });
    listByRuleSetSpy.mockClear();
    component.openCreateRule();
    expect(dialogOpen).toHaveBeenCalledWith(
      RuleDialogComponent,
      expect.objectContaining({
        data: { ruleSetId: 1 },
        disableClose: true,
      }),
    );
    expect(listByRuleSetSpy).toHaveBeenCalledWith(1);
  });

  it('deletes a rule via ConfirmDialog + RuleService.delete', () => {
    listByRuleSetSpy.mockClear();
    component.confirmDeleteRule(component.ruleRows()[0]);
    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({ confirmText: 'Delete' }),
      }),
    );
    expect(deleteRuleSpy).toHaveBeenCalledWith(10);
    expect(listByRuleSetSpy).toHaveBeenCalled();
  });

  it('navigates back to /rule-sets', () => {
    component.goBack();
    expect(navigateSpy).toHaveBeenCalledWith(['/rule-sets']);
  });

  it('shows not-found empty state for missing rule set', () => {
    selectedRuleSet.set(null);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Rule set not found');
  });
});
