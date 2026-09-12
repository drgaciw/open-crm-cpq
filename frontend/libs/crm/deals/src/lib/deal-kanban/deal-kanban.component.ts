import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { CurrencyPipe } from '@angular/common';
import { DealService } from '../deal.service';
import { DealDto, DealStage } from '@frontend/shared-models';
import { DealLoseReasonDialogComponent } from '../lose-reason-dialog/lose-reason-dialog.component';

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
    MatCardModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatMenuModule, CurrencyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="kanban-header">
      <button mat-icon-button (click)="router.navigate(['/deals'])" aria-label="Back to deals list">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <h1>Deal Pipeline</h1>
      @if (pipelineTotals(); as totals) {
        <div class="pipeline-totals" aria-label="Pipeline summary">
          <span class="total-chip">{{ totals.count }} open</span>
          <span class="total-chip amount">{{ totals.amount | currency:'USD':'symbol':'1.0-0' }}</span>
          <span class="total-chip weighted">{{ totals.weighted | currency:'USD':'symbol':'1.0-0' }} weighted</span>
        </div>
      }
      <span class="spacer"></span>
      <button mat-raised-button color="primary" (click)="router.navigate(['/deals/new'])">
        <mat-icon>add</mat-icon> New Deal
      </button>
    </div>

    @if (service.error()) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ service.error() }}</span>
        <button mat-button type="button" (click)="onRetry()">Retry</button>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
      </div>
    }

    @if (service.loading()) {
      <div class="loading" role="status" aria-live="polite"><mat-spinner diameter="40"></mat-spinner></div>
    } @else if (!service.error()) {
      <div class="kanban-board">
        @for (col of columns(); track col.stage) {
          <div class="kanban-column">
            <div class="column-header" [style.border-top-color]="col.color">
              <span class="column-title">{{ col.label }}</span>
              <span class="column-count">{{ col.deals.length }}</span>
            </div>
            <div class="column-summary">
              {{ col.totalValue | currency:'USD':'symbol':'1.0-0' }}
            </div>
            <div class="column-cards">
              @for (deal of col.deals; track deal.id) {
                <mat-card class="deal-card" (click)="router.navigate(['/deals', deal.id])">
                  <div class="card-top">
                    <div class="card-title">{{ deal.name }}</div>
                    @if (canClose(deal.stage)) {
                      <button
                        mat-icon-button
                        class="card-menu-btn"
                        [matMenuTriggerFor]="cardMenu"
                        [disabled]="actionLoading()"
                        (click)="$event.stopPropagation()"
                        aria-label="Deal stage actions"
                      >
                        <mat-icon>more_vert</mat-icon>
                      </button>
                      <mat-menu #cardMenu="matMenu">
                        @if (canAdvance(deal.stage)) {
                          <button mat-menu-item (click)="onAdvance(deal); $event.stopPropagation()" [disabled]="actionLoading()">
                            <mat-icon>arrow_forward</mat-icon><span>Advance Stage</span>
                          </button>
                        }
                        <button mat-menu-item (click)="onWin(deal); $event.stopPropagation()" [disabled]="actionLoading()">
                          <mat-icon>emoji_events</mat-icon><span>Mark Won</span>
                        </button>
                        <button mat-menu-item (click)="onLose(deal); $event.stopPropagation()" [disabled]="actionLoading()">
                          <mat-icon>cancel</mat-icon><span>Mark Lost</span>
                        </button>
                      </mat-menu>
                    }
                  </div>
                  @if (deal.amount) {
                    <div class="card-amount">{{ deal.amount | currency:'USD':'symbol':'1.0-0' }}</div>
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
  styles: `
    .kanban-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; flex-wrap: wrap; }
    .kanban-header h1 { margin: 0; font-size: 1.5rem; }
    .pipeline-totals { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
    .total-chip {
      background: #e3f2fd; color: #1565c0; border-radius: 16px; padding: 4px 12px;
      font-size: 0.8rem; font-weight: 600;
    }
    .total-chip.amount { background: #e8f5e9; color: #2e7d32; }
    .total-chip.weighted { background: #fff3e0; color: #ef6c00; }
    .spacer { flex: 1; }
    .loading { display: flex; justify-content: center; padding: 48px; }
    .error-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 16px;
      padding: 12px 16px;
      border-radius: 8px;
      background: #ffebee;
      color: #c62828;
    }
    .kanban-board { display: flex; gap: 16px; overflow-x: auto; padding-bottom: 16px; min-height: 500px; }
    .kanban-column { flex: 0 0 280px; background: #f5f5f5; border-radius: 8px; display: flex; flex-direction: column; }
    .column-header {
      padding: 12px 16px; display: flex; justify-content: space-between; align-items: center;
      border-top: 3px solid; border-radius: 8px 8px 0 0; background: white;
    }
    .column-title { font-weight: 600; font-size: 0.875rem; }
    .column-count {
      background: #e0e0e0; border-radius: 50%; width: 24px; height: 24px;
      display: flex; align-items: center; justify-content: center; font-size: 0.75rem; font-weight: 600;
    }
    .column-summary { padding: 8px 16px; font-size: 0.8rem; color: #666; background: white; border-bottom: 1px solid #eee; }
    .column-cards { padding: 8px; flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; }
    .deal-card { cursor: pointer; padding: 12px !important; }
    .deal-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.15); }
    .card-top { display: flex; align-items: flex-start; gap: 4px; }
    .card-title { font-weight: 500; margin-bottom: 4px; flex: 1; }
    .card-menu-btn { width: 32px; height: 32px; line-height: 32px; margin: -4px -8px 0 0; }
    .card-amount { color: #2e7d32; font-weight: 600; font-size: 0.9rem; margin-bottom: 8px; }
    .card-contact, .card-org { display: flex; align-items: center; gap: 4px; font-size: 0.8rem; color: #666; }
    .card-icon { font-size: 16px; width: 16px; height: 16px; }
    .empty-column { text-align: center; color: #999; padding: 24px 8px; font-size: 0.85rem; }
  `,
})
export class DealKanbanComponent implements OnInit {
  readonly service = inject(DealService);
  readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  readonly actionLoading = signal(false);

  private readonly stageConfig: { stage: DealStage; label: string; color: string }[] = [
    { stage: 'PROSPECTING', label: 'Prospecting', color: '#2196f3' },
    { stage: 'QUALIFICATION', label: 'Qualification', color: '#ff9800' },
    { stage: 'NEEDS_ANALYSIS', label: 'Needs Analysis', color: '#00bcd4' },
    { stage: 'PROPOSAL', label: 'Proposal', color: '#9c27b0' },
    { stage: 'NEGOTIATION', label: 'Negotiation', color: '#f44336' },
    { stage: 'WON', label: 'Won', color: '#4caf50' },
    { stage: 'LOST', label: 'Lost', color: '#757575' },
  ];

  /** Single-pass O(stages + deals) bucketing (salvage #436); avoids filter-per-stage. */
  columns = computed<KanbanColumn[]>(() => {
    const deals = this.service.deals();
    const stageMap = new Map<DealStage, { deals: DealDto[]; totalValue: number }>();
    for (const cfg of this.stageConfig) {
      stageMap.set(cfg.stage, { deals: [], totalValue: 0 });
    }
    for (const d of deals) {
      const bucket = stageMap.get(d.stage);
      if (bucket) {
        bucket.deals.push(d);
        bucket.totalValue += d.amount ?? 0;
      }
    }
    return this.stageConfig.map((cfg) => {
      const bucket = stageMap.get(cfg.stage)!;
      return {
        ...cfg,
        deals: bucket.deals,
        totalValue: bucket.totalValue,
      };
    });
  });

  /** Open-pipeline totals from GET /deals/pipeline (excludes WON/LOST when API does). */
  pipelineTotals = computed(() => {
    const summary = this.service.pipelineSummary();
    if (!summary?.length) return null;
    const open = summary.filter((s) => s.stage !== 'WON' && s.stage !== 'LOST');
    const source = open.length ? open : summary;
    return {
      count: source.reduce((n, s) => n + (s.count ?? 0), 0),
      amount: source.reduce((n, s) => n + (s.totalAmount ?? 0), 0),
      weighted: source.reduce((n, s) => n + (s.weightedAmount ?? 0), 0),
    };
  });

  ngOnInit(): void {
    this.service.list(0, 200, 'createdAt,desc');
    this.service.loadPipelineSummary();
  }

  clearError(): void {
    this.service.error.set(null);
  }

  onRetry(): void {
    this.refreshBoard();
  }

  canAdvance(stage: DealStage): boolean {
    return stage !== 'WON' && stage !== 'LOST' && stage !== 'NEGOTIATION';
  }

  canClose(stage: DealStage): boolean {
    return stage !== 'WON' && stage !== 'LOST';
  }

  onAdvance(deal: DealDto): void {
    this.actionLoading.set(true);
    this.service.advanceStage(deal.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.refreshBoard();
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onWin(deal: DealDto): void {
    if (!confirm(`Mark "${deal.name}" as Won?`)) return;
    this.actionLoading.set(true);
    this.service.markWon(deal.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.refreshBoard();
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onLose(deal: DealDto): void {
    const dialogRef = this.dialog.open(DealLoseReasonDialogComponent, { width: '500px' });
    dialogRef.afterClosed().subscribe((reason: string | undefined) => {
      if (!reason) return;
      this.actionLoading.set(true);
      this.service.markLost(deal.id, reason).subscribe({
        next: () => {
          this.actionLoading.set(false);
          this.refreshBoard();
        },
        error: () => this.actionLoading.set(false),
      });
    });
  }

  private refreshBoard(): void {
    this.service.list(0, 200, 'createdAt,desc');
    this.service.loadPipelineSummary();
  }
}
