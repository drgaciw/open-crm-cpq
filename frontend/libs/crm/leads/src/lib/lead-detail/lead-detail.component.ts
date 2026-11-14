import { ChangeDetectionStrategy, Component, inject, OnInit, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { LeadService } from '../lead.service';
import { JevSuggestion, LeadStatus } from '@frontend/shared-models';
import {
  AdvisorySuggestionComponent,
  AgentPanelComponent,
  AgentPanelState,
} from '@frontend/shared-ui';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LoseReasonDialogComponent } from './lose-reason-dialog.component';

@Component({
  selector: 'lib-lead-detail',
  standalone: true,
  imports: [
    RouterLink, MatCardModule, MatButtonModule, MatIconModule,
    MatChipsModule, MatProgressSpinnerModule, MatSnackBarModule, CurrencyPipe, DatePipe,
    AdvisorySuggestionComponent, AgentPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (service.loading()) {
      <div class="loading" role="status" aria-live="polite"><mat-spinner diameter="40"></mat-spinner></div>
    } @else if (service.error(); as err) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ err }}</span>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
        <button mat-button type="button" (click)="retry()">Retry</button>
      </div>
    } @else if (service.selectedLead(); as lead) {
      <div class="detail-header">
        <button mat-icon-button (click)="goBack()" aria-label="Go back to leads"><mat-icon>arrow_back</mat-icon></button>
        <div class="header-info">
          <h1>{{ lead.title }}</h1>
          <mat-chip [class]="getStatusClass(lead.status)">{{ lead.status }}</mat-chip>
        </div>
        <div class="header-actions">
          @if (canEdit(lead.status)) {
            <button mat-stroked-button (click)="onEdit()">
              <mat-icon>edit</mat-icon> Edit
            </button>
          }
          @if (canDelete(lead.status)) {
            <button mat-stroked-button color="warn" (click)="onDelete()">
              <mat-icon>delete</mat-icon> Delete
            </button>
          }
        </div>
      </div>

      <app-agent-panel
        title="Lead scoring"
        [state]="agentPanelState(lead.scoreSuggestion)"
        emptyMessage="No score suggestion yet. When Jev is flag-off or still computing, this panel stays empty and labelled — never a silent failure."
        [evidence]="scoreEvidence(lead.scoreSuggestion)"
        [showActions]="!!lead.scoreSuggestion"
        (dismiss)="onAdvisoryDismiss()"
        (acknowledge)="onAdvisoryAcknowledge()"
      >
        <app-advisory-suggestion
          [suggestion]="lead.scoreSuggestion"
          heading="Likelihood to qualify (0–100)"
          [labels]="scoreLevelLabels" />
      </app-agent-panel>

      <div class="action-bar">
        @if (lead.status === 'NEW') {
          <button mat-raised-button color="primary" (click)="onMarkContacted()" [disabled]="actionLoading()">
            @if (actionLoading()) {
              <mat-spinner diameter="20"></mat-spinner>
            } @else {
              <mat-icon>phone</mat-icon>
            }
            Mark Contacted
          </button>
          <button mat-stroked-button color="warn" (click)="onMarkLost()" [disabled]="actionLoading()">
            <mat-icon>close</mat-icon> Mark Lost
          </button>
        }
        @if (lead.status === 'CONTACTED') {
          <button mat-raised-button color="primary" (click)="onQualify()" [disabled]="actionLoading()">
            @if (actionLoading()) {
              <mat-spinner diameter="20"></mat-spinner>
            } @else {
              <mat-icon>check_circle</mat-icon>
            }
            Qualify
          </button>
          <button mat-stroked-button color="warn" (click)="onMarkLost()" [disabled]="actionLoading()">
            <mat-icon>close</mat-icon> Mark Lost
          </button>
        }
        @if (lead.status === 'QUALIFIED') {
          <button mat-raised-button color="primary" (click)="onConvertToDeal()" [disabled]="actionLoading()">
            @if (actionLoading()) {
              <mat-spinner diameter="20"></mat-spinner>
            } @else {
              <mat-icon>arrow_forward</mat-icon>
            }
            Convert to Deal
          </button>
          <button mat-stroked-button color="warn" (click)="onMarkLost()" [disabled]="actionLoading()">
            <mat-icon>close</mat-icon> Mark Lost
          </button>
        }
        @if (lead.status === 'CONVERTED') {
          <div class="info-banner converted">
            <mat-icon>check_circle</mat-icon>
            <span>This lead has been converted to a deal</span>
            @if (lead.convertedDealId) {
              <a [routerLink]="['/deals', lead.convertedDealId]" class="deal-link">
                <mat-icon>open_in_new</mat-icon> View Deal
              </a>
            }
          </div>
        }
        @if (lead.status === 'LOST') {
          <div class="info-banner lost">
            <mat-icon>cancel</mat-icon>
            <span>This lead was marked as lost</span>
          </div>
        }
      </div>

      <div class="detail-grid">
        <mat-card>
          <mat-card-header><mat-card-title>Lead Information</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Source</span>
                <span class="value">{{ lead.source || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Estimated Value</span>
                <span class="value">{{ lead.estimatedValue ? (lead.estimatedValue | currency) : '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Created</span>
                <span class="value">{{ lead.createdAt | date: 'medium' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Updated</span>
                <span class="value">{{ lead.updatedAt | date: 'medium' }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        @if (lead.contactId && lead.contactName) {
          <mat-card>
            <mat-card-header><mat-card-title>Contact</mat-card-title></mat-card-header>
            <mat-card-content>
              <div class="contact-link">
                <mat-icon>person</mat-icon>
                <a [routerLink]="['/contacts', lead.contactId]" class="link">
                  {{ lead.contactName }}
                </a>
              </div>
            </mat-card-content>
          </mat-card>
        }

        @if (lead.organizationId && lead.organizationName) {
          <mat-card>
            <mat-card-header><mat-card-title>Organization</mat-card-title></mat-card-header>
            <mat-card-content>
              <div class="org-link">
                <mat-icon>business</mat-icon>
                <a [routerLink]="['/organizations', lead.organizationId]" class="link">
                  {{ lead.organizationName }}
                </a>
              </div>
            </mat-card-content>
          </mat-card>
        }

        @if (lead.description) {
          <mat-card class="full-width">
            <mat-card-header><mat-card-title>Description</mat-card-title></mat-card-header>
            <mat-card-content><p>{{ lead.description }}</p></mat-card-content>
          </mat-card>
        }

        @if (lead.lostReason) {
          <mat-card class="full-width lost-reason-card">
            <mat-card-header><mat-card-title>Lost Reason</mat-card-title></mat-card-header>
            <mat-card-content><p>{{ lead.lostReason }}</p></mat-card-content>
          </mat-card>
        }
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
    .detail-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
    .header-info { flex: 1; display: flex; align-items: center; gap: 12px; }
    .header-info h1 { margin: 0; font-size: 1.5rem; }
    .header-actions { display: flex; gap: 8px; }
    .action-bar { margin-bottom: 24px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
    .detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(350px, 1fr)); gap: 16px; }
    .full-width { grid-column: 1 / -1; }
    .info-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; padding: 16px 0; }
    .info-item { display: flex; flex-direction: column; gap: 4px; }
    .label { font-size: 0.75rem; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }
    .value { font-size: 0.95rem; }
    .contact-link, .org-link { display: flex; align-items: center; gap: 8px; padding: 16px 0; }
    .contact-detail { display: flex; align-items: center; gap: 8px; padding: 8px 0; color: #666; font-size: 0.9rem; }
    .link { color: #1976d2; text-decoration: none; font-weight: 500; }
    .link:hover { text-decoration: underline; }

    mat-chip { font-size: 0.75rem; font-weight: 500; min-height: 24px; padding: 4px 8px; }
    .status-new { background-color: #2196f3 !important; color: white !important; }
    .status-contacted { background-color: #ff9800 !important; color: white !important; }
    .status-qualified { background-color: #4caf50 !important; color: white !important; }
    .status-converted { background-color: #9c27b0 !important; color: white !important; }
    .status-lost { background-color: #f44336 !important; color: white !important; }

    .info-banner { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-radius: 8px; font-weight: 500; }
    .info-banner.converted { background-color: #e8f5e9; color: #2e7d32; }
    .info-banner.lost { background-color: #ffebee; color: #c62828; }
    .deal-link { display: flex; align-items: center; gap: 4px; margin-left: auto; color: inherit; text-decoration: none; }
    .deal-link:hover { text-decoration: underline; }
    .lost-reason-card { border-left: 4px solid #f44336; }
  `],
})
export class LeadDetailComponent implements OnInit {
  /** Rubric lead-score-v1: probability keys are rubric levels, lowest first. */
  readonly scoreLevelLabels: Record<string, string> = {
    '0': 'Very unlikely', '1': 'Unlikely', '2': 'Uncertain', '3': 'Likely', '4': 'Very likely',
  };

  readonly service = inject(LeadService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  id = input.required<string>();
  actionLoading = signal(false);

  ngOnInit(): void {
    this.service.getById(Number(this.id()));
  }

  agentPanelState(suggestion: JevSuggestion | null | undefined): AgentPanelState {
    return suggestion ? 'ready' : 'empty';
  }

  scoreEvidence(suggestion: JevSuggestion | null | undefined): string[] {
    if (!suggestion) {
      return [];
    }
    return [
      `Model ${suggestion.model}`,
      `Rubric ${suggestion.rubricVersion}`,
      'Advisory only — does not change lead status or score authority',
    ];
  }

  onAdvisoryDismiss(): void {
    this.snackBar.open('Advisory dismissed (no record change)', undefined, { duration: 2500 });
  }

  onAdvisoryAcknowledge(): void {
    this.snackBar.open('Advisory acknowledged (not a decision)', undefined, { duration: 2500 });
  }

  clearError(): void {
    this.service.error.set(null);
  }

  retry(): void {
    this.service.getById(Number(this.id()));
  }

  goBack(): void {
    this.router.navigate(['/leads']);
  }

  onEdit(): void {
    this.router.navigate(['/leads', this.id(), 'edit']);
  }

  onDelete(): void {
    const lead = this.service.selectedLead();
    if (lead && confirm(`Delete lead "${lead.title}"?`)) {
      this.service.delete(lead.id).subscribe(() => this.router.navigate(['/leads']));
    }
  }

  onMarkContacted(): void {
    const lead = this.service.selectedLead();
    if (!lead) return;

    this.actionLoading.set(true);
    this.service.markContacted(lead.id).subscribe({
      next: (updated) => {
        this.service.selectedLead.set(updated);
        this.actionLoading.set(false);
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onQualify(): void {
    const lead = this.service.selectedLead();
    if (!lead) return;

    this.actionLoading.set(true);
    this.service.qualify(lead.id).subscribe({
      next: (updated) => {
        this.service.selectedLead.set(updated);
        this.actionLoading.set(false);
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onConvertToDeal(): void {
    const lead = this.service.selectedLead();
    if (!lead) return;

    if (!confirm(`Convert "${lead.title}" to a deal?`)) return;

    this.actionLoading.set(true);
    this.service.convertToDeal(lead.id).subscribe({
      next: (convertedLead) => {
        this.actionLoading.set(false);
        this.service.selectedLead.set(convertedLead);
        const dealId = convertedLead.convertedDealId;
        if (dealId != null) {
          this.router.navigate(['/deals', dealId]);
        }
      },
      error: () => this.actionLoading.set(false),
    });
  }

  onMarkLost(): void {
    const lead = this.service.selectedLead();
    if (!lead) return;

    const dialogRef = this.dialog.open(LoseReasonDialogComponent, {
      width: '500px',
    });

    dialogRef.afterClosed().subscribe((reason: string) => {
      if (reason && lead) {
        this.actionLoading.set(true);
        this.service.markLost(lead.id, reason).subscribe({
          next: (updated) => {
            this.service.selectedLead.set(updated);
            this.actionLoading.set(false);
          },
          error: () => this.actionLoading.set(false),
        });
      }
    });
  }

  canEdit(status: LeadStatus): boolean {
    return status !== 'CONVERTED' && status !== 'LOST';
  }

  canDelete(status: LeadStatus): boolean {
    return status !== 'CONVERTED';
  }

  getStatusClass(status: LeadStatus): string {
    return `status-${status.toLowerCase()}`;
  }
}
