import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { NAV_GROUPS, NAV_ITEMS, NavGroup, NavItem } from './nav-items';

interface NavSection {
  readonly group: NavGroup;
  readonly items: readonly NavItem[];
}

const NAV_SECTIONS: readonly NavSection[] = NAV_GROUPS.map((group) => ({
  group,
  items: NAV_ITEMS.filter((item) => item.group === group),
}));

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatListModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sidebar-header">
      <h2 class="sidebar-title">Open CRM CPQ</h2>
    </div>
    <mat-nav-list>
      @for (section of sections; track section.group) {
        <h3 class="nav-group">{{ section.group }}</h3>
        @for (item of section.items; track item.route) {
          <a mat-list-item [routerLink]="item.route" routerLinkActive="active-link">
            <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
            <span matListItemTitle>{{ item.label }}</span>
          </a>
        }
      }
    </mat-nav-list>
  `,
  styles: [
    `
      :host {
        display: flex;
        flex-direction: column;
        height: 100%;
      }
      .sidebar-header {
        padding: var(--crm-space-md, 16px) var(--crm-space-lg, 24px);
        border-bottom: 1px solid var(--crm-color-line, #d8e0ed);
      }
      .sidebar-title {
        margin: 0;
        font-size: 1.25rem;
        font-weight: 600;
        color: var(--crm-color-brand, #1b5fd1);
      }
      .nav-group {
        margin: var(--crm-space-md, 16px) var(--crm-space-md, 16px) var(--crm-space-xs, 4px);
        font-size: 0.7rem;
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--crm-color-muted, #52627b);
      }
      .active-link {
        background-color: color-mix(in srgb, var(--crm-color-brand, #1b5fd1) 10%, transparent) !important;
        color: var(--crm-color-brand, #1b5fd1) !important;
      }
    `,
  ],
})
export class SidebarComponent {
  readonly sections = NAV_SECTIONS;
}
