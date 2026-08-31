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
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import {
  ADJUSTMENT_TYPES,
  AdjustmentType,
  OptionCreateRequest,
  ProductOptionGroupDto,
} from '@frontend/shared-models';
import { ProductOptionService } from '../product-option.service';

export interface OptionCreateDialogData {
  groupId: number;
  groupName: string;
  productId: number;
}

function httpErrorMessage(err: unknown, fallback: string): string {
  const e = err as { error?: { message?: string }; message?: string } | null;
  return e?.error?.message || e?.message || fallback;
}

@Component({
  selector: 'cpq-option-create-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Add option</h2>
    <mat-dialog-content>
      <p class="subtitle">Group: {{ data.groupName }}</p>
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
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Price adjustment</mat-label>
            <input
              matInput
              type="number"
              formControlName="priceAdjustment"
              step="0.01"
            />
          </mat-form-field>
          <mat-form-field appearance="outline" class="half">
            <mat-label>Adjustment type</mat-label>
            <mat-select
              formControlName="adjustmentType"
              aria-label="Adjustment type"
            >
              <mat-option [value]="null">—</mat-option>
              @for (t of adjustmentTypes; track t) {
                <mat-option [value]="t">{{ t }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>

        <mat-checkbox formControlName="defaultOption">Default option</mat-checkbox>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [disabled]="saving()" (click)="onCancel()">
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
          Add
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .subtitle {
        margin: 0 0 8px;
        color: #616161;
        font-size: 0.875rem;
      }
      .create-form {
        display: flex;
        flex-direction: column;
        gap: 8px;
        min-width: min(420px, 90vw);
        padding-top: 4px;
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
        flex: 1 1 140px;
      }
      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 12px;
        padding: 8px 10px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
        font-size: 0.875rem;
      }
    `,
  ],
})
export class OptionCreateDialogComponent {
  private readonly dialogRef = inject(
    MatDialogRef<OptionCreateDialogComponent, ProductOptionGroupDto | null>,
  );
  readonly data = inject<OptionCreateDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  private readonly optionService = inject(ProductOptionService);

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly adjustmentTypes: AdjustmentType[] = [...ADJUSTMENT_TYPES];

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    priceAdjustment: this.fb.control<number | null>(null),
    adjustmentType: this.fb.control<AdjustmentType | null>(null),
    defaultOption: this.fb.nonNullable.control(false),
  });

  onCancel(): void {
    this.dialogRef.close(null);
  }

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: OptionCreateRequest = {
      productId: this.data.productId,
      name: value.name.trim(),
      defaultOption: value.defaultOption,
    };
    if (
      value.priceAdjustment != null &&
      !Number.isNaN(Number(value.priceAdjustment))
    ) {
      request.priceAdjustment = Number(value.priceAdjustment);
    }
    if (value.adjustmentType) {
      request.adjustmentType = value.adjustmentType;
    }

    this.error.set(null);
    this.saving.set(true);
    this.optionService.addOption(this.data.groupId, request).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.dialogRef.close(updated);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(httpErrorMessage(err, 'Failed to add option'));
      },
    });
  }
}
