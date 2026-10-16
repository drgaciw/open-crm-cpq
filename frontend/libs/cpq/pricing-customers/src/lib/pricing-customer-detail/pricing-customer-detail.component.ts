import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
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
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import {
  ConfirmDialogComponent,
  EmptyStateComponent,
  PageHeaderComponent,
} from '@frontend/shared-ui';
import {
  CUSTOMER_REGION_LABELS,
  CUSTOMER_SEGMENT_LABELS,
  CustomerRegion,
  CustomerSegment,
  PricingCustomerDto,
  PricingCustomerUpdateRequest,
} from '@frontend/shared-models';
import { PricingCustomerService } from '@frontend/cpq-pricing-data-access';

const SEGMENTS: CustomerSegment[] = [
  'STARTUP',
  'SMB',
  'MID_MARKET',
  'ENTERPRISE',
  'STRATEGIC',
];

const REGIONS: CustomerRegion[] = ['NA', 'EMEA', 'APAC', 'LATAM'];

function httpErrorMessage(
  err: unknown,
  fallback = 'Failed to update pricing customer',
): string {
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
  selector: 'cpq-pricing-customer-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    PageHeaderComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    } @else if (customer(); as customer) {
      <app-page-header [title]="customer.name" [subtitle]="subtitle(customer)">
        <div actions class="header-actions">
          <button
            mat-stroked-button
            type="button"
            (click)="goBack()"
            [disabled]="saving() || deleting()"
          >
            <mat-icon>arrow_back</mat-icon>
            Back to pricing customers
          </button>
          @if (!editing()) {
            <button
              mat-raised-button
              color="primary"
              type="button"
              (click)="startEdit()"
              [disabled]="deleting()"
            >
              <mat-icon>edit</mat-icon>
              Edit
            </button>
            <button
              mat-stroked-button
              color="warn"
              type="button"
              (click)="onDelete()"
              [disabled]="deleting()"
            >
              @if (deleting()) {
                <mat-spinner diameter="20" />
              } @else {
                <ng-container>
                  <mat-icon>delete</mat-icon>
                  Delete
                </ng-container>
              }
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
                <ng-container>
                  <mat-icon>save</mat-icon>
                  Save
                </ng-container>
              }
            </button>
          }
        </div>
      </app-page-header>

      @if (actionError(); as err) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ err }}</span>
          <button mat-button type="button" (click)="clearActionError()">
            Dismiss
          </button>
        </div>
      }

      <div class="detail-body">
        <mat-card>
          <mat-card-header>
            <mat-card-title>
              {{ editing() ? 'Edit pricing customer' : 'Pricing customer details' }}
            </mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (!editing()) {
              <div class="info-grid">
                <div class="info-item">
                  <span class="label">External ID</span>
                  <span class="value">{{ customer.externalId }}</span>
                </div>
                <div class="info-item">
                  <span class="label">Type</span>
                  <span class="value">{{ customer.type || '—' }}</span>
                </div>
                <div class="info-item">
                  <span class="label">Segment</span>
                  <span class="value">{{ segmentLabel(customer.segment) }}</span>
                </div>
                <div class="info-item">
                  <span class="label">Region</span>
                  <span class="value">{{ regionLabel(customer.region) }}</span>
                </div>
                <div class="info-item">
                  <span class="label">Industry</span>
                  <span class="value">{{ customer.industry || '—' }}</span>
                </div>
                <div class="info-item">
                  <span class="label">Churn score</span>
                  <span class="value">{{
                    customer.churnScore ?? '—'
                  }}</span>
                </div>
                <div class="info-item">
                  <span class="label">Annual revenue</span>
                  <span class="value">{{
                    customer.annualRevenue ?? '—'
                  }}</span>
                </div>
                <div class="info-item">
                  <span class="label">Updated</span>
                  <span class="value">{{ customer.updatedAt || '—' }}</span>
                </div>
              </div>
            } @else {
              <form [formGroup]="form" class="edit-form" (ngSubmit)="onSave()">
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

                <div class="row">
                  <mat-form-field appearance="outline" class="half">
                    <mat-label>Type</mat-label>
                    <input
                      matInput
                      formControlName="type"
                      maxlength="50"
                      autocomplete="off"
                    />
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="half">
                    <mat-label>Industry</mat-label>
                    <input
                      matInput
                      formControlName="industry"
                      maxlength="100"
                      autocomplete="off"
                    />
                  </mat-form-field>
                </div>

                <div class="row">
                  <mat-form-field appearance="outline" class="half">
                    <mat-label>Segment</mat-label>
                    <mat-select formControlName="segment">
                      <mat-option value="">—</mat-option>
                      @for (seg of segments; track seg) {
                        <mat-option [value]="seg">{{
                          segmentLabels[seg]
                        }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="half">
                    <mat-label>Region</mat-label>
                    <mat-select formControlName="region">
                      <mat-option value="">—</mat-option>
                      @for (reg of regions; track reg) {
                        <mat-option [value]="reg">{{
                          regionLabels[reg]
                        }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>
                </div>

                <div class="row">
                  <mat-form-field appearance="outline" class="half">
                    <mat-label>Churn score</mat-label>
                    <input
                      matInput
                      type="number"
                      formControlName="churnScore"
                      min="0"
                    />
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="half">
                    <mat-label>Annual revenue</mat-label>
                    <input
                      matInput
                      type="number"
                      formControlName="annualRevenue"
                      min="0"
                    />
                  </mat-form-field>
                </div>
              </form>
            }
          </mat-card-content>
        </mat-card>
      </div>
    } @else {
      <app-empty-state
        icon="search_off"
        title="Pricing customer not found"
        [message]="
          actionError() ||
          'This pricing customer could not be loaded from /api/v1/pricing-customers.'
        "
        actionLabel="Back to pricing customers"
        (action)="goBack()"
      />
    }
  `,
  styles: [
    `
      .loading {
        display: flex;
        justify-content: center;
        padding: 48px;
      }

      .header-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
      }

      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 0 32px 16px;
        padding: 10px 12px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
        font-size: 0.875rem;
      }

      .detail-body {
        padding: 0 32px 32px;
      }

      .info-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 20px;
        margin-top: 16px;
      }

      .info-item {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }

      .label {
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.4px;
        color: #757575;
      }

      .value {
        font-size: 15px;
        color: #212121;
      }

      .edit-form {
        display: flex;
        flex-direction: column;
        gap: 4px;
        margin-top: 8px;
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

      @media (max-width: 768px) {
        .detail-body {
          padding: 0 16px 24px;
        }

        .error-banner {
          margin: 0 16px 16px;
        }

        .info-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class PricingCustomerDetailComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);
  readonly service = inject(PricingCustomerService);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly segments = SEGMENTS;
  readonly regions = REGIONS;
  readonly segmentLabels = CUSTOMER_SEGMENT_LABELS;
  readonly regionLabels = CUSTOMER_REGION_LABELS;

  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly deleting = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly loading = computed(() => this.service.loading() && !this.customer());

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    type: [''],
    segment: ['' as CustomerSegment | ''],
    region: ['' as CustomerRegion | ''],
    industry: [''],
    churnScore: this.fb.control<number | null>(null),
    annualRevenue: this.fb.control<number | null>(null),
  });

  readonly customer = computed<PricingCustomerDto | null>(() => {
    const selected = this.service.selectedCustomer();
    if (!selected) {
      return null;
    }
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId) || selected.id !== numericId) {
      return null;
    }
    return selected;
  });

  ngOnInit(): void {
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId)) {
      this.service.selectedCustomer.set(null);
      this.service.error.set('Invalid pricing customer id');
      this.service.loading.set(false);
      return;
    }
    this.service.getById(numericId);
  }

  subtitle(customer: PricingCustomerDto): string {
    const parts = [
      customer.externalId,
      customer.segment ? CUSTOMER_SEGMENT_LABELS[customer.segment] : null,
      customer.region ? CUSTOMER_REGION_LABELS[customer.region] : null,
    ].filter(Boolean);
    return parts.length > 0 ? parts.join(' · ') : `ID ${customer.id}`;
  }

  segmentLabel(segment?: CustomerSegment): string {
    return segment ? CUSTOMER_SEGMENT_LABELS[segment] : '—';
  }

  regionLabel(region?: CustomerRegion): string {
    return region ? CUSTOMER_REGION_LABELS[region] : '—';
  }

  startEdit(): void {
    const customer = this.customer();
    if (!customer) {
      return;
    }
    this.actionError.set(null);
    this.form.reset({
      name: customer.name,
      type: customer.type ?? '',
      segment: customer.segment ?? '',
      region: customer.region ?? '',
      industry: customer.industry ?? '',
      churnScore: customer.churnScore ?? null,
      annualRevenue: customer.annualRevenue ?? null,
    });
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
    this.actionError.set(null);
    this.saving.set(false);
  }

  clearActionError(): void {
    this.actionError.set(null);
    this.service.error.set(null);
  }

  onSave(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const customer = this.customer();
    if (!customer) {
      return;
    }

    const value = this.form.getRawValue();
    const request: PricingCustomerUpdateRequest = {
      name: value.name.trim(),
    };

    const type = value.type?.trim();
    request.type = type || undefined;
    request.segment = value.segment || undefined;
    request.region = value.region || undefined;
    const industry = value.industry?.trim();
    request.industry = industry || undefined;
    if (value.churnScore != null && !Number.isNaN(Number(value.churnScore))) {
      request.churnScore = Number(value.churnScore);
    }
    if (
      value.annualRevenue != null &&
      !Number.isNaN(Number(value.annualRevenue))
    ) {
      request.annualRevenue = Number(value.annualRevenue);
    }

    this.actionError.set(null);
    this.saving.set(true);
    this.service.update(customer.id, request).subscribe({
      next: (updated) => {
        this.service.selectedCustomer.set(updated);
        this.saving.set(false);
        this.editing.set(false);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.actionError.set(httpErrorMessage(err));
      },
    });
  }

  onDelete(): void {
    const customer = this.customer();
    if (!customer || this.deleting() || this.editing()) {
      return;
    }

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete pricing customer',
        message: `Soft-delete "${customer.name}" (${customer.externalId})? It will be removed from the pricing customers list.`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.actionError.set(null);
      this.deleting.set(true);
      this.service.delete(customer.id).subscribe({
        next: () => {
          this.deleting.set(false);
          this.service.selectedCustomer.set(null);
          void this.router.navigate(['/pricing-customers']);
        },
        error: (err: unknown) => {
          this.deleting.set(false);
          this.actionError.set(
            httpErrorMessage(err, 'Failed to delete pricing customer'),
          );
        },
      });
    });
  }

  goBack(): void {
    void this.router.navigate(['/pricing-customers']);
  }
}
