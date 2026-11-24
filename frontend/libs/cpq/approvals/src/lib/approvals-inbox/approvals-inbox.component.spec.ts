import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { computed, signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ApprovalsInboxComponent } from './approvals-inbox.component';
import { ApprovalService } from '../approval.service';
import { MOCK_PENDING_APPROVALS } from '../approvals.mock';
import { ApprovalRequestDto } from '@frontend/shared-models';

describe('ApprovalsInboxComponent', () => {
  let fixture: ComponentFixture<ApprovalsInboxComponent>;
  let component: ApprovalsInboxComponent;
  let router: Router;
  let listPendingSpy: ReturnType<typeof vi.fn>;
  let approveSpy: ReturnType<typeof vi.fn>;
  let rejectSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  const pending = signal<ApprovalRequestDto[]>([...MOCK_PENDING_APPROVALS]);
  const loading = signal(false);
  const listReadConfirmed = signal(true);
  const error = signal<string | null>(null);

  beforeEach(async () => {
    pending.set([...MOCK_PENDING_APPROVALS]);
    loading.set(false);
    listReadConfirmed.set(true);
    error.set(null);

    listPendingSpy = vi.fn(() => {
      pending.set([...MOCK_PENDING_APPROVALS]);
    });
    approveSpy = vi.fn((id: number) => {
      pending.update((rows) => rows.filter((r) => r.id !== id));
      return of(
        MOCK_PENDING_APPROVALS.find((a) => a.id === id) ??
          MOCK_PENDING_APPROVALS[0],
      );
    });
    rejectSpy = vi.fn((id: number) => {
      pending.update((rows) => rows.filter((r) => r.id !== id));
      return of(
        MOCK_PENDING_APPROVALS.find((a) => a.id === id) ??
          MOCK_PENDING_APPROVALS[0],
      );
    });
    dialogOpen = vi.fn(() => ({
      afterClosed: () => of(true),
    }));

    const serviceMock = {
      pending,
      loading,
      error,
      isEmpty: computed(() => listReadConfirmed() && pending().length === 0 && !loading()),
      listPending: listPendingSpy,
      approve: approveSpy,
      reject: rejectSpy,
    };

    await TestBed.configureTestingModule({
      imports: [ApprovalsInboxComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ApprovalService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ApprovalsInboxComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('loads pending on init via ApprovalService.listPending', () => {
    expect(listPendingSpy).toHaveBeenCalled();
    expect(component.pending()).toHaveLength(2);
  });

  it('names the initial approvals loading indicator', () => {
    pending.set([]);
    listReadConfirmed.set(false);
    loading.set(true);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="progressbar"]')?.getAttribute('aria-label')).toBe('Loading approvals');
    expect(host.querySelector('app-empty-state')).toBeNull();
  });

  it('renders quote links and action buttons', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Quote 7');
    expect(el.textContent).toContain('Deal Desk');
    expect(el.querySelectorAll('button').length).toBeGreaterThan(0);
  });

  it('approve confirms then calls ApprovalService.approve', () => {
    component.onApprove(MOCK_PENDING_APPROVALS[0]);
    expect(dialogOpen).toHaveBeenCalled();
    expect(approveSpy).toHaveBeenCalledWith(10);
  });

  // H-CPQ-Q-005 (UAT 2026-11-14): reject used window.prompt for the reason, then a second confirm
  // dialog. One accessible dialog now collects the optional reason and confirms.
  const rejectWith = (result: unknown) => {
    dialogOpen.mockReturnValue({ afterClosed: () => of(result) });
  };

  it('reject opens one dialog, never window.prompt, and sends the reason it returns', () => {
    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue('from prompt');
    rejectWith({ comments: 'Too steep' });
    component.onReject(MOCK_PENDING_APPROVALS[1]);
    expect(promptSpy).not.toHaveBeenCalled();
    expect(dialogOpen).toHaveBeenCalledTimes(1);
    expect(dialogOpen.mock.calls[0][0].name).toBe('ApprovalRejectDialogComponent');
    expect(dialogOpen.mock.calls[0][1].data).toEqual(
      expect.objectContaining({ approvalId: 11, quoteId: MOCK_PENDING_APPROVALS[1].quoteId }),
    );
    expect(rejectSpy).toHaveBeenCalledWith(11, { comments: 'Too steep' });
    promptSpy.mockRestore();
  });

  it('sends no comments when the reason is blank', () => {
    rejectWith({ comments: '   ' });
    component.onReject(MOCK_PENDING_APPROVALS[1]);
    expect(rejectSpy).toHaveBeenCalledWith(11, {});
  });

  it('cancels reject when the dialog is dismissed', () => {
    rejectWith(undefined);
    component.onReject(MOCK_PENDING_APPROVALS[0]);
    expect(rejectSpy).not.toHaveBeenCalled();
  });

  // Found by the Jev + Playwright MCP UAT (2026-11-15): every row's buttons were named "Approve",
  // "Reject" and "Open quote", so neither a screen reader nor the agent could tell which quote a
  // button acts on (the agent opened the quote link instead). Names start with the visible text.
  it('names each row action after its approval and quote', () => {
    const names = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('td button'))
      .map((b) => b.getAttribute('aria-label'));
    const [first, second] = MOCK_PENDING_APPROVALS;
    expect(names).toEqual([
      `Approve approval #${first.id} for quote ${first.quoteId}`,
      `Reject approval #${first.id} for quote ${first.quoteId}`,
      `Open quote ${first.quoteId}`,
      `Approve approval #${second.id} for quote ${second.quoteId}`,
      `Reject approval #${second.id} for quote ${second.quoteId}`,
      `Open quote ${second.quoteId}`,
    ]);
  });

  // Approve and Reject open a dialog before anything is decided; screen readers announce that only
  // when the button declares it (WAI-ARIA aria-haspopup="dialog"). Open quote navigates instead.
  it('declares which row actions open a dialog', () => {
    const popups = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('td button'))
      .map((b) => [b.textContent?.trim(), b.getAttribute('aria-haspopup')]);
    expect(popups.slice(0, 3)).toEqual([
      [expect.stringContaining('Approve'), 'dialog'],
      [expect.stringContaining('Reject'), 'dialog'],
      [expect.stringContaining('Open quote'), null],
    ]);
  });

  it('openQuote navigates to quote detail', async () => {
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.openQuote(7);
    expect(navigateSpy).toHaveBeenCalledWith(['/quotes', 7]);
  });

  it('surfaces action HTTP errors', () => {
    approveSpy.mockReturnValueOnce(
      throwError(() => ({ error: { message: 'Already decided' } })),
    );
    component.onApprove(MOCK_PENDING_APPROVALS[0]);
    expect(component.actionError()).toBe('Already decided');
  });

  it('appends errorCode to action HTTP error banners', () => {
    approveSpy.mockReturnValueOnce(
      throwError(() => ({
        error: {
          message: 'Already decided',
          errorCode: 'APPROVAL_ALREADY_DECIDED',
        },
      })),
    );
    component.onApprove(MOCK_PENDING_APPROVALS[0]);
    expect(component.actionError()).toBe(
      'Already decided [APPROVAL_ALREADY_DECIDED]',
    );
  });

  it('shows empty state when no pending rows', () => {
    pending.set([]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No pending approvals');
  });

  it('keeps unresolved results hidden after dismissing a failed read', () => {
    pending.set([]);
    listReadConfirmed.set(false);
    error.set('Pending approvals could not be loaded');
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const dismiss: HTMLButtonElement = el.querySelector('[role="alert"] button')!;
    dismiss.click();
    fixture.detectChanges();

    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelector('app-empty-state')).toBeNull();
    expect(el.querySelector('table')).toBeNull();
    expect(listPendingSpy).toHaveBeenCalledTimes(1);
  });
});
