import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { finalize } from 'rxjs';
import { QuoteComparison, QuoteComparisonService } from './quote-comparison.service';

@Component({
  selector: 'cpq-quote-comparison',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-label="Opportunity quote comparison" class="comparison">
      <h2>Alternative quotes</h2>
      <button type="button" (click)="load()" [disabled]="busy()">{{ result() ? 'Refresh comparison' : 'Compare opportunity quotes' }}</button>
      @if (busy()) { <p role="status">Loading comparison…</p> }
      @if (error(); as message) { <p role="alert">{{ message }}</p> }
      @if (result(); as comparison) {
        @if (comparison.quotes.length === 0) { <p>No quotes for this opportunity.</p> }
        @else {
          <p>Baseline: {{ baselineName() }}. Amount differences are only available for the same currency.</p>
          <p>Margin, valid-until and payment terms are unavailable in the current quote data.</p>
          <div class="table-scroll" tabindex="0" role="region" aria-label="Side-by-side quote comparison">
            <table>
              <caption>Quotes for opportunity {{ comparison.dealExternalId }}</caption>
              <thead><tr><th scope="col">Attribute</th>
                @for (quote of comparison.quotes; track quote.id) {
                  <th scope="col">{{ quote.quoteNumber }} — {{ quote.name }}
                    @if (quote.primary) { <span class="badge">Primary</span> }
                    @else { <button type="button" [disabled]="busy()" (click)="markPrimary(quote.id)" [attr.aria-label]="'Mark ' + quote.quoteNumber + ' as Primary'">Mark as Primary</button> }
                  </th>
                }
              </tr></thead>
              <tbody>
                @for (row of rows; track row.key) {
                  <tr><th scope="row">{{ row.label }}</th>
                    @for (quote of comparison.quotes; track quote.id) {
                      <td>{{ value(quote, row.key) }}</td>
                    }
                  </tr>
                }
                <tr><th scope="row">Total delta vs baseline</th>
                  @for (quote of comparison.quotes; track quote.id) {
                    <td>@if (quote.delta.comparableCurrency) { {{ quote.delta.grandTotal | number:'1.2-2' }} {{ quote.currency }} ({{ quote.delta.grandTotalPercent ?? '—' }}%) } @else { Different currency — not comparable }</td>
                  }
                </tr>
                <tr><th scope="row">SKU changes vs baseline</th>
                  @for (quote of comparison.quotes; track quote.id) {
                    <td>Added: {{ quote.skuDiff.added.join(', ') || 'None' }}<br>Removed: {{ quote.skuDiff.removed.join(', ') || 'None' }}<br>Changed: {{ quote.skuDiff.changed.join(', ') || 'None' }}</td>
                  }
                </tr>
              </tbody>
            </table>
          </div>
        }
      }
    </section>
  `,
  styles: `
    .comparison { padding: 16px; border: 1px solid #ddd; border-radius: 8px; }
    .table-scroll { overflow-x: auto; margin-top: 16px; }
    table { border-collapse: collapse; width: 100%; }
    th, td { padding: 12px; text-align: left; border: 1px solid #ddd; min-width: 160px; }
    .badge { display: inline-block; padding: 4px 8px; background: #dcefe0; color: #174d25; border-radius: 4px; }
    button { margin: 4px; padding: 8px 12px; cursor: pointer; }
  `,
})
export class QuoteComparisonComponent {
  readonly dealExternalId = input.required<string>();
  readonly primaryChanged = output<void>();
  private readonly service = inject(QuoteComparisonService);
  private readonly destroyRef = inject(DestroyRef);
  readonly result = signal<QuoteComparison | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly baselineName = computed(() => this.result()?.quotes.find(q => q.id === this.result()?.baselineQuoteId)?.quoteNumber ?? 'None');
  private generation = 0;
  readonly rows = [
    { key: 'status', label: 'Status' }, { key: 'currency', label: 'Currency' },
    { key: 'listTotal', label: 'List total' }, { key: 'subtotal', label: 'Subtotal' },
    { key: 'discountTotal', label: 'Discount total' }, { key: 'discountPercent', label: 'Discount %' },
    { key: 'tax', label: 'Tax' }, { key: 'grandTotal', label: 'Grand total' },
    { key: 'netAmount', label: 'Net amount' }, { key: 'marginAmount', label: 'Margin amount' },
    { key: 'marginPercent', label: 'Margin %' }, { key: 'billingFrequencies', label: 'Billing frequencies' },
    { key: 'termMonths', label: 'Term (months)' }, { key: 'validUntil', label: 'Valid until' },
    { key: 'paymentTerms', label: 'Payment terms' }, { key: 'lineCount', label: 'Line count' },
  ] as const;

  constructor() {
    effect(() => {
      this.dealExternalId();
      untracked(() => { this.generation++; this.result.set(null); this.error.set(null); this.busy.set(false); });
    });
  }

  load(): void {
    if (this.busy() || !this.dealExternalId().trim()) return;
    const generation = ++this.generation;
    this.busy.set(true); this.error.set(null);
    this.service.compare(this.dealExternalId()).pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => { if (generation === this.generation) this.busy.set(false); })).subscribe({
      next: result => { if (generation === this.generation) this.result.set(result); },
      error: () => { if (generation === this.generation) this.error.set('Could not load comparison. Retry using the comparison button.'); },
    });
  }

  markPrimary(id: number): void {
    if (this.busy()) return;
    const generation = this.generation;
    this.busy.set(true); this.error.set(null);
    this.service.markPrimary(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        if (generation === this.generation) {
          this.busy.set(false);
          this.primaryChanged.emit();
          this.load();
        }
      },
      error: () => { if (generation === this.generation) { this.busy.set(false); this.error.set('Could not mark quote as primary. Refresh and retry.'); } },
    });
  }

  value(quote: QuoteComparison['quotes'][number], key: typeof this.rows[number]['key']): string {
    const value = quote[key];
    if (value == null) return 'Unavailable';
    return Array.isArray(value) ? value.join(', ') || 'Unavailable' : String(value);
  }
}
