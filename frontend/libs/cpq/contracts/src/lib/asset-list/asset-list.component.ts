import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { EmptyStateComponent, PageHeaderComponent } from '@frontend/shared-ui';
import { ASSET_STATUS_LABELS, AssetDto } from '@frontend/shared-models';
import { ContractService, isAccessDenied } from '../contract.service';

/**
 * Read-only installed base (DRG-761). The API requires InstalledBase.Read and answers 403 otherwise,
 * including to anonymous dev-mode requests (docs/runbooks/asset-read-access.md); the page says so.
 */
@Component({
  selector: 'cpq-asset-list',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, EmptyStateComponent, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Assets" subtitle="Installed base: what customers own under their contracts" />

    @switch (state()) {
      @case ('loading') {
        <p class="note">Loading assets…</p>
      }
      @case ('denied') {
        <div class="notice" role="status">
          <mat-icon aria-hidden="true">lock</mat-icon>
          <div>
            <p><strong>Viewing assets requires the InstalledBase.Read role.</strong></p>
            <p>Your account does not have it. Ask an administrator to assign it.</p>
          </div>
        </div>
      }
      @case ('failed') {
        <div class="error-banner" role="alert">
          <mat-icon aria-hidden="true">error_outline</mat-icon>
          <span>Assets could not be loaded.</span>
          <button mat-button type="button" (click)="load()">Retry</button>
        </div>
      }
      @default {
        @if (assets().length === 0) {
          <app-empty-state
            icon="inventory_2"
            title="No assets yet"
            message="Assets are created when a contract is activated."
          />
        } @else {
          <div class="assets-scroll" role="region" aria-label="Asset columns" tabindex="0">
            <table class="assets" aria-label="Assets">
              <thead>
                <tr>
                  <th scope="col">Product</th>
                  <th scope="col">Account</th>
                  <th scope="col">Quantity</th>
                  <th scope="col">Status</th>
                  <th scope="col">Start</th>
                  <th scope="col">End</th>
                  <th scope="col">Contract</th>
                </tr>
              </thead>
              <tbody>
                @for (a of assets(); track a.id) {
                  <tr>
                    <td>{{ a.productRef ?? '—' }}</td>
                    <td>{{ a.accountId }}</td>
                    <td>{{ a.quantity ?? '—' }}</td>
                    <td>{{ status(a) }}</td>
                    <td>{{ a.effectiveStart ?? '—' }}</td>
                    <td>{{ a.effectiveEnd ?? '—' }}</td>
                    <td>
                      @if (a.subscriptionId !== undefined && a.subscriptionId !== null) {
                        <button
                          mat-button
                          type="button"
                          [attr.aria-label]="'Open contract ' + a.subscriptionId"
                          (click)="openContract(a.subscriptionId)"
                        >
                          Contract {{ a.subscriptionId }}
                        </button>
                      } @else {
                        —
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    }
  `,
  styles: [
    `
      .notice {
        display: flex;
        gap: 12px;
        margin: 0 32px 16px;
        padding: 16px;
        border-radius: 8px;
        background: var(--mat-sys-surface-container-low, #f5f5f5);
      }
      .notice p {
        margin: 0 0 4px;
      }
      .note {
        margin: 32px;
      }
      .assets-scroll {
        margin: 0 32px;
        overflow-x: auto;
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
    `,
  ],
})
export class AssetListComponent implements OnInit {
  private readonly service = inject(ContractService);
  private readonly router = inject(Router);

  readonly assets = signal<AssetDto[]>([]);
  readonly state = signal<'loading' | 'ready' | 'denied' | 'failed'>('loading');

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.service.assets().subscribe({
      next: (rows) => {
        this.assets.set(rows ?? []);
        this.state.set('ready');
      },
      error: (err: unknown) => this.state.set(isAccessDenied(err) ? 'denied' : 'failed'),
    });
  }

  status(a: AssetDto): string {
    return ASSET_STATUS_LABELS[a.status] ?? a.status;
  }

  openContract(id: number): void {
    void this.router.navigate(['/contracts', id]);
  }
}
