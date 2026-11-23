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
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { LoadingService } from '@frontend/shared-data-access';
import { CommandPaletteItem, CommandPaletteService } from '@frontend/shared-ui/command-palette';
import { ToolbarComponent } from './toolbar.component';
import { SidebarComponent } from './sidebar.component';

/** Matches plan: mobile chrome below 768px. */
const MOBILE_QUERY = '(max-width: 767.98px)';

const WORKSPACE_COMMANDS: CommandPaletteItem[] = [
  {
    id: 'nav-dashboard',
    label: 'Dashboard',
    category: 'CRM',
    icon: 'dashboard',
    keywords: ['home', 'overview'],
    route: ['/dashboard'],
  },
  {
    id: 'nav-accounts',
    label: 'Accounts',
    category: 'CRM',
    icon: 'business',
    keywords: ['organizations', 'companies', 'customers'],
    route: ['/organizations'],
  },
  {
    id: 'nav-contacts',
    label: 'Contacts',
    category: 'CRM',
    icon: 'people',
    keywords: ['people', 'person'],
    route: ['/contacts'],
  },
  {
    id: 'nav-leads',
    label: 'Leads',
    category: 'CRM',
    icon: 'leaderboard',
    keywords: ['prospects'],
    route: ['/leads'],
  },
  {
    id: 'nav-opportunities',
    label: 'Opportunities',
    category: 'CRM',
    icon: 'handshake',
    keywords: ['deals', 'pipeline'],
    route: ['/deals'],
  },
  {
    id: 'nav-opportunity-kanban',
    label: 'Opportunity Pipeline Kanban',
    category: 'CRM',
    icon: 'view_kanban',
    keywords: ['deals', 'board', 'stages', 'pipeline', 'kanban'],
    route: ['/deals', 'kanban'],
  },
  {
    id: 'nav-quotes',
    label: 'Quotes',
    category: 'CPQ',
    icon: 'request_quote',
    keywords: ['proposals', 'cpq'],
    route: ['/quotes'],
  },
  {
    id: 'nav-catalog',
    label: 'Catalog',
    category: 'CPQ',
    icon: 'inventory_2',
    keywords: ['products', 'sku'],
    route: ['/catalog'],
  },
  {
    id: 'nav-price-books',
    label: 'Price Books',
    category: 'CPQ',
    icon: 'menu_book',
    keywords: ['pricing', 'pricebooks', 'lists'],
    route: ['/price-books'],
  },
  {
    id: 'nav-pricing-simulator',
    label: 'Pricing Simulator',
    category: 'CPQ',
    icon: 'science',
    keywords: ['simulate', 'bulk', 'pricing'],
    route: ['/pricing/simulator'],
  },
  {
    id: 'nav-approvals',
    label: 'Approvals',
    category: 'CPQ',
    icon: 'fact_check',
    keywords: ['deal desk', 'inbox', 'review'],
    route: ['/approvals'],
  },
  {
    id: 'nav-product-bundles',
    label: 'Product Bundles',
    category: 'CPQ',
    icon: 'inventory',
    keywords: ['bundles', 'packages', 'options'],
    route: ['/product-bundles'],
  },
  {
    id: 'nav-rule-sets',
    label: 'Rule Sets',
    category: 'CPQ',
    icon: 'rule',
    keywords: ['rules', 'pricing rules', 'configuration'],
    route: ['/rule-sets'],
  },
  {
    id: 'action-new-lead',
    label: 'New Lead',
    category: 'Quick Create',
    icon: 'person_add',
    keywords: ['create lead', 'add lead'],
    route: ['/leads', 'new'],
  },
  {
    id: 'action-new-opportunity',
    label: 'New Opportunity',
    category: 'Quick Create',
    icon: 'add_business',
    keywords: ['create opportunity', 'new deal', 'create deal'],
    route: ['/deals', 'new'],
  },
  {
    id: 'action-new-quote',
    label: 'New Quote',
    category: 'Quick Create',
    icon: 'post_add',
    keywords: ['create quote', 'new proposal'],
    route: ['/quotes', 'new'],
  },
];

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    MatSidenavModule,
    MatProgressBarModule,
    ToolbarComponent,
    SidebarComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(window:keydown)': 'onWindowKeydown($event)',
  },
  template: `
    <div class="shell-container">
      <a class="skip-link" href="#main-content" (click)="skipToMain($event, mainContent)">Skip to main content</a>
      <app-toolbar
        (toggleSidenav)="onToggleSidenav()"
        (openCommandPalette)="openCommandPalette()"
      />
      @if (loadingService.loading()) {
        <mat-progress-bar
          class="global-loading"
          mode="indeterminate"
          aria-label="Loading"
          role="progressbar"
        />
      }
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
          <main #mainContent id="main-content" class="page-container" tabindex="-1">
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
        padding: var(--crm-space-sm, 8px) var(--crm-space-md, 16px);
        background: var(--crm-color-brand, #1b5fd1);
        color: var(--crm-color-on-brand, #fff);
        text-decoration: none;
        border-radius: 0 0 var(--crm-radius-sm, 4px) 0;
      }
      .skip-link:focus {
        left: 0;
        outline: 3px solid var(--crm-focus-ring, #ffb747);
        outline-offset: 2px;
      }
      .global-loading {
        position: relative;
        z-index: 2;
        height: 3px;
      }
      .sidenav-container {
        flex: 1;
      }
      .sidenav {
        width: 240px;
      }
      .content {
        background-color: var(--crm-color-canvas, #f4f7fb);
      }
      .page-container {
        padding: var(--crm-space-lg, 24px);
        max-width: var(--crm-page-max, 1400px);
        margin: 0 auto;
      }
      .page-container:focus {
        outline: none;
      }
      @media (max-width: 767.98px) {
        .page-container {
          padding: var(--crm-space-md, 16px);
        }
      }
    `,
  ],
})
export class ShellComponent implements OnInit {
  @ViewChild('sidenavRef') sidenav!: MatSidenav;

  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly destroyRef = inject(DestroyRef);
  private readonly commandPalette = inject(CommandPaletteService);
  readonly loadingService = inject(LoadingService);
  readonly commandItems: CommandPaletteItem[] = WORKSPACE_COMMANDS;

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

  skipToMain(event: MouseEvent, mainContent: HTMLElement): void {
    event.preventDefault();
    mainContent.focus();
  }

  onOpenedChange(opened: boolean): void {
    this.sidenavOpened.set(opened);
  }

  openCommandPalette(): void {
    this.commandPalette.open(this.commandItems);
  }

  onWindowKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      if (this.commandPalette.isOpen()) {
        this.commandPalette.close();
      } else {
        this.openCommandPalette();
      }
    }
  }
}
