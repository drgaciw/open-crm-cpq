import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import {
  RuleSetCreateRequest,
  RuleSetDto,
  RulesEngineType,
} from '@frontend/shared-models';
import { RuleSetService } from '@frontend/cpq-pricing-data-access';

const ENGINE_TYPES: RulesEngineType[] = ['DROOLS', 'OPENL'];

/**
 * New rule set dialog matching RuleSetCreateRequest.
 * Required: name. Optional: description, engineType, effectiveDate, expirationDate.
 */
@Component({
  selector: 'cpq-rule-set-create-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>New rule set</h2>
    <mat-dialog-content>
      @if (error()) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }
      <form [formGroup]="form" class="create-form" (ngSubmit)="onSubmit()">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Name</mat-label>
          <input
            matInput
            formControlName="name"
            maxlength="255"
            autocomplete="off"
          />
          @if (form.get('name')?.hasError('required')) {
            <mat-error>Name is required</mat-error>
          }
          @if (form.get('name')?.hasError('maxlength')) {
            <mat-error>Name must be at most 255 characters</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <textarea
            matInput
            formControlName="description"
            rows="3"
            maxlength="2000"
          ></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Engine type</mat-label>
          <mat-select formControlName="engineType">
            @for (eng of engineTypes; track eng) {
              <mat-option [value]="eng">{{ eng }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Effective date</mat-label>
            <input
              matInput
              type="date"
              formControlName="effectiveDate"
            />
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Expiration date</mat-label>
            <input
              matInput
              type="date"
              formControlName="expirationDate"
            />
          </mat-form-field>
        </div>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="saving()" mat-dialog-close>
        Cancel
      </button>
      <button
        mat-raised-button
        color="primary"
        type="button"
        [disabled]="form.invalid || saving()"
        (click)="onSubmit()"
      >
        @if (saving()) {
          <mat-spinner diameter="20" />
        } @else {
          Create
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      min-width: min(520px, 90vw);
      padding-top: 8px;
    }

    .create-form {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .full-width {
      width: 100%;
    }

    .row {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }

    .half {
      flex: 1 1 180px;
    }

    .error-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
      padding: 10px 12px;
      border-radius: 8px;
      background: #ffebee;
      color: #c62828;
      font-size: 0.875rem;
    }
  `,
})
export class RuleSetCreateDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly ruleSetService = inject(RuleSetService);
  private readonly dialogRef = inject(
    MatDialogRef<RuleSetCreateDialogComponent, RuleSetDto>,
  );

  readonly engineTypes = ENGINE_TYPES;

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    engineType: ['DROOLS' as RulesEngineType],
    effectiveDate: [''],
    expirationDate: [''],
  });

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: RuleSetCreateRequest = {
      name: value.name.trim(),
    };

    const description = value.description?.trim();
    if (description) {
      request.description = description;
    }
    if (value.engineType) {
      request.engineType = value.engineType;
    }
    const effectiveDate = value.effectiveDate?.trim();
    if (effectiveDate) {
      request.effectiveDate = effectiveDate;
    }
    const expirationDate = value.expirationDate?.trim();
    if (expirationDate) {
      request.expirationDate = expirationDate;
    }

    this.error.set(null);
    this.saving.set(true);
    this.ruleSetService.create(request).subscribe({
      next: (ruleSet) => {
        this.saving.set(false);
        this.dialogRef.close(ruleSet);
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.saving.set(false);
        this.error.set(
          err?.error?.message || err?.message || 'Failed to create rule set',
        );
      },
    });
  }
}
