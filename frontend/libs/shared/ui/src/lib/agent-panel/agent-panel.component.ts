import {
  Component,
  ChangeDetectionStrategy,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

/**
 * UI-CRM-SLDS Phase 3 agent / advisory chrome.
 * Surfaces advisory content with explicit states; never enables Jev runtime flags (ADR-015).
 */
export type AgentPanelState =
  | 'loading'
  | 'empty'
  | 'error'
  | 'ready'
  | 'disabled'
  | 'validation';

@Component({
  selector: 'app-agent-panel',
  standalone: true,
  imports: [MatButtonModule, MatChipsModule, MatIconModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside
      class="agent-panel"
      [class.agent-panel--collapsed]="collapsed()"
      data-testid="agent-panel"
      [attr.aria-label]="title() + ' (advisory)'"
    >
      <header class="agent-panel__header">
        <div class="agent-panel__title-row">
          <mat-icon class="agent-panel__icon" aria-hidden="true">smart_toy</mat-icon>
          <h2 class="agent-panel__title">{{ title() }}</h2>
          <mat-chip class="agent-panel__chip" disableRipple>Advisory — not a decision</mat-chip>
        </div>
        <button
          mat-icon-button
          type="button"
          class="agent-panel__toggle"
          (click)="toggleCollapsed()"
          [attr.aria-expanded]="!collapsed()"
          [attr.aria-controls]="bodyId"
          [attr.aria-label]="collapsed() ? 'Expand agent panel' : 'Collapse agent panel'"
        >
          <mat-icon>{{ collapsed() ? 'expand_more' : 'expand_less' }}</mat-icon>
        </button>
      </header>

      @if (!collapsed()) {
        <div class="agent-panel__body" [id]="bodyId">
          <div class="agent-panel__tools">
            <ng-content select="[tools]" />
          </div>
          @switch (state()) {
            @case ('loading') {
              <div class="agent-panel__state" role="status" aria-live="polite">
                <mat-spinner diameter="32"></mat-spinner>
                <p>{{ loadingMessage() }}</p>
              </div>
            }
            @case ('empty') {
              <div class="agent-panel__state" role="status">
                <mat-icon aria-hidden="true">info_outline</mat-icon>
                <p>{{ emptyMessage() }}</p>
              </div>
            }
            @case ('error') {
              <div class="agent-panel__state agent-panel__state--error" role="alert">
                <mat-icon aria-hidden="true">error_outline</mat-icon>
                <p>{{ errorMessage() || 'Advisory unavailable.' }}</p>
              </div>
            }
            @case ('disabled') {
              <div class="agent-panel__state" role="status">
                <mat-icon aria-hidden="true">block</mat-icon>
                <p>{{ disabledMessage() }}</p>
              </div>
            }
            @case ('validation') {
              <div class="agent-panel__state agent-panel__state--warn" role="status">
                <mat-icon aria-hidden="true">warning_amber</mat-icon>
                <p>{{ validationMessage() }}</p>
              </div>
            }
            @case ('ready') {
              <div class="agent-panel__content">
                <ng-content />
                @if (evidence().length > 0) {
                  <section class="agent-panel__evidence" aria-label="Evidence">
                    <h3 class="agent-panel__evidence-title">Evidence</h3>
                    <ul>
                      @for (item of evidence(); track item) {
                        <li>{{ item }}</li>
                      }
                    </ul>
                  </section>
                }
              </div>
              @if (showActions()) {
                <footer class="agent-panel__actions">
                  <button mat-button type="button" (click)="dismiss.emit()">Dismiss</button>
                  <button mat-stroked-button type="button" (click)="acknowledge.emit()">
                    Acknowledge
                  </button>
                </footer>
              }
            }
          }
        </div>
      }
    </aside>
  `,
  styles: [`
    .agent-panel {
      display: block;
      margin: 0 0 var(--crm-space-md, 16px);
      max-width: 520px;
      border: 1px dashed var(--crm-color-line, #d8e0ed);
      border-radius: var(--crm-radius-sm, 8px);
      background: var(--crm-color-surface, #fff);
      overflow: hidden;
    }
    .agent-panel__header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--crm-space-sm, 8px);
      padding: 12px var(--crm-space-md, 16px);
      border-bottom: 1px solid var(--crm-color-line, #d8e0ed);
      background: color-mix(in srgb, var(--crm-color-brand, #1b5fd1) 4%, transparent);
    }
    .agent-panel--collapsed .agent-panel__header {
      border-bottom: none;
    }
    .agent-panel__title-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--crm-space-sm, 8px);
      min-width: 0;
    }
    .agent-panel__icon { color: var(--crm-color-brand, #1b5fd1); }
    .agent-panel__title {
      margin: 0;
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--crm-color-ink, #17254b);
    }
    .agent-panel__chip {
      font-size: 11px;
      min-height: 22px;
    }
    .agent-panel__body {
      padding: var(--crm-space-md, 16px);
    }
    .agent-panel__tools:empty {
      display: none;
    }
    .agent-panel__tools:not(:empty) {
      margin-bottom: var(--crm-space-md, 16px);
    }
    .agent-panel__state {
      display: flex;
      align-items: flex-start;
      gap: var(--crm-space-sm, 8px);
      color: var(--crm-color-muted, #52627b);
      font-size: 0.875rem;
    }
    .agent-panel__state p { margin: 0; }
    .agent-panel__state--error { color: var(--crm-color-danger, #c62828); }
    .agent-panel__state--warn { color: var(--crm-color-warning-ink, #8a5a00); }
    .agent-panel__evidence {
      margin-top: var(--crm-space-md, 16px);
      padding-top: var(--crm-space-sm, 8px);
      border-top: 1px solid var(--crm-color-line, #d8e0ed);
    }
    .agent-panel__evidence-title {
      margin: 0 0 var(--crm-space-sm, 8px);
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--crm-color-muted, #52627b);
    }
    .agent-panel__evidence ul {
      margin: 0;
      padding-left: 1.1rem;
      color: var(--crm-color-ink, #17254b);
      font-size: 0.8125rem;
    }
    .agent-panel__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--crm-space-sm, 8px);
      margin-top: var(--crm-space-md, 16px);
      padding-top: var(--crm-space-sm, 8px);
      border-top: 1px solid var(--crm-color-line, #d8e0ed);
    }
  `],
})
export class AgentPanelComponent {
  readonly bodyId = `agent-panel-body-${Math.random().toString(36).slice(2, 9)}`;

  title = input('Agent suggestions');
  state = input<AgentPanelState>('empty');
  emptyMessage = input('No advisory available yet.');
  loadingMessage = input('Loading advisory…');
  disabledMessage = input(
    'Advisory is off in this environment. Jev runtime flags stay disabled (ADR-015).',
  );
  errorMessage = input<string | null>(null);
  validationMessage = input('Check inputs before requesting an advisory.');
  evidence = input<string[]>([]);
  showActions = input(false);
  /** Used until the user toggles collapse. */
  initiallyCollapsed = input(false);

  dismiss = output<void>();
  acknowledge = output<void>();

  private readonly collapseOverride = signal<boolean | null>(null);

  readonly collapsed = computed(
    () => this.collapseOverride() ?? this.initiallyCollapsed(),
  );

  toggleCollapsed(): void {
    this.collapseOverride.set(!this.collapsed());
  }
}
