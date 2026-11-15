import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ApprovalRejectDialogComponent } from './approval-reject-dialog.component';

describe('ApprovalRejectDialogComponent', () => {
  const close = vi.fn();

  beforeEach(async () => {
    close.mockReset();
    await TestBed.configureTestingModule({
      imports: [ApprovalRejectDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { approvalId: 11, quoteId: 7 } },
      ],
    }).compileComponents();
  });

  it('names the approval and quote, and labels the reason as optional', () => {
    const fixture = TestBed.createComponent(ApprovalRejectDialogComponent);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Reject approval #11 for quote 7?');
    expect(text).toContain('Reason (optional)');
  });

  it('closes with the trimmed reason on Reject', () => {
    const fixture = TestBed.createComponent(ApprovalRejectDialogComponent);
    fixture.componentInstance.form.controls.comments.setValue('  Too steep  ');
    fixture.componentInstance.onSubmit();
    expect(close).toHaveBeenCalledWith({ comments: 'Too steep' });
  });
});
