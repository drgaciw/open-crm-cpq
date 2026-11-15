import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { FormValidators } from '@frontend/shared-utils';

/**
 * Asks why a product is retired (POST /products/{id}/retire requires a reason).
 * Closes with the trimmed reason, or undefined on cancel. Retiring cannot be undone.
 */
@Component({
  selector: 'lib-retire-product-dialog',
  standalone: true,
  imports: [MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Retire product</h2>
    <mat-dialog-content>
      <p class="hint">A retired product cannot be sold or reactivated.</p>
      <form [formGroup]="form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Reason</mat-label>
          <textarea matInput formControlName="reason" rows="3" placeholder="Why is this product retired?"></textarea>
          @if (form.get('reason')?.hasError('required')) {
            <mat-error>Reason is required</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="warn" [disabled]="form.invalid" (click)="onSubmit()">Retire</button>
    </mat-dialog-actions>
  `,
  styles: `
    .full-width { width: 100%; }
    .hint { margin: 0 0 12px; }
    mat-dialog-content { min-width: 400px; padding-top: 16px; }
  `,
})
export class RetireProductDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(MatDialogRef<RetireProductDialogComponent>);

  form: FormGroup = this.fb.group({
    reason: ['', FormValidators.requiredTrimmed],
  });

  onSubmit(): void {
    if (this.form.valid) {
      this.dialogRef.close(String(this.form.value.reason).trim());
    }
  }
}
