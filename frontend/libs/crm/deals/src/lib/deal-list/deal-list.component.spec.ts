import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal, computed } from '@angular/core';
import { of, throwError } from 'rxjs';
import { DealListComponent } from './deal-list.component';
import { DealService } from '../deal.service';
import { DealDto, PaginationMeta } from '@frontend/shared-models';

function deal(partial: Partial<DealDto> & Pick<DealDto, 'id' | 'name' | 'stage'>): DealDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('DealListComponent action wiring', () => {
  let fixture: ComponentFixture<DealListComponent>;
  let component: DealListComponent;
  let listSpy: ReturnType<typeof vi.fn>;
  let searchSpy: ReturnType<typeof vi.fn>;
  let advanceSpy: ReturnType<typeof vi.fn>;
  let markWonSpy: ReturnType<typeof vi.fn>;
  let markLostSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  });
  const deals = signal<DealDto[]>([]);
  const loading = signal(false);

  beforeEach(async () => {
    listSpy = vi.fn();
    searchSpy = vi.fn();
    advanceSpy = vi.fn(() => of(deal({ id: 1, name: 'A', stage: 'QUALIFICATION' })));
    markWonSpy = vi.fn(() => of(deal({ id: 1, name: 'A', stage: 'WON' })));
    markLostSpy = vi.fn(() => of(deal({ id: 1, name: 'A', stage: 'LOST' })));
    dialogOpen = vi.fn(() => ({ afterClosed: () => of(undefined) }));

    const serviceMock = {
      deals,
      loading,
      pagination,
      isEmpty: computed(() => deals().length === 0 && !loading()),
      list: listSpy,
      search: searchSpy,
      delete: vi.fn(() => of(void 0)),
      advanceStage: advanceSpy,
      markWon: markWonSpy,
      markLost: markLostSpy,
    };

    await TestBed.configureTestingModule({
      imports: [DealListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: DealService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DealListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('ngOnInit loads the deal list', () => {
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'createdAt,desc');
  });

  it('renders shared SearchInput instead of raw input', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('app-search-input')).toBeTruthy();
    expect(el.querySelector('input[aria-label="Search deals"]')).toBeNull();
  });

  it('renders app-page-header and app-empty-state when unfiltered empty', () => {
    deals.set([]);
    loading.set(false);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('app-page-header')).toBeTruthy();
    expect(el.querySelector('app-empty-state')).toBeTruthy();
    expect(el.querySelector('.empty-state-title')?.textContent).toContain('No deals yet');
    expect(el.querySelector('.empty-state-action')?.textContent).toContain('Create Deal');
  });

  it('hides Create Deal action when a stage filter is active and empty', () => {
    deals.set([]);
    loading.set(false);
    component.onTabChange(1); // PROSPECTING
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.empty-state-title')?.textContent).toContain('No matching deals');
    expect(el.querySelector('.empty-state-action')).toBeNull();
  });

  it('loading spinner wrapper exposes role=status and aria-live=polite', () => {
    loading.set(true);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const loadingEl = el.querySelector('.loading') as HTMLElement;
    expect(loadingEl).toBeTruthy();
    expect(loadingEl.getAttribute('role')).toBe('status');
    expect(loadingEl.getAttribute('aria-live')).toBe('polite');
  });

  it('onSearch with term calls DealService.search', () => {
    component.onSearch('  acme  ');
    expect(searchSpy).toHaveBeenCalledWith('acme');
  });

  it('onSearch with blank term reloads list for current stage', () => {
    listSpy.mockClear();
    component.onTabChange(2); // QUALIFICATION
    listSpy.mockClear();
    component.onSearch('   ');
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'createdAt,desc', 'QUALIFICATION');
    expect(searchSpy).not.toHaveBeenCalled();
  });

  it('gates advance: open stages yes, NEGOTIATION/WON/LOST no', () => {
    expect(component.canAdvance('PROSPECTING')).toBe(true);
    expect(component.canAdvance('PROPOSAL')).toBe(true);
    expect(component.canAdvance('NEGOTIATION')).toBe(false);
    expect(component.canAdvance('WON')).toBe(false);
    expect(component.canAdvance('LOST')).toBe(false);
  });

  it('onAdvance calls DealService.advanceStage and refreshes', () => {
    listSpy.mockClear();
    component.onAdvance(deal({ id: 11, name: 'Pipe', stage: 'PROSPECTING' }));

    expect(advanceSpy).toHaveBeenCalledWith(11);
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'createdAt,desc', undefined);
    expect(component.actionLoading()).toBe(false);
  });

  it('onWin calls markWon after confirm', () => {
    listSpy.mockClear();
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    component.onWin(deal({ id: 12, name: 'Close me', stage: 'NEGOTIATION' }));

    expect(markWonSpy).toHaveBeenCalledWith(12);
    expect(listSpy).toHaveBeenCalled();
    expect(component.actionLoading()).toBe(false);
  });

  it('onWin does nothing when confirm cancelled', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    component.onWin(deal({ id: 12, name: 'Close me', stage: 'NEGOTIATION' }));
    expect(markWonSpy).not.toHaveBeenCalled();
  });

  it('onLose opens Material dialog and posts reason', () => {
    listSpy.mockClear();
    dialogOpen.mockReturnValueOnce({ afterClosed: () => of('No budget') });

    component.onLose(deal({ id: 13, name: 'Lost hope', stage: 'PROPOSAL' }));

    expect(dialogOpen).toHaveBeenCalled();
    expect(markLostSpy).toHaveBeenCalledWith(13, 'No budget');
    expect(listSpy).toHaveBeenCalled();
    expect(component.actionLoading()).toBe(false);
  });

  it('onLose skips API when dialog cancelled', () => {
    dialogOpen.mockReturnValueOnce({ afterClosed: () => of(undefined) });
    component.onLose(deal({ id: 13, name: 'Lost hope', stage: 'PROPOSAL' }));
    expect(markLostSpy).not.toHaveBeenCalled();
  });

  it('clears actionLoading when advance fails', () => {
    advanceSpy.mockReturnValueOnce(throwError(() => new Error('fail')));
    component.onAdvance(deal({ id: 11, name: 'Pipe', stage: 'PROSPECTING' }));
    expect(component.actionLoading()).toBe(false);
  });
});
