import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

@Component({
  selector: 'lib-lose-reason-dialog',
  standalone: true,
  imports: [MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Mark Lead as Lost</h2>
    <mat-dialog-content>
      <form [formGroup]="form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Reason</mat-label>
          <textarea matInput formControlName="reason" rows="4" placeholder="Why was this lead lost?"></textarea>
          @if (form.get('reason')?.hasError('required')) {
            <mat-error>Reason is required</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="warn" [disabled]="form.invalid" (click)="onSubmit()">
        Mark as Lost
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { min-width: 400px; padding-top: 16px; }
  `],
})
export class LoseReasonDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(MatDialogRef<LoseReasonDialogComponent>);

  form: FormGroup = this.fb.group({
    reason: ['', Validators.required],
  });

  onSubmit(): void {
    if (this.form.valid) {
      this.dialogRef.close(this.form.value.reason);
    }
  }
}
