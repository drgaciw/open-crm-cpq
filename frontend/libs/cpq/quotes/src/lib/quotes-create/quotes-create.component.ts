import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
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
import { QuoteCreateRequest } from '@frontend/shared-models';
import { QuoteService } from '../quote.service';

function httpErrorMessage(err: unknown): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || 'An error occurred';
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
}

@Component({
  selector: 'cpq-quotes-create',
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
    <div class="form-header">
      <button mat-icon-button type="button" (click)="goBack()" aria-label="Go back">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <h1>New Quote</h1>
    </div>

    @if (error(); as err) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ err }}</span>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
      </div>
    }

    <mat-card class="form-card">
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Name</mat-label>
              <input matInput formControlName="name" autocomplete="off" />
              @if (form.controls.name.hasError('required') && form.controls.name.touched) {
                <mat-error>Name is required</mat-error>
              }
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Customer external ID</mat-label>
              <input matInput formControlName="customerExternalId" autocomplete="off" />
              @if (
                form.controls.customerExternalId.hasError('required') &&
                form.controls.customerExternalId.touched
              ) {
                <mat-error>Customer external ID is required</mat-error>
              }
            </mat-form-field>
          </div>

          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Currency</mat-label>
              <input
                matInput
                formControlName="currency"
                maxlength="3"
                placeholder="USD"
                autocomplete="off"
              />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Duration (months)</mat-label>
              <input matInput type="number" formControlName="durationInMonths" min="1" />
            </mat-form-field>
          </div>

          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Deal external ID</mat-label>
              <input matInput formControlName="dealExternalId" autocomplete="off" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Region</mat-label>
              <input
                matInput
                formControlName="region"
                maxlength="20"
                placeholder="NA"
                autocomplete="off"
              />
            </mat-form-field>
          </div>

          <div class="form-actions">
            <button mat-button type="button" (click)="goBack()" [disabled]="saving()">
              Cancel
            </button>
            <button
              mat-raised-button
              color="primary"
              type="submit"
              [disabled]="form.invalid || saving()"
            >
              @if (saving()) {
                <mat-spinner diameter="20" />
              } @else {
                Create quote
              }
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: [
    `
      .form-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 0 32px 24px;
      }

      .form-header h1 {
        margin: 0;
        font-size: 1.5rem;
      }

      .form-card {
        max-width: 720px;
        margin: 0 32px 32px;
      }

      .form-row {
        display: flex;
        gap: 16px;
        margin-bottom: 8px;
      }

      .two-col > * {
        flex: 1;
      }

      .full-width {
        width: 100%;
      }

      .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        margin-top: 24px;
        padding-top: 16px;
        border-top: 1px solid #eee;
      }

      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 0 32px 16px;
        padding: 12px 16px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
      }

      @media (max-width: 768px) {
        .form-header,
        .form-card,
        .error-banner {
          margin-left: 16px;
          margin-right: 16px;
        }

        .form-row.two-col {
          flex-direction: column;
        }
      }
    `,
  ],
})
export class QuotesCreateComponent {
  private readonly fb = inject(FormBuilder);
  private readonly quoteService = inject(QuoteService);
  private readonly router = inject(Router);

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    customerExternalId: ['', Validators.required],
    currency: [''],
    durationInMonths: [null as number | null],
    dealExternalId: [''],
    region: [''],
  });

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set(null);

    this.quoteService.create(this.toRequest()).subscribe({
      next: (quote) => {
        void this.router.navigate(['/quotes', quote.id]);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(httpErrorMessage(err));
      },
    });
  }

  goBack(): void {
    void this.router.navigate(['/quotes']);
  }

  clearError(): void {
    this.error.set(null);
  }

  private toRequest(): QuoteCreateRequest {
    const raw = this.form.getRawValue();
    const request: QuoteCreateRequest = {
      name: raw.name.trim(),
      customerExternalId: raw.customerExternalId.trim(),
    };
    const currency = raw.currency.trim();
    if (currency) {
      request.currency = currency;
    }
    const dealExternalId = raw.dealExternalId.trim();
    if (dealExternalId) {
      request.dealExternalId = dealExternalId;
    }
    const region = raw.region.trim();
    if (region) {
      request.region = region;
    }
    if (raw.durationInMonths != null && Number.isFinite(raw.durationInMonths)) {
      request.durationInMonths = Number(raw.durationInMonths);
    }
    return request;
  }
}
