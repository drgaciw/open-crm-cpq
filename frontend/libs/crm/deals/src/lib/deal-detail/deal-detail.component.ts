import {
  Component,
  inject,
  OnInit,
  input,
  ChangeDetectionStrategy,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { STAGE_COLORS, STAGE_LABELS } from '@frontend/shared-models';
import { DealService } from '../deal.service';

@Component({
  selector: 'app-deal-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    CurrencyPipe,
    DatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (service.loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    } @else if (service.selectedDeal(); as deal) {
      <div class="detail-header">
        <button
          mat-icon-button
          (click)="goBack()"
          aria-label="Go back to deals"
        >
          <mat-icon>arrow_back</mat-icon>
        </button>
        <div class="header-info">
          <h1>{{ deal.name }}</h1>
          <span class="stage-badge" [style.background]="stageColor(deal.stage)">
            {{ stageLabel(deal.stage) }}
          </span>
        </div>
        <div class="header-actions">
          <button mat-stroked-button (click)="onEdit()">
            <mat-icon>edit</mat-icon> Edit
          </button>
          @if (deal.stage !== 'WON' && deal.stage !== 'LOST') {
            <button mat-raised-button color="primary" (click)="onAdvance()">
              <mat-icon>arrow_forward</mat-icon> Advance Stage
            </button>
            <button
              mat-raised-button
              style="background:#4caf50;color:white"
              (click)="onWin()"
            >
              <mat-icon>emoji_events</mat-icon> Won
            </button>
            <button mat-stroked-button color="warn" (click)="onLose()">
              <mat-icon>cancel</mat-icon> Lost
            </button>
          }
        </div>
      </div>

      <div class="detail-grid">
        <mat-card>
          <mat-card-header
            ><mat-card-title>Deal Information</mat-card-title></mat-card-header
          >
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Amount</span
                ><span class="value">{{
                  deal.amount
                    ? (deal.amount | currency: 'USD' : 'symbol' : '1.0-0')
                    : '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">Probability</span
                ><span class="value">{{
                  deal.probability ? deal.probability + '%' : '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">Expected Close</span
                ><span class="value">{{
                  deal.expectedCloseDate
                    ? (deal.expectedCloseDate | date: 'mediumDate')
                    : '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">Created</span
                ><span class="value">{{
                  deal.createdAt | date: 'mediumDate'
                }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header
            ><mat-card-title>Relationships</mat-card-title></mat-card-header
          >
          <mat-card-content>
            <div class="relationship-list">
              @if (deal.primaryContactId) {
                <div class="rel-item">
                  <mat-icon>person</mat-icon>
                  <a
                    [routerLink]="['/contacts', deal.primaryContactId]"
                    class="link"
                  >
                    {{ deal.primaryContactName }}
                  </a>
                </div>
              }
              @if (deal.organizationId) {
                <div class="rel-item">
                  <mat-icon>business</mat-icon>
                  <a
                    [routerLink]="['/organizations', deal.organizationId]"
                    class="link"
                  >
                    {{ deal.organizationName }}
                  </a>
                </div>
              }
              @if (!deal.primaryContactId && !deal.organizationId) {
                <p class="no-relations">No linked contacts or organizations.</p>
              }
            </div>
          </mat-card-content>
        </mat-card>

        @if (deal.lostReason) {
          <mat-card class="full-width lost-card">
            <mat-card-header
              ><mat-card-title>Lost Reason</mat-card-title></mat-card-header
            >
            <mat-card-content
              ><p>{{ deal.lostReason }}</p></mat-card-content
            >
          </mat-card>
        }

        @if (deal.description) {
          <mat-card class="full-width">
            <mat-card-header
              ><mat-card-title>Description</mat-card-title></mat-card-header
            >
            <mat-card-content
              ><p>{{ deal.description }}</p></mat-card-content
            >
          </mat-card>
        }
      </div>
    }
  `,
  styles: [
    `
      .loading {
        display: flex;
        justify-content: center;
        padding: 48px;
      }
      .detail-header {
        display: flex;
        align-items: center;
        gap: 16px;
        margin-bottom: 24px;
        flex-wrap: wrap;
      }
      .header-info {
        flex: 1;
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .header-info h1 {
        margin: 0;
        font-size: 1.5rem;
      }
      .stage-badge {
        color: white;
        padding: 4px 12px;
        border-radius: 12px;
        font-size: 0.8rem;
        font-weight: 500;
      }
      .header-actions {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
      }
      .detail-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
        gap: 16px;
      }
      .full-width {
        grid-column: 1 / -1;
      }
      .info-grid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 16px;
        padding: 16px 0;
      }
      .info-item {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      .label {
        font-size: 0.75rem;
        color: #666;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }
      .value {
        font-size: 0.95rem;
      }
      .relationship-list {
        padding: 16px 0;
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .rel-item {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .link {
        color: #1976d2;
        text-decoration: none;
        font-weight: 500;
      }
      .link:hover {
        text-decoration: underline;
      }
      .no-relations {
        color: #999;
        font-style: italic;
      }
      .lost-card {
        border-left: 4px solid #d32f2f;
      }
    `,
  ],
})
export class DealDetailComponent implements OnInit {
  readonly service = inject(DealService);
  private readonly router = inject(Router);
  id = input.required<string>();

  stageColor(stage: string): string {
    return STAGE_COLORS[stage] ?? '#666';
  }
  stageLabel(stage: string): string {
    return STAGE_LABELS[stage] ?? stage;
  }

  ngOnInit(): void {
    this.service.getById(Number(this.id()));
  }
  goBack(): void {
    this.router.navigate(['/deals']);
  }
  onEdit(): void {
    this.router.navigate(['/deals', this.id(), 'edit']);
  }

  onAdvance(): void {
    this.service.advanceStage(Number(this.id())).subscribe({
      next: (d) => {
        this.service.selectedDeal.set(d);
      },
      error: () => {},
    });
  }
  onWin(): void {
    if (confirm('Mark this deal as Won?')) {
      this.service.markWon(Number(this.id())).subscribe({
        next: (d) => {
          this.service.selectedDeal.set(d);
        },
        error: () => {},
      });
    }
  }
  onLose(): void {
    const reason = prompt('Why was this deal lost?');
    if (reason) {
      this.service.markLost(Number(this.id()), reason).subscribe({
        next: (d) => {
          this.service.selectedDeal.set(d);
        },
        error: () => {},
      });
    }
  }
}
