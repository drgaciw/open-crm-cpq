import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
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

  it('shows organization, amount, currency, stage, and owner', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Northwind');
    expect(el.textContent).toContain('EUR');
    expect(el.textContent).toContain('Proposal');
    expect(el.textContent).toContain('ada');
    expect(el.querySelector('[data-testid="deal-organization"] a')?.getAttribute('href')).toBe('/organizations/9');
    expect(el.textContent).not.toContain('Linked quote');
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
});
