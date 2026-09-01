import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import {
  ConfirmDialogComponent,
  EmptyStateComponent,
  PageHeaderComponent,
} from '@frontend/shared-ui';
import {
  APPROVAL_TIER_LABELS,
  ApprovalRequestDto,
  ApprovalTier,
} from '@frontend/shared-models';
import { ApprovalService } from '../approval.service';

function httpErrorMessage(err: unknown): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || 'An error occurred';
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
}

@Component({
  selector: 'cpq-approvals-inbox',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header
      title="Approvals"
      subtitle="Pending quote approvals via quote-service BFF"
    >
      <div actions>
        <button
          mat-stroked-button
          type="button"
          (click)="refresh()"
          [disabled]="loading() || actionBusy()"
        >
          <mat-icon>refresh</mat-icon>
          Refresh
        </button>
      </div>
    </app-page-header>

    @if (error(); as err) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ err }}</span>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
        <button mat-button type="button" (click)="refresh()">Retry</button>
      </div>
    }

    @if (actionError(); as actionErr) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ actionErr }}</span>
        <button mat-button type="button" (click)="clearActionError()">
          Dismiss
        </button>
      </div>
    }

    @if (loading() && pending().length === 0) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40" />
      </div>
    } @else if (!loading() && isEmpty()) {
      <app-empty-state
        icon="fact_check"
        title="No pending approvals"
        message="Approvals waiting for a decision will appear here."
      />
    } @else {
      <div class="table-container">
        <table mat-table [dataSource]="pending()" [trackBy]="trackById">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>ID</th>
            <td mat-cell *matCellDef="let row">{{ row.id }}</td>
          </ng-container>

          <ng-container matColumnDef="quoteId">
            <th mat-header-cell *matHeaderCellDef>Quote</th>
            <td mat-cell *matCellDef="let row">
              <a
                class="quote-link"
                [routerLink]="['/quotes', row.quoteId]"
                (click)="$event.stopPropagation()"
              >
                Quote {{ row.quoteId }}
              </a>
            </td>
          </ng-container>

          <ng-container matColumnDef="tier">
            <th mat-header-cell *matHeaderCellDef>Tier</th>
            <td mat-cell *matCellDef="let row">{{ tierLabel(row.tier) }}</td>
          </ng-container>

          <ng-container matColumnDef="requestedBy">
            <th mat-header-cell *matHeaderCellDef>Requested by</th>
            <td mat-cell *matCellDef="let row">{{ row.requestedBy || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="requestedAt">
            <th mat-header-cell *matHeaderCellDef>Requested</th>
            <td mat-cell *matCellDef="let row">
              {{
                row.requestedAt
                  ? (row.requestedAt | date: 'medium')
                  : row.createdAt
                    ? (row.createdAt | date: 'medium')
                    : '—'
              }}
            </td>
          </ng-container>

          <ng-container matColumnDef="comments">
            <th mat-header-cell *matHeaderCellDef>Comments</th>
            <td mat-cell *matCellDef="let row">{{ row.comments || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let row" class="actions-cell">
              <button
                mat-stroked-button
                color="primary"
                type="button"
                (click)="onApprove(row)"
                [disabled]="actionBusy()"
              >
                <mat-icon>check</mat-icon>
                Approve
              </button>
              <button
                mat-stroked-button
                color="warn"
                type="button"
                (click)="onReject(row)"
                [disabled]="actionBusy()"
              >
                <mat-icon>close</mat-icon>
                Reject
              </button>
              <button
                mat-button
                type="button"
                (click)="openQuote(row.quoteId)"
                [disabled]="actionBusy()"
              >
                <mat-icon>open_in_new</mat-icon>
                Open quote
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
        </table>
      </div>
    }
  `,
  styles: [
    `
      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 0 32px 16px;
        padding: 12px 16px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
      }

      .loading {
        display: flex;
        justify-content: center;
        padding: 48px;
      }

      .table-container {
        margin: 0 32px 32px;
        overflow: auto;
        border: 1px solid rgba(0, 0, 0, 0.12);
        border-radius: 8px;
      }

      table {
        width: 100%;
      }

      .quote-link {
        color: #1976d2;
        text-decoration: none;
        font-weight: 500;
      }

      .quote-link:hover {
        text-decoration: underline;
      }

      .actions-cell {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        padding-top: 8px;
        padding-bottom: 8px;
      }

      @media (max-width: 768px) {
        .error-banner,
        .table-container {
          margin-left: 16px;
          margin-right: 16px;
        }
      }
    `,
  ],
})
export class ApprovalsInboxComponent implements OnInit {
  readonly approvalService = inject(ApprovalService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  readonly displayedColumns = [
    'id',
    'quoteId',
    'tier',
    'requestedBy',
    'requestedAt',
    'comments',
    'actions',
  ];

  readonly actionBusy = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly pending = computed(() => this.approvalService.pending());
  readonly loading = computed(() => this.approvalService.loading());
  readonly error = computed(() => this.approvalService.error());
  readonly isEmpty = computed(() => this.approvalService.isEmpty());

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.clearActionError();
    this.approvalService.listPending();
  }

  clearError(): void {
    this.approvalService.error.set(null);
  }

  clearActionError(): void {
    this.actionError.set(null);
  }

  tierLabel(tier: ApprovalTier): string {
    return APPROVAL_TIER_LABELS[tier] ?? tier;
  }

  trackById(_index: number, row: ApprovalRequestDto): number {
    return row.id;
  }

  openQuote(quoteId: number): void {
    void this.router.navigate(['/quotes', quoteId]);
  }

  onApprove(row: ApprovalRequestDto): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Approve request',
        message: `Approve approval #${row.id} for quote ${row.quoteId} (${this.tierLabel(row.tier)})?`,
        confirmText: 'Approve',
        cancelText: 'Cancel',
        confirmColor: 'primary' as const,
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.runAction(() => this.approvalService.approve(row.id));
    });
  }

  onReject(row: ApprovalRequestDto): void {
    const comments =
      typeof window !== 'undefined'
        ? window.prompt(
            `Reject approval #${row.id} for quote ${row.quoteId}. Optional reason:`,
            '',
          )
        : '';
    if (comments === null) {
      return;
    }
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Reject request',
        message: `Reject approval #${row.id} for quote ${row.quoteId}?`,
        confirmText: 'Reject',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      const trimmed = comments.trim();
      this.runAction(() =>
        this.approvalService.reject(
          row.id,
          trimmed ? { comments: trimmed } : {},
        ),
      );
    });
  }

  private runAction(
    factory: () => ReturnType<ApprovalService['approve']>,
  ): void {
    this.clearActionError();
    this.actionBusy.set(true);
    factory().subscribe({
      next: () => this.actionBusy.set(false),
      error: (err) => {
        this.actionBusy.set(false);
        this.actionError.set(httpErrorMessage(err));
      },
    });
  }
}
