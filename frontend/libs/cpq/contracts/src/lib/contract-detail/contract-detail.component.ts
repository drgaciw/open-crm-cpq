import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { CurrencyFormatPipe, PageHeaderComponent } from '@frontend/shared-ui';
import {
  ASSET_STATUS_LABELS,
  AssetDto,
  SUBSCRIPTION_STATUS_LABELS,
  SubscriptionDto,
} from '@frontend/shared-models';
import { ContractService, isAccessDenied } from '../contract.service';

/** ERP sync states of SubscriptionDto.erpSyncStatus, by label. */
const ERP_SYNC_LABELS: Record<string, string> = {
  PENDING: 'Pending',
  SYNCED: 'Synced',
  FAILED: 'Failed',
};

/** Read-only contract detail with the contract's assets (DRG-761). Lifecycle actions stay held. */
@Component({
  selector: 'cpq-contract-detail',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (contract(); as c) {
      <app-page-header [title]="c.subscriptionNumber ?? 'Contract ' + c.id" subtitle="Contract">
        <div actions>
          <button mat-stroked-button type="button" (click)="back()">
            <mat-icon aria-hidden="true">arrow_back</mat-icon>
            Back to contracts
          </button>
        </div>
      </app-page-header>

      <section class="card" aria-labelledby="terms-heading">
        <h2 id="terms-heading">Terms</h2>
        <dl class="terms">
          @for (term of terms(); track term.label) {
            <div class="term">
              <dt>{{ term.label }}</dt>
              <dd>{{ term.value }}</dd>
            </div>
          }
        </dl>
      </section>

      <section class="card" aria-labelledby="assets-heading">
        <h2 id="assets-heading">Assets</h2>
        @switch (assetState()) {
          @case ('loading') {
            <p>Loading assets…</p>
          }
          @case ('denied') {
            <p role="status">Viewing assets requires the InstalledBase.Read role.</p>
          }
          @case ('failed') {
            <p role="status">Assets could not be loaded.</p>
          }
          @default {
            @if (assets().length === 0) {
              <p>No assets yet. Assets are created when the contract is activated.</p>
            } @else {
              <table class="assets" aria-label="Assets on this contract">
                <thead>
                  <tr>
                    <th scope="col">Product</th>
                    <th scope="col">Quantity</th>
                    <th scope="col">Status</th>
                    <th scope="col">Start</th>
                    <th scope="col">End</th>
                  </tr>
                </thead>
                <tbody>
                  @for (a of assets(); track a.id) {
                    <tr>
                      <td>{{ a.productRef ?? '—' }}</td>
                      <td>{{ a.quantity ?? '—' }}</td>
                      <td>{{ assetStatus(a) }}</td>
                      <td>{{ a.effectiveStart ?? '—' }}</td>
                      <td>{{ a.effectiveEnd ?? '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          }
        }
      </section>
    } @else if (error(); as message) {
      <app-page-header title="Contract" />
      <div class="error-banner" role="alert">
        <mat-icon aria-hidden="true">error_outline</mat-icon>
        <span>{{ message }}</span>
        <button mat-button type="button" (click)="back()">Back to contracts</button>
      </div>
    } @else {
      <p class="loading">Loading contract…</p>
    }
  `,
  styles: [
    `
      .card {
        margin: 0 32px 16px;
        padding: 16px 24px;
        border-radius: 8px;
        background: var(--mat-sys-surface-container-low, #fafafa);
      }
      .terms {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
        gap: 12px 24px;
        margin: 0;
      }
      dt {
        font-size: 12px;
        color: var(--mat-sys-on-surface-variant, #555);
      }
      dd {
        margin: 2px 0 0;
      }
      .assets {
        width: 100%;
        border-collapse: collapse;
      }
      .assets th,
      .assets td {
        text-align: left;
        padding: 8px;
        border-bottom: 1px solid var(--mat-sys-outline-variant, #ddd);
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
      .loading {
        margin: 32px;
      }
    `,
  ],
})
export class ContractDetailComponent implements OnInit {
  private readonly service = inject(ContractService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly money = new CurrencyFormatPipe();

  readonly contract = signal<SubscriptionDto | null>(null);
  readonly error = signal<string | null>(null);
  readonly assets = signal<AssetDto[]>([]);
  readonly assetState = signal<'loading' | 'ready' | 'denied' | 'failed'>('loading');

  readonly terms = computed(() => {
    const c = this.contract();
    if (!c) {
      return [];
    }
    const dash = (v: string | undefined | null) => (v ? v : '—');
    const amount = (v: number | undefined | null) =>
      v === undefined || v === null ? '—' : this.money.transform(v, c.currencyCode || 'USD');
    return [
      { label: 'Status', value: SUBSCRIPTION_STATUS_LABELS[c.status] ?? c.status },
      { label: 'Customer', value: dash(c.customerId) },
      { label: 'Plan', value: dash(c.planName) },
      { label: 'Source quote', value: dash(c.quoteId) },
      { label: 'Start date', value: dash(c.startDate) },
      { label: 'End date', value: dash(c.endDate) },
      { label: 'Renewal date', value: dash(c.renewalDate) },
      { label: 'MRR', value: amount(c.mrr) },
      { label: 'ARR', value: amount(c.arr) },
      { label: 'Currency', value: dash(c.currencyCode) },
      { label: 'ERP sync', value: c.erpSyncStatus ? (ERP_SYNC_LABELS[c.erpSyncStatus] ?? c.erpSyncStatus) : '—' },
    ];
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.service.get(id).subscribe({
      next: (c) => this.contract.set(c),
      error: (err: unknown) => {
        const notFound = err instanceof HttpErrorResponse && err.status === 404;
        this.error.set(notFound ? 'Contract not found.' : 'The contract could not be loaded.');
      },
    });
    this.service.assets({ subscriptionId: id }).subscribe({
      next: (rows) => {
        this.assets.set(rows ?? []);
        this.assetState.set('ready');
      },
      error: (err: unknown) => this.assetState.set(isAccessDenied(err) ? 'denied' : 'failed'),
    });
  }

  assetStatus(a: AssetDto): string {
    return ASSET_STATUS_LABELS[a.status] ?? a.status;
  }

  back(): void {
    void this.router.navigate(['/contracts']);
  }
}
