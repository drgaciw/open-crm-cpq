import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { TextFieldModule } from '@angular/cdk/text-field';
import { MatButtonModule } from '@angular/material/button';
import { PricingExecutionService } from '@frontend/cpq-pricing-data-access';
import {
  BulkPricingSimulationResponse,
  PricingContext,
  PricingResult,
} from '@frontend/shared-models';
import { CopyButtonComponent, DataTableComponent, TableColumn } from '@frontend/shared-ui';
import { MOCK_BULK_CONTEXTS } from '../pricing-simulator.mock';

const BULK_SOFT_CAP = 50;
const VIRTUAL_SCROLL_THRESHOLD = 25;

interface BulkResultRow {
  quoteExternalId: string;
  customerExternalId: string;
  region: string;
  subscriptionType: string;
  status: 'Success' | 'Failed';
  productExternalId: string;
  net: number | string;
  arr: number | string;
  approvalState: string;
  errorMessage: string;
}

type ParsedContexts =
  | { ok: true; contexts: PricingContext[] }
  | { ok: false; reason: 'invalid' | 'too-few' };

function parseContexts(raw: string): ParsedContexts {
  const trimmed = raw.trim();
  if (!trimmed) {
    return { ok: false, reason: 'invalid' };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    return { ok: false, reason: 'invalid' };
  }

  const contexts = contextsFromParsed(parsed);
  if (!contexts) {
    return { ok: false, reason: 'invalid' };
  }
  if (contexts.length < 2) {
    return { ok: false, reason: 'too-few' };
  }
  return { ok: true, contexts };
}

function contextsFromParsed(parsed: unknown): PricingContext[] | null {
  if (Array.isArray(parsed)) {
    return parsed as PricingContext[];
  }
  if (!parsed || typeof parsed !== 'object') {
    return null;
  }
  const record = parsed as { contexts?: unknown };
  if (Array.isArray(record.contexts)) {
    return record.contexts as PricingContext[];
  }
  return [parsed as PricingContext];
}

function toRow(context: PricingContext, result: PricingResult | undefined): BulkResultRow {
  const success = result?.success === true;
  const fromResult = result?.lineResults?.find((line) => line.productExternalId)
    ?.productExternalId;
  const fromContext = context.lineItems?.find((line) => line.productExternalId)
    ?.productExternalId;

  return {
    quoteExternalId: context.quoteExternalId ?? '',
    customerExternalId: context.customerExternalId ?? '',
    region: context.region ?? '',
    subscriptionType: context.subscriptionType ?? '',
    status: success ? 'Success' : 'Failed',
    productExternalId: fromResult || fromContext || '',
    net: result?.waterfall?.netPrice ?? '',
    arr: result?.financialMetrics?.arr ?? '',
    approvalState: result?.approvalState ?? '',
    errorMessage: success ? '' : (result?.errorMessage ?? ''),
  };
}

