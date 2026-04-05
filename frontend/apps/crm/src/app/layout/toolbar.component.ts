import { Component, output, ChangeDetectionStrategy } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-toolbar',
  standalone: true,
  imports: [MatToolbarModule, MatIconModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-toolbar color="primary">
      <button
        mat-icon-button
        (click)="toggleSidenav.emit()"
        aria-label="Toggle menu"
      >
        <mat-icon>menu</mat-icon>
      </button>
      <span class="toolbar-title">Open CRM</span>
      <span class="toolbar-spacer"></span>
      <button mat-icon-button aria-label="Notifications">
        <mat-icon>notifications</mat-icon>
      </button>
      <button mat-icon-button aria-label="Account">
        <mat-icon>account_circle</mat-icon>
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
export class ToolbarComponent {
  toggleSidenav = output<void>();
}
