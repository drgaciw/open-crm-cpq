import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal, computed } from '@angular/core';
import { of } from 'rxjs';
import { DealKanbanComponent } from './deal-kanban.component';
import { DealService } from '../deal.service';
import { DealDto, PipelineSummaryDto } from '@frontend/shared-models';

function deal(partial: Partial<DealDto> & Pick<DealDto, 'id' | 'name' | 'stage'>): DealDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('DealKanbanComponent pipeline wiring', () => {
  let fixture: ComponentFixture<DealKanbanComponent>;
  let component: DealKanbanComponent;
  let listSpy: ReturnType<typeof vi.fn>;
  let loadPipelineSpy: ReturnType<typeof vi.fn>;
  let advanceSpy: ReturnType<typeof vi.fn>;
  let markWonSpy: ReturnType<typeof vi.fn>;
  let markLostSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  const deals = signal<DealDto[]>([
    deal({ id: 1, name: 'A', stage: 'PROSPECTING', amount: 1000 }),
    deal({ id: 2, name: 'B', stage: 'PROPOSAL', amount: 5000 }),
    deal({ id: 3, name: 'C', stage: 'PROSPECTING', amount: 250 }),
    deal({ id: 4, name: 'D', stage: 'NEGOTIATION' }),
  ]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pipelineSummary = signal<PipelineSummaryDto[] | null>([
    { stage: 'PROSPECTING', count: 1, totalAmount: 1000, weightedAmount: 100 },
    { stage: 'PROPOSAL', count: 1, totalAmount: 5000, weightedAmount: 2500 },
    { stage: 'WON', count: 3, totalAmount: 9000, weightedAmount: 9000 },
  ]);

  beforeEach(async () => {
    listSpy = vi.fn();
    loadPipelineSpy = vi.fn();
    advanceSpy = vi.fn(() => of(deal({ id: 1, name: 'A', stage: 'QUALIFICATION' })));
    markWonSpy = vi.fn(() => of(deal({ id: 2, name: 'B', stage: 'WON' })));
    markLostSpy = vi.fn(() => of(deal({ id: 2, name: 'B', stage: 'LOST' })));
    dialogOpen = vi.fn(() => ({ afterClosed: () => of(undefined) }));
    deals.set([
      deal({ id: 1, name: 'A', stage: 'PROSPECTING', amount: 1000 }),
      deal({ id: 2, name: 'B', stage: 'PROPOSAL', amount: 5000 }),
      deal({ id: 3, name: 'C', stage: 'PROSPECTING', amount: 250 }),
      deal({ id: 4, name: 'D', stage: 'NEGOTIATION' }),
    ]);
    error.set(null);
    loading.set(false);

    const serviceMock = {
      deals,
      loading,
      error,
      pipelineSummary,
      isEmpty: computed(() => deals().length === 0 && !loading()),
      list: listSpy,
      loadPipelineSummary: loadPipelineSpy,
      advanceStage: advanceSpy,
      markWon: markWonSpy,
      markLost: markLostSpy,
    };

    await TestBed.configureTestingModule({
      imports: [DealKanbanComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: DealService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DealKanbanComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('ngOnInit loads deals list and pipeline summary', () => {
    expect(listSpy).toHaveBeenCalledWith(0, 200, 'createdAt,desc');
    expect(loadPipelineSpy).toHaveBeenCalled();
  });

  it('pipelineTotals sums open stages from loadPipelineSummary', () => {
    const totals = component.pipelineTotals();
    expect(totals).toEqual({
      count: 2,
      amount: 6000,
      weighted: 2600,
    });
  });

  it('columns group deals by stage with single-pass totals', () => {
    const cols = component.columns();
    expect(cols).toHaveLength(7);

    const prospecting = cols.find((c) => c.stage === 'PROSPECTING');
    const proposal = cols.find((c) => c.stage === 'PROPOSAL');
    const negotiation = cols.find((c) => c.stage === 'NEGOTIATION');
    const qualification = cols.find((c) => c.stage === 'QUALIFICATION');

    expect(prospecting?.deals.map((d) => d.id)).toEqual([1, 3]);
    expect(prospecting?.totalValue).toBe(1250);
    expect(proposal?.deals).toHaveLength(1);
    expect(proposal?.totalValue).toBe(5000);
    expect(negotiation?.deals).toHaveLength(1);
    expect(negotiation?.totalValue).toBe(0);
    expect(qualification?.deals).toEqual([]);
    expect(qualification?.totalValue).toBe(0);
  });

  it('onAdvance wires advanceStage and refreshes board + summary', () => {
    listSpy.mockClear();
    loadPipelineSpy.mockClear();

    component.onAdvance(deal({ id: 1, name: 'A', stage: 'PROSPECTING' }));

    expect(advanceSpy).toHaveBeenCalledWith(1);
    expect(listSpy).toHaveBeenCalledWith(0, 200, 'createdAt,desc');
    expect(loadPipelineSpy).toHaveBeenCalled();
    expect(component.actionLoading()).toBe(false);
  });

  it('onWin wires markWon after confirm', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    listSpy.mockClear();

    component.onWin(deal({ id: 2, name: 'B', stage: 'PROPOSAL' }));

    expect(markWonSpy).toHaveBeenCalledWith(2);
    expect(listSpy).toHaveBeenCalled();
  });

  it('onLose opens dialog and calls markLost with reason', () => {
    dialogOpen.mockReturnValueOnce({ afterClosed: () => of('Competitor') });
    listSpy.mockClear();
    loadPipelineSpy.mockClear();

    component.onLose(deal({ id: 2, name: 'B', stage: 'NEGOTIATION' }));

    expect(dialogOpen).toHaveBeenCalled();
    expect(markLostSpy).toHaveBeenCalledWith(2, 'Competitor');
    expect(listSpy).toHaveBeenCalled();
    expect(loadPipelineSpy).toHaveBeenCalled();
  });

  it('hides advance for NEGOTIATION terminal close path', () => {
    expect(component.canAdvance('NEGOTIATION')).toBe(false);
    expect(component.canClose('NEGOTIATION')).toBe(true);
  });

  it('shows error banner and hides board on failed load', () => {
    deals.set([]);
    loading.set(false);
    error.set('Failed to load deals [DEALS_UNAVAILABLE]');
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const banner = el.querySelector('.error-banner') as HTMLElement;
    expect(banner).toBeTruthy();
    expect(banner.getAttribute('role')).toBe('alert');
    expect(banner.textContent).toContain('Failed to load deals [DEALS_UNAVAILABLE]');
    expect(banner.textContent).toContain('Retry');
    expect(el.querySelector('.kanban-board')).toBeNull();
    expect(el.querySelector('.empty-column')).toBeNull();
    expect(el.querySelector('app-empty-state')).toBeNull();
  });

  it('shows per-column empty state only when load succeeded', () => {
    deals.set([]);
    loading.set(false);
    error.set(null);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.kanban-board')).toBeTruthy();
    expect(el.querySelectorAll('.empty-column').length).toBeGreaterThan(0);
    expect(el.querySelector('.error-banner')).toBeNull();
  });

  it('clearError dismisses the banner', () => {
    error.set('Failed to load deals [DEALS_UNAVAILABLE]');
    fixture.detectChanges();
    component.clearError();
    fixture.detectChanges();
    expect(error()).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('.error-banner')).toBeNull();
  });

  it('onRetry reloads list and pipeline summary', () => {
    listSpy.mockClear();
    loadPipelineSpy.mockClear();
    error.set('Failed to load deals [DEALS_UNAVAILABLE]');
    component.onRetry();
    expect(listSpy).toHaveBeenCalledWith(0, 200, 'createdAt,desc');
    expect(loadPipelineSpy).toHaveBeenCalled();
  });

  it('loading spinner wrapper exposes role=status and aria-live=polite', () => {
    loading.set(true);
    error.set(null);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const loadingEl = el.querySelector('.loading') as HTMLElement;
    expect(loadingEl).toBeTruthy();
    expect(loadingEl.getAttribute('role')).toBe('status');
    expect(loadingEl.getAttribute('aria-live')).toBe('polite');
    expect(el.querySelector('.kanban-board')).toBeNull();
  });
});
