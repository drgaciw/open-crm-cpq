import {
  Component,
  ChangeDetectionStrategy,
  input,
  viewChild,
  ElementRef,
  afterNextRender,
  signal,
  Injector,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Page header with title, subtitle, and projection slots for breadcrumb, actions,
 * highlights (record key fields), and search.
 *
 * Additive slots ([breadcrumb], [highlights]) are optional; existing callers keep working.
 *
 * @example
 * <app-page-header [title]="'Acme'" [subtitle]="'Technology'">
 *   <nav breadcrumb aria-label="Breadcrumb">...</nav>
 *   <div actions>...</div>
 *   <div highlights>...</div>
 * </app-page-header>
 */
@Component({
  selector: 'app-page-header',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header" [class.page-header--record]="hasBreadcrumbSlot() || hasHighlightsSlot()">
      <div class="page-header-top">
        <div
          class="page-header-breadcrumb"
          #breadcrumbContainer
          [hidden]="!hasBreadcrumbSlot()"
        >
          <ng-content select="[breadcrumb]"></ng-content>
        </div>

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

        <div
          class="page-header-highlights"
          #highlightsContainer
          [hidden]="!hasHighlightsSlot()"
        >
          <ng-content select="[highlights]"></ng-content>
        </div>
      </div>

      <!-- ng-content must stay mounted so projection can be detected; hide when unused -->
      <div
        class="page-header-search"
        #searchContainer
        [hidden]="!hasSearchSlot()"
      >
        <ng-content select="[search]"></ng-content>
      </div>
    </div>
  `,
  styles: [`
    .page-header {
      background: var(--crm-color-surface, #fff);
      border-bottom: 1px solid var(--crm-color-line, #e0e0e0);
      padding: 0;
      margin-bottom: var(--crm-space-lg, 24px);
    }

    .page-header-top {
      padding: var(--crm-space-lg, 24px) 32px;
    }

    .page-header-breadcrumb {
      margin-bottom: var(--crm-space-sm, 8px);
      font-size: 0.8125rem;
      color: var(--crm-color-muted, #757575);
    }

    .page-header-breadcrumb[hidden],
    .page-header-highlights[hidden],
    .page-header-search[hidden] {
      display: none;
      padding: 0;
      margin: 0;
    }

    .page-header-breadcrumb ::ng-deep a {
      color: var(--crm-color-brand, #1b5fd1);
      text-decoration: none;
      font-weight: 500;
    }

    .page-header-breadcrumb ::ng-deep a:hover {
      text-decoration: underline;
    }

    .page-header-content {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--crm-space-lg, 24px);
    }

    .page-header-text {
      flex: 1;
      min-width: 0;
    }

    .page-header-title {
      margin: 0;
      font-size: 28px;
      font-weight: 600;
      color: var(--crm-color-ink, #212121);
      line-height: 1.2;
    }

    .page-header-subtitle {
      margin: var(--crm-space-sm, 8px) 0 0;
      font-size: 14px;
      color: var(--crm-color-muted, #757575);
      line-height: 1.5;
    }

    .page-header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-shrink: 0;
    }

    .page-header-highlights {
      display: flex;
      flex-wrap: wrap;
      gap: var(--crm-space-md, 16px) 32px;
      margin-top: var(--crm-space-md, 16px);
      padding-top: var(--crm-space-md, 16px);
      border-top: 1px solid var(--crm-color-line, #e0e0e0);
    }

    .page-header-search {
      padding: 0 32px var(--crm-space-lg, 24px);
    }

    @media (max-width: 768px) {
      .page-header-top {
        padding: var(--crm-space-md, 16px);
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
        margin-top: var(--crm-space-xs, 4px);
      }

      .page-header-actions {
        width: 100%;
        justify-content: stretch;
      }

      .page-header-actions ::ng-deep button {
        flex: 1;
      }

      .page-header-search {
        padding: 0 var(--crm-space-md, 16px) var(--crm-space-md, 16px);
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
  `],
})
export class PageHeaderComponent {
  private readonly injector = inject(Injector);

  title = input.required<string>();
  subtitle = input<string>('');

  private readonly searchContainer = viewChild<ElementRef<HTMLElement>>('searchContainer');
  private readonly breadcrumbContainer = viewChild<ElementRef<HTMLElement>>('breadcrumbContainer');
  private readonly highlightsContainer = viewChild<ElementRef<HTMLElement>>('highlightsContainer');
  private readonly searchSlotPresent = signal(false);
  private readonly breadcrumbSlotPresent = signal(false);
  private readonly highlightsSlotPresent = signal(false);

  constructor() {
    afterNextRender(
      () => {
        const searchEl = this.searchContainer()?.nativeElement;
        this.searchSlotPresent.set(!!searchEl && searchEl.childElementCount > 0);
        const crumbEl = this.breadcrumbContainer()?.nativeElement;
        this.breadcrumbSlotPresent.set(!!crumbEl && crumbEl.childElementCount > 0);
        const highlightsEl = this.highlightsContainer()?.nativeElement;
        this.highlightsSlotPresent.set(!!highlightsEl && highlightsEl.childElementCount > 0);
      },
      { injector: this.injector },
    );
  }

  protected hasSearchSlot(): boolean {
    return this.searchSlotPresent();
  }

  protected hasBreadcrumbSlot(): boolean {
    return this.breadcrumbSlotPresent();
  }

  protected hasHighlightsSlot(): boolean {
    return this.highlightsSlotPresent();
  }
}
