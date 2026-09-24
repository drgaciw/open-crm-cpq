import { Component, output, ChangeDetectionStrategy, inject } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SessionService } from '@frontend/shared-data-access';

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
      <span class="toolbar-title">Open CRM</span>
      <span class="toolbar-spacer"></span>
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
        margin-left: 8px;
        font-weight: 500;
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

  toggleSidenav = output<void>();

  signOut(): void {
    this.session.signOut();
  }
}
