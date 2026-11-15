import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { AssetDto } from '@frontend/shared-models';
import { AssetListComponent } from './asset-list.component';
import { ContractService } from '../contract.service';
import { MOCK_ASSETS } from '../contracts.mock';

describe('AssetListComponent', () => {
  let fixture: ComponentFixture<AssetListComponent>;
  let assets: ReturnType<typeof vi.fn>;

  async function render(answer: Observable<AssetDto[]> = of(MOCK_ASSETS)) {
    assets = vi.fn().mockReturnValue(answer);
    await TestBed.configureTestingModule({
      imports: [AssetListComponent],
      providers: [provideRouter([]), provideNoopAnimations(), { provide: ContractService, useValue: { assets } }],
    }).compileComponents();
    fixture = TestBed.createComponent(AssetListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const text = () => el().textContent?.replace(/\s+/g, ' ') ?? '';

  it('loads the installed base', async () => {
    await render();
    expect(assets).toHaveBeenCalledWith();
  });

  it('lists assets by product, account, quantity, status label and term', async () => {
    await render();
    const row = el().querySelector('tbody tr')?.textContent?.replace(/\s+/g, ' ') ?? '';
    expect(row).toContain('ANALYTICS-PRO');
    expect(row).toContain('ACME-001');
    expect(row).toContain('25');
    expect(row).toContain('Active');
    expect(row).not.toContain('ACTIVE');
    expect(row).toContain('2026-12-01');
  });

  it('links an asset to its contract', async () => {
    await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const link = el().querySelector('button[aria-label="Open contract 7"]') as HTMLButtonElement;
    link.click();
    expect(navigate).toHaveBeenCalledWith(['/contracts', 7]);
  });

  it('explains the access requirement when the API refuses the read', async () => {
    await render(throwError(() => new HttpErrorResponse({ status: 403 })));
    const notice = el().querySelector('[role="status"]');
    expect(notice?.textContent).toContain('Viewing assets requires the InstalledBase.Read role.');
    expect(notice?.textContent).toContain('Ask an administrator to assign it.');
    expect(el().querySelector('table')).toBeNull();
    expect(el().querySelector('[role="alert"]')).toBeNull();
  });

  it('says when there are no assets', async () => {
    await render(of([]));
    expect(text()).toContain('No assets yet');
    expect(text()).toContain('Assets are created when a contract is activated.');
  });

  it('shows any other failure as an error', async () => {
    await render(throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Service Unavailable' })));
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Assets could not be loaded.');
  });
});
