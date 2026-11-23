import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import {
  CdkDrag,
  CdkDragDrop,
  CdkDropList,
  DragDropModule,
} from '@angular/cdk/drag-drop';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { CurrencyPipe } from '@angular/common';
import { DealService } from '../deal.service';
import { DEFAULT_BASE_CURRENCY, DealDto, DealStage } from '@frontend/shared-models';
import { DealLoseReasonDialogComponent } from '../lose-reason-dialog/lose-reason-dialog.component';
import { dealActionErrorMessage } from '../deal-action-error';
import {
  DEAL_STAGE_ORDER,
  canAdvanceDeal,
  canCloseDeal,
  dealStageColor,
  dealStageLabel,
  isOpenDealStage,
} from '../deal-stage';

interface KanbanColumn {
  stage: DealStage;
  label: string;
  color: string;
  deals: DealDto[];
  /** Sum of base amounts in the board's base currency (ADR-032). */
  totalValue: number;
  /** Deals with an amount but no base amount in that currency. */
  unconverted: number;
}

@Component({
  selector: 'app-deal-kanban',
  standalone: true,
  imports: [
    DragDropModule,
    MatCardModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatMenuModule, CurrencyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="kanban-header">
      <button mat-icon-button (click)="router.navigate(['/deals'])" aria-label="Back to deals list">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <h1>Opportunity pipeline</h1>
      @if (pipelineTotals(); as totals) {
        <div class="pipeline-totals" aria-label="Pipeline summary">
          <span class="total-chip">{{ totals.count }} open</span>
          <span class="total-chip amount">{{ totals.amount | currency:baseCurrency():'symbol':'1.0-0' }}</span>
          <span class="total-chip weighted">{{ totals.weighted | currency:baseCurrency():'symbol':'1.0-0' }} weighted</span>
          @if (totals.unconverted > 0) {
            <span class="total-chip unconverted" data-testid="unconverted-chip">{{ totals.unconverted }} not converted</span>
          }
        </div>
      }
      <span class="spacer"></span>
      <button mat-raised-button color="primary" (click)="router.navigate(['/deals/new'])">
        <mat-icon>add</mat-icon> New opportunity
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

    @if (actionError(); as message) {
      <!-- A refused action is not a load failure: the board stays, and Retry (which reloads) is not offered. -->
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ message }}</span>
        <button mat-button type="button" (click)="actionError.set(null)">Dismiss</button>
      </div>
    }

    @if (service.loading()) {
      <div class="loading" role="status" aria-live="polite"><mat-spinner diameter="40"></mat-spinner></div>
    } @else if (!service.error()) {
      <div class="kanban-board" cdkDropListGroup>
        @for (col of columns(); track col.stage) {
          <div class="kanban-column">
            <div class="column-header" [style.border-top-color]="col.color">
              <span class="column-title">{{ col.label }}</span>
              <span class="column-count">{{ col.deals.length }}</span>
            </div>
            <div class="column-summary">
              {{ col.totalValue | currency:baseCurrency():'symbol':'1.0-0' }}
              @if (col.unconverted > 0) {
                <span class="column-unconverted">+{{ col.unconverted }} not converted</span>
              }
            </div>
            <div
              class="column-cards"
              cdkDropList
              [id]="'kanban-stage-' + col.stage"
              [cdkDropListData]="col.stage"
              [cdkDropListEnterPredicate]="canEnterStage"
              (cdkDropListDropped)="onDrop($event)"
            >
              @for (deal of col.deals; track deal.id) {
                <mat-card
                  class="deal-card"
                  cdkDrag
                  [cdkDragData]="deal"
                  [cdkDragDisabled]="!canClose(deal.stage) || actionLoading()"
                  (click)="router.navigate(['/deals', deal.id])"
                >
                  <div *cdkDragPlaceholder class="deal-card-placeholder" aria-hidden="true"></div>
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
                    <div class="card-amount">{{ deal.amount | currency:(deal.currency || 'USD'):'symbol':'1.0-0' }}</div>
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
    .column-unconverted { margin-left: 6px; color: #8a5a00; }
    .total-chip.unconverted { color: #8a5a00; }
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
    .deal-card-placeholder {
      min-height: 76px;
      border-radius: var(--crm-radius-sm);
      border: 2px dashed var(--crm-color-brand);
      background: var(--crm-color-info-container);
      opacity: 0.65;
    }
    .deal-card.cdk-drag-preview {
      border: 1px solid var(--crm-color-brand);
      border-radius: var(--crm-radius-sm);
      background: var(--crm-color-surface);
    }
    .deal-card.cdk-drag-animating {
      transition: transform 200ms cubic-bezier(0, 0, 0.2, 1);
    }
    .column-cards.cdk-drop-list-dragging .deal-card:not(.cdk-drag-placeholder) {
      transition: transform 200ms cubic-bezier(0, 0, 0.2, 1);
    }
    .column-cards.cdk-drop-list-receiving {
      background: var(--crm-color-track);
      border-radius: 0 0 var(--crm-radius-sm) var(--crm-radius-sm);
    }
  `,
})
export class DealKanbanComponent implements OnInit {
  readonly service = inject(DealService);
  readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly liveAnnouncer = inject(LiveAnnouncer);

  readonly actionLoading = signal(false);
  /** Error from Advance / Won / Lost; kept apart from the service's load error so the board stays visible. */
  readonly actionError = signal<string | null>(null);

  /** Enter predicate shared across all stage columns (stable reference for CDK). */
  readonly canEnterStage = (
    drag: CdkDrag<DealDto>,
    drop: CdkDropList<DealStage>,
  ): boolean => this.canTransition(drag.data, drop.data);

  /**
   * Currency for every total on the board. Prefer the base currency already on the deal,
   * then the pipeline summary, then the catalog default. Never inferred by summing `amount`.
   */
  baseCurrency = computed(() => {
    const fromDeal = this.service.deals().find((deal) => deal.baseCurrency)?.baseCurrency;
    if (fromDeal) {
      return fromDeal;
    }
    const fromSummary = this.service.pipelineSummary()?.find((stage) => stage.baseCurrency)?.baseCurrency;
    return fromSummary ?? DEFAULT_BASE_CURRENCY;
  });

  /** Single-pass O(stages + deals) bucketing; column totals add base amounts only. */
  columns = computed<KanbanColumn[]>(() => {
    const deals = this.service.deals();
    const base = this.baseCurrency();
    const stageMap = new Map<DealStage, { deals: DealDto[]; totalValue: number; unconverted: number }>();
    for (const stage of DEAL_STAGE_ORDER) {
      stageMap.set(stage, { deals: [], totalValue: 0, unconverted: 0 });
    }
    for (const d of deals) {
      const bucket = stageMap.get(d.stage);
      if (bucket) {
        bucket.deals.push(d);
        const contribution = baseAmountContribution(d, base);
        bucket.totalValue += contribution.amount;
        bucket.unconverted += contribution.unconverted;
      }
    }
    return DEAL_STAGE_ORDER.map((stage) => {
      const bucket = stageMap.get(stage) ?? { deals: [], totalValue: 0, unconverted: 0 };
      return {
        stage,
        label: dealStageLabel(stage),
        color: dealStageColor(stage),
        deals: bucket.deals,
        totalValue: bucket.totalValue,
        unconverted: bucket.unconverted,
      };
    });
  });

  /**
   * Open-pipeline header from deal DTO base-currency fields (BC-13-05).
   * `amount` in the deal currency is never added, so mixed currencies cannot be summed.
   */
  pipelineTotals = computed(() => {
    const base = this.baseCurrency();
    const open = this.service.deals().filter((deal) => isOpenDealStage(deal.stage));
    let amount = 0;
    let weighted = 0;
    let unconverted = 0;
    for (const deal of open) {
      const contribution = baseAmountContribution(deal, base);
      amount += contribution.amount;
      weighted += contribution.weighted;
      unconverted += contribution.unconverted;
    }
    return {
      count: open.length,
      amount,
      weighted,
      unconverted,
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
    return canAdvanceDeal(stage);
  }

  canClose(stage: DealStage): boolean {
    return canCloseDeal(stage);
  }

  canTransition(deal: DealDto | undefined, targetStage: DealStage): boolean {
    if (!deal || this.actionLoading()) {
      return false;
    }
    if (deal.stage === targetStage) {
      return true;
    }
    if (targetStage === 'WON' || targetStage === 'LOST') {
      return canCloseDeal(deal.stage);
    }
    if (!canAdvanceDeal(deal.stage)) {
      return false;
    }
    const sourceIndex = DEAL_STAGE_ORDER.indexOf(deal.stage);
    return sourceIndex >= 0 && DEAL_STAGE_ORDER[sourceIndex + 1] === targetStage;
  }

  onDrop(event: CdkDragDrop<DealStage, DealStage, DealDto>): void {
    if (!event.isPointerOverContainer || event.previousContainer === event.container) {
      return;
    }
    const deal = event.item.data;
    const targetStage = event.container.data;
    if (!deal || deal.stage === targetStage || !this.canTransition(deal, targetStage)) {
      return;
    }
    if (targetStage === 'LOST') {
      this.onLose(deal);
      return;
    }
    if (targetStage === 'WON') {
      this.executeWin(deal);
      return;
    }
    this.onAdvance(deal);
  }

  onAdvance(deal: DealDto): void {
    this.actionLoading.set(true);
    this.service.advanceStage(deal.id).subscribe({
      next: (updated) => this.commitStage(updated),
      error: (err) => this.failStage(err, 'Could not advance this opportunity'),
    });
  }

  onWin(deal: DealDto): void {
    if (!confirm(`Mark "${deal.name}" as Won?`)) return;
    this.executeWin(deal);
  }

  private executeWin(deal: DealDto): void {
    this.actionLoading.set(true);
    this.service.markWon(deal.id).subscribe({
      next: (updated) => this.commitStage(updated),
      error: (err) => this.failStage(err, 'Could not mark this opportunity as Won'),
    });
  }

  onLose(deal: DealDto): void {
    const dialogRef = this.dialog.open(DealLoseReasonDialogComponent, { width: '500px' });
    dialogRef.afterClosed().subscribe((reason: string | undefined) => {
      if (!reason) return;
      this.actionLoading.set(true);
      this.service.markLost(deal.id, reason).subscribe({
        next: (updated) => this.commitStage(updated),
        error: (err) => this.failStage(err, 'Could not mark this opportunity as Lost'),
      });
    });
  }

  private commitStage(updated: DealDto): void {
    this.actionError.set(null);
    this.service.applyStageChange(updated);
    this.actionLoading.set(false);
    this.refreshBoard();
    void this.liveAnnouncer.announce(
      `Moved "${updated.name}" to ${dealStageLabel(updated.stage)}`,
      'polite',
    );
  }

  private failStage(err: unknown, fallback: string): void {
    this.actionLoading.set(false);
    const message = dealActionErrorMessage(err, fallback);
    this.actionError.set(message);
    void this.liveAnnouncer.announce(message, 'assertive');
  }

  private refreshBoard(): void {
    this.service.list(0, 200, 'createdAt,desc');
    this.service.loadPipelineSummary();
  }
}

/** Adds base-currency fields only. A transaction `amount` never enters the total. */
function baseAmountContribution(
  deal: DealDto,
  baseCurrency: string,
): { amount: number; weighted: number; unconverted: number } {
  if (deal.baseAmount != null && deal.baseCurrency === baseCurrency) {
    return {
      amount: deal.baseAmount,
      weighted: deal.baseWeightedAmount ?? 0,
      unconverted: 0,
    };
  }
  if (deal.amount != null) {
    return { amount: 0, weighted: 0, unconverted: 1 };
  }
  return { amount: 0, weighted: 0, unconverted: 0 };
}
