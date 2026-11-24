import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { AssetDto, SubscriptionDto } from '@frontend/shared-models';
import { ContractDetailComponent } from './contract-detail.component';
import { ContractService } from '../contract.service';
import { MOCK_ASSETS, MOCK_CONTRACTS } from '../contracts.mock';

describe('ContractDetailComponent', () => {
  let fixture: ComponentFixture<ContractDetailComponent>;
  let get: ReturnType<typeof vi.fn>;
  let assets: ReturnType<typeof vi.fn>;

  async function render(
    contract: Observable<SubscriptionDto> = of(MOCK_CONTRACTS[0]),
    assetAnswer: Observable<AssetDto[]> = of(MOCK_ASSETS),
  ) {
    get = vi.fn().mockReturnValue(contract);
    assets = vi.fn().mockReturnValue(assetAnswer);
    await TestBed.configureTestingModule({
      imports: [ContractDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ContractService, useValue: { get, assets } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '7' }) } } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ContractDetailComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent?.replace(/\s+/g, ' ') ?? '';
  const field = (label: string) =>
    Array.from(el().querySelectorAll('dt')).find((dt) => dt.textContent?.trim() === label)
      ?.nextElementSibling?.textContent?.replace(/\s+/g, ' ').trim();

  it('loads the contract named in the route and its assets', async () => {
    await render();
    expect(get).toHaveBeenCalledWith(7);
    expect(assets).toHaveBeenCalledWith({ subscriptionId: 7 });
  });

  it('heads the page with the contract number and shows its terms by label', async () => {
    await render();
    expect(el().querySelector('h1')?.textContent).toContain('SUB-2026-0007');
    expect(field('Status')).toBe('Pending');
    expect(field('Customer')).toBe('ACME-001');
    expect(field('Plan')).toBe('Analytics Suite');
    expect(field('Source quote')).toBe('Q-20261115-0074');
    expect(field('Start date')).toBe('2026-12-01');
    expect(field('End date')).toBe('2027-11-30');
    expect(field('Renewal date')).toBe('2027-11-30');
    expect(field('MRR')).toContain('1,000');
    expect(field('ARR')).toContain('12,000');
    expect(field('ERP sync')).toBe('Pending');
  });

  it('shows "—" for terms the contract does not have', async () => {
    await render(of(MOCK_CONTRACTS[1]), of([]));
    expect(field('Source quote')).toBe('—');
    expect(field('ARR')).toBe('—');
  });

  it("lists the contract's assets", async () => {
    await render();
    const table = el().querySelector('table[aria-label="Assets on this contract"]');
    expect(table?.textContent).toContain('ANALYTICS-PRO');
    expect(table?.textContent).toContain('25');
    expect(table?.textContent).toContain('Active');
  });

  it('places the populated asset table in a named, keyboard-focusable scroll area', async () => {
    await render();
    const table = el().querySelector('table[aria-label="Assets on this contract"]');
    expect(table?.closest('[role="region"][aria-label="Contract asset columns"][tabindex="0"]')).toBeTruthy();
  });

  it('explains a refused asset read instead of showing an error or an empty list', async () => {
    await render(of(MOCK_CONTRACTS[0]), throwError(() => new HttpErrorResponse({ status: 403 })));
    expect(text()).toContain('Viewing assets requires the InstalledBase.Read role.');
    expect(el().querySelector('table[aria-label="Assets on this contract"]')).toBeNull();
  });

  it('says when the contract has no assets yet', async () => {
    await render(of(MOCK_CONTRACTS[0]), of([]));
    expect(text()).toContain('No assets yet. Assets are created when the contract is activated.');
  });

  it('shows a not-found message for an unknown contract', async () => {
    await render(throwError(() => new HttpErrorResponse({ status: 404 })), of([]));
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Contract not found');
  });

  it('goes back to the contract list', async () => {
    await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const back = Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Back to contracts')) as HTMLButtonElement;
    back.click();
    expect(navigate).toHaveBeenCalledWith(['/contracts']);
  });

  it('is read-only: it offers no lifecycle action', async () => {
    await render();
    const buttons = Array.from(el().querySelectorAll('button')).map((b) => b.textContent?.trim() ?? '');
    expect(buttons.filter((b) => /activate|suspend|cancel|renew|edit|delete/i.test(b))).toEqual([]);
  });
});
