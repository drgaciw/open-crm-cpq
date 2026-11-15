import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';

export interface ApprovalRejectDialogData {
  approvalId: number;
  quoteId: number;
}

/** Result of a confirmed reject; undefined when the dialog is cancelled. */
export interface ApprovalRejectResult {
  comments: string;
}

/**
 * Confirms a rejection and collects the optional reason in one dialog (H-CPQ-Q-005: replaces a
 * window.prompt followed by a second confirm dialog).
 */
@Component({
  selector: 'lib-approval-reject-dialog',
  standalone: true,
  imports: [MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Reject request</h2>
    <mat-dialog-content>
      <p>Reject approval #{{ data.approvalId }} for quote {{ data.quoteId }}?</p>
      <form [formGroup]="form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Reason (optional)</mat-label>
          <textarea matInput formControlName="comments" rows="3"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="warn" (click)="onSubmit()">Reject</button>
    </mat-dialog-actions>
  `,
  styles: `
    .full-width { width: 100%; }
    mat-dialog-content { min-width: 400px; }
  `,
})
export class ApprovalRejectDialogComponent {
  readonly data = inject<ApprovalRejectDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(MatDialogRef<ApprovalRejectDialogComponent>);

  readonly form = this.fb.nonNullable.group({ comments: [''] });

  onSubmit(): void {
    this.dialogRef.close({ comments: this.form.controls.comments.value.trim() } satisfies ApprovalRejectResult);
  }
}
