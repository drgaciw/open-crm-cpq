import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { QuoteDto, QuoteUpdateRequest } from '@frontend/shared-models';
import { QuoteService } from '../quote.service';

function toDateInput(value: string | undefined | null): string {
  if (!value) {
    return '';
  }
  return value.length >= 10 ? value.slice(0, 10) : value;
}

/**
 * Prefer message; append errorCode (e.g. QUOTE_NOT_EDITABLE) when present.
 * Handles HttpErrorResponse body and ApiError from apiResponseInterceptor.
 */
export function quoteHeaderHttpErrorMessage(err: unknown): string {
  const e = err as {
    error?: { message?: string; errorCode?: string };
    message?: string;
    errorCode?: string;
  } | null;
  const message = e?.error?.message || e?.message || 'An error occurred';
  const code = e?.error?.errorCode || e?.errorCode;
  if (code && !message.includes(code)) {
    return `${message} (${code})`;
  }
  return message;
}

/**
 * Inline panel for editing quote header fields matching QuoteUpdateRequest.
 * Visible Edit/Save when {@link editable} (DRAFT|REJECTED). Does not send
 * customerExternalId / dealType / status / money fields.
 */
@Component({
  selector: 'cpq-quote-header-edit',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-card class="header-edit-card">
      <mat-card-header>
        <mat-card-title>Quote header</mat-card-title>
        <div class="header-actions">
          @if (editable()) {
            @if (!editing()) {
              <button
                mat-stroked-button
                color="primary"
                type="button"
                (click)="startEdit()"
                [disabled]="saving()"
              >
                <mat-icon>edit</mat-icon>
                Edit
              </button>
            } @else {
              <button
                mat-button
                type="button"
                (click)="cancelEdit()"
                [disabled]="saving()"
              >
                Cancel
              </button>
              <button
                mat-raised-button
                color="primary"
                type="button"
                (click)="onSave()"
                [disabled]="form.invalid || saving()"
              >
                @if (saving()) {
                  <mat-spinner diameter="20" />
                } @else {
                  <mat-icon>save</mat-icon>
                  Save
                }
              </button>
            }
          }
        </div>
      </mat-card-header>

      <mat-card-content>
        @if (error(); as err) {
          <div class="error-banner" role="alert">
            <mat-icon>error_outline</mat-icon>
            <span>{{ err }}</span>
            <button mat-button type="button" (click)="clearError()">Dismiss</button>
          </div>
        }

        @if (editing() && editable()) {
          <form class="edit-form" [formGroup]="form" (ngSubmit)="onSave()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Name</mat-label>
              <input matInput formControlName="name" maxlength="255" autocomplete="off" />
              @if (form.controls.name.hasError('required') && form.controls.name.touched) {
                <mat-error>Name is required</mat-error>
              }
              @if (form.controls.name.hasError('maxlength')) {
                <mat-error>Name must be at most 255 characters</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Description</mat-label>
              <textarea matInput formControlName="description" rows="3"></textarea>
            </mat-form-field>

            <div class="row">
              <mat-form-field appearance="outline" class="half">
                <mat-label>Deal external ID</mat-label>
                <input matInput formControlName="dealExternalId" autocomplete="off" />
              </mat-form-field>
              <mat-form-field appearance="outline" class="half">
                <mat-label>Subscription type</mat-label>
                <input matInput formControlName="subscriptionType" autocomplete="off" />
              </mat-form-field>
            </div>

            <div class="row">
              <mat-form-field appearance="outline" class="half">
                <mat-label>Currency</mat-label>
                <input
                  matInput
                  formControlName="currency"
                  maxlength="3"
                  placeholder="USD"
                  autocomplete="off"
                />
                @if (form.controls.currency.hasError('maxlength')) {
                  <mat-error>Currency must be 3 characters</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline" class="half">
                <mat-label>Region</mat-label>
                <input
                  matInput
                  formControlName="region"
                  maxlength="20"
                  placeholder="NA"
                  autocomplete="off"
                />
                @if (form.controls.region.hasError('maxlength')) {
                  <mat-error>Region must be at most 20 characters</mat-error>
                }
              </mat-form-field>
            </div>

            <div class="row">
              <mat-form-field appearance="outline" class="half">
                <mat-label>Duration (months)</mat-label>
                <input matInput type="number" formControlName="durationInMonths" min="1" />
              </mat-form-field>
              <mat-form-field appearance="outline" class="half">
                <mat-label>Start date</mat-label>
                <input matInput type="date" formControlName="startDate" />
              </mat-form-field>
            </div>

            <div class="row">
              <mat-form-field appearance="outline" class="half">
                <mat-label>End date</mat-label>
                <input matInput type="date" formControlName="endDate" />
              </mat-form-field>
              <mat-form-field appearance="outline" class="half">
                <mat-label>Notes</mat-label>
                <input matInput formControlName="notes" autocomplete="off" />
              </mat-form-field>
            </div>
          </form>
        } @else {
          <p class="hint">
            @if (editable()) {
              Edit header fields (name, dates, region, notes). Customer, deal type, status, and
              totals are not editable here.
            } @else {
              Header edit is available only for DRAFT or REJECTED quotes.
            }
          </p>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [
    `
      .header-edit-card {
        margin-top: 0;
      }

      mat-card-header {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
      }

      .header-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
        margin-left: auto;
      }

      .error-banner {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px;
        margin: 12px 0;
        padding: 12px 16px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
      }

      .edit-form {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin-top: 12px;
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

      .hint {
        margin: 12px 0 0;
        font-size: 13px;
        color: #757575;
        line-height: 1.5;
      }

      @media (max-width: 768px) {
        .row {
          flex-direction: column;
        }
      }
    `,
  ],
})
export class QuoteHeaderEditComponent {
  private readonly fb = inject(FormBuilder);
  private readonly quoteService = inject(QuoteService);

  /** Quote whose header is being edited. */
  readonly quote = input.required<QuoteDto>();
  /** True when status is DRAFT or REJECTED (parent `canEditLines`). */
  readonly editable = input(false);

  /** Emitted after successful PUT + parent should refresh UI. */
  readonly saved = output<QuoteDto>();

  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    dealExternalId: [''],
    subscriptionType: [''],
    currency: ['', [Validators.maxLength(3)]],
    region: ['', [Validators.maxLength(20)]],
    durationInMonths: [null as number | null],
    startDate: [''],
    endDate: [''],
    notes: [''],
  });

  /** Exit edit mode when quote becomes non-editable (e.g. after submit). */
  private readonly syncEditable = effect(() => {
    const canEdit = this.editable();
    untracked(() => {
      if (!canEdit && this.editing()) {
        this.editing.set(false);
        this.error.set(null);
      }
    });
  });

  startEdit(): void {
    if (!this.editable() || this.saving()) {
      return;
    }
    this.patchFromQuote(this.quote());
    this.error.set(null);
    this.editing.set(true);
  }

  cancelEdit(): void {
    if (this.saving()) {
      return;
    }
    this.editing.set(false);
    this.error.set(null);
  }

  clearError(): void {
    this.error.set(null);
  }

  onSave(): void {
    if (!this.editable() || this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const id = this.quote().id;
    const request = this.toRequest();
    this.saving.set(true);
    this.error.set(null);
    this.quoteService.update(id, request).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.editing.set(false);
        this.saved.emit(updated);
        this.quoteService.getById(id);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(quoteHeaderHttpErrorMessage(err));
      },
    });
  }

  private patchFromQuote(q: QuoteDto): void {
    this.form.setValue({
      name: q.name ?? '',
      description: q.description ?? '',
      dealExternalId: q.dealExternalId ?? '',
      subscriptionType: q.subscriptionType ?? '',
      currency: q.currency ?? '',
      region: q.region ?? '',
      durationInMonths: q.durationInMonths ?? null,
      startDate: toDateInput(q.startDate),
      endDate: toDateInput(q.endDate),
      notes: q.notes ?? '',
    });
  }

  private toRequest(): QuoteUpdateRequest {
    const raw = this.form.getRawValue();
    const request: QuoteUpdateRequest = {
      name: raw.name.trim(),
    };
    const description = raw.description.trim();
    if (description) {
      request.description = description;
    }
    const dealExternalId = raw.dealExternalId.trim();
    if (dealExternalId) {
      request.dealExternalId = dealExternalId;
    }
    const subscriptionType = raw.subscriptionType.trim();
    if (subscriptionType) {
      request.subscriptionType = subscriptionType;
    }
    const currency = raw.currency.trim();
    if (currency) {
      request.currency = currency.toUpperCase();
    }
    const region = raw.region.trim();
    if (region) {
      request.region = region;
    }
    if (raw.durationInMonths != null && Number.isFinite(raw.durationInMonths)) {
      request.durationInMonths = Number(raw.durationInMonths);
    }
    if (raw.startDate) {
      request.startDate = raw.startDate;
    }
    if (raw.endDate) {
      request.endDate = raw.endDate;
    }
    const notes = raw.notes.trim();
    if (notes) {
      request.notes = notes;
    }
    return request;
  }
}
