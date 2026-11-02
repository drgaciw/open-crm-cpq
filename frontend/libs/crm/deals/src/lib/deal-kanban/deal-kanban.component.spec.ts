import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal, computed } from '@angular/core';
import { of, throwError } from 'rxjs';
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
  let applyStageChange: ReturnType<typeof vi.fn>;

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
