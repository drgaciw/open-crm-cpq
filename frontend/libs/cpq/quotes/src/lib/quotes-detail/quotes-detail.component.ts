import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import {
  CurrencyFormatPipe,
  EmptyStateComponent,
  PageHeaderComponent,
  StatusBadgeComponent,
} from '@frontend/shared-ui';
import { QuoteListItem } from '../quotes.model';
import { findMockQuote } from '../quotes.mock';

@Component({
  selector: 'cpq-quotes-detail',
  standalone: true,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    PageHeaderComponent,
    StatusBadgeComponent,
    EmptyStateComponent,
    CurrencyFormatPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (quote(); as quote) {
      <app-page-header [title]="quote.name" [subtitle]="quote.quoteNumber">
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
                <span class="value">{{ quote.dealType }}</span>
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
                  quote.totalAmount | currencyFormat: quote.currency
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">ARR</span>
                <span class="value">{{
                  quote.arr != null
                    ? (quote.arr | currencyFormat: quote.currency)
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
                <span class="value">{{ quote.lineItemCount }}</span>
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
        message="This quote is not in the mock data set."
        actionLabel="Back to quotes"
        (action)="goBack()"
      />
    }
  `,
  styles: [
    `
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
export class QuotesDetailComponent {
  private readonly router = inject(Router);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly quote = computed<QuoteListItem | undefined>(() => {
    const raw = this.id();
    const numericId = Number(raw);
    if (!Number.isFinite(numericId)) {
      return undefined;
    }
    return findMockQuote(numericId);
  });

  goBack(): void {
    void this.router.navigate(['/quotes']);
  }
}
