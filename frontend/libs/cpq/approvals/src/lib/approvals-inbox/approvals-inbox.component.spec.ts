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
  const error = signal<string | null>(null);

  beforeEach(async () => {
    pending.set([...MOCK_PENDING_APPROVALS]);
    loading.set(false);
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
      isEmpty: computed(() => pending().length === 0 && !loading()),
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

  it('reject prompts then confirms and calls ApprovalService.reject', () => {
    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue('Too steep');
    component.onReject(MOCK_PENDING_APPROVALS[1]);
    expect(promptSpy).toHaveBeenCalled();
    expect(dialogOpen).toHaveBeenCalled();
    expect(rejectSpy).toHaveBeenCalledWith(11, { comments: 'Too steep' });
    promptSpy.mockRestore();
  });

  it('cancels reject when prompt is dismissed', () => {
    const promptSpy = vi.spyOn(window, 'prompt').mockReturnValue(null);
    component.onReject(MOCK_PENDING_APPROVALS[0]);
    expect(dialogOpen).not.toHaveBeenCalled();
    expect(rejectSpy).not.toHaveBeenCalled();
    promptSpy.mockRestore();
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
});
