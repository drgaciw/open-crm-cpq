import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import { DealDto, LeadDto, PipelineSummaryDto, DealStage, SpringPageResponse, STAGE_COLORS, STAGE_LABELS, STATUS_COLORS } from '@frontend/shared-models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatTableModule, MatProgressSpinnerModule, CurrencyPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-header">
      <h1>Dashboard</h1>
      <p class="subtitle">Overview of your CRM pipeline</p>
    </div>

    @if (loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
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
            <span class="kpi-value">{{ recentLeads().length }}</span>
            <span class="kpi-label">Recent Leads</span>
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
              <p class="empty-text">No recent deals.</p>
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
              <p class="empty-text">No recent leads.</p>
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
    .dashboard-header { margin-bottom: 24px; }
    .dashboard-header h1 { margin: 0; font-size: 1.75rem; font-weight: 600; }
    .subtitle { margin: 4px 0 0; color: #666; }
    .loading { display: flex; justify-content: center; padding: 48px; }

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
  private readonly router = inject(Router);

  loading = signal(true);
  pipeline = signal<PipelineSummaryDto[]>([]);
  recentDeals = signal<DealDto[]>([]);
  recentLeads = signal<LeadDto[]>([]);

  pipelineStages: DealStage[] = ['PROSPECTING', 'QUALIFICATION', 'NEEDS_ANALYSIS', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST'];

  stageColor(stage: string): string { return STAGE_COLORS[stage] ?? '#666'; }
  stageLabel(stage: string): string { return STAGE_LABELS[stage] ?? stage; }
  statusColor(status: string): string { return STATUS_COLORS[status] ?? '#666'; }

  totalDeals(): number {
    return this.pipeline().reduce((sum, item) => sum + item.count, 0);
  }
  totalValue(): number {
    return this.pipeline().reduce((sum, item) => sum + item.totalAmount, 0);
  }
  wonValue(): number {
    const wonStage = this.pipeline().find((item) => item.stage === 'WON');
    return wonStage?.totalAmount ?? 0;
  }

  stageCount(stage: DealStage): number {
    const stageItem = this.pipeline().find((item) => item.stage === stage);
    return stageItem?.count ?? 0;
  }
  stageValue(stage: DealStage): number {
    const stageItem = this.pipeline().find((item) => item.stage === stage);
    return stageItem?.totalAmount ?? 0;
  }
  barWidth(stage: DealStage): number {
    const maxValue = Math.max(
      ...this.pipeline().map((item) => item.totalAmount),
      1
    );
    return (this.stageValue(stage) / maxValue) * 100;
  }

  trackById(index: number, item: { id: string | number }): string | number {
    return item.id;
  }

  ngOnInit(): void {
    forkJoin({
      pipeline: this.http.get<PipelineSummaryDto[]>('/api/v1/deals/pipeline'),
      deals: this.http.get<SpringPageResponse<DealDto>>('/api/v1/deals', { params: { page: '0', size: '5', sort: 'createdAt,desc' } }),
      leads: this.http.get<SpringPageResponse<LeadDto>>('/api/v1/leads', { params: { page: '0', size: '5', sort: 'createdAt,desc' } }),
    }).subscribe({
      next: ({ pipeline, deals, leads }) => {
        this.pipeline.set(pipeline);
        this.recentDeals.set(deals.content ?? deals);
        this.recentLeads.set(leads.content ?? leads);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