@Component({
  selector: 'cpq-bulk-simulator',
  standalone: true,
  imports: [ScrollingModule, TextFieldModule, MatButtonModule, CopyButtonComponent, DataTableComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section data-testid="bulk-simulator" class="bulk-simulator">
      <label class="json-label" for="bulk-json-input">Pricing contexts (JSON)</label>
      <textarea
        id="bulk-json-input"
        data-testid="bulk-json-input"
        class="json-input"
        cdkTextareaAutosize
        cdkAutosizeMinRows="8"
        cdkAutosizeMaxRows="24"
        [value]="jsonText()"
        (input)="onJsonInput($event)"
        placeholder="Paste a JSON array of pricing contexts, or { &quot;contexts&quot;: [...] }"
      ></textarea>

      <div class="actions">
        <button
          mat-raised-button
          color="primary"
          type="button"
          data-testid="bulk-simulate"
          (click)="simulate()"
        >
          Simulate bulk
        </button>
        <button
          mat-stroked-button
          type="button"
          data-testid="bulk-load-sample"
          (click)="loadSample()"
        >
          Load sample
        </button>
        <label class="file-label">
          Upload JSON
          <input
            data-testid="bulk-file-input"
            type="file"
            accept=".json,application/json"
            (change)="onFileSelected($event)"
          />
        </label>
        @if (jsonText().trim()) {
          <app-copy-button [value]="jsonText()" label="JSON payload" />
        }
      </div>

      @if (validationError(); as message) {
        <p role="alert" class="validation">{{ message }}</p>
      }

      @if (overCap()) {
        <p role="status" class="cap-warning">
          More than {{ softCap }} pricing contexts. Bulk simulate still runs; large batches may be slow.
        </p>
      }

      @if (response(); as result) {
        <div data-testid="bulk-summary" class="summary">
          <p>Total requests: {{ result.totalRequests }}</p>
          <p>Successful requests: {{ result.successfulRequests }}</p>
          <p>Failed requests: {{ result.failedRequests }}</p>
          <p>Total execution time: {{ result.totalExecutionTimeMs }} ms</p>
          <div class="summary-actions">
            <button
              mat-stroked-button
              type="button"
              data-testid="bulk-download-results"
              (click)="downloadResults()"
            >
              Download results JSON
            </button>
            <app-copy-button [value]="resultsJson()" label="results JSON" />
            <button
              mat-stroked-button
              type="button"
              data-testid="bulk-toggle-virtual-scroll"
              [attr.aria-pressed]="isVirtualized()"
              (click)="toggleVirtualScroll()"
            >
              {{ isVirtualized() ? 'Switch to table view' : 'Switch to virtualized list' }}
            </button>
          </div>
        </div>
        @if (isVirtualized()) {
          <div class="virtual-results-shell" role="region" aria-label="Bulk simulation results">
            <div class="virtual-header-row" role="row">
              @for (col of columns; track col.key) {
                <span class="virtual-cell virtual-header-cell" role="columnheader">{{ col.header }}</span>
              }
            </div>
            <cdk-virtual-scroll-viewport
              [itemSize]="44"
              class="bulk-virtual-viewport"
              data-testid="bulk-virtual-viewport"
            >
              <div
                *cdkVirtualFor="let row of rows(); trackBy: trackResultRow"
                class="virtual-data-row"
                role="row"
              >
                <span class="virtual-cell">{{ row.quoteExternalId }}</span>
                <span class="virtual-cell">{{ row.customerExternalId }}</span>
                <span class="virtual-cell">{{ row.region }}</span>
                <span class="virtual-cell">{{ row.subscriptionType }}</span>
                <span
                  class="virtual-cell virtual-status"
                  [class.status-failed]="row.status === 'Failed'"
                >{{ row.status }}</span>
                <span class="virtual-cell">{{ row.productExternalId }}</span>
                <span class="virtual-cell">{{ row.net }}</span>
                <span class="virtual-cell">{{ row.arr }}</span>
                <span class="virtual-cell">{{ row.approvalState }}</span>
                <span class="virtual-cell">{{ row.errorMessage }}</span>
              </div>
            </cdk-virtual-scroll-viewport>
          </div>
        } @else {
          <app-data-table [columns]="columns" [data]="rows()" [clickable]="false" />
        }
      }
    </section>
  `,
  styles: [
    `
      .bulk-simulator {
        display: flex;
        flex-direction: column;
        gap: 12px;
        padding: 16px 0 32px;
      }

      .json-label {
        font-weight: 600;
      }

      .json-input {
        width: 100%;
        box-sizing: border-box;
        font-family: ui-monospace, monospace;
        font-size: 0.875rem;
        padding: 12px;
      }

      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: center;
      }

      .file-label {
        display: inline-flex;
        flex-direction: column;
        gap: 4px;
        font-size: 0.875rem;
      }

      .validation {
        margin: 0;
        color: #b71c1c;
      }

      .cap-warning {
        margin: 0;
        color: #e65100;
      }

      .summary p {
        margin: 0 0 4px;
      }

      .summary-actions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--crm-space-sm);
        margin-top: var(--crm-space-sm);
      }

      .virtual-results-shell {
        border: 1px solid var(--crm-color-line);
        border-radius: var(--crm-radius-sm);
        background: var(--crm-color-surface);
        overflow: hidden;
      }

      .virtual-header-row,
      .virtual-data-row {
        display: grid;
        grid-template-columns: repeat(10, minmax(96px, 1fr));
        align-items: center;
        gap: var(--crm-space-xs);
        padding: 0 var(--crm-space-sm);
        font-size: 0.8125rem;
      }

      .virtual-header-row {
        min-height: 40px;
        background: var(--crm-color-canvas);
        border-bottom: 1px solid var(--crm-color-line);
        font-weight: 600;
        color: var(--crm-color-muted);
      }

      .bulk-virtual-viewport {
        height: 440px;
        width: 100%;
        display: block;
      }

      .virtual-data-row {
        height: 44px;
        border-bottom: 1px solid var(--crm-color-line);
        color: var(--crm-color-ink);
      }

      .virtual-cell {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .virtual-status {
        font-weight: 600;
        color: var(--crm-color-success-strong);
      }

      .virtual-status.status-failed {
        color: var(--crm-color-danger);
      }
    `,
  ],
})
export class BulkSimulatorComponent {
  private readonly pricingExecution = inject(PricingExecutionService);

  readonly softCap = BULK_SOFT_CAP;
  readonly virtualScrollThreshold = VIRTUAL_SCROLL_THRESHOLD;
  readonly columns: TableColumn[] = [
    { key: 'quoteExternalId', header: 'Quote', sortable: false },
    { key: 'customerExternalId', header: 'Customer', sortable: false },
    { key: 'region', header: 'Region', sortable: false },
    { key: 'subscriptionType', header: 'Subscription', sortable: false },
    { key: 'status', header: 'Status', sortable: false },
    { key: 'productExternalId', header: 'Product', sortable: false },
    { key: 'net', header: 'Net', sortable: false },
    { key: 'arr', header: 'ARR', sortable: false },
    { key: 'approvalState', header: 'Approval', sortable: false },
    { key: 'errorMessage', header: 'Error', sortable: false },
  ];

  readonly jsonText = signal('');
  readonly validationError = signal<string | null>(null);
  readonly overCap = signal(false);
  readonly response = signal<BulkPricingSimulationResponse | null>(null);
  readonly rows = signal<BulkResultRow[]>([]);
  readonly resultsJson = computed(() => {
    const result = this.response();
    return result ? JSON.stringify(result, null, 2) : '';
  });
  private readonly virtualScrollOverride = signal<boolean | null>(null);
  readonly isVirtualized = computed(
    () =>
      this.virtualScrollOverride() ??
      this.rows().length > this.virtualScrollThreshold,
  );

  toggleVirtualScroll(): void {
    this.virtualScrollOverride.set(!this.isVirtualized());
  }

  trackResultRow(index: number, row: BulkResultRow): string {
    return `${index}-${row.quoteExternalId}`;
  }

  onJsonInput(event: Event): void {
    const target = event.target;
    if (!(target instanceof HTMLTextAreaElement)) {
      return;
    }
    this.jsonText.set(target.value);
  }

  loadSample(): void {
    this.jsonText.set(JSON.stringify(MOCK_BULK_CONTEXTS));
    this.validationError.set(null);
  }

  onFileSelected(event: Event): void {
    const input = event.target;
    if (!(input instanceof HTMLInputElement)) {
      return;
    }
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.jsonText.set(typeof reader.result === 'string' ? reader.result : '');
      this.validationError.set(null);
    };
    reader.readAsText(file);
  }

  simulate(): void {
    const parsed = parseContexts(this.jsonText());
    if (!parsed.ok) {
      this.validationError.set(
        parsed.reason === 'invalid'
          ? 'Invalid JSON'
          : 'At least 2 pricing contexts are required.',
      );
      this.response.set(null);
      this.rows.set([]);
      this.overCap.set(false);
      return;
    }

    this.validationError.set(null);
    this.virtualScrollOverride.set(null);
    this.overCap.set(parsed.contexts.length > BULK_SOFT_CAP);
    this.pricingExecution.simulateBulk({ contexts: parsed.contexts }).subscribe({
      next: (response) => {
        this.response.set(response);
        this.rows.set(
          parsed.contexts.map((context, index) => toRow(context, response.results[index])),
        );
      },
      error: () => {
        this.response.set(null);
        this.rows.set([]);
        this.validationError.set('Bulk simulation failed.');
      },
    });
  }

  downloadResults(): void {
    const result = this.response();
    if (!result) {
      return;
    }
    const blob = new Blob([JSON.stringify(result, null, 2)], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'bulk-pricing-simulation-results.json';
    anchor.click();
    URL.revokeObjectURL(url);
  }
}
