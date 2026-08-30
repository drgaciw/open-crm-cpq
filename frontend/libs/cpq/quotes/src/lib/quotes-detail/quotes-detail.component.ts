import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  CurrencyFormatPipe,
  EmptyStateComponent,
  PageHeaderComponent,
  StatusBadgeComponent,
} from '@frontend/shared-ui';
import { QuoteService } from '../quote.service';

@Component({
  selector: 'cpq-quotes-detail',
  standalone: true,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    CurrencyFormatPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40" />
      </div>
    } @else if (error(); as err) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ err }}</span>
        <button mat-button type="button" (click)="retry()">Retry</button>
        <button mat-button type="button" (click)="goBack()">Back to quotes</button>
      </div>
    } @else if (quote(); as quote) {
      <app-page-header
        [title]="quote.name"
        [subtitle]="quote.quoteNumber ?? 'Quote ' + quote.id"
      >
        <div actions>
          <button mat-stroked-button type="button" (click)="goBack()">
            <mat-icon>arrow_back</mat-icon>
            Back to quotes
          </button>
        </div>
      </app-page-header>

      <div class="detail-body">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Quote details</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Status</span>
                <app-status-badge [status]="quote.status" type="quote" />
              </div>
              <div class="info-item">
                <span class="label">Deal type</span>
                <span class="value">{{ quote.dealType || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Customer</span>
                <span class="value">{{ quote.customerExternalId }}</span>
              </div>
              <div class="info-item">
                <span class="label">Deal</span>
                <span class="value">{{ quote.dealExternalId || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Total amount</span>
                <span class="value">{{
                  quote.totalAmount != null
                    ? (quote.totalAmount | currencyFormat: quote.currency || 'USD')
                    : '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">ARR</span>
                <span class="value">{{
                  quote.arr != null
                    ? (quote.arr | currencyFormat: quote.currency || 'USD')
                    : '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">Duration</span>
                <span class="value">{{ quote.durationInMonths }} months</span>
              </div>
              <div class="info-item">
                <span class="label">Region</span>
                <span class="value">{{ quote.region || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Line items</span>
                <span class="value">{{ lineItemCount() }}</span>
              </div>
              <div class="info-item full-width">
                <span class="label">Description</span>
                <span class="value">{{ quote.description || '—' }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      </div>
    } @else {
      <app-empty-state
        icon="search_off"
        title="Quote not found"
        message="This quote could not be loaded from the API."
        actionLabel="Back to quotes"
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

      .error-banner {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px;
        margin: 24px 32px;
        padding: 12px 16px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
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

      .info-item.full-width {
        grid-column: 1 / -1;
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

      @media (max-width: 768px) {
        .error-banner {
          margin: 16px;
        }

        .detail-body {
          padding: 0 16px 24px;
        }

        .info-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class QuotesDetailComponent implements OnInit {
  readonly quoteService = inject(QuoteService);
  private readonly router = inject(Router);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly loading = computed(() => this.quoteService.loading());
  readonly error = computed(() => this.quoteService.error());
  readonly quote = computed(() => this.quoteService.selectedQuote());
  readonly lineItemCount = computed(
    () => this.quote()?.lineItems?.length ?? 0,
  );

  ngOnInit(): void {
    this.load();
  }

  goBack(): void {
    void this.router.navigate(['/quotes']);
  }

  retry(): void {
    this.load();
  }

  private load(): void {
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId)) {
      this.quoteService.selectedQuote.set(null);
      this.quoteService.error.set('Invalid quote id');
      this.quoteService.loading.set(false);
      return;
    }
    this.quoteService.selectedQuote.set(null);
    this.quoteService.getById(numericId);
  }
}
