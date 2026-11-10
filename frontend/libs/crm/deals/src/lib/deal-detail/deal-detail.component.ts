import { Component, inject, OnInit, input, signal, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatDialog } from '@angular/material/dialog';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { DealDto, DealStage } from '@frontend/shared-models';
import { DealService } from '../deal.service';
import { DealLoseReasonDialogComponent } from '../lose-reason-dialog/lose-reason-dialog.component';
import { dealActionErrorMessage } from '../deal-action-error';
import { canAdvanceDeal, canCloseDeal, dealStageColor, dealStageLabel } from '../deal-stage';

@Component({
  selector: 'app-deal-detail',
  standalone: true,
  imports: [
    RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatChipsModule,
    MatProgressSpinnerModule, MatDividerModule, CurrencyPipe, DatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
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
    } @else if (service.selectedDeal(); as deal) {
      <div class="detail-header">
        <button mat-icon-button (click)="goBack()" aria-label="Go back to deals"><mat-icon>arrow_back</mat-icon></button>
        <div class="header-info">
          <h1>{{ deal.name }}</h1>
          <span class="stage-badge" [style.background]="stageColor(deal.stage)">
            {{ stageLabel(deal.stage) }}
          </span>
        </div>
        <div class="header-actions">
          <button mat-stroked-button (click)="onEdit()" [disabled]="actionLoading()">
            <mat-icon>edit</mat-icon> Edit
          </button>
          @if (canAdvance(deal.stage)) {
            <button mat-raised-button color="primary" (click)="onAdvance()" [disabled]="actionLoading()">
              @if (actionLoading()) {
                <mat-spinner diameter="18" class="btn-spinner"></mat-spinner>
              } @else {
                <mat-icon>arrow_forward</mat-icon>
              }
              Advance Stage
            </button>
          }
          @if (canClose(deal.stage)) {
            <button mat-raised-button class="win-btn" (click)="onWin()" [disabled]="actionLoading()">
              <mat-icon>emoji_events</mat-icon> Won
            </button>
            <button mat-stroked-button color="warn" (click)="onLose()" [disabled]="actionLoading()">
              <mat-icon>cancel</mat-icon> Lost
            </button>
          }
        </div>
      </div>

      <div class="detail-grid">
        <mat-card>
          <mat-card-header><mat-card-title>Opportunity</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item"><span class="label">Amount</span><span class="value">{{ deal.amount !== null && deal.amount !== undefined ? (deal.amount | currency:(deal.currency || 'USD'):'symbol':'1.0-0') : '—' }}</span></div>
              <div class="info-item" data-testid="deal-currency"><span class="label">Currency</span><span class="value">{{ deal.currency || '—' }}</span></div>
              <div class="info-item" data-testid="deal-stage"><span class="label">Stage</span><span class="value">{{ stageLabel(deal.stage) }}</span></div>
              <div class="info-item" data-testid="deal-owner"><span class="label">Owner</span><span class="value">{{ deal.ownerId || '—' }}</span></div>
              @if (deal.amount && deal.baseCurrency && deal.baseCurrency !== deal.currency) {
                <div class="info-item" data-testid="base-amount"><span class="label">Amount ({{ deal.baseCurrency }})</span><span class="value">{{ deal.baseAmount | currency:deal.baseCurrency:'symbol':'1.0-0' }} at {{ deal.fxRate }} ({{ deal.fxSource }}, {{ deal.fxRateAsOf | date:'mediumDate' }})</span></div>
              } @else if (deal.amount && !deal.baseCurrency) {
                <div class="info-item" data-testid="base-amount-missing"><span class="label">Base amount</span><span class="value">Not converted: no FX rate for {{ deal.currency }}</span></div>
              }
              <div class="info-item"><span class="label">Probability</span><span class="value">{{ deal.probability ? deal.probability + '%' : '—' }}</span></div>
              <div class="info-item"><span class="label">Expected Close</span><span class="value">{{ deal.expectedCloseDate ? (deal.expectedCloseDate | date:'mediumDate') : '—' }}</span></div>
              <div class="info-item" data-testid="deal-term"><span class="label">Term</span><span class="value">{{ deal.termMonths ? deal.termMonths + (deal.termMonths === 1 ? ' month' : ' months') : '—' }}</span></div>
              <div class="info-item"><span class="label">Created</span><span class="value">{{ deal.createdAt | date:'mediumDate' }}</span></div>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header><mat-card-title>Relationships</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="relationship-list">
              @if (deal.primaryContactId) {
                <div class="rel-item">
                  <mat-icon>person</mat-icon>
                  <a [routerLink]="['/contacts', deal.primaryContactId]" class="link">
                    {{ deal.primaryContactName }}
                  </a>
                </div>
              }
              @if (deal.organizationId) {
                <div class="rel-item" data-testid="deal-organization">
                  <mat-icon>business</mat-icon>
                  <a [routerLink]="['/organizations', deal.organizationId]" class="link">
                    {{ deal.organizationName || ('Organization ' + deal.organizationId) }}
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
            <mat-card-header><mat-card-title>Lost Reason</mat-card-title></mat-card-header>
            <mat-card-content><p>{{ deal.lostReason }}</p></mat-card-content>
          </mat-card>
        }

        @if (deal.description) {
          <mat-card class="full-width">
            <mat-card-header><mat-card-title>Description</mat-card-title></mat-card-header>
            <mat-card-content><p>{{ deal.description }}</p></mat-card-content>
          </mat-card>
        }
      </div>
    }
  `,
  styles: `
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
    .detail-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
    .header-info { flex: 1; display: flex; align-items: center; gap: 12px; }
    .header-info h1 { margin: 0; font-size: 1.5rem; }
    .stage-badge { color: white; padding: 4px 12px; border-radius: 12px; font-size: 0.8rem; font-weight: 500; }
    .header-actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
    .win-btn { background: #4caf50; color: white; }
    .btn-spinner { display: inline-block; margin-right: 8px; }
    .detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(380px, 1fr)); gap: 16px; }
    .full-width { grid-column: 1 / -1; }
    .info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; padding: 16px 0; }
    .info-item { display: flex; flex-direction: column; gap: 4px; }
    .label { font-size: 0.75rem; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }
    .value { font-size: 0.95rem; }
    .relationship-list { padding: 16px 0; display: flex; flex-direction: column; gap: 12px; }
    .rel-item { display: flex; align-items: center; gap: 8px; }
    .link { color: #1976d2; text-decoration: none; font-weight: 500; }
    .link:hover { text-decoration: underline; }
    .no-relations { color: #999; font-style: italic; }
    .lost-card { border-left: 4px solid #d32f2f; }
  `,
})
export class DealDetailComponent implements OnInit {
  readonly service = inject(DealService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  id = input.required<string>();
  readonly actionLoading = signal(false);

  stageColor(stage: DealStage): string {
    return dealStageColor(stage);
  }

  stageLabel(stage: DealStage): string {
    return dealStageLabel(stage);
  }

  canAdvance(stage: DealStage): boolean {
    return canAdvanceDeal(stage);
  }

  canClose(stage: DealStage): boolean {
    return canCloseDeal(stage);
  }

  ngOnInit(): void {
    this.service.getById(Number(this.id()));
  }

  clearError(): void {
    this.service.error.set(null);
  }

  onRetry(): void {
    this.service.getById(Number(this.id()));
  }

  goBack(): void {
    this.router.navigate(['/deals']);
  }

  onEdit(): void {
    this.router.navigate(['/deals', this.id(), 'edit']);
  }

  onAdvance(): void {
    this.actionLoading.set(true);
    this.service.advanceStage(Number(this.id())).subscribe({
      next: (updated) => this.commitStage(updated),
      error: (err) => this.failStage(err, 'Could not advance this opportunity'),
    });
  }

  onWin(): void {
    if (!confirm('Mark this deal as Won?')) return;
    this.actionLoading.set(true);
    this.service.markWon(Number(this.id())).subscribe({
      next: (updated) => this.commitStage(updated),
      error: (err) => this.failStage(err, 'Could not mark this opportunity as Won'),
    });
  }

  onLose(): void {
    const dialogRef = this.dialog.open(DealLoseReasonDialogComponent, { width: '500px' });
    dialogRef.afterClosed().subscribe((reason: string | undefined) => {
      if (!reason) return;
      this.actionLoading.set(true);
      this.service.markLost(Number(this.id()), reason).subscribe({
        next: (updated) => this.commitStage(updated),
        error: (err) => this.failStage(err, 'Could not mark this opportunity as Lost'),
      });
    });
  }

  private commitStage(updated: DealDto): void {
    const current = this.service.selectedDeal();
    const merged = current && current.id === updated.id ? { ...current, ...updated } : updated;
    this.service.applyStageChange(merged);
    this.service.selectedDeal.set(merged);
    this.actionLoading.set(false);
  }

  private failStage(err: unknown, fallback: string): void {
    this.actionLoading.set(false);
    this.service.error.set(dealActionErrorMessage(err, fallback));
  }
}
