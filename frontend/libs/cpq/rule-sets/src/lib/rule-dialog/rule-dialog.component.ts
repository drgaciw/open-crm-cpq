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
  RULE_STATUS_LABELS,
  RuleCreateRequest,
  RuleDto,
  RuleType,
  RuleUpdateRequest,
} from '@frontend/shared-models';
import { RuleService } from '@frontend/cpq-pricing-data-access';
import { Observable, catchError, map, of, switchMap } from 'rxjs';

const RULE_TYPES: RuleType[] = [
  'PRICING',
  'CONFIGURATION',
  'APPROVAL',
  'RENEWAL',
  'RISK',
];

export interface RuleDialogData {
  ruleSetId: number;
  /** Present for edit mode. */
  rule?: RuleDto;
}

function httpErrorMessage(err: unknown, fallback: string): string {
  const e = err as { error?: { message?: string }; message?: string } | null;
  return e?.error?.message || e?.message || fallback;
}

/**
 * Create/edit pricing DRL rule.
 * Calls RuleService.validateSyntax before save (edit only — needs id) and after save.
 */
@Component({
  selector: 'cpq-rule-dialog',
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
    <h2 mat-dialog-title>{{ isEdit ? 'Edit rule' : 'New rule' }}</h2>
    <mat-dialog-content>
      @if (error()) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }
      @if (validationMessage(); as msg) {
        <div
          class="validation-banner"
          [class.validation-ok]="validationOk()"
          role="status"
        >
          <mat-icon>{{ validationOk() ? 'check_circle' : 'warning' }}</mat-icon>
          <span>{{ msg }}</span>
        </div>
      }
      <form [formGroup]="form" class="rule-form" (ngSubmit)="onSubmit()">
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
          <mat-label>Description</mat-label>
          <textarea
            matInput
            formControlName="description"
            rows="2"
            maxlength="2000"
          ></textarea>
        </mat-form-field>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Rule type</mat-label>
            <mat-select formControlName="ruleType">
              @for (t of ruleTypes; track t) {
                <mat-option [value]="t">{{ t }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Priority (100–1000)</mat-label>
            <input
              matInput
              type="number"
              formControlName="priority"
              min="100"
              max="1000"
              step="1"
            />
            @if (form.get('priority')?.hasError('required')) {
              <mat-error>Priority is required</mat-error>
            }
            @if (
              form.get('priority')?.hasError('min') ||
              form.get('priority')?.hasError('max')
            ) {
              <mat-error>Priority must be between 100 and 1000</mat-error>
            }
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>DRL content</mat-label>
          <textarea
            matInput
            formControlName="content"
            rows="10"
            class="drl-textarea"
            spellcheck="false"
            autocomplete="off"
          ></textarea>
          @if (form.get('content')?.hasError('required')) {
            <mat-error>DRL content is required</mat-error>
          }
        </mat-form-field>

        @if (isEdit && data.rule; as rule) {
          <p class="meta-line">
            Status: {{ statusLabels[rule.status] }} · ID {{ rule.id }}
          </p>
        }
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
          {{ isEdit ? 'Save' : 'Create' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content {
      min-width: min(640px, 92vw);
      padding-top: 8px;
    }

    .rule-form {
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

    .drl-textarea {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas,
        'Liberation Mono', 'Courier New', monospace;
      font-size: 0.8125rem;
      line-height: 1.4;
    }

    .meta-line {
      margin: 4px 0 0;
      font-size: 0.8125rem;
      color: #757575;
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

    .validation-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
      padding: 10px 12px;
      border-radius: 8px;
      background: #fff8e1;
      color: #f57f17;
      font-size: 0.875rem;
    }

    .validation-banner.validation-ok {
      background: #e8f5e9;
      color: #2e7d32;
    }
  `,
})
export class RuleDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly ruleService = inject(RuleService);
  private readonly dialogRef = inject(
    MatDialogRef<RuleDialogComponent, RuleDto>,
  );
  readonly data = inject<RuleDialogData>(MAT_DIALOG_DATA);

  readonly ruleTypes = RULE_TYPES;
  readonly statusLabels = RULE_STATUS_LABELS;
  readonly isEdit = !!this.data.rule;

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly validationMessage = signal<string | null>(null);
  readonly validationOk = signal(false);

  readonly form = this.fb.nonNullable.group({
    name: [
      this.data.rule?.name ?? '',
      [Validators.required, Validators.maxLength(255)],
    ],
    description: [this.data.rule?.description ?? ''],
    ruleType: [
      (this.data.rule?.ruleType ?? 'PRICING') as RuleType,
      [Validators.required],
    ],
    priority: [
      this.data.rule?.priority ?? 100,
      [Validators.required, Validators.min(100), Validators.max(1000)],
    ],
    content: [this.data.rule?.content ?? '', [Validators.required]],
  });

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const name = value.name.trim();
    const content = value.content.trim();
    const description = value.description?.trim();
    const priority = Number(value.priority);

    this.error.set(null);
    this.validationMessage.set(null);
    this.saving.set(true);

    if (this.isEdit && this.data.rule) {
      const ruleId = this.data.rule.id;
      const request: RuleUpdateRequest = {
        name,
        ruleType: value.ruleType,
        priority,
        content,
      };
      if (description) {
        request.description = description;
      }

      // Before save (edit only): soft-validate current server DRL; never block update.
      this.softValidate(ruleId)
        .pipe(
          switchMap(() => this.ruleService.update(ruleId, request)),
          switchMap((updated) => this.validateAfterSave(updated)),
        )
        .subscribe({
          next: (updated) => {
            this.saving.set(false);
            this.dialogRef.close(updated);
          },
          error: (err: unknown) => {
            this.saving.set(false);
            this.validationOk.set(false);
            this.error.set(
              httpErrorMessage(err, 'Failed to save or validate rule'),
            );
          },
        });
      return;
    }

    const createRequest: RuleCreateRequest = {
      ruleSetId: this.data.ruleSetId,
      name,
      ruleType: value.ruleType,
      priority,
      content,
    };
    if (description) {
      createRequest.description = description;
    }

    // Create has no id before save — validateSyntax only after create.
    this.ruleService
      .create(createRequest)
      .pipe(switchMap((created) => this.validateAfterSave(created)))
      .subscribe({
        next: (created) => {
          this.saving.set(false);
          this.dialogRef.close(created);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.validationOk.set(false);
          this.error.set(
            httpErrorMessage(err, 'Failed to create or validate rule'),
          );
        },
      });
  }

  /** Soft pre-save validate: errors become a warning, do not abort save. */
  private softValidate(ruleId: number): Observable<null> {
    return this.ruleService.validateSyntax(ruleId).pipe(
      map(() => null),
      catchError((err: unknown) => {
        this.validationOk.set(false);
        this.validationMessage.set(
          httpErrorMessage(err, 'Pre-save DRL validation failed (continuing)'),
        );
        return of(null);
      }),
    );
  }

  /** Hard post-save validate: failure aborts close so the user can fix DRL. */
  private validateAfterSave(rule: RuleDto): Observable<RuleDto> {
    return this.ruleService.validateSyntax(rule.id).pipe(
      map(() => {
        this.validationOk.set(true);
        this.validationMessage.set('DRL syntax valid');
        return rule;
      }),
    );
  }
}
