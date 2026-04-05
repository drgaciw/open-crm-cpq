import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Page header component with title, subtitle, and content projection slots for actions and search
 *
 * @example
 * <app-page-header
 *   [title]="'Leads'"
 *   [subtitle]="'Manage your sales leads'"
 * >
 *   <div actions>
 *     <button mat-raised-button color="primary">
 *       <mat-icon>add</mat-icon>
 *       New Lead
 *     </button>
 *   </div>
 *   <div search>
 *     <app-search-input (searchChange)="onSearch($event)" />
 *   </div>
 * </app-page-header>
 */
@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div class="page-header-top">
        <div class="page-header-content">
          <div class="page-header-text">
            <h1 class="page-header-title">{{ title() }}</h1>
            @if (subtitle()) {
              <p class="page-header-subtitle">{{ subtitle() }}</p>
            }
          </div>

          <div class="page-header-actions">
            <ng-content select="[actions]"></ng-content>
          </div>
        </div>
      </div>

      @if (hasSearchSlot()) {
        <div class="page-header-search">
          <ng-content select="[search]"></ng-content>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .page-header {
        background: white;
        border-bottom: 1px solid #e0e0e0;
        padding: 0;
        margin-bottom: 24px;
      }

      .page-header-top {
        padding: 24px 32px;
      }

      .page-header-content {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 24px;
      }

      .page-header-text {
        flex: 1;
        min-width: 0;
      }

      .page-header-title {
        margin: 0;
        font-size: 28px;
        font-weight: 600;
        color: #212121;
        line-height: 1.2;
      }

      .page-header-subtitle {
        margin: 8px 0 0;
        font-size: 14px;
        color: #757575;
        line-height: 1.5;
      }

      .page-header-actions {
        display: flex;
        align-items: center;
        gap: 12px;
        flex-shrink: 0;
      }

      .page-header-search {
        padding: 0 32px 24px;
      }

      @media (max-width: 768px) {
        .page-header-top {
          padding: 16px 16px;
        }

        .page-header-content {
          flex-direction: column;
          align-items: stretch;
        }

        .page-header-title {
          font-size: 24px;
        }

        .page-header-subtitle {
          font-size: 13px;
          margin-top: 4px;
        }

        .page-header-actions {
          width: 100%;
          justify-content: stretch;
        }

        .page-header-actions ::ng-deep button {
          flex: 1;
        }

        .page-header-search {
          padding: 0 16px 16px;
        }
      }

      @media (max-width: 480px) {
        .page-header-title {
          font-size: 20px;
        }

        .page-header-actions {
          flex-direction: column;
        }
      }
    `,
  ],
})
export class PageHeaderComponent {
  // Inputs using signal-based API
  title = input.required<string>();
  subtitle = input<string>('');

  // Helper method to check if search slot has content
  // Note: In a real app, you might use ViewChild or ContentChild to detect projected content
  // For simplicity, we're assuming the slot exists if it's used
  protected hasSearchSlot(): boolean {
    // This is a simplified implementation
    // In production, you might want to use ContentChildren to detect projected content
    return true;
  }
}
