import {
  Component,
  ViewChild,
  ChangeDetectionStrategy,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MatSidenavModule, MatSidenav } from '@angular/material/sidenav';
import { ToolbarComponent } from './toolbar.component';
import { SidebarComponent } from './sidebar.component';

/** Matches plan: mobile chrome below 768px. */
const MOBILE_QUERY = '(max-width: 767.98px)';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, MatSidenavModule, ToolbarComponent, SidebarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="shell-container">
      <a class="skip-link" href="#main-content">Skip to main content</a>
      <app-toolbar (toggleSidenav)="onToggleSidenav()" />
      <mat-sidenav-container class="sidenav-container">
        <mat-sidenav
          #sidenavRef
          class="sidenav"
          role="navigation"
          aria-label="Main navigation"
          [mode]="sidenavMode()"
          [opened]="sidenavOpened()"
          (openedChange)="onOpenedChange($event)"
        >
          <app-sidebar />
        </mat-sidenav>
        <mat-sidenav-content class="content">
          <main id="main-content" class="page-container" tabindex="-1">
            <router-outlet />
          </main>
        </mat-sidenav-content>
      </mat-sidenav-container>
    </div>
  `,
  styles: [
    `
      .shell-container {
        display: flex;
        flex-direction: column;
        height: 100vh;
      }
      .skip-link {
        position: absolute;
        left: -9999px;
        top: 0;
        z-index: 1000;
        padding: 8px 16px;
        background: #1976d2;
        color: #fff;
        text-decoration: none;
        border-radius: 0 0 4px 0;
      }
      .skip-link:focus {
        left: 0;
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
      .page-container:focus {
        outline: none;
      }
      @media (max-width: 767.98px) {
        .page-container {
          padding: 16px;
        }
      }
    `,
  ],
})
export class ShellComponent implements OnInit {
  @ViewChild('sidenavRef') sidenav!: MatSidenav;

  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly destroyRef = inject(DestroyRef);

  /** `over` below 768px; `side` at desktop. */
  readonly sidenavMode = signal<'side' | 'over'>('side');
  /** Desktop starts open; mobile starts closed. */
  readonly sidenavOpened = signal(true);

  ngOnInit(): void {
    this.breakpointObserver
      .observe(MOBILE_QUERY)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((state) => {
        if (state.matches) {
          this.sidenavMode.set('over');
          this.sidenavOpened.set(false);
        } else {
          this.sidenavMode.set('side');
          this.sidenavOpened.set(true);
        }
      });
  }

  onToggleSidenav(): void {
    this.sidenavOpened.update((opened) => !opened);
  }

  onOpenedChange(opened: boolean): void {
    this.sidenavOpened.set(opened);
  }
}
