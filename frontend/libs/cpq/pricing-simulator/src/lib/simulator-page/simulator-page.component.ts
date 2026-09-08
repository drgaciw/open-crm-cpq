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
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import {
  CurrencyFormatPipe,
  EmptyStateComponent,
  PageHeaderComponent,
} from '@frontend/shared-ui';
import {
  PricingContext,
  PricingResult,
  SubscriptionType,
} from '@frontend/shared-models';
import { PricingExecutionService } from '@frontend/cpq-pricing-data-access';
import { MOCK_PRICING_CONTEXT } from '../pricing-simulator.mock';

/** Prefer message; append errorCode when present (H1 shape). */
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
  selector: 'cpq-simulator-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    PageHeaderComponent,
    EmptyStateComponent,
    CurrencyFormatPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Pricing Simulator"
      subtitle="What-if sandbox — POST /api/v1/pricing/execute"
    >
      <div actions>
        <button
          mat-raised-button
          color="primary"
          type="button"
          [disabled]="form.invalid || loading()"
          (click)="onSimulate()"
        >
          <mat-icon>play_arrow</mat-icon>
          Simulate
        </button>
      </div>
    </app-page-header>

    <div class="simulator-layout">
      <mat-card class="panel form-panel" appearance="outlined">
        <mat-card-header>
          <mat-card-title>Pricing context</mat-card-title>
          <mat-card-subtitle>Build a PricingContext and run pricing execute</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form class="form-grid" [formGroup]="form" (ngSubmit)="onSimulate()">
            <mat-form-field appearance="outline">
              <mat-label>Customer external ID</mat-label>
              <input matInput formControlName="customerExternalId" autocomplete="off" />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Product external ID</mat-label>
              <input matInput formControlName="productExternalId" autocomplete="off" />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Quantity</mat-label>
              <input matInput type="number" formControlName="quantity" min="1" />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>List price override</mat-label>
              <input matInput type="number" formControlName="listPriceOverride" min="0" step="0.01" />
              <mat-hint>Optional; default list is server-side</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Region</mat-label>
              <mat-select formControlName="region">
                @for (r of regions; track r) {
                  <mat-option [value]="r">{{ r }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Currency</mat-label>
              <mat-select formControlName="currency">
                @for (c of currencies; track c) {
                  <mat-option [value]="c">{{ c }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Duration (months)</mat-label>
              <input matInput type="number" formControlName="durationInMonths" min="1" />
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Subscription type</mat-label>
              <mat-select formControlName="subscriptionType">
                @for (t of subscriptionTypes; track t) {
                  <mat-option [value]="t">{{ t }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-card class="panel result-panel" appearance="outlined">
        <mat-card-header>
          <mat-card-title>Result</mat-card-title>
          <mat-card-subtitle>PricingResult from execute</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (error()) {
            <app-empty-state
              icon="error_outline"
              title="Simulation failed"
              [message]="error()!"
              actionLabel="Dismiss"
              (action)="clearError()"
            />
          } @else if (!result()) {
            <app-empty-state
              icon="science"
              title="No simulation yet"
              message="Fill the form and click Simulate to run pricing execute."
            />
          } @else {
            <div class="success-banner" role="status">
              <mat-icon>check_circle</mat-icon>
              <span>
                Execute OK — approval {{ result()!.approvalState ?? 'APPROVED' }}
                @if (result()!.executionTimeMs != null) {
                  · {{ result()!.executionTimeMs }} ms
                }
              </span>
            </div>

            <div class="metrics-row">
              <div class="metric">
                <span class="metric-label">Net</span>
                <span class="metric-value">{{
                  result()!.waterfall?.netPrice | currencyFormat: currencyCode()
                }}</span>
              </div>
              <div class="metric">
                <span class="metric-label">ARR</span>
                <span class="metric-value">{{
                  result()!.financialMetrics?.arr | currencyFormat: currencyCode()
                }}</span>
              </div>
              <div class="metric">
                <span class="metric-label">Margin</span>
                <span class="metric-value"
                  >{{ result()!.financialMetrics?.estimatedMarginPct ?? '-' }}%</span
                >
              </div>
            </div>

            <h3 class="section-title">Line results</h3>
            <div class="table-wrap">
              <table class="line-table">
                <thead>
                  <tr>
                    <th>Product</th>
                    <th>Qty</th>
                    <th>List</th>
                    <th>Unit</th>
                    <th>Final</th>
                  </tr>
                </thead>
                <tbody>
                  @for (line of result()!.lineResults ?? []; track line.productExternalId) {
                    <tr>
                      <td>{{ line.productExternalId }}</td>
                      <td>{{ line.quantity }}</td>
                      <td>{{ line.listPrice | currencyFormat: currencyCode() }}</td>
                      <td>{{ line.unitPrice | currencyFormat: currencyCode() }}</td>
                      <td>{{ line.finalPrice | currencyFormat: currencyCode() }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .simulator-layout {
        display: grid;
        grid-template-columns: minmax(280px, 1fr) minmax(280px, 1fr);
        gap: 24px;
        padding: 0 32px 32px;
      }

      .panel {
        min-width: 0;
      }

      .form-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 8px 16px;
        margin-top: 8px;
      }

      .success-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 12px 16px;
        border-radius: 8px;
        margin-bottom: 16px;
        background: #e8f5e9;
        color: #2e7d32;
      }

      .metrics-row {
        display: flex;
        flex-wrap: wrap;
        gap: 16px;
        margin-bottom: 20px;
      }

      .metric {
        display: flex;
        flex-direction: column;
        min-width: 100px;
        padding: 12px 16px;
        border-radius: 8px;
        background: #f5f5f5;
      }

      .metric-label {
        font-size: 0.75rem;
        color: #616161;
        text-transform: uppercase;
        letter-spacing: 0.04em;
      }

      .metric-value {
        font-size: 1.125rem;
        font-weight: 600;
        color: #212121;
      }

      .section-title {
        margin: 0 0 8px;
        font-size: 1rem;
        font-weight: 600;
      }

      .table-wrap {
        overflow-x: auto;
      }

      .line-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.875rem;
      }

      .line-table th,
      .line-table td {
        text-align: left;
        padding: 8px 12px;
        border-bottom: 1px solid #e0e0e0;
      }

      .line-table th {
        color: #616161;
        font-weight: 600;
      }

      @media (max-width: 960px) {
        .simulator-layout {
          grid-template-columns: 1fr;
          padding: 0 16px 24px;
        }

        .form-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class SimulatorPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly pricingExecution = inject(PricingExecutionService);

  readonly regions = ['NA', 'EMEA', 'APAC', 'LATAM'] as const;
  readonly currencies = ['USD', 'EUR', 'GBP'] as const;
  readonly subscriptionTypes: SubscriptionType[] = ['NEW', 'AMENDMENT', 'RENEWAL'];

  readonly loading = signal(false);
  readonly result = signal<PricingResult | null>(null);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    customerExternalId: [
      MOCK_PRICING_CONTEXT.customerExternalId,
      [Validators.required, Validators.minLength(1)],
    ],
    productExternalId: [
      MOCK_PRICING_CONTEXT.lineItems[0]?.productExternalId ?? 'PROD-001',
      [Validators.required],
    ],
    quantity: [
      MOCK_PRICING_CONTEXT.lineItems[0]?.quantity ?? 1,
      [Validators.required, Validators.min(1)],
    ],
    listPriceOverride: [null as number | null],
    region: [MOCK_PRICING_CONTEXT.region ?? 'NA', Validators.required],
    currency: [MOCK_PRICING_CONTEXT.currency ?? 'USD', Validators.required],
    durationInMonths: [
      MOCK_PRICING_CONTEXT.durationInMonths ?? 12,
      [Validators.required, Validators.min(1)],
    ],
    subscriptionType: [
      (MOCK_PRICING_CONTEXT.subscriptionType as SubscriptionType) ?? 'NEW',
      Validators.required,
    ],
  });

  currencyCode(): string {
    return this.form.controls.currency.value;
  }

  onSimulate(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const context = this.toContext();
    this.pricingExecution.execute(context).subscribe({
      next: (pricingResult) => {
        if (pricingResult.success) {
          this.result.set(pricingResult);
          this.error.set(null);
        } else {
          this.result.set(null);
          this.error.set(
            pricingResult.errorMessage ?? 'Pricing execute returned success=false',
          );
        }
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.result.set(null);
        this.error.set(httpErrorMessage(err, 'Failed to execute pricing'));
        this.loading.set(false);
      },
    });
  }

  clearError(): void {
    this.error.set(null);
  }

  /** Test helper: clear result panel. */
  clearResultForTest(): void {
    this.result.set(null);
    this.error.set(null);
  }

  /** Test helper: surface the error empty-state. */
  setErrorForTest(message: string | null): void {
    this.error.set(message);
    if (message) {
      this.result.set(null);
    }
  }

  private toContext(): PricingContext {
    const v = this.form.getRawValue();
    const override = v.listPriceOverride;
    return {
      customerExternalId: v.customerExternalId.trim(),
      quoteExternalId: MOCK_PRICING_CONTEXT.quoteExternalId,
      region: v.region,
      currency: v.currency,
      durationInMonths: v.durationInMonths,
      subscriptionType: v.subscriptionType,
      lineItems: [
        {
          productExternalId: v.productExternalId.trim(),
          lineItemType: 'SUBSCRIPTION',
          quantity: v.quantity,
          ...(override != null && !Number.isNaN(override)
            ? { listPriceOverride: Number(override) }
            : {}),
        },
      ],
    };
  }
}
