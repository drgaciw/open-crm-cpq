import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import {
  ConfirmDialogComponent,
  EmptyStateComponent,
  PageHeaderComponent,
} from '@frontend/shared-ui';
import {
  RULE_SET_STATUS_LABELS,
  RULE_STATUS_LABELS,
  RuleDto,
  RuleSetDto,
} from '@frontend/shared-models';
import {
  RuleService,
  RuleSetService,
} from '@frontend/cpq-pricing-data-access';
import {
  RuleDialogComponent,
  RuleDialogData,
} from '../rule-dialog/rule-dialog.component';

interface RuleTableRow {
  id: number;
  name: string;
  ruleType: string;
  priority: number | null;
  status: string;
}

function httpErrorMessage(err: unknown, fallback: string): string {
  const e = err as { error?: { message?: string }; message?: string } | null;
  return e?.error?.message || e?.message || fallback;
}

@Component({
  selector: 'cpq-rule-set-detail',
  standalone: true,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    PageHeaderComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading() && !ruleSet()) {
      <div class="loading" role="status" aria-live="polite">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    } @else if (ruleSet(); as ruleSet) {
      <app-page-header [title]="ruleSet.name" [subtitle]="subtitle(ruleSet)">
        <div actions class="header-actions">
          <button mat-stroked-button type="button" (click)="goBack()">
            <mat-icon>arrow_back</mat-icon>
            Back to rule sets
          </button>
          @if (canActivate()) {
            <button
              mat-raised-button
              color="primary"
              type="button"
              class="activate-btn"
              [disabled]="mutating()"
              (click)="confirmActivate()"
            >
              <mat-icon>play_arrow</mat-icon>
              Activate
            </button>
          }
          @if (canDeprecate()) {
            <button
              mat-stroked-button
              color="warn"
              type="button"
              class="deprecate-btn"
              [disabled]="mutating()"
              (click)="confirmDeprecate()"
            >
              <mat-icon>block</mat-icon>
              Deprecate
            </button>
          }
        </div>
      </app-page-header>

      @if (bannerError(); as err) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ err }}</span>
          <button mat-button type="button" (click)="clearError()">
            Dismiss
          </button>
        </div>
      }

      <div class="detail-body">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Rule set details</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Status</span>
                <span class="value">{{
                  statusLabels[ruleSet.status] || ruleSet.status
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">Engine</span>
                <span class="value">{{ ruleSet.engineType || 'DROOLS' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Version</span>
                <span class="value">{{
                  ruleSet.ruleSetVersion != null ? ruleSet.ruleSetVersion : '—'
                }}</span>
              </div>
              <div class="info-item">
                <span class="label">Effective</span>
                <span class="value">{{ ruleSet.effectiveDate || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Expires</span>
                <span class="value">{{ ruleSet.expirationDate || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Updated</span>
                <span class="value">{{ ruleSet.updatedAt || '—' }}</span>
              </div>
              <div class="info-item full">
                <span class="label">Description</span>
                <span class="value">{{ ruleSet.description || '—' }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <section class="rules-section" aria-labelledby="rules-heading">
          <div class="rules-header">
            <h3 id="rules-heading" class="rules-title">Rules</h3>
            <button
              mat-stroked-button
              type="button"
              class="add-rule-btn"
              [disabled]="mutating()"
              (click)="openCreateRule()"
            >
              <mat-icon>add</mat-icon>
              Add rule
            </button>
          </div>

          @if (!rulesLoading() && ruleRows().length === 0) {
            <app-empty-state
              icon="code"
              title="No rules"
              message="This rule set has no DRL rules yet. Rules load via /api/v1/rules/by-rule-set/{id}."
            />
          } @else {
            <div class="rules-table-wrap">
              @if (rulesLoading()) {
                <div class="rules-loading" role="status">
                  <mat-spinner diameter="32"></mat-spinner>
                </div>
              }
              <table class="rules-table">
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Type</th>
                    <th scope="col">Priority</th>
                    <th scope="col">Status</th>
                    <th scope="col" class="actions-col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of ruleRows(); track row.id) {
                    <tr>
                      <td>{{ row.name }}</td>
                      <td>{{ row.ruleType }}</td>
                      <td>{{ row.priority != null ? row.priority : '—' }}</td>
                      <td>{{ row.status }}</td>
                      <td class="actions-col">
                        <button
                          mat-button
                          type="button"
                          class="row-edit-btn"
                          [disabled]="mutating()"
                          (click)="openEditRule(row)"
                        >
                          Edit
                        </button>
                        <button
                          mat-button
                          color="warn"
                          type="button"
                          class="row-delete-btn"
                          [disabled]="mutating()"
                          (click)="confirmDeleteRule(row)"
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
              @if (rulesPagination().totalElements > 0) {
                <mat-paginator
                  [length]="rulesPagination().totalElements"
                  [pageSize]="rulesPagination().size"
                  [pageIndex]="rulesPagination().page"
                  [pageSizeOptions]="[10, 20, 50, 100]"
                  (page)="onRulesPageChange($event)"
                />
              }
            </div>
          }
        </section>
      </div>
    } @else {
      <app-empty-state
        icon="search_off"
        title="Rule set not found"
        [message]="
          bannerError() ||
          'This rule set could not be loaded from /api/v1/rule-sets.'
        "
        actionLabel="Back to rule sets"
        (action)="goBack()"
      />
    }
  `,
  styles: [
    `
      .loading {
        display: flex;
        justify-content: center;
        padding: 48px;
      }

      .header-actions {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
      }

      .error-banner {
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 0 32px 16px;
        padding: 10px 12px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
        font-size: 0.875rem;
      }

      .detail-body {
        padding: 0 32px 32px;
        display: flex;
        flex-direction: column;
        gap: 24px;
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

      .info-item.full {
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

      .rules-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        margin-bottom: 12px;
        flex-wrap: wrap;
      }

      .rules-title {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 600;
      }

      .rules-table-wrap {
        position: relative;
        border: 1px solid #e0e0e0;
        border-radius: 8px;
        overflow: auto;
        background: #fff;
      }

      .rules-loading {
        position: absolute;
        inset: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(255, 255, 255, 0.7);
        z-index: 1;
      }

      .rules-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.875rem;
      }

      .rules-table th,
      .rules-table td {
        padding: 12px 16px;
        text-align: left;
        border-bottom: 1px solid #eee;
      }

      .rules-table th {
        font-weight: 600;
        color: #424242;
        background: #fafafa;
      }

      .actions-col {
        white-space: nowrap;
        text-align: right;
      }

      @media (max-width: 768px) {
        .detail-body {
          padding: 0 16px 24px;
        }

        .error-banner {
          margin: 0 16px 16px;
        }

        .info-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class RuleSetDetailComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  readonly ruleSetService = inject(RuleSetService);
  readonly ruleService = inject(RuleService);

  /** Route param `:id` (string from Angular router). */
  readonly id = input.required<string>();

  readonly statusLabels = RULE_SET_STATUS_LABELS;

  readonly mutating = signal(false);
  readonly actionError = signal<string | null>(null);

  readonly loading = computed(
    () => this.ruleSetService.loading() && !this.ruleSet(),
  );
  readonly rulesLoading = computed(
    () => this.ruleService.loading() && !!this.ruleSet(),
  );
  readonly bannerError = computed(
    () =>
      this.actionError() ??
      this.ruleSetService.error() ??
      this.ruleService.error(),
  );
  readonly rulesPagination = computed(() => this.ruleService.pagination());

  readonly ruleSet = computed<RuleSetDto | null>(() => {
    const selected = this.ruleSetService.selectedRuleSet();
    if (!selected) {
      return null;
    }
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId) || selected.id !== numericId) {
      return null;
    }
    return selected;
  });

  readonly canActivate = computed(() => this.ruleSet()?.status === 'DRAFT');
  readonly canDeprecate = computed(() => this.ruleSet()?.status === 'ACTIVE');

  readonly ruleRows = computed<RuleTableRow[]>(() =>
    this.ruleService.rules().map((r) => this.toRuleRow(r)),
  );

  ngOnInit(): void {
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId)) {
      this.ruleSetService.selectedRuleSet.set(null);
      this.ruleService.rules.set([]);
      this.ruleSetService.error.set('Invalid rule set id');
      this.ruleSetService.loading.set(false);
      return;
    }
    this.ruleSetService.getById(numericId);
    this.ruleService.listByRuleSet(numericId);
  }

  subtitle(ruleSet: RuleSetDto): string {
    const parts = [
      RULE_SET_STATUS_LABELS[ruleSet.status],
      ruleSet.engineType || 'DROOLS',
    ].filter(Boolean);
    return parts.length > 0 ? parts.join(' · ') : `ID ${ruleSet.id}`;
  }

  onRulesPageChange(event: PageEvent): void {
    const numericId = Number(this.id());
    if (!Number.isFinite(numericId)) {
      return;
    }
    this.ruleService.listByRuleSet(numericId, event.pageIndex, event.pageSize);
  }

  clearError(): void {
    this.actionError.set(null);
    this.ruleSetService.error.set(null);
    this.ruleService.error.set(null);
  }

  goBack(): void {
    void this.router.navigate(['/rule-sets']);
  }

  confirmActivate(): void {
    const ruleSet = this.ruleSet();
    if (!ruleSet || !this.canActivate() || this.mutating()) {
      return;
    }

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Activate rule set',
        message: `Activate "${ruleSet.name}"? Status will change from Draft to Active.`,
        confirmText: 'Activate',
        cancelText: 'Cancel',
        confirmColor: 'primary' as const,
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.actionError.set(null);
      this.mutating.set(true);
      this.ruleSetService.activate(ruleSet.id).subscribe({
        next: (updated) => {
          this.mutating.set(false);
          this.ruleSetService.selectedRuleSet.set(updated);
        },
        error: (err: unknown) => {
          this.mutating.set(false);
          this.actionError.set(
            httpErrorMessage(err, 'Failed to activate rule set'),
          );
        },
      });
    });
  }

  confirmDeprecate(): void {
    const ruleSet = this.ruleSet();
    if (!ruleSet || !this.canDeprecate() || this.mutating()) {
      return;
    }

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Deprecate rule set',
        message: `Deprecate "${ruleSet.name}"? Status will change from Active to Deprecated.`,
        confirmText: 'Deprecate',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.actionError.set(null);
      this.mutating.set(true);
      this.ruleSetService.deprecate(ruleSet.id).subscribe({
        next: (updated) => {
          this.mutating.set(false);
          this.ruleSetService.selectedRuleSet.set(updated);
        },
        error: (err: unknown) => {
          this.mutating.set(false);
          this.actionError.set(
            httpErrorMessage(err, 'Failed to deprecate rule set'),
          );
        },
      });
    });
  }

  openCreateRule(): void {
    const ruleSet = this.ruleSet();
    if (!ruleSet || this.mutating()) {
      return;
    }
    const data: RuleDialogData = { ruleSetId: ruleSet.id };
    const ref = this.dialog.open(RuleDialogComponent, {
      width: '720px',
      disableClose: true,
      data,
    });
    ref.afterClosed().subscribe((created: RuleDto | undefined) => {
      if (!created) {
        return;
      }
      this.actionError.set(null);
      this.ruleService.listByRuleSet(ruleSet.id);
    });
  }

  openEditRule(row: RuleTableRow): void {
    const ruleSet = this.ruleSet();
    if (!ruleSet || this.mutating()) {
      return;
    }
    const rule = this.ruleService.rules().find((r) => r.id === row.id);
    if (!rule) {
      return;
    }
    const data: RuleDialogData = { ruleSetId: ruleSet.id, rule };
    const ref = this.dialog.open(RuleDialogComponent, {
      width: '720px',
      disableClose: true,
      data,
    });
    ref.afterClosed().subscribe((saved: RuleDto | undefined) => {
      if (!saved) {
        return;
      }
      this.actionError.set(null);
      this.ruleService.listByRuleSet(
        ruleSet.id,
        this.rulesPagination().page,
        this.rulesPagination().size,
      );
    });
  }

  confirmDeleteRule(row: RuleTableRow): void {
    const ruleSet = this.ruleSet();
    if (!ruleSet || this.mutating()) {
      return;
    }

    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete rule',
        message: `Delete rule "${row.name}"? This cannot be undone from the portal.`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });

    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.actionError.set(null);
      this.mutating.set(true);
      this.ruleService.delete(row.id).subscribe({
        next: () => {
          this.mutating.set(false);
          this.ruleService.listByRuleSet(
            ruleSet.id,
            this.rulesPagination().page,
            this.rulesPagination().size,
          );
        },
        error: (err: unknown) => {
          this.mutating.set(false);
          this.actionError.set(httpErrorMessage(err, 'Failed to delete rule'));
        },
      });
    });
  }

  private toRuleRow(r: RuleDto): RuleTableRow {
    return {
      id: r.id,
      name: r.name,
      ruleType: r.ruleType,
      priority: r.priority ?? null,
      status: RULE_STATUS_LABELS[r.status] ?? r.status,
    };
  }
}
