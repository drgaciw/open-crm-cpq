import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { PricingExecutionService } from '@frontend/cpq-pricing-data-access';
import {
  BulkPricingSimulationResponse,
  PricingContext,
  PricingResult,
} from '@frontend/shared-models';
import { DataTableComponent, TableColumn } from '@frontend/shared-ui';
import { MOCK_BULK_CONTEXTS } from '../pricing-simulator.mock';

const BULK_SOFT_CAP = 50;

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

function toRow(
  context: PricingContext | null,
  result: PricingResult | undefined,
  index: number,
): BulkResultRow {
  const success = result?.success === true;
  const fromResult = result?.lineResults?.find((line) => line?.productExternalId)
    ?.productExternalId;
  const fromContext = context?.lineItems?.find((line) => line?.productExternalId)
    ?.productExternalId;

  return {
    quoteExternalId: context?.quoteExternalId ?? `Context ${index + 1}`,
    customerExternalId: context?.customerExternalId ?? '',
    region: context?.region ?? '',
    subscriptionType: context?.subscriptionType ?? '',
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
  imports: [MatButtonModule, DataTableComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section data-testid="bulk-simulator" class="bulk-simulator">
      <label class="json-label" for="bulk-json-input">Pricing contexts (JSON)</label>
      <textarea
        id="bulk-json-input"
        data-testid="bulk-json-input"
        class="json-input"
        rows="12"
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
          <button
            mat-stroked-button
            type="button"
            data-testid="bulk-download-results"
            (click)="downloadResults()"
          >
            Download results JSON
          </button>
        </div>
        <app-data-table [columns]="columns" [data]="rows()" [clickable]="false" />
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
    `,
  ],
})
export class BulkSimulatorComponent {
  private readonly pricingExecution = inject(PricingExecutionService);

  readonly softCap = BULK_SOFT_CAP;
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
    this.overCap.set(parsed.contexts.length > BULK_SOFT_CAP);
    this.pricingExecution.simulateBulk({ contexts: parsed.contexts }).subscribe({
      next: (response) => {
        this.response.set(response);
        this.rows.set(
          parsed.contexts.map((context, index) => toRow(context, response.results[index], index)),
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
