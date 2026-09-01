import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

/**
 * Empty state component to display when lists or tables have no data
 *
 * @example
 * <app-empty-state
 *   [icon]="'inbox'"
 *   [title]="'No leads found'"
 *   [message]="'Get started by creating your first lead.'"
 *   [actionLabel]="'Create Lead'"
 *   (action)="onCreateLead()"
 * />
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty-state" role="status" aria-live="polite">
      @if (icon()) {
        <mat-icon class="empty-state-icon">{{ icon() }}</mat-icon>
      }

      @if (title()) {
        <h3 class="empty-state-title">{{ title() }}</h3>
      }

      @if (message()) {
        <p class="empty-state-message">{{ message() }}</p>
      }

      @if (actionLabel()) {
        <button
          type="button"
          mat-raised-button
          color="primary"
          (click)="onAction()"
          class="empty-state-action"
        >
          {{ actionLabel() }}
        </button>
      }
    </div>
  `,
  styles: [`
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 64px 24px;
      text-align: center;
      min-height: 400px;
    }

    .empty-state-icon {
      width: 80px;
      height: 80px;
      font-size: 80px;
      color: #bdbdbd;
      margin-bottom: 24px;
    }

    .empty-state-title {
      margin: 0 0 12px;
      font-size: 24px;
      font-weight: 500;
      color: #424242;
    }

    .empty-state-message {
      margin: 0 0 32px;
      font-size: 14px;
      color: #757575;
      max-width: 400px;
      line-height: 1.6;
    }

    .empty-state-action {
      min-width: 140px;
    }

    @media (max-width: 600px) {
      .empty-state {
        padding: 48px 16px;
        min-height: 300px;
      }

      .empty-state-icon {
        width: 64px;
        height: 64px;
        font-size: 64px;
        margin-bottom: 16px;
      }

      .empty-state-title {
        font-size: 20px;
        margin-bottom: 8px;
      }

      .empty-state-message {
        font-size: 13px;
        margin-bottom: 24px;
      }
    }
  `],
})
export class EmptyStateComponent {
  // Inputs using signal-based API
  icon = input<string>('inbox');
  title = input<string>('');
  message = input<string>('');
  actionLabel = input<string>('');

  // Outputs using signal-based API
  action = output<void>();

  onAction(): void {
    this.action.emit();
  }
}
