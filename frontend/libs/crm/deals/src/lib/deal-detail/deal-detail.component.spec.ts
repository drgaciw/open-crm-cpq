import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { MatDialog } from '@angular/material/dialog';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { StagePathComponent } from '@frontend/shared-ui';
import { DealDetailComponent } from './deal-detail.component';
import { DealService } from '../deal.service';
import { DealDto } from '@frontend/shared-models';

function deal(partial: Partial<DealDto> & Pick<DealDto, 'id' | 'name' | 'stage'>): DealDto {
  return {
    uuid: `u-${partial.id}`,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...partial,
  };
}

describe('DealDetailComponent', () => {
  let fixture: ComponentFixture<DealDetailComponent>;
  let component: DealDetailComponent;
  let markWon: ReturnType<typeof vi.fn>;
  let applyStageChange: ReturnType<typeof vi.fn>;

  const selectedDeal = signal<DealDto | null>(null);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const deals = signal<DealDto[]>([]);

  const openDeal = deal({
    id: 4,
    name: 'Expansion',
    stage: 'PROPOSAL',
    amount: 42000,
    currency: 'EUR',
    organizationId: 9,
    organizationName: 'Northwind',
    ownerId: 'ada',
  });

  beforeEach(async () => {
    markWon = vi.fn(() => of(deal({ id: 4, name: 'Expansion', stage: 'WON' })));
    applyStageChange = vi.fn();
    selectedDeal.set(openDeal);
    deals.set([openDeal]);
    loading.set(false);
    error.set(null);

    await TestBed.configureTestingModule({
      imports: [DealDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: DealService,
          useValue: {
            selectedDeal,
            deals,
            loading,
            error,
            getById: vi.fn(),
            advanceStage: vi.fn(() => of(openDeal)),
            markWon,
            markLost: vi.fn(() => of(openDeal)),
            applyStageChange,
          },
        },
        { provide: MatDialog, useValue: { open: vi.fn(() => ({ afterClosed: () => of(undefined) })) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DealDetailComponent);
    fixture.componentRef.setInput('id', '4');
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('names the progress indicator while reading an opportunity', () => {
    loading.set(true);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"] [role="progressbar"]')?.getAttribute('aria-label'))
      .toBe('Loading opportunity');
  });

  it('names the progress indicator during a pending stage update', () => {
    component.actionLoading.set(true);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.header-actions [role="progressbar"]')?.getAttribute('aria-label'))
      .toBe('Updating opportunity stage');
  });

  it('shows organization, amount, currency, stage, and owner', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Northwind');
    expect(el.textContent).toContain('EUR');
    expect(el.textContent).toContain('Proposal');
    expect(el.textContent).toContain('ada');
    expect(el.querySelector('[data-testid="deal-organization"] a')?.getAttribute('href')).toBe('/organizations/9');
    expect(el.textContent).not.toContain('Linked quote');
  });

  it('shows a probability of 0 as 0%, not as missing (H-CRM-012)', () => {
    selectedDeal.set(deal({ id: 5, name: 'Lost one', stage: 'LOST', probability: 0 }));
    fixture.detectChanges();
    const items = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.info-item'));
    const probability = items.find((i) => i.querySelector('.label')?.textContent?.trim() === 'Probability');
    expect(probability?.querySelector('.value')?.textContent?.trim()).toBe('0%');
  });

  it('still offers Edit on a closed opportunity: notes and dates stay editable (H-CRM-040, ADR-032 D-03)', () => {
    for (const stage of ['WON', 'LOST'] as const) {
      selectedDeal.set(deal({ id: 6, name: 'Closed', stage }));
      fixture.detectChanges();
      const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.header-actions button'))
        .map((b) => b.textContent?.replace(/\s+/g, ' ').trim() ?? '');
      expect(buttons.some((t) => t.endsWith('Edit'))).toBe(true);
    }
  });

  it('onWin explains OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE and keeps the open stage', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    markWon.mockReturnValueOnce(throwError(() => ({
      errorCode: 'OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE',
    })));

    component.onWin();
    fixture.detectChanges();

    const banner = (fixture.nativeElement as HTMLElement).querySelector('.error-banner');
    expect(banner?.getAttribute('role')).toBe('alert');
    expect(banner?.textContent).toContain('Accept the quote');
    expect(banner?.textContent).toContain('OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE');
    expect(applyStageChange).not.toHaveBeenCalled();
    expect(selectedDeal()?.stage).toBe('PROPOSAL');
  });

  describe('StagePathComponent wiring (DoD P5.1)', () => {
    it('renders app-stage-path with completed, active, and upcoming states for an open opportunity', () => {
      const pathDe = fixture.debugElement.query(By.directive(StagePathComponent));
      expect(pathDe).toBeTruthy();

      const stages = component.stagePathItems();
      expect(stages.map((s) => [s.id, s.state])).toEqual([
        ['PROSPECTING', 'completed'],
        ['QUALIFICATION', 'completed'],
        ['NEEDS_ANALYSIS', 'completed'],
        ['PROPOSAL', 'active'],
        ['NEGOTIATION', 'upcoming'],
        ['WON', 'upcoming'],
        ['LOST', 'upcoming'],
      ]);

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('[data-testid="stage-path"]')).toBeTruthy();
      expect(el.querySelector('[data-stage-id="PROPOSAL"]')?.getAttribute('aria-current')).toBe('step');
      expect((pathDe.componentInstance as StagePathComponent).interactive()).toBe(false);
      expect(el.querySelectorAll('[data-testid="stage-path"] button').length).toBe(0);
    });

    it('marks open stages completed and WON active when opportunity is WON', () => {
      selectedDeal.set({ ...openDeal, stage: 'WON' });
      fixture.detectChanges();

      const stages = component.stagePathItems();
      expect(stages.find((s) => s.id === 'PROSPECTING')?.state).toBe('completed');
      expect(stages.find((s) => s.id === 'NEGOTIATION')?.state).toBe('completed');
      expect(stages.find((s) => s.id === 'WON')?.state).toBe('active');
      expect(stages.find((s) => s.id === 'LOST')?.state).toBe('upcoming');
    });

    it('marks the persisted LOST stage as current and keeps the danger style on the read-only path', () => {
      selectedDeal.set({ ...openDeal, stage: 'LOST' });
      fixture.detectChanges();

      const stages = component.stagePathItems();
      expect(stages.find((s) => s.id === 'LOST')?.state).toBe('danger');
      expect(stages.find((s) => s.id === 'WON')?.state).toBe('upcoming');

      const el = fixture.nativeElement as HTMLElement;
      const path = el.querySelector('[data-testid="stage-path"]');
      const lost = path?.querySelector('[data-stage-id="LOST"]');
      expect(lost?.classList.contains('stage-path__step--danger')).toBe(true);
      expect(path?.querySelector('[aria-current="step"]')).toBe(lost);
      expect(path?.querySelector('button, [tabindex]')).toBeNull();
    });
  });
});
