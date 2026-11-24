import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { of, Subject } from 'rxjs';
import { LeadDetailComponent } from './lead-detail.component';
import { LeadService } from '../lead.service';
import { LeadDto } from '@frontend/shared-models';

/** H-CRM-020: the list badge says "New" but the detail chip printed the raw enum "NEW". */
describe('LeadDetailComponent status', () => {
  const selectedLead = signal<LeadDto | null>(null);

  async function render(
    status: LeadDto['status'],
    serviceOverrides: Partial<LeadService> = {},
    lossReason?: string,
  ): Promise<ComponentFixture<LeadDetailComponent>> {
    await TestBed.configureTestingModule({
      imports: [LeadDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: LeadService,
          useValue: {
            selectedLead,
            loading: signal(false),
            error: signal<string | null>(null),
            getById: vi.fn(),
            ...serviceOverrides,
          },
        },
        { provide: MatDialog, useValue: { open: vi.fn(() => ({ afterClosed: () => of(lossReason) })) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(LeadDetailComponent);
    fixture.componentRef.setInput('id', '7');
    selectedLead.set({ id: 7, title: 'Fresh lead', status } as LeadDto);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => vi.restoreAllMocks());

  it('names the progressbar while the lead detail loads', async () => {
    const fixture = await render('NEW', { loading: signal(true) });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="progressbar"]')?.getAttribute('aria-label')).toBe('Loading lead details');
    expect(el.querySelector('h1')).toBeNull();
  });

  it.each([
    ['NEW', 'Mark Contacted', 'markContacted', 'Marking lead contacted', 'Mark Lost', 'close'],
    ['CONTACTED', 'Qualify', 'qualify', 'Qualifying lead', 'Mark Lost', 'close'],
    ['QUALIFIED', 'Convert to Deal', 'convertToDeal', 'Converting lead to deal', 'Mark Lost', 'close'],
    ['CONTACTED', 'Mark Lost', 'markLost', 'Marking lead lost', 'Qualify', 'check_circle'],
  ] as const)('scopes pending progress on a %s lead to %s', async (
    status, action, serviceAction, progressName, otherAction, otherIcon,
  ) => {
    if (serviceAction === 'convertToDeal') vi.spyOn(window, 'confirm').mockReturnValue(true);
    const pending = new Subject<LeadDto>();
    const send = vi.fn(() => pending);
    const fixture = await render(status, { [serviceAction]: send }, 'Owned reason');
    const el = fixture.nativeElement as HTMLElement;
    const button = (name: string) => Array.from(el.querySelectorAll<HTMLButtonElement>('.action-bar button'))
      .find((control) => control.textContent?.trim().endsWith(name))!;

    button(action).click();
    fixture.detectChanges();

    expect(send).toHaveBeenCalledOnce();
    expect(button(action).disabled).toBe(true);
    expect(button(otherAction).disabled).toBe(true);
    expect(button(otherAction).querySelector('[role="progressbar"]')).toBeNull();
    expect(button(otherAction).querySelector('mat-icon')?.textContent?.trim()).toBe(otherIcon);
    expect(button(action).getAttribute('aria-label')).toBe(action);
    expect(button(action).querySelector('[role="progressbar"]')?.getAttribute('aria-label')).toBe(progressName);

    pending.error(new Error('Synthetic test rejection'));
    fixture.detectChanges();
    expect(button(action).disabled).toBe(false);
    expect(button(otherAction).disabled).toBe(false);
    expect(el.querySelector('.action-bar [role="progressbar"]')).toBeNull();
  });

  it('shows the status in words, like the list badge', async () => {
    const el = (await render('NEW')).nativeElement as HTMLElement;
    const badge = el.querySelector('app-status-badge');
    expect(badge?.textContent?.trim()).toBe('New');
    expect(el.textContent).not.toMatch(/\bNEW\b/);
  });
});
