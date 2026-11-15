import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { LeadDetailComponent } from './lead-detail.component';
import { LeadService } from '../lead.service';
import { LeadDto } from '@frontend/shared-models';

/** H-CRM-020: the list badge says "New" but the detail chip printed the raw enum "NEW". */
describe('LeadDetailComponent status', () => {
  const selectedLead = signal<LeadDto | null>(null);

  async function render(status: LeadDto['status']): Promise<HTMLElement> {
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
          },
        },
        { provide: MatDialog, useValue: { open: vi.fn() } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(LeadDetailComponent);
    fixture.componentRef.setInput('id', '7');
    selectedLead.set({ id: 7, title: 'Fresh lead', status } as LeadDto);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the status in words, like the list badge', async () => {
    const el = await render('NEW');
    const badge = el.querySelector('app-status-badge');
    expect(badge?.textContent?.trim()).toBe('New');
    expect(el.textContent).not.toMatch(/\bNEW\b/);
  });
});
