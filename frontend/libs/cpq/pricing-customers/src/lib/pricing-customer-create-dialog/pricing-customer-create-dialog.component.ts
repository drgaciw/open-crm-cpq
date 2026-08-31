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
  CUSTOMER_REGION_LABELS,
  CUSTOMER_SEGMENT_LABELS,
  CustomerRegion,
  CustomerSegment,
  PricingCustomerCreateRequest,
  PricingCustomerDto,
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

/**
 * New pricing customer dialog matching PricingCustomerCreateRequest.
 * Required: externalId, name. Optional: type, segment, region, industry,
 * churnScore, annualRevenue, metadata.
 */
@Component({
  selector: 'cpq-pricing-customer-create-dialog',
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
    <h2 mat-dialog-title>New pricing customer</h2>
    <mat-dialog-content>
      @if (error()) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ error() }}</span>
        </div>
      }
      <form [formGroup]="form" class="create-form" (ngSubmit)="onSubmit()">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>External ID</mat-label>
          <input
            matInput
            formControlName="externalId"
            maxlength="100"
            autocomplete="off"
          />
          @if (form.get('externalId')?.hasError('required')) {
            <mat-error>External ID is required</mat-error>
          }
        </mat-form-field>

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
              placeholder="e.g. ACCOUNT"
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
                <mat-option [value]="seg">{{ segmentLabels[seg] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="half">
            <mat-label>Region</mat-label>
            <mat-select formControlName="region">
              <mat-option value="">—</mat-option>
              @for (reg of regions; track reg) {
                <mat-option [value]="reg">{{ regionLabels[reg] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>

        <div class="row">
          <mat-form-field appearance="outline" class="half">
            <mat-label>Churn score</mat-label>
            <input matInput type="number" formControlName="churnScore" min="0" />
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
export class PricingCustomerCreateDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly customerService = inject(PricingCustomerService);
  private readonly dialogRef = inject(
    MatDialogRef<PricingCustomerCreateDialogComponent, PricingCustomerDto>,
  );

  readonly segments = SEGMENTS;
  readonly regions = REGIONS;
  readonly segmentLabels = CUSTOMER_SEGMENT_LABELS;
  readonly regionLabels = CUSTOMER_REGION_LABELS;

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    externalId: ['', [Validators.required, Validators.maxLength(100)]],
    name: ['', [Validators.required, Validators.maxLength(255)]],
    type: [''],
    segment: ['' as CustomerSegment | ''],
    region: ['' as CustomerRegion | ''],
    industry: [''],
    churnScore: this.fb.control<number | null>(null),
    annualRevenue: this.fb.control<number | null>(null),
  });

  onSubmit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: PricingCustomerCreateRequest = {
      externalId: value.externalId.trim(),
      name: value.name.trim(),
    };

    const type = value.type?.trim();
    if (type) {
      request.type = type;
    }
    if (value.segment) {
      request.segment = value.segment;
    }
    if (value.region) {
      request.region = value.region;
    }
    const industry = value.industry?.trim();
    if (industry) {
      request.industry = industry;
    }
    if (value.churnScore != null && !Number.isNaN(Number(value.churnScore))) {
      request.churnScore = Number(value.churnScore);
    }
    if (
      value.annualRevenue != null &&
      !Number.isNaN(Number(value.annualRevenue))
    ) {
      request.annualRevenue = Number(value.annualRevenue);
    }

    this.error.set(null);
    this.saving.set(true);
    this.customerService.create(request).subscribe({
      next: (customer) => {
        this.saving.set(false);
        this.dialogRef.close(customer);
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.saving.set(false);
        this.error.set(
          err?.error?.message ||
            err?.message ||
            'Failed to create pricing customer',
        );
      },
    });
  }
}
