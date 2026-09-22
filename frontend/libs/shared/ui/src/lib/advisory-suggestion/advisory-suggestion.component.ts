import { Component, ChangeDetectionStrategy, computed, input } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { JevSuggestion } from '@frontend/shared-models';

interface ProbabilityRow {
  key: string;
  label: string;
  percent: number;
  suggested: boolean;
}

/**
 * Shows a Jev suggestion with its probabilities and a fixed advisory label (ADR-015).
 * Renders nothing when there is no suggestion, so callers can bind it unconditionally.
 *
 * @example
 * <app-advisory-suggestion
 *   [suggestion]="lead().scoreSuggestion"
 *   heading="Likelihood to qualify"
 *   [labels]="{ '0': 'Very unlikely', '4': 'Very likely' }" />
 */
@Component({
  selector: 'app-advisory-suggestion',
  standalone: true,
  imports: [MatChipsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (suggestion(); as current) {
      <section class="advisory-suggestion" role="note" [attr.aria-label]="heading() + ' (advisory)'">
        <header class="advisory-header">
          <span class="advisory-heading">{{ heading() }}</span>
          <mat-chip class="advisory-chip" disableRipple>Advisory — not a decision</mat-chip>
        </header>

        <p class="advisory-value">
          <strong>{{ displayValue() }}</strong>
          <span class="advisory-confidence">{{ confidencePercent() }}% confidence</span>
        </p>

        <ul class="advisory-bars">
          @for (row of rows(); track row.key) {
            <li class="advisory-bar" [class.suggested]="row.suggested">
              <span class="bar-label">{{ row.label }}</span>
              <span
                class="bar-track"
                role="meter"
                aria-valuemin="0"
                aria-valuemax="100"
                [attr.aria-valuenow]="row.percent"
                [attr.aria-label]="row.label + ' probability'"
              >
                <span class="bar-fill" [style.width.%]="row.percent"></span>
              </span>
              <span class="bar-percent">{{ row.percent }}%</span>
            </li>
          }
        </ul>

        <footer class="advisory-meta">{{ current.model }} · {{ current.rubricVersion }}</footer>
      </section>
    }
  `,
  styles: [`
    .advisory-suggestion {
      border: 1px dashed #9e9e9e;
      border-radius: 8px;
      padding: 12px 16px;
      font-size: 13px;
    }
    .advisory-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .advisory-heading { font-weight: 500; }
    .advisory-chip { font-size: 11px; min-height: 22px; }
    .advisory-value { margin: 8px 0; display: flex; gap: 8px; align-items: baseline; }
    .advisory-confidence, .advisory-meta { color: #616161; font-size: 12px; }
    .advisory-bars { list-style: none; margin: 0; padding: 0; display: grid; gap: 4px; }
    .advisory-bar { display: grid; grid-template-columns: minmax(80px, 30%) 1fr 40px; gap: 8px; align-items: center; }
    .advisory-bar.suggested .bar-label { font-weight: 600; }
    .bar-track { background: #eeeeee; border-radius: 4px; height: 8px; overflow: hidden; }
    .bar-fill { display: block; height: 100%; background: #1976d2; }
    .bar-percent { text-align: right; font-variant-numeric: tabular-nums; }
    .advisory-meta { margin-top: 8px; }
  `],
})
export class AdvisorySuggestionComponent {
  suggestion = input<JevSuggestion | null | undefined>(null);
  heading = input('Suggestion');
  /** Optional display labels keyed by probability key; keys without a label are shown as-is. */
  labels = input<Record<string, string>>({});

  displayValue = computed(() => {
    const current = this.suggestion();
    return current ? (this.labels()[current.value] ?? current.value) : '';
  });

  confidencePercent = computed(() => Math.round((this.suggestion()?.confidence ?? 0) * 100));

  rows = computed<ProbabilityRow[]>(() => {
    const current = this.suggestion();
    if (!current) {
      return [];
    }
    const labels = this.labels();
    return Object.entries(current.probabilities).map(([key, probability]) => ({
      key,
      label: labels[key] ?? key,
      percent: Math.round(probability * 100),
      suggested: key === current.value,
    }));
  });
}
