import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';

/** Result of a confirmed reject; undefined when the dialog is cancelled. */
export interface QuoteRejectResult {
  reason: string;
}

/**
 * Collects the optional rejection reason (H-CPQ-Q-005: replaces window.prompt, which cannot be
 * styled, labelled for assistive tech or tested, and blocks the page).
 */
@Component({
  selector: 'lib-quote-reject-dialog',
  standalone: true,
  imports: [MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Reject quote</h2>
    <mat-dialog-content>
      <form [formGroup]="form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Rejection reason (optional)</mat-label>
          <textarea matInput formControlName="reason" rows="3"></textarea>
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
    mat-dialog-content { min-width: 400px; padding-top: 16px; }
  `,
})
export class QuoteRejectDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(MatDialogRef<QuoteRejectDialogComponent>);

  form: FormGroup = this.fb.group({ reason: [''] });

  onSubmit(): void {
    this.dialogRef.close({ reason: String(this.form.value.reason ?? '').trim() } satisfies QuoteRejectResult);
  }
}
