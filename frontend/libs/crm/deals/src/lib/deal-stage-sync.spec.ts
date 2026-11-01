import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { computed, signal } from '@angular/core';
import { of } from 'rxjs';
import { DealListComponent } from './deal-list/deal-list.component';
import { DealKanbanComponent } from './deal-kanban/deal-kanban.component';
import { DealService } from './deal.service';
import { DealDto, PipelineSummaryDto } from '@frontend/shared-models';

function deal(partial: Partial<DealDto> & Pick<DealDto, 'id' | 'name' | 'stage'>): DealDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('list and kanban share stage changes', () => {
  let listFixture: ComponentFixture<DealListComponent>;
  let kanbanFixture: ComponentFixture<DealKanbanComponent>;

  const deals = signal<DealDto[]>([
    deal({
      id: 1,
      name: 'Shared',
      stage: 'PROSPECTING',
      amount: 999999,
      currency: 'JPY',
      baseAmount: 1000,
      baseCurrency: 'USD',
      baseWeightedAmount: 100,
      organizationName: 'Northwind',
    }),
  ]);
  const selectedDeal = signal<DealDto | null>(null);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pagination = signal({ page: 0, size: 20, totalElements: 1, totalPages: 1 });
  const pipelineSummary = signal<PipelineSummaryDto[] | null>([
    { stage: 'PROSPECTING', count: 1, totalAmount: 1000, weightedAmount: 100, baseCurrency: 'USD' },
  ]);

  const service = {
    deals,
    selectedDeal,
    loading,
    error,
    pagination,
    pipelineSummary,
    isEmpty: computed(() => deals().length === 0 && !loading()),
    list: vi.fn(),
    search: vi.fn(),
    loadPipelineSummary: vi.fn(),
    delete: vi.fn(() => of(undefined)),
    advanceStage: vi.fn(() => of(deal({
      id: 1,
      name: 'Shared',
      stage: 'QUALIFICATION',
      amount: 999999,
      currency: 'JPY',
      baseAmount: 1000,
      baseCurrency: 'USD',
      baseWeightedAmount: 250,
    }))),
    markWon: vi.fn(),
    markLost: vi.fn(),
    applyStageChange(updated: DealDto): void {
      deals.update((rows) => rows.map((row) => (row.id === updated.id ? { ...row, ...updated } : row)));
      const selected = selectedDeal();
      if (selected && selected.id === updated.id) {
        selectedDeal.set({ ...selected, ...updated });
      }
    },
  };

  beforeEach(async () => {
    deals.set([
      deal({
        id: 1,
        name: 'Shared',
        stage: 'PROSPECTING',
        amount: 999999,
        currency: 'JPY',
        baseAmount: 1000,
        baseCurrency: 'USD',
        baseWeightedAmount: 100,
        organizationName: 'Northwind',
      }),
    ]);
    loading.set(false);
    error.set(null);

    await TestBed.configureTestingModule({
      imports: [DealListComponent, DealKanbanComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: DealService, useValue: service },
        { provide: MatDialog, useValue: { open: vi.fn(() => ({ afterClosed: () => of(undefined) })) } },
      ],
    }).compileComponents();

    listFixture = TestBed.createComponent(DealListComponent);
    kanbanFixture = TestBed.createComponent(DealKanbanComponent);
    listFixture.detectChanges();
    kanbanFixture.detectChanges();
  });

  it('moves the deal in both views from the same signal and keeps the base-currency total', () => {
    listFixture.componentInstance.onAdvance(deals()[0]);
    listFixture.detectChanges();
    kanbanFixture.detectChanges();

    expect(deals()[0].stage).toBe('QUALIFICATION');

    const kanban = kanbanFixture.componentInstance;
    const qualification = kanban.columns().find((column) => column.stage === 'QUALIFICATION');
    const prospecting = kanban.columns().find((column) => column.stage === 'PROSPECTING');
    expect(qualification?.deals.map((row) => row.id)).toEqual([1]);
    expect(prospecting?.deals).toEqual([]);
    expect(kanban.pipelineTotals()).toEqual({
      count: 1,
      amount: 1000,
      weighted: 250,
      unconverted: 0,
    });
    expect(listFixture.componentInstance.visibleDeals()[0].stage).toBe('QUALIFICATION');
  });
});
