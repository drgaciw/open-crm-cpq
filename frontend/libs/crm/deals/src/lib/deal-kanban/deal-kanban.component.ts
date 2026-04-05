import {
  Component,
  inject,
  OnInit,
  signal,
  computed,
  ChangeDetectionStrategy,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CurrencyPipe } from '@angular/common';
import { DealService } from '../deal.service';
import { DealDto, DealStage } from '@frontend/shared-models';

interface KanbanColumn {
  stage: DealStage;
  label: string;
  color: string;
  deals: DealDto[];
  totalValue: number;
}

@Component({
  selector: 'app-deal-kanban',
  standalone: true,
  imports: [
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CurrencyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="kanban-header">
      <button mat-icon-button (click)="router.navigate(['/deals'])">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <h1>Deal Pipeline</h1>
      <span class="spacer"></span>
      <button
        mat-raised-button
        color="primary"
        (click)="router.navigate(['/deals/new'])"
      >
        <mat-icon>add</mat-icon> New Deal
      </button>
    </div>

    @if (service.loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
    } @else {
      <div class="kanban-board">
        @for (col of columns(); track col.stage) {
          <div class="kanban-column">
            <div class="column-header" [style.border-top-color]="col.color">
              <span class="column-title">{{ col.label }}</span>
              <span class="column-count">{{ col.deals.length }}</span>
            </div>
            <div class="column-summary">
              {{ col.totalValue | currency: 'USD' : 'symbol' : '1.0-0' }}
            </div>
            <div class="column-cards">
              @for (deal of col.deals; track deal.id) {
                <mat-card
                  class="deal-card"
                  (click)="router.navigate(['/deals', deal.id])"
                >
                  <div class="card-title">{{ deal.name }}</div>
                  @if (deal.amount) {
                    <div class="card-amount">
                      {{ deal.amount | currency: 'USD' : 'symbol' : '1.0-0' }}
                    </div>
                  }
                  @if (deal.primaryContactName) {
                    <div class="card-contact">
                      <mat-icon class="card-icon">person</mat-icon>
                      {{ deal.primaryContactName }}
                    </div>
                  }
                  @if (deal.organizationName) {
                    <div class="card-org">
                      <mat-icon class="card-icon">business</mat-icon>
                      {{ deal.organizationName }}
                    </div>
                  }
                </mat-card>
              }
              @if (col.deals.length === 0) {
                <div class="empty-column">No deals</div>
              }
            </div>
          </div>
        }
      </div>
    }
  `,
  styles: [
    `
      .kanban-header {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 24px;
      }
      .kanban-header h1 {
        margin: 0;
        font-size: 1.5rem;
      }
      .spacer {
        flex: 1;
      }
      .loading {
        display: flex;
        justify-content: center;
        padding: 48px;
      }
      .kanban-board {
        display: flex;
        gap: 16px;
        overflow-x: auto;
        padding-bottom: 16px;
        min-height: 500px;
      }
      .kanban-column {
        flex: 0 0 280px;
        background: #f5f5f5;
        border-radius: 8px;
        display: flex;
        flex-direction: column;
      }
      .column-header {
        padding: 12px 16px;
        display: flex;
        justify-content: space-between;
        align-items: center;
        border-top: 3px solid;
        border-radius: 8px 8px 0 0;
        background: white;
      }
      .column-title {
        font-weight: 600;
        font-size: 0.875rem;
      }
      .column-count {
        background: #e0e0e0;
        border-radius: 50%;
        width: 24px;
        height: 24px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 0.75rem;
        font-weight: 600;
      }
      .column-summary {
        padding: 8px 16px;
        font-size: 0.8rem;
        color: #666;
        background: white;
        border-bottom: 1px solid #eee;
      }
      .column-cards {
        padding: 8px;
        flex: 1;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .deal-card {
        cursor: pointer;
        padding: 12px !important;
      }
      .deal-card:hover {
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
      }
      .card-title {
        font-weight: 500;
        margin-bottom: 4px;
      }
      .card-amount {
        color: #2e7d32;
        font-weight: 600;
        font-size: 0.9rem;
        margin-bottom: 8px;
      }
      .card-contact,
      .card-org {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 0.8rem;
        color: #666;
      }
      .card-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
      }
      .empty-column {
        text-align: center;
        color: #999;
        padding: 24px 8px;
        font-size: 0.85rem;
      }
    `,
  ],
})
export class DealKanbanComponent implements OnInit {
  readonly service = inject(DealService);
  readonly router = inject(Router);

  private readonly stageConfig: {
    stage: DealStage;
    label: string;
    color: string;
  }[] = [
    { stage: 'PROSPECTING', label: 'Prospecting', color: '#2196f3' },
    { stage: 'QUALIFICATION', label: 'Qualification', color: '#ff9800' },
    { stage: 'NEEDS_ANALYSIS', label: 'Needs Analysis', color: '#00bcd4' },
    { stage: 'PROPOSAL', label: 'Proposal', color: '#9c27b0' },
    { stage: 'NEGOTIATION', label: 'Negotiation', color: '#f44336' },
    { stage: 'WON', label: 'Won', color: '#4caf50' },
    { stage: 'LOST', label: 'Lost', color: '#757575' },
  ];

  columns = computed<KanbanColumn[]>(() => {
    const deals = this.service.deals();
    return this.stageConfig.map((cfg) => {
      const stageDeals = deals.filter((d) => d.stage === cfg.stage);
      return {
        ...cfg,
        deals: stageDeals,
        totalValue: stageDeals.reduce((sum, d) => sum + (d.amount ?? 0), 0),
      };
    });
  });

  ngOnInit(): void {
    // Load all deals (large page) for kanban view
    this.service.list(0, 200, 'createdAt,desc');
  }
}
