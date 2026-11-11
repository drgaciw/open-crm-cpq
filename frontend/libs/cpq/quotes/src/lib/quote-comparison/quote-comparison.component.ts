import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subscription, finalize } from 'rxjs';
import { QuoteComparisonApiService } from './quote-comparison-api.service';
import { QuoteAlternative, QuoteComparison } from './quote-comparison.model';

type MoneyField = 'subtotal' | 'totalDiscount' | 'totalTax' | 'totalAmount' | 'arr' | 'tcv' | 'acv';

@Component({
  selector: 'cpq-quote-comparison',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`
    :host { display: block; font-family: system-ui, sans-serif; color: #172033; }
    section { padding: 24px; }
    h1 { margin: 0 0 12px; font-size: 24px; line-height: 1.3; font-weight: 600; }
    section > p { margin: 12px 0; line-height: 1.5; }
    a { color: #175cd3; text-decoration: underline; text-underline-offset: 3px; }
    button { padding: 8px 12px; border: 1px solid #aab7ca; border-radius: 6px; background: white; color: #174a91; font: inherit; cursor: pointer; }
    button:disabled { cursor: default; opacity: .65; }
    th button { display: block; margin-top: 10px; }
    a:focus-visible, button:focus-visible, [tabindex]:focus-visible { outline: 3px solid #2563eb; outline-offset: 3px; }
    [role=alert], [role=note] { padding: 12px; border: 1px solid #c7d2e3; border-radius: 8px; background: #f3f6fb; }
    [role=region] { overflow-x: auto; margin-top: 16px; border: 1px solid #ccd5e1; border-radius: 8px; background: white; }
    table { width: 100%; border-collapse: collapse; text-align: left; }
    caption { padding: 12px 16px; text-align: left; color: #526076; }
    th, td { padding: 12px 16px; border-top: 1px solid #dce2eb; border-right: 1px solid #dce2eb; vertical-align: top; line-height: 1.45; }
    thead th { min-width: 240px; background: #f0f4fa; font-weight: 600; }
    thead th:first-child { min-width: 160px; }
    tbody th { background: #f8fafc; font-weight: 600; }
    td p, th div { margin: 4px 0; }
    ul { margin: 0; padding: 0; list-style: none; }
    li + li { margin-top: 12px; }
    @media (max-width: 600px) { section { padding: 16px; } th, td { padding: 10px 12px; } }
  `],
  template: `
    <section class="p-6 space-y-4" aria-labelledby="comparison-title">
      <h1 id="comparison-title" class="text-2xl font-semibold">Compare quote alternatives</h1>
      <p>Primary marks your preferred alternative. It does not accept a quote or change the forecast.</p>
      @if (anchorId(); as quoteId) {
        <a [routerLink]="['/quotes', quoteId]" class="underline">Back to quote</a>
      } @else {
        <a routerLink="/quotes" class="underline">Back to quotes</a>
      }
      @if (loading()) {
        <p role="status" aria-live="polite">Loading alternatives…</p>
      }
      @if (error(); as message) {
        <div role="alert" class="p-3 border rounded">
          <p>{{ message }}</p>
          @if (anchorId()) {
            <button type="button" class="underline" [disabled]="saving() || loading()" (click)="retry()">Retry</button>
          }
        </div>
      }
      @if (saving()) {
        <p role="status" aria-live="polite">Saving primary preference…</p>
      }
      @if (notice(); as message) {
        <p role="status" aria-live="polite">{{ message }}</p>
      }
      @if (comparison(); as data) {
        <p>Opportunity {{ data.dealExternalId }} · Customer {{ data.customerExternalId }}</p>
        @if (!data.sameCurrency) {
          <p class="p-3 border rounded" role="note">Currencies differ or are unavailable. Amounts are shown in each quote's currency and are not ranked or converted.</p>
        }
        @if (data.quotes.length === 0) {
          <p>No quote alternatives are available for this opportunity.</p>
        } @else {
          @if (data.quotes.length === 1) {
            <p>No other alternatives are available for this opportunity yet.</p>
          }
          <div class="overflow-x-auto" tabindex="0" role="region" aria-label="Quote alternatives table" [attr.aria-busy]="loading() || saving()">
            <table class="w-full border-collapse text-left">
              <caption class="text-left py-3">Persisted quote values. Missing metrics and verified margin are shown as unavailable.</caption>
              <thead>
                <tr>
                  <th scope="col" class="p-3 border">Attribute</th>
                  @for (quote of data.quotes; track quote.id) {
                    <th scope="col" class="p-3 border align-top min-w-56">
                      <a [routerLink]="['/quotes', quote.id]" class="underline">{{ quote.quoteNumber }}</a>
                      <div>{{ quote.name }}</div>
                      <button type="button" class="mt-2 underline"
                        [attr.aria-label]="data.primaryQuoteId === quote.id ? quote.quoteNumber + ' is primary' : 'Mark ' + quote.quoteNumber + ' primary'"
                        [disabled]="saving() || loading() || data.primaryQuoteId === quote.id"
                        (click)="markPrimary(quote.id)">
                        {{ data.primaryQuoteId === quote.id ? 'Primary' : 'Mark primary' }}
                      </button>
                    </th>
                  }
                </tr>
              </thead>
              <tbody>
                <tr><th scope="row" class="p-3 border">Status</th>@for (quote of data.quotes; track quote.id) {<td class="p-3 border">{{ quote.status }}</td>}</tr>
                <tr><th scope="row" class="p-3 border">Deal type</th>@for (quote of data.quotes; track quote.id) {<td class="p-3 border">{{ quote.dealType }}</td>}</tr>
                <tr><th scope="row" class="p-3 border">Currency</th>@for (quote of data.quotes; track quote.id) {<td class="p-3 border">{{ quote.currency || 'Not available' }}</td>}</tr>
                <tr><th scope="row" class="p-3 border">Term</th>@for (quote of data.quotes; track quote.id) {<td class="p-3 border">{{ quote.durationInMonths }} months</td>}</tr>
                <tr><th scope="row" class="p-3 border">Dates</th>@for (quote of data.quotes; track quote.id) {<td class="p-3 border">{{ quote.startDate || 'Not available' }} – {{ quote.endDate || 'Not available' }}</td>}</tr>
                <tr><th scope="row" class="p-3 border">Region</th>@for (quote of data.quotes; track quote.id) {<td class="p-3 border">{{ quote.region || 'Not available' }}</td>}</tr>
                @for (row of moneyRows; track row.key) {
                  <tr><th scope="row" class="p-3 border">{{ row.label }}</th>@for (quote of data.quotes; track quote.id) {<td class="p-3 border">{{ money(quote, row.key) }}</td>}</tr>
                }
                <tr><th scope="row" class="p-3 border">Margin</th>@for (quote of data.quotes; track quote.id) {<td class="p-3 border"><span>Not available</span><p class="text-sm">{{ quote.margin.reason }}</p></td>}</tr>
                <tr><th scope="row" class="p-3 border">Ramp schedule</th>@for (quote of data.quotes; track quote.id) {<td class="p-3 border">{{ quote.rampScheduleStale ? 'Stale — review and reprice' : 'No staleness reported' }}</td>}</tr>
                <tr>
                  <th scope="row" class="p-3 border">Products and quantities</th>
                  @for (quote of data.quotes; track quote.id) {
                    <td class="p-3 border align-top">
                      @if (quote.lines.length === 0) {<span>No line items</span>}
                      <ul class="space-y-3">
                        @for (line of quote.lines; track $index) {
                          <li>
                            <strong>{{ line.productName || line.productExternalId }}</strong> ({{ line.productExternalId }})
                            <p>{{ line.quantity }} {{ line.unitOfMeasure || 'units' }} · {{ line.billingFrequency || 'Billing not available' }}</p>
                            <p>{{ line.productType || 'Product type not available' }} · {{ line.lineItemType || 'Line type not available' }}</p>
                          </li>
                        }
                      </ul>
                    </td>
                  }
                </tr>
              </tbody>
            </table>
          </div>
        }
      }
    </section>
  `,
})
export class QuoteComparisonComponent {
  private readonly api = inject(QuoteComparisonApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly id = input.required<string>();
  readonly anchorId = signal<number | null>(null);
  readonly comparison = signal<QuoteComparison | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly moneyRows: { key: MoneyField; label: string }[] = [
    { key: 'subtotal', label: 'Subtotal' },
    { key: 'totalDiscount', label: 'Discount' },
    { key: 'totalTax', label: 'Tax' },
    { key: 'totalAmount', label: 'Total' },
    { key: 'tcv', label: 'TCV' },
    { key: 'arr', label: 'ARR' },
    { key: 'acv', label: 'ACV' },
  ];
  private readVersion = 0;
  private scopeVersion = 0;
  private currentRead: Subscription | undefined;
  private readonly format = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  private readonly loadOnIdChange = effect(() => {
    const rawId = this.id();
    untracked(() => {
      this.scopeVersion++;
      this.cancelRead();
      this.comparison.set(null);
      this.notice.set(null);
      const quoteId = /^[1-9]\d*$/.test(rawId) ? Number(rawId) : NaN;
      this.anchorId.set(Number.isSafeInteger(quoteId) ? quoteId : null);
      if (this.anchorId() === null) {
        this.error.set('Choose a valid quote to compare.');
        this.loading.set(false);
      } else {
        this.load();
      }
    });
  });

  retry(): void {
    if (!this.saving() && !this.loading()) this.load();
  }

  markPrimary(quoteId: number): void {
    const data = this.comparison();
    if (this.saving() || this.loading() || !data?.quotes.some((quote) => quote.id === quoteId)
      || data.primaryQuoteId === quoteId) return;
    const scope = this.scopeVersion;
    this.cancelRead();
    this.saving.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.markPrimary(quoteId).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.saving.set(false)))
      .subscribe({
        next: (result) => {
          if (scope === this.scopeVersion) {
            this.comparison.set(result);
            this.notice.set('Primary preference saved.');
          } else {
            // A completed write may affect a group visited during navigation. Reconcile the current scope by GET.
            this.load();
          }
        },
        error: (failure: unknown) => {
          if (scope === this.scopeVersion) this.error.set(this.errorMessage(failure));
          else this.load(); // Transport failure can follow a committed write; reconcile the current scope.
        },
      });
  }

  money(quote: QuoteAlternative, field: MoneyField): string {
    const value = quote[field];
    return value === null || !Number.isFinite(value) ? 'Not available'
      : `${quote.currency || 'Currency unavailable'} ${this.format.format(value)}`;
  }

  private load(): void {
    const quoteId = this.anchorId();
    if (quoteId === null) return;
    this.cancelRead();
    const version = this.readVersion;
    this.loading.set(true);
    this.error.set(null);
    this.currentRead = this.api.compare(quoteId).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
      if (version === this.readVersion) this.loading.set(false);
    })).subscribe({
      next: (result) => { if (version === this.readVersion) this.comparison.set(result); },
      error: (failure: unknown) => { if (version === this.readVersion) this.error.set(this.errorMessage(failure)); },
    });
  }

  private cancelRead(): void {
    this.readVersion++;
    this.currentRead?.unsubscribe();
  }

  private errorMessage(failure: unknown): string {
    const value = failure as { message?: string; errorCode?: string; error?: { message?: string; errorCode?: string } } | null;
    const message = value?.error?.message || value?.message || 'Unable to load quote alternatives. Try again.';
    const code = value?.errorCode || value?.error?.errorCode;
    return code ? `${message} [${code}]` : message;
  }
}
