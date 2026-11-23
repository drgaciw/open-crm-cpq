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
  CONFIG_RULE_STATUSES,
  CONFIG_RULE_TYPES,
  ConfigRuleStatus,
  ConfigRuleType,
  ConfigurationRuleDto,
} from '../configuration-rule.model';
import { ConfigurationRuleService } from '../configuration-rule.service';

export interface ConfigRuleCreateDialogData {
  productId: number;
  rule?: ConfigurationRuleDto;
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
  selector: 'cpq-config-rule-create-dialog',
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
    <h2 mat-dialog-title>{{ editing ? 'Edit configuration rule' : 'New configuration rule' }}</h2>
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
          @if (form.get('name')?.hasError('required') || form.get('name')?.hasError('pattern')) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Rule type</mat-label>
          <mat-select formControlName="ruleType" aria-label="Rule type">
            @for (t of ruleTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
          @if (form.get('ruleType')?.hasError('required')) {
            <mat-error>Rule type is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Target product ID (optional)</mat-label>
          <input
            matInput
            type="number"
            formControlName="targetProductId"
            min="1"
          />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Condition expression (optional)</mat-label>
          <textarea
            matInput
            formControlName="conditionExpression"
            rows="3"
            placeholder="Stored only — not evaluated in this UI"
          ></textarea>
          <mat-hint>Stored as text; no SpEL evaluation here</mat-hint>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description (optional)</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Error message (optional)</mat-label>
          <textarea matInput formControlName="errorMessage" rows="2" maxlength="500"></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Status</mat-label>
          <mat-select formControlName="status">
            @for (status of statuses; track status) {
              <mat-option [value]="status">{{ status }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Priority</mat-label>
          <input matInput type="number" formControlName="priority" />
        </mat-form-field>
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
          {{ editing ? 'Save' : 'Create' }}
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
        min-width: min(440px, 90vw);
        padding-top: 8px;
      }
      .full-width {
        width: 100%;
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
export class ConfigRuleCreateDialogComponent {
  private readonly dialogRef = inject(
    MatDialogRef<ConfigRuleCreateDialogComponent, ConfigurationRuleDto | null>,
  );
  private readonly data = inject<ConfigRuleCreateDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  private readonly ruleService = inject(ConfigurationRuleService);

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly ruleTypes: ConfigRuleType[] = [...CONFIG_RULE_TYPES];
  readonly statuses = CONFIG_RULE_STATUSES;
  readonly editing = this.data.rule !== undefined;

  readonly form = this.fb.nonNullable.group({
    name: [this.data.rule?.name ?? '', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
    ruleType: this.fb.nonNullable.control<ConfigRuleType | ''>(this.data.rule?.ruleType ?? '', {
      validators: [Validators.required],
    }),
    targetProductId: this.fb.control<number | null>(this.data.rule?.targetProductId ?? null, Validators.min(1)),
    conditionExpression: [this.data.rule?.conditionExpression ?? ''],
    description: [this.data.rule?.description ?? ''],
    errorMessage: [this.data.rule?.errorMessage ?? '', Validators.maxLength(500)],
    priority: this.fb.nonNullable.control(this.data.rule?.priority ?? 0),
    status: this.fb.nonNullable.control<ConfigRuleStatus>(
      this.statuses.find((status) => status === this.data.rule?.status) ?? 'ACTIVE',
    ),
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
    const details = {
      name: value.name.trim(),
      ruleType: value.ruleType as ConfigRuleType,
      priority: Number(value.priority) || 0,
      description: value.description.trim() || undefined,
      targetProductId: value.targetProductId ?? undefined,
      conditionExpression: value.conditionExpression.trim() || undefined,
      errorMessage: value.errorMessage.trim() || undefined,
    };
    const rule = this.data.rule;
    const request = rule
      ? this.ruleService.update(rule.id, { ...details, status: value.status })
      : this.ruleService.create({
          ...details,
          sourceProductId: this.data.productId,
          ...(value.status === 'ACTIVE' ? {} : { status: value.status }),
        });

    this.error.set(null);
    this.saving.set(true);
    request.subscribe({
      next: (created) => {
        this.saving.set(false);
        this.dialogRef.close(created);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(
          httpErrorMessage(err, rule ? 'Failed to save configuration rule' : 'Failed to create configuration rule'),
        );
      },
    });
  }
}
