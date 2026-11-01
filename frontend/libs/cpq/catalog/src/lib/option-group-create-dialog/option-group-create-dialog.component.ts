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
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import {
  OptionGroupCreateRequest,
  ProductOptionGroupDto,
  SELECTION_TYPES,
  SelectionType,
} from '@frontend/shared-models';
import { ProductOptionService } from '../product-option.service';

export interface OptionGroupCreateDialogData {
  productId: number;
}

function httpErrorMessage(err: unknown, fallback: string): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || fallback;
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
}

@Component({
  selector: 'cpq-option-group-create-dialog',
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
    <h2 mat-dialog-title>New option group</h2>
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
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Selection type</mat-label>
          <mat-select formControlName="selectionType" aria-label="Selection type">
            @for (t of selectionTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
          @if (form.get('selectionType')?.hasError('required')) {
            <mat-error>Selection type is required</mat-error>
          }
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Min selections</mat-label>
            <input matInput type="number" formControlName="minSelections" min="0" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="half">
            <mat-label>Max selections</mat-label>
            <input matInput type="number" formControlName="maxSelections" min="0" />
          </mat-form-field>
        </div>
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
          Create
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .create-form {
        display: flex;
        flex-direction: column;
        gap: 4px;
        min-width: min(420px, 90vw);
        padding-top: 8px;
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
export class OptionGroupCreateDialogComponent {
  private readonly dialogRef = inject(
    MatDialogRef<OptionGroupCreateDialogComponent, ProductOptionGroupDto | null>,
  );
  private readonly data = inject<OptionGroupCreateDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  private readonly optionService = inject(ProductOptionService);

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly selectionTypes: SelectionType[] = [...SELECTION_TYPES];

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    selectionType: this.fb.nonNullable.control<SelectionType | ''>('', {
      validators: [Validators.required],
    }),
    minSelections: this.fb.nonNullable.control(0),
    maxSelections: this.fb.control<number | null>(null),
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
    const request: OptionGroupCreateRequest = {
      productId: this.data.productId,
      name: value.name.trim(),
      selectionType: value.selectionType as SelectionType,
      minSelections: Number(value.minSelections) || 0,
      // Backend rejects omitted sortOrder under Jackson creator-binding; always send 0.
      sortOrder: 0,
    };
    if (value.maxSelections != null && !Number.isNaN(Number(value.maxSelections))) {
      request.maxSelections = Number(value.maxSelections);
    }

    this.error.set(null);
    this.saving.set(true);
    this.optionService.createGroup(request).subscribe({
      next: (created) => {
        this.saving.set(false);
        this.dialogRef.close(created);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(httpErrorMessage(err, 'Failed to create option group'));
      },
    });
  }
}
