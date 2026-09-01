import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  RULE_SET_STATUS_LABELS,
  RULE_STATUS_LABELS,
  RuleImpactDto,
} from '@frontend/shared-models';
import { RuleService } from '@frontend/cpq-pricing-data-access';

export interface RuleImpactDialogData {
  ruleId: number;
  /** Optional label for the dialog title while loading. */
  ruleName?: string;
}

function httpErrorMessage(err: unknown, fallback: string): string {
  const e = err as { error?: { message?: string }; message?: string } | null;
  return e?.error?.message || e?.message || fallback;
}

function displayOrDash(value: string | number | boolean | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  if (typeof value === 'boolean') {
    return value ? 'Yes' : 'No';
  }
  return String(value);
}

/**
 * Read-only impact panel for a pricing DRL rule.
 * Loads via {@link RuleService.analyzeImpact} → GET /api/v1/rules/{id}/impact.
 */
@Component({
  selector: 'cpq-rule-impact-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>
      Rule impact
      @if (titleSuffix()) {
        <span class="title-suffix"> — {{ titleSuffix() }}</span>
      }
    </h2>
    <mat-dialog-content>
      @if (loading()) {
        <div class="loading" role="status" aria-live="polite">
          <mat-spinner diameter="36"></mat-spinner>
          <span>Loading impact from /api/v1/rules/{{ data.ruleId }}/impact…</span>
        </div>
      } @else if (error(); as err) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ err }}</span>
        </div>
      } @else if (impact(); as impact) {
        <div class="info-grid" data-testid="rule-impact-fields">
          <div class="info-item">
            <span class="label">Rule ID</span>
            <span class="value">{{ impact.ruleId }}</span>
          </div>
          <div class="info-item">
            <span class="label">Name</span>
            <span class="value">{{ display(impact.ruleName) }}</span>
          </div>
          <div class="info-item">
            <span class="label">Type</span>
            <span class="value">{{ display(impact.ruleType) }}</span>
          </div>
          <div class="info-item">
            <span class="label">Status</span>
            <span class="value">{{ ruleStatusLabel(impact.ruleStatus) }}</span>
          </div>
          <div class="info-item">
            <span class="label">Priority</span>
            <span class="value">{{ display(impact.priority) }}</span>
          </div>
          <div class="info-item">
            <span class="label">Rule set ID</span>
            <span class="value">{{ display(impact.ruleSetId) }}</span>
          </div>
          <div class="info-item">
            <span class="label">Rule set status</span>
            <span class="value">{{
              ruleSetStatusLabel(impact.ruleSetStatus)
            }}</span>
          </div>
          <div class="info-item">
            <span class="label">Active rule set</span>
            <span class="value">{{ display(impact.activeRuleSet) }}</span>
          </div>
          <div class="info-item">
            <span class="label">Total rules in set</span>
            <span class="value">{{ display(impact.totalRulesInRuleSet) }}</span>
          </div>
          <div class="info-item">
            <span class="label">Active rules in set</span>
            <span class="value">{{ display(impact.activeRulesInRuleSet) }}</span>
          </div>
          <div class="info-item">
            <span class="label">Pricing executions (all-time)</span>
            <span class="value">{{
              display(impact.pricingExecutionsWithRuleSet)
            }}</span>
          </div>
          <div class="info-item">
            <span class="label">Pricing executions (last 30d)</span>
            <span class="value">{{
              display(impact.pricingExecutionsWithRuleSetLast30Days)
            }}</span>
          </div>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" class="close-btn" (click)="close()">
        Close
      </button>
      @if (error() && !loading()) {
        <button
          mat-stroked-button
          color="primary"
          type="button"
          class="retry-btn"
          (click)="load()"
        >
          Retry
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [
    `
      .title-suffix {
        font-weight: 400;
        color: #616161;
      }

      .loading {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 24px 8px;
        color: #616161;
        font-size: 0.875rem;
      }

      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 12px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
        font-size: 0.875rem;
      }

      .info-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 16px 20px;
        min-width: min(520px, 80vw);
        margin: 8px 0 4px;
      }

      .info-item {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }

      .label {
        font-size: 11px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.4px;
        color: #757575;
      }

      .value {
        font-size: 14px;
        color: #212121;
        word-break: break-word;
      }

      @media (max-width: 560px) {
        .info-grid {
          grid-template-columns: 1fr;
          min-width: 0;
        }
      }
    `,
  ],
})
export class RuleImpactDialogComponent implements OnInit {
  readonly data = inject<RuleImpactDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<RuleImpactDialogComponent>);
  private readonly ruleService = inject(RuleService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly impact = signal<RuleImpactDto | null>(null);

  readonly titleSuffix = signal<string | null>(null);

  ngOnInit(): void {
    if (this.data.ruleName) {
      this.titleSuffix.set(this.data.ruleName);
    }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.ruleService.analyzeImpact(this.data.ruleId).subscribe({
      next: (dto) => {
        this.impact.set(dto);
        this.loading.set(false);
        if (dto.ruleName) {
          this.titleSuffix.set(dto.ruleName);
        }
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.impact.set(null);
        this.error.set(
          httpErrorMessage(err, 'Failed to load rule impact'),
        );
      },
    });
  }

  close(): void {
    this.dialogRef.close();
  }

  display(value: string | number | boolean | null | undefined): string {
    return displayOrDash(value);
  }

  ruleStatusLabel(status: RuleImpactDto['ruleStatus']): string {
    if (!status) {
      return '—';
    }
    return RULE_STATUS_LABELS[status] ?? status;
  }

  ruleSetStatusLabel(status: RuleImpactDto['ruleSetStatus']): string {
    if (!status) {
      return '—';
    }
    return RULE_SET_STATUS_LABELS[status] ?? status;
  }
}
