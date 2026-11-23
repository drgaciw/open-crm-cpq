import { Component, output, ChangeDetectionStrategy, inject } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SessionService } from '@frontend/shared-data-access';
import { CommandPaletteService } from '@frontend/shared-ui';

@Component({
  selector: 'app-toolbar',
  standalone: true,
  imports: [MatToolbarModule, MatIconModule, MatButtonModule, MatTooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-toolbar color="primary">
      <button
        mat-icon-button
        type="button"
        (click)="toggleSidenav.emit()"
        aria-label="Toggle menu"
      >
        <mat-icon>menu</mat-icon>
      </button>
      <span class="toolbar-title">Open CRM CPQ</span>
      <span class="toolbar-spacer"></span>
      <button
        mat-button
        type="button"
        class="command-palette-trigger"
        data-testid="command-palette-trigger"
        (click)="openCommandPalette.emit()"
        aria-label="Open command palette"
        [matTooltip]="'Command palette (' + shortcutLabel + ')'"
      >
        <mat-icon class="command-palette-trigger__icon" aria-hidden="true">search</mat-icon>
        <span class="command-palette-trigger__label">Search…</span>
        <kbd class="command-palette-trigger__shortcut">{{ shortcutLabel }}</kbd>
      </button>
      <button
        mat-icon-button
        type="button"
        (click)="signOut()"
        aria-label="Sign out"
        matTooltip="Sign out"
      >
        <mat-icon>logout</mat-icon>
      </button>
    </mat-toolbar>
  `,
  styles: [
    `
      .toolbar-spacer {
        flex: 1 1 auto;
      }
      .toolbar-title {
        margin-left: var(--crm-space-sm, 8px);
        font-weight: 500;
      }
      .command-palette-trigger {
        display: inline-flex;
        align-items: center;
        gap: var(--crm-space-sm);
        margin-right: var(--crm-space-sm);
        padding: 0 var(--crm-space-sm);
        min-height: 34px;
        border: 1px solid color-mix(in srgb, var(--crm-color-on-brand) 28%, transparent);
        border-radius: var(--crm-radius-sm);
        background: color-mix(in srgb, var(--crm-color-on-brand) 12%, transparent);
        color: var(--crm-color-on-brand);
      }
      .command-palette-trigger:hover {
        background: color-mix(in srgb, var(--crm-color-on-brand) 20%, transparent);
      }
      .command-palette-trigger__icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }
      .command-palette-trigger__label {
        font-size: 0.875rem;
        font-weight: 400;
      }
      .command-palette-trigger__shortcut {
        padding: 1px 6px;
        border-radius: var(--crm-radius-sm);
        border: 1px solid color-mix(in srgb, var(--crm-color-on-brand) 32%, transparent);
        background: color-mix(in srgb, var(--crm-color-brand-strong) 35%, transparent);
        color: var(--crm-color-on-brand);
        font-family: inherit;
        font-size: 0.75rem;
        line-height: 1.4;
      }
      @media (max-width: 767.98px) {
        .command-palette-trigger__label {
          display: none;
        }
      }
    `,
  ],
})
/**
 * App toolbar. Sign-out ends the gateway session and the Entra session (ADR-016); in local `dev` mode there is no
 * session, and the gateway simply returns to the app.
 */
export class ToolbarComponent {
  private readonly session = inject(SessionService);
  private readonly commandPalette = inject(CommandPaletteService);

  readonly shortcutLabel = this.commandPalette.shortcutLabel();

  toggleSidenav = output<void>();
  openCommandPalette = output<void>();

  signOut(): void {
    this.session.signOut();
  }
}
