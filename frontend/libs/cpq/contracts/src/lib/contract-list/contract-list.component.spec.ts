import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { SpringPageResponse, SubscriptionDto } from '@frontend/shared-models';
import { Subject, of, throwError } from 'rxjs';
import { ContractListComponent } from './contract-list.component';
import { ContractService } from '../contract.service';
import { MOCK_CONTRACTS } from '../contracts.mock';

describe('ContractListComponent', () => {
  let fixture: ComponentFixture<ContractListComponent>;
  let list: ReturnType<typeof vi.fn>;

  const page = (content = MOCK_CONTRACTS) =>
    of({ content, number: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 });

  async function render(response = page()) {
    list = vi.fn().mockReturnValue(response);
    await TestBed.configureTestingModule({
      imports: [ContractListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ContractService, useValue: { list } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ContractListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';
  const rows = () => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr'));

  it('loads page 0 of contracts on init', async () => {
    await render();
    expect(list).toHaveBeenCalledWith(0, 20, undefined);
  });

  it('keeps the latest sort results and pagination when the earlier sort finishes last', async () => {
    await render();
    const earlier = new Subject<SpringPageResponse<SubscriptionDto>>();
    list.mockReturnValueOnce(earlier);
    list.mockReturnValueOnce(page([MOCK_CONTRACTS[1]]));
    fixture.componentInstance.onSort({ active: 'subscriptionNumber', direction: 'asc' });
    fixture.componentInstance.onSort({ active: 'subscriptionNumber', direction: 'desc' });
    expect(list).toHaveBeenLastCalledWith(0, 20, 'subscriptionNumber,desc');

    earlier.next({ content: MOCK_CONTRACTS, number: 0, size: 20, totalElements: 2, totalPages: 1 });
    fixture.detectChanges();

    expect(rows()).toHaveLength(1);
    expect(rows()[0].textContent).toContain(MOCK_CONTRACTS[1].subscriptionNumber);
    expect(fixture.componentInstance.pagination().totalElements).toBe(1);
    expect(fixture.componentInstance.loading()).toBe(false);
  });

  it('keeps the current rows visible when an earlier sort fails', async () => {
    await render();
    const earlier = new Subject<never>();
    list.mockReturnValueOnce(earlier);
    list.mockReturnValueOnce(page([MOCK_CONTRACTS[1]]));
    fixture.componentInstance.onSort({ active: 'subscriptionNumber', direction: 'asc' });
    fixture.componentInstance.onSort({ active: 'subscriptionNumber', direction: 'desc' });

    earlier.error({ message: 'Earlier sort failed' });
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toBeNull();
    expect(rows()).toHaveLength(1);
  });

  it('shows each contract by number, customer, plan, status label, term and ARR', async () => {
    await render();
    expect(rows().length).toBe(2);
    const first = rows()[0].textContent?.replace(/\s+/g, ' ') ?? '';
    expect(first).toContain('SUB-2026-0007');
    expect(first).toContain('ACME-001');
    expect(first).toContain('Analytics Suite');
    expect(first).toContain('Pending');
    expect(first).not.toContain('PENDING');
    expect(first).toContain('2026-12-01');
    expect(first).toContain('2027-11-30');
    expect(first).toContain('12,000');
  });

  it('opens a contract when its row is clicked', async () => {
    await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (rows()[1] as HTMLElement).click();
    expect(navigate).toHaveBeenCalledWith(['/contracts', 8]);
  });

  it('says how contracts come into being when there are none', async () => {
    await render(page([]));
    expect(text()).toContain('No contracts yet');
    expect(text()).toContain('A contract is created when a customer accepts a quote.');
  });

  it('shows the API error and offers a retry', async () => {
    await render(throwError(() => ({ message: 'Service unavailable' })));
    const alert = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Service unavailable');
    list.mockReturnValue(page());
    (alert?.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(list).toHaveBeenCalledTimes(2);
    expect(rows().length).toBe(2);
  });

  it('is read-only: it offers no create, edit or lifecycle action', async () => {
    await render();
    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .map((b) => b.textContent?.trim() ?? '');
    expect(buttons.filter((b) => /new|create|edit|activate|suspend|cancel|delete/i.test(b))).toEqual([]);
  });
});
