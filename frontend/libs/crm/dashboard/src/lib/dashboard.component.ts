import { ChangeDetectionStrategy, Component, inject, OnInit, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import { DealDto, LeadDto, PipelineSummaryDto, DealStage, SpringPageResponse, STAGE_COLORS, STAGE_LABELS, STATUS_COLORS } from '@frontend/shared-models';
import { LeadService } from '@frontend/crm-leads';
import { PageHeaderComponent } from '@frontend/shared-ui';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    CurrencyPipe,
    DatePipe,
    PageHeaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Dashboard"
      subtitle="Overview of your CRM pipeline"
    />

    @if (loading()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    } @else if (loadError()) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ loadError() }}</span>
        <button mat-button type="button" (click)="retry()">Retry</button>
      </div>
    } @else {
      <!-- KPI Cards -->
      <div class="kpi-grid">
        <mat-card class="kpi-card">
          <mat-icon class="kpi-icon" style="color:#2196f3">handshake</mat-icon>
          <div class="kpi-info">
            <span class="kpi-value">{{ totalDeals() }}</span>
            <span class="kpi-label">Total Deals</span>
          </div>
        </mat-card>
        <mat-card class="kpi-card">
          <mat-icon class="kpi-icon" style="color:#4caf50">attach_money</mat-icon>
          <div class="kpi-info">
            <span class="kpi-value">{{ totalValue() | currency:'USD':'symbol':'1.0-0' }}</span>
            <span class="kpi-label">Pipeline Value</span>
          </div>
        </mat-card>
        <mat-card class="kpi-card">
          <mat-icon class="kpi-icon" style="color:#ff9800">leaderboard</mat-icon>
          <div class="kpi-info">
            <span class="kpi-value">{{ totalLeadCount() }}</span>
            <span class="kpi-label">Total Leads</span>
          </div>
        </mat-card>
        <mat-card class="kpi-card">
          <mat-icon class="kpi-icon" style="color:#9c27b0">emoji_events</mat-icon>
          <div class="kpi-info">
            <span class="kpi-value">{{ wonValue() | currency:'USD':'symbol':'1.0-0' }}</span>
            <span class="kpi-label">Won Value</span>
          </div>
        </mat-card>
      </div>

      <!-- Pipeline Breakdown -->
      @if (pipeline().length > 0) {
        <mat-card class="pipeline-card">
          <mat-card-header><mat-card-title>Pipeline Breakdown</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="pipeline-bars">
              @for (stage of pipelineStages; track stage) {
                <div class="pipeline-row">
                  <span class="stage-name">{{ stageLabel(stage) }}</span>
                  <div class="bar-container">
                    <div class="bar" [style.width.%]="barWidth(stage)" [style.background]="stageColor(stage)"></div>
                  </div>
                  <span class="stage-stats">
                    {{ stageCount(stage) }} deals &middot; {{ stageValue(stage) | currency:'USD':'symbol':'1.0-0' }}
                  </span>
                </div>
              }
            </div>
          </mat-card-content>
        </mat-card>
      }

      <!-- Recent Activity -->
      <div class="activity-grid">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Recent Deals</mat-card-title>
            <span class="card-spacer"></span>
            <a mat-button [routerLink]="['/deals']">View All</a>
          </mat-card-header>
          <mat-card-content>
            @if (recentDeals().length === 0) {
              <p class="empty-text" role="status">No recent deals.</p>
            } @else {
              <table mat-table [dataSource]="recentDeals()" [trackBy]="trackById" class="compact-table">
                <ng-container matColumnDef="name">
                  <th mat-header-cell *matHeaderCellDef>Name</th>
                  <td mat-cell *matCellDef="let d"><a [routerLink]="['/deals', d.id]" class="link">{{ d.name }}</a></td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <th mat-header-cell *matHeaderCellDef>Amount</th>
                  <td mat-cell *matCellDef="let d">{{ d.amount | currency:'USD':'symbol':'1.0-0' }}</td>
                </ng-container>
                <ng-container matColumnDef="stage">
                  <th mat-header-cell *matHeaderCellDef>Stage</th>
                  <td mat-cell *matCellDef="let d">
                    <span class="stage-badge" [style.background]="stageColor(d.stage)">{{ stageLabel(d.stage) }}</span>
                  </td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="['name','amount','stage']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['name','amount','stage']"></tr>
              </table>
            }
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header>
            <mat-card-title>Recent Leads</mat-card-title>
            <span class="card-spacer"></span>
            <a mat-button [routerLink]="['/leads']">View All</a>
          </mat-card-header>
          <mat-card-content>
            @if (recentLeads().length === 0) {
              <p class="empty-text" role="status">No recent leads.</p>
            } @else {
              <table mat-table [dataSource]="recentLeads()" [trackBy]="trackById" class="compact-table">
                <ng-container matColumnDef="title">
                  <th mat-header-cell *matHeaderCellDef>Title</th>
                  <td mat-cell *matCellDef="let l"><a [routerLink]="['/leads', l.id]" class="link">{{ l.title }}</a></td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Status</th>
                  <td mat-cell *matCellDef="let l">
                    <span class="stage-badge" [style.background]="statusColor(l.status)">{{ l.status }}</span>
                  </td>
                </ng-container>
                <ng-container matColumnDef="createdAt">
                  <th mat-header-cell *matHeaderCellDef>Created</th>
                  <td mat-cell *matCellDef="let l">{{ l.createdAt | date:'shortDate' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="['title','status','createdAt']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['title','status','createdAt']"></tr>
              </table>
            }
          </mat-card-content>
        </mat-card>
      </div>
    }
  `,
  styles: [`
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

    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .kpi-card { display: flex; align-items: center; gap: 16px; padding: 20px !important; }
    .kpi-icon { font-size: 40px; width: 40px; height: 40px; }
    .kpi-info { display: flex; flex-direction: column; }
    .kpi-value { font-size: 1.5rem; font-weight: 700; }
    .kpi-label { font-size: 0.8rem; color: #666; }

    .pipeline-card { margin-bottom: 24px; }
    .pipeline-bars { display: flex; flex-direction: column; gap: 12px; padding: 16px 0; }
    .pipeline-row { display: flex; align-items: center; gap: 12px; }
    .stage-name { width: 100px; font-size: 0.85rem; font-weight: 500; }
    .bar-container { flex: 1; height: 24px; background: #f0f0f0; border-radius: 12px; overflow: hidden; }
    .bar { height: 100%; border-radius: 12px; transition: width 0.3s ease; min-width: 2px; }
    .stage-stats { width: 180px; font-size: 0.8rem; color: #666; text-align: right; }

    .activity-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); gap: 16px; }
    .card-spacer { flex: 1; }
    .compact-table { width: 100%; }
    .stage-badge { color: white; padding: 2px 8px; border-radius: 10px; font-size: 0.7rem; font-weight: 500; }
    .link { color: #1976d2; text-decoration: none; font-weight: 500; }
    .link:hover { text-decoration: underline; }
    .empty-text { color: #999; text-align: center; padding: 24px; }
  `],
})
export class DashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly leadService = inject(LeadService);

  loading = signal(true);
  loadError = signal<string | null>(null);
  pipeline = signal<PipelineSummaryDto[]>([]);
  recentDeals = signal<DealDto[]>([]);
  recentLeads = signal<LeadDto[]>([]);
  /** Sum of per-status counts from GET /api/v1/leads/stats */
  totalLeadCount = signal(0);

  pipelineStages: DealStage[] = ['PROSPECTING', 'QUALIFICATION', 'NEEDS_ANALYSIS', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'];

  stageColor(stage: string): string { return STAGE_COLORS[stage] ?? '#666'; }
  stageLabel(stage: string): string { return STAGE_LABELS[stage] ?? stage; }
  statusColor(status: string): string { return STATUS_COLORS[status] ?? '#666'; }

  trackById(index: number, item: { id?: string | number }): string | number {
    return item?.id ?? index;
  }

  // ⚡ Bolt: Use computed signals to memoize expensive array operations instead of re-evaluating on every change detection cycle
  totalDeals = computed(() => this.pipeline().reduce((sum, item) => sum + item.count, 0));
  totalValue = computed(() => this.pipeline().reduce((sum, item) => sum + item.totalAmount, 0));

  // ⚡ Bolt: Convert O(N) array search into O(1) map lookup for frequent stage lookups
  pipelineMap = computed(() => {
    const map = new Map<string, PipelineSummaryDto>();
    for (const item of this.pipeline()) {
      map.set(item.stage, item);
    }
    return map;
  });

  wonValue = computed(() => this.pipelineMap().get('WON')?.totalAmount ?? 0);

  maxPipelineValue = computed(() => Math.max(...this.pipeline().map((item) => item.totalAmount), 1));

  stageCount(stage: DealStage): number {
    return this.pipelineMap().get(stage)?.count ?? 0;
  }
  stageValue(stage: DealStage): number {
    return this.pipelineMap().get(stage)?.totalAmount ?? 0;
  }

  barWidth(stage: DealStage): number {
    return (this.stageValue(stage) / this.maxPipelineValue()) * 100;
  }

  ngOnInit(): void {
    this.loadDashboard();
  }

  retry(): void {
    this.loadDashboard();
  }

  private loadDashboard(): void {
    this.loading.set(true);
    this.loadError.set(null);

    forkJoin({
      pipeline: this.http.get<PipelineSummaryDto[]>('/api/v1/deals/pipeline'),
      deals: this.http.get<SpringPageResponse<DealDto>>('/api/v1/deals', { params: { page: '0', size: '5', sort: 'createdAt,desc' } }),
      leads: this.http.get<SpringPageResponse<LeadDto>>('/api/v1/leads', { params: { page: '0', size: '5', sort: 'createdAt,desc' } }),
      leadStats: this.leadService.getStatusCounts(),
    }).subscribe({
      next: ({ pipeline, deals, leads, leadStats }) => {
        this.pipeline.set(pipeline);
        this.recentDeals.set(deals.content ?? deals);
        this.recentLeads.set(leads.content ?? leads);
        const sum = Object.values(leadStats).reduce((a, n) => a + (typeof n === 'number' ? n : 0), 0);
        this.totalLeadCount.set(sum);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string }; message?: string }) => {
        this.loadError.set(
          err?.error?.message || err?.message || 'Failed to load dashboard data.',
        );
        this.loading.set(false);
      },
    });
  }
}
