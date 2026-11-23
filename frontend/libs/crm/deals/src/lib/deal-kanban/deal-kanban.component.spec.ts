import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import {
  CdkDrag,
  CdkDragDrop,
  CdkDropList,
  CdkDropListGroup,
} from '@angular/cdk/drag-drop';
import { MatDialog } from '@angular/material/dialog';
import { signal, computed } from '@angular/core';
import { of, throwError } from 'rxjs';
import { DealKanbanComponent } from './deal-kanban.component';
import { DealService } from '../deal.service';
import { DealDto, DealStage, PipelineSummaryDto } from '@frontend/shared-models';

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
  let applyStageChange: ReturnType<typeof vi.fn>;
  let announceSpy: ReturnType<typeof vi.fn>;

  const deals = signal<DealDto[]>([
    deal({ id: 1, name: 'A', stage: 'PROSPECTING', amount: 1000, currency: 'USD', baseAmount: 1000, baseCurrency: 'USD' }),
    deal({ id: 2, name: 'B', stage: 'PROPOSAL', amount: 5000, currency: 'USD', baseAmount: 5000, baseCurrency: 'USD' }),
    deal({ id: 3, name: 'C', stage: 'PROSPECTING', amount: 250, currency: 'USD', baseAmount: 250, baseCurrency: 'USD' }),
    deal({ id: 4, name: 'D', stage: 'NEGOTIATION' }),
  ]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pipelineSummary = signal<PipelineSummaryDto[] | null>([
    { stage: 'PROSPECTING', count: 1, totalAmount: 1000, weightedAmount: 100, baseCurrency: 'USD', unconvertedCount: 0 },
    { stage: 'PROPOSAL', count: 1, totalAmount: 5000, weightedAmount: 2500, baseCurrency: 'USD', unconvertedCount: 0 },
    { stage: 'WON', count: 3, totalAmount: 9000, weightedAmount: 9000, baseCurrency: 'USD', unconvertedCount: 0 },
  ]);

  beforeEach(async () => {
    listSpy = vi.fn();
    loadPipelineSpy = vi.fn();
    advanceSpy = vi.fn(() => of(deal({ id: 1, name: 'A', stage: 'QUALIFICATION' })));
    markWonSpy = vi.fn(() => of(deal({ id: 2, name: 'B', stage: 'WON' })));
    markLostSpy = vi.fn(() => of(deal({ id: 2, name: 'B', stage: 'LOST' })));
    dialogOpen = vi.fn(() => ({ afterClosed: () => of(undefined) }));
    applyStageChange = vi.fn();
    announceSpy = vi.fn(() => Promise.resolve());
    pipelineSummary.set([
      { stage: 'PROSPECTING', count: 1, totalAmount: 1000, weightedAmount: 100, baseCurrency: 'USD', unconvertedCount: 0 },
      { stage: 'PROPOSAL', count: 1, totalAmount: 5000, weightedAmount: 2500, baseCurrency: 'USD', unconvertedCount: 0 },
      { stage: 'WON', count: 3, totalAmount: 9000, weightedAmount: 9000, baseCurrency: 'USD', unconvertedCount: 0 },
    ]);
    deals.set([
      deal({ id: 1, name: 'A', stage: 'PROSPECTING', amount: 1000, currency: 'USD', baseAmount: 1000, baseCurrency: 'USD' }),
      deal({ id: 2, name: 'B', stage: 'PROPOSAL', amount: 5000, currency: 'USD', baseAmount: 5000, baseCurrency: 'USD' }),
      deal({ id: 3, name: 'C', stage: 'PROSPECTING', amount: 250, currency: 'USD', baseAmount: 250, baseCurrency: 'USD' }),
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
      applyStageChange,
    };

    await TestBed.configureTestingModule({
      imports: [DealKanbanComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: DealService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
        { provide: LiveAnnouncer, useValue: { announce: announceSpy } },
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

  it('pipelineTotals sum open deals from base-currency DTO fields', () => {
    const totals = component.pipelineTotals();
    expect(totals).toEqual({
      count: 4,
      amount: 6250,
      weighted: 0,
      unconverted: 0,
    });
  });

  it('pipeline header does not add mixed transaction amounts', () => {
    deals.set([
      deal({
        id: 1, name: 'USD', stage: 'PROSPECTING', amount: 100, currency: 'USD',
        baseAmount: 100, baseCurrency: 'USD', baseWeightedAmount: 10,
      }),
      deal({
        id: 2, name: 'EUR', stage: 'PROPOSAL', amount: 999999, currency: 'EUR',
        baseAmount: 200, baseCurrency: 'USD', baseWeightedAmount: 50,
      }),
      deal({ id: 3, name: 'GBP', stage: 'NEGOTIATION', amount: 500, currency: 'GBP' }),
      deal({
        id: 4, name: 'Won', stage: 'WON', amount: 800, currency: 'USD',
        baseAmount: 800, baseCurrency: 'USD', baseWeightedAmount: 800,
      }),
    ]);

    expect(component.pipelineTotals()).toEqual({
      count: 3,
      amount: 300,
      weighted: 60,
      unconverted: 1,
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

  it('ADR-032: column totals add base amounts only and count deals without a base amount', () => {
    deals.set([
      deal({ id: 1, name: 'USD', stage: 'PROSPECTING', amount: 1000000, currency: 'USD', baseAmount: 1000000, baseCurrency: 'USD' }),
      deal({ id: 2, name: 'EUR converted', stage: 'PROSPECTING', amount: 1000000, currency: 'EUR', baseAmount: 1085000, baseCurrency: 'USD' }),
      deal({ id: 3, name: 'GBP no rate', stage: 'PROSPECTING', amount: 500000, currency: 'GBP' }),
    ]);

    const prospecting = component.columns().find((c) => c.stage === 'PROSPECTING');

    expect(prospecting?.totalValue).toBe(2085000);
    expect(prospecting?.unconverted).toBe(1);
  });

  it('ADR-032: base currency comes from the deal, then the pipeline summary', () => {
    expect(component.baseCurrency()).toBe('USD');

    deals.set([
      deal({ id: 9, name: 'No base', stage: 'PROSPECTING', amount: 10, currency: 'GBP' }),
    ]);
    pipelineSummary.set([
      { stage: 'PROSPECTING', count: 1, totalAmount: 10, weightedAmount: 1, baseCurrency: 'EUR', unconvertedCount: 2 },
    ]);

    expect(component.baseCurrency()).toBe('EUR');
    expect(component.pipelineTotals()).toEqual({
      count: 1,
      amount: 0,
      weighted: 0,
      unconverted: 1,
    });
  });

  it('onAdvance wires advanceStage and refreshes board + summary', () => {
    listSpy.mockClear();
    loadPipelineSpy.mockClear();

    component.onAdvance(deal({ id: 1, name: 'A', stage: 'PROSPECTING' }));

    expect(advanceSpy).toHaveBeenCalledWith(1);
    expect(applyStageChange).toHaveBeenCalledWith(expect.objectContaining({ id: 1, stage: 'QUALIFICATION' }));
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

  it('onWin shows an actionable error when the quote is not accepted', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    listSpy.mockClear();
    markWonSpy.mockReturnValueOnce(throwError(() => ({
      errorCode: 'OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE',
    })));

    component.onWin(deal({ id: 2, name: 'B', stage: 'PROPOSAL' }));
    fixture.detectChanges();

    const banner = (fixture.nativeElement as HTMLElement).querySelector('.error-banner');
    expect(banner?.textContent).toContain('Accept the quote');
    expect(banner?.textContent).toContain('OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE');
    expect(applyStageChange).not.toHaveBeenCalled();
    expect(listSpy).not.toHaveBeenCalled();
    expect(deals().find((row) => row.id === 2)?.stage).toBe('PROPOSAL');
  });

  it('keeps the board visible when Mark Won is refused, with a dismiss-only banner (UAT 2026-11-14)', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    markWonSpy.mockReturnValueOnce(throwError(() => ({
      errorCode: 'OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE',
    })));

    component.onWin(deal({ id: 2, name: 'B', stage: 'PROPOSAL' }));
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.kanban-board')).toBeTruthy();
    const banner = el.querySelector('.error-banner') as HTMLElement;
    expect(banner.getAttribute('role')).toBe('alert');
    expect(banner.textContent).toContain('OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE');
    // Retry reloads the board; it would not retry the refused win, so it is not offered here.
    expect(banner.textContent).not.toContain('Retry');
    // A refused action is not a load failure.
    expect(error()).toBeNull();

    const dismiss = Array.from(banner.querySelectorAll('button')).find((b) => b.textContent?.includes('Dismiss'));
    dismiss?.click();
    fixture.detectChanges();
    expect(el.querySelector('.error-banner')).toBeNull();
    expect(el.querySelector('.kanban-board')).toBeTruthy();
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

  describe('CDK Drag & Drop (Phase 2.1)', () => {
    function makeDrag(d: DealDto): CdkDrag<DealDto> {
      return { data: d } as unknown as CdkDrag<DealDto>;
    }

    function makeDropList(stage: DealStage): CdkDropList<DealStage> {
      return { data: stage } as unknown as CdkDropList<DealStage>;
    }

    function makeDropEvent(
      d: DealDto,
      from: DealStage,
      to: DealStage,
    ): CdkDragDrop<DealStage, DealStage, DealDto> {
      const prev = makeDropList(from);
      const curr = from === to ? prev : makeDropList(to);
      return {
        previousContainer: prev,
        container: curr,
        item: makeDrag(d),
        previousIndex: 0,
        currentIndex: 0,
        isPointerOverContainer: true,
        distance: { x: 100, y: 0 },
        dropPoint: { x: 100, y: 0 },
        event: new MouseEvent('mouseup'),
      };
    }

    it('wires cdkDropListGroup, per-stage cdkDropList, and cdkDrag on cards', () => {
      deals.set([
        deal({ id: 1, name: 'Open Deal', stage: 'PROSPECTING' }),
        deal({ id: 2, name: 'Closed Deal', stage: 'WON' }),
      ]);
      fixture.detectChanges();

      const group = fixture.debugElement.query(By.directive(CdkDropListGroup));
      expect(group).toBeTruthy();

      const dropLists = fixture.debugElement.queryAll(By.directive(CdkDropList));
      expect(dropLists).toHaveLength(7);

      const drags = fixture.debugElement.queryAll(By.directive(CdkDrag));
      expect(drags).toHaveLength(2);

      const openDrag = drags
        .map((de) => de.injector.get(CdkDrag))
        .find((d) => (d.data as DealDto).id === 1);
      const closedDrag = drags
        .map((de) => de.injector.get(CdkDrag))
        .find((d) => (d.data as DealDto).id === 2);

      expect(openDrag?.disabled).toBe(false);
      expect(closedDrag?.disabled).toBe(true);
    });

    it('enter predicate enforces canAdvanceDeal (one-step forward) and canCloseDeal (WON/LOST)', () => {
      const prospecting = deal({ id: 1, name: 'P', stage: 'PROSPECTING' });
      const negotiation = deal({ id: 4, name: 'N', stage: 'NEGOTIATION' });
      const won = deal({ id: 5, name: 'W', stage: 'WON' });

      expect(component.canEnterStage(makeDrag(prospecting), makeDropList('QUALIFICATION'))).toBe(true);
      expect(component.canEnterStage(makeDrag(prospecting), makeDropList('PROPOSAL'))).toBe(false);
      expect(component.canEnterStage(makeDrag(prospecting), makeDropList('WON'))).toBe(true);
      expect(component.canEnterStage(makeDrag(prospecting), makeDropList('LOST'))).toBe(true);
      expect(component.canEnterStage(makeDrag(prospecting), makeDropList('PROSPECTING'))).toBe(true);

      expect(component.canEnterStage(makeDrag(negotiation), makeDropList('PROPOSAL'))).toBe(false);
      expect(component.canEnterStage(makeDrag(negotiation), makeDropList('WON'))).toBe(true);
      expect(component.canEnterStage(makeDrag(negotiation), makeDropList('LOST'))).toBe(true);

      expect(component.canEnterStage(makeDrag(won), makeDropList('PROSPECTING'))).toBe(false);
      expect(component.canEnterStage(makeDrag(won), makeDropList('LOST'))).toBe(false);
    });

    it('onDrop to next valid stage advances deal and announces via LiveAnnouncer', () => {
      listSpy.mockClear();
      loadPipelineSpy.mockClear();
      const target = deal({ id: 1, name: 'A', stage: 'PROSPECTING' });

      component.onDrop(makeDropEvent(target, 'PROSPECTING', 'QUALIFICATION'));

      expect(advanceSpy).toHaveBeenCalledWith(1);
      expect(applyStageChange).toHaveBeenCalledWith(
        expect.objectContaining({ id: 1, stage: 'QUALIFICATION' }),
      );
      expect(listSpy).toHaveBeenCalled();
      expect(loadPipelineSpy).toHaveBeenCalled();
      expect(announceSpy).toHaveBeenCalledWith(
        'Moved "A" to Qualification',
        'polite',
      );
    });

    it('onDrop to LOST opens DealLoseReasonDialogComponent and calls markLost with reason', () => {
      dialogOpen.mockReturnValueOnce({ afterClosed: () => of('Budget frozen') });
      listSpy.mockClear();
      const target = deal({ id: 2, name: 'B', stage: 'PROPOSAL' });

      component.onDrop(makeDropEvent(target, 'PROPOSAL', 'LOST'));

      expect(dialogOpen).toHaveBeenCalled();
      expect(markLostSpy).toHaveBeenCalledWith(2, 'Budget frozen');
      expect(announceSpy).toHaveBeenCalledWith('Moved "B" to Lost', 'polite');
    });

    it('onDrop to LOST does nothing when lose-reason dialog is cancelled', () => {
      dialogOpen.mockReturnValueOnce({ afterClosed: () => of(undefined) });
      const target = deal({ id: 2, name: 'B', stage: 'PROPOSAL' });

      component.onDrop(makeDropEvent(target, 'PROPOSAL', 'LOST'));

      expect(dialogOpen).toHaveBeenCalled();
      expect(markLostSpy).not.toHaveBeenCalled();
      expect(applyStageChange).not.toHaveBeenCalled();
    });

    it('onDrop to WON calls markWon and announces via LiveAnnouncer', () => {
      listSpy.mockClear();
      const target = deal({ id: 2, name: 'B', stage: 'PROPOSAL' });

      component.onDrop(makeDropEvent(target, 'PROPOSAL', 'WON'));

      expect(markWonSpy).toHaveBeenCalledWith(2);
      expect(applyStageChange).toHaveBeenCalledWith(
        expect.objectContaining({ id: 2, stage: 'WON' }),
      );
      expect(announceSpy).toHaveBeenCalledWith('Moved "B" to Won', 'polite');
    });

    it('onDrop to WON rolls back cleanly on OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE and announces assertively', () => {
      listSpy.mockClear();
      markWonSpy.mockReturnValueOnce(
        throwError(() => ({ errorCode: 'OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE' })),
      );
      const target = deal({ id: 2, name: 'B', stage: 'PROPOSAL' });

      component.onDrop(makeDropEvent(target, 'PROPOSAL', 'WON'));
      fixture.detectChanges();

      expect(applyStageChange).not.toHaveBeenCalled();
      expect(listSpy).not.toHaveBeenCalled();
      expect(deals().find((row) => row.id === 2)?.stage).toBe('PROPOSAL');
      expect(component.actionLoading()).toBe(false);
      expect((fixture.nativeElement as HTMLElement).querySelector('.kanban-board')).toBeTruthy();
      expect((fixture.nativeElement as HTMLElement).querySelector('.error-banner')?.textContent).toContain(
        'OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE',
      );
      expect(announceSpy).toHaveBeenCalledWith(
        expect.stringContaining('OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE'),
        'assertive',
      );
    });

    it('onDrop ignores same-column or disallowed stage drops', () => {
      const target = deal({ id: 1, name: 'A', stage: 'PROSPECTING' });

      component.onDrop(makeDropEvent(target, 'PROSPECTING', 'PROSPECTING'));
      component.onDrop(makeDropEvent(target, 'PROSPECTING', 'PROPOSAL'));

      expect(advanceSpy).not.toHaveBeenCalled();
      expect(markWonSpy).not.toHaveBeenCalled();
      expect(markLostSpy).not.toHaveBeenCalled();
    });

    it('onDrop ignores drop when pointer was released outside container (e.g. over a refused skip column)', () => {
      const target = deal({ id: 1, name: 'A', stage: 'QUALIFICATION' });
      const event = {
        ...makeDropEvent(target, 'QUALIFICATION', 'NEEDS_ANALYSIS'),
        isPointerOverContainer: false,
      };

      component.onDrop(event);

      expect(advanceSpy).not.toHaveBeenCalled();
      expect(markWonSpy).not.toHaveBeenCalled();
      expect(markLostSpy).not.toHaveBeenCalled();
    });

    it('preserves mat-menu keyboard action trigger on open deal cards', () => {
      const menuButtons = (fixture.nativeElement as HTMLElement).querySelectorAll(
        'button.card-menu-btn[aria-label="Deal stage actions"]',
      );
      expect(menuButtons.length).toBe(4);
    });
  });
});
