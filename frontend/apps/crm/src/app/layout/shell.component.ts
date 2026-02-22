import { Component, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatSidenavModule, MatSidenav } from '@angular/material/sidenav';
import { ToolbarComponent } from './toolbar.component';
import { SidebarComponent } from './sidebar.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, MatSidenavModule, ToolbarComponent, SidebarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="shell-container">
      <app-toolbar (toggleSidenav)="onToggleSidenav()" />
      <mat-sidenav-container class="sidenav-container">
        <mat-sidenav #sidenavRef mode="side" opened class="sidenav">
          <app-sidebar />
        </mat-sidenav>
        <mat-sidenav-content class="content">
          <main class="page-container">
            <router-outlet />
          </main>
        </mat-sidenav-content>
      </mat-sidenav-container>
    </div>
  `,
  styles: [`
    .shell-container {
      display: flex;
      flex-direction: column;
      height: 100vh;
    }
    .sidenav-container {
      flex: 1;
    }
    .sidenav {
      width: 240px;
    }
    .content {
      background-color: #fafafa;
    }
    .page-container {
      padding: 24px;
      max-width: 1400px;
      margin: 0 auto;
    }
  `],
})
export class ShellComponent {
  @ViewChild('sidenavRef') sidenav!: MatSidenav;

  onToggleSidenav(): void {
    this.sidenav.toggle();
  }
}
