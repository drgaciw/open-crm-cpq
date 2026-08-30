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
  ]);
  const loading = signal(false);
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

    const serviceMock = {
      deals,
      loading,
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

  it('columns group deals by stage', () => {
    const cols = component.columns();
    const prospecting = cols.find((c) => c.stage === 'PROSPECTING');
    const proposal = cols.find((c) => c.stage === 'PROPOSAL');
    expect(prospecting?.deals).toHaveLength(1);
    expect(proposal?.deals).toHaveLength(1);
    expect(prospecting?.totalValue).toBe(1000);
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
});
