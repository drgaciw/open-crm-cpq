import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  ConfirmDialogComponent,
  EmptyStateComponent,
} from '@frontend/shared-ui';
import { ConfigurationRuleDto } from '../configuration-rule.model';
import { ConfigurationRuleService } from '../configuration-rule.service';
import {
  ConfigRuleCreateDialogComponent,
  ConfigRuleCreateDialogData,
} from '../config-rule-create-dialog/config-rule-create-dialog.component';

function httpErrorMessage(err: unknown, fallback: string): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || fallback;
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
}

@Component({
  selector: 'cpq-config-rules-panel',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="nested-section" aria-labelledby="config-rules-heading">
      <div class="section-header">
        <h3 id="config-rules-heading" class="section-title">
          Configuration rules
        </h3>
        <button
          mat-stroked-button
          color="primary"
          type="button"
          class="add-rule-btn"
          [disabled]="busy() || productId() == null"
          (click)="openCreate()"
        >
          <mat-icon>add</mat-icon>
          Add rule
        </button>
      </div>

      @if (error(); as err) {
        <div class="section-error" role="alert">{{ err }}</div>
      }

      @if (loading()) {
        <div class="section-loading" role="status">
          <mat-spinner diameter="32"></mat-spinner>
        </div>
      } @else if (rules().length === 0) {
        <app-empty-state
          icon="rule"
          title="No configuration rules"
          message="Add REQUIRES / EXCLUDES / RECOMMENDS / DEFAULT / VALIDATES rules for this product."
        />
      } @else {
        <table class="rules-table">
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">Type</th>
              <th scope="col">Target</th>
              <th scope="col">Priority</th>
              <th scope="col">Condition</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            @for (rule of rules(); track rule.id) {
              <tr>
                <td>{{ rule.name }}</td>
                <td>{{ rule.ruleType }}</td>
                <td>{{ rule.targetProductId ?? '—' }}</td>
                <td>{{ rule.priority }}</td>
                <td class="condition-cell">
                  {{ rule.conditionExpression || '—' }}
                </td>
                <td>
                  <button
                    mat-button
                    color="warn"
                    type="button"
                    class="delete-rule-btn"
                    [disabled]="busy()"
                    (click)="confirmDelete(rule)"
                  >
                    <mat-icon>delete</mat-icon>
                    Delete
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
  styles: [
    `
      .nested-section {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }

      .section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        flex-wrap: wrap;
      }

      .section-title {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 600;
        color: #212121;
      }

      .section-error {
        padding: 8px 10px;
        border-radius: 8px;
        background: #ffebee;
        color: #c62828;
        font-size: 0.875rem;
      }

      .section-loading {
        display: flex;
        justify-content: center;
        padding: 24px;
      }

      .rules-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.875rem;
      }

      .rules-table th,
      .rules-table td {
        text-align: left;
        padding: 8px 10px;
        border-bottom: 1px solid #eeeeee;
        vertical-align: top;
      }

      .rules-table th {
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.4px;
        color: #757575;
      }

      .condition-cell {
        max-width: 220px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        color: #616161;
        font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        font-size: 0.8rem;
      }

      @media (max-width: 768px) {
        .section-header {
          flex-direction: column;
          align-items: stretch;
        }

        .section-header button {
          width: 100%;
        }

        .rules-table th:nth-child(5),
        .rules-table td:nth-child(5) {
          display: none;
        }
      }
    `,
  ],
})
export class ConfigRulesPanelComponent {
  private readonly ruleService = inject(ConfigurationRuleService);
  private readonly dialog = inject(MatDialog);

  /** Source product id from catalog-detail. */
  readonly productId = input<number | null>(null);

  readonly rules = signal<ConfigurationRuleDto[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  private readonly loadOnProductId = effect(() => {
    const _id = this.productId();
    untracked(() => this.reload());
  });

  openCreate(): void {
    const id = this.productId();
    if (id == null || this.busy()) {
      return;
    }
    const data: ConfigRuleCreateDialogData = { productId: id };
    const ref = this.dialog.open(ConfigRuleCreateDialogComponent, {
      width: '480px',
      data,
    });
    ref.afterClosed().subscribe((created: ConfigurationRuleDto | null) => {
      if (created) {
        this.rules.update((list) => [...list, created]);
      }
    });
  }

  confirmDelete(rule: ConfigurationRuleDto): void {
    if (this.busy()) {
      return;
    }
    const ref = this.dialog.open(ConfirmDialogComponent, {
      width: '400px',
      data: {
        title: 'Delete configuration rule',
        message: `Delete rule "${rule.name}"? This cannot be undone.`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmColor: 'warn' as const,
      },
    });
    ref.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) {
        return;
      }
      this.busy.set(true);
      this.error.set(null);
      this.ruleService.delete(rule.id).subscribe({
        next: () => {
          this.busy.set(false);
          this.rules.update((list) => list.filter((r) => r.id !== rule.id));
        },
        error: (err: unknown) => {
          this.busy.set(false);
          this.error.set(
            httpErrorMessage(err, 'Failed to delete configuration rule'),
          );
        },
      });
    });
  }

  private reload(): void {
    const id = this.productId();
    if (id == null) {
      this.rules.set([]);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.ruleService.listByProduct(id).subscribe({
      next: (list) => {
        this.rules.set(list ?? []);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.rules.set([]);
        this.error.set(
          httpErrorMessage(err, 'Failed to load configuration rules'),
        );
      },
    });
  }
}
