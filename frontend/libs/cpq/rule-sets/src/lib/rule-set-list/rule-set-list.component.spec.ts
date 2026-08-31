import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { computed, signal } from '@angular/core';
import { of } from 'rxjs';
import { RuleSetListComponent } from './rule-set-list.component';
import { RuleSetCreateDialogComponent } from '../rule-set-create-dialog/rule-set-create-dialog.component';
import { MOCK_RULE_SETS } from '../rule-sets.mock';
import { RuleSetService } from '@frontend/cpq-pricing-data-access';
import { PaginationMeta, RuleSetDto } from '@frontend/shared-models';

describe('RuleSetListComponent', () => {
  let fixture: ComponentFixture<RuleSetListComponent>;
  let component: RuleSetListComponent;
  let router: Router;
  let listSpy: ReturnType<typeof vi.fn>;
  let listByStatusSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  const ruleSets = signal<RuleSetDto[]>([...MOCK_RULE_SETS]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: MOCK_RULE_SETS.length,
    totalPages: 1,
  });

  beforeEach(async () => {
    listSpy = vi.fn(() => {
      ruleSets.set([...MOCK_RULE_SETS]);
      pagination.set({
        page: 0,
        size: 20,
        totalElements: MOCK_RULE_SETS.length,
        totalPages: 1,
      });
    });
    listByStatusSpy = vi.fn((status: string) => {
      const next = MOCK_RULE_SETS.filter((r) => r.status === status);
      ruleSets.set(next);
      pagination.set({
        page: 0,
        size: 20,
        totalElements: next.length,
        totalPages: Math.max(1, Math.ceil(next.length / 20) || 1),
      });
    });

    const serviceMock = {
      ruleSets,
      loading,
      error,
      pagination,
      isEmpty: computed(() => ruleSets().length === 0 && !loading()),
      list: listSpy,
      listByStatus: listByStatusSpy,
      selectedRuleSet: signal<RuleSetDto | null>(null),
    };

    dialogOpen = vi.fn().mockReturnValue({
      afterClosed: () => of(undefined),
    });

    await TestBed.configureTestingModule({
      imports: [RuleSetListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: RuleSetService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RuleSetListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    ruleSets.set([...MOCK_RULE_SETS]);
    loading.set(false);
    error.set(null);
  });

  it('loads rule sets via RuleSetService.list on init', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc');
    expect(compiled.textContent).toContain('Rule sets');
    expect(compiled.textContent).toContain('/api/v1/rule-sets');
    expect(component.tableRows().length).toBe(MOCK_RULE_SETS.length);
    expect(compiled.textContent).toContain('Standard Pricing');
  });

  it('filters by status via RuleSetService.listByStatus', () => {
    component.onStatusChange('ACTIVE');
    fixture.detectChanges();

    expect(listByStatusSpy).toHaveBeenCalledWith('ACTIVE', 0, 20);
    expect(component.tableRows().length).toBe(1);
    expect(component.tableRows()[0].name).toBe('Enterprise Discount');
  });

  it('shows empty state when filters match nothing', () => {
    listByStatusSpy.mockImplementation(() => {
      ruleSets.set([]);
      pagination.set({
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      });
    });
    component.onStatusChange('ARCHIVED');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(component.isEmpty()).toBe(true);
    expect(compiled.textContent).toContain('No matching rule sets');
  });

  it('shows empty state when there are no rule sets', () => {
    ruleSets.set([]);
    pagination.set({
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No rule sets yet');
  });

  it('shows error banner when RuleSetService.error is set', () => {
    error.set('Failed to load rule sets');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load rule sets');
  });

  it('navigates to detail on row click', () => {
    component.onRowClick(component.tableRows()[0]);
    expect(router.navigate).toHaveBeenCalledWith(['/rule-sets', 1]);
  });

  it('opens create dialog and navigates on success', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(MOCK_RULE_SETS[0]),
    });
    component.openCreateDialog();
    expect(dialogOpen).toHaveBeenCalledWith(
      RuleSetCreateDialogComponent,
      expect.objectContaining({ width: '560px', disableClose: true }),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/rule-sets', 1]);
  });

  it('clears status filter and reloads all', () => {
    component.onStatusChange('DRAFT');
    listSpy.mockClear();
    component.clearStatusFilter();
    expect(component.statusFilter()).toBe('');
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc');
  });
});
