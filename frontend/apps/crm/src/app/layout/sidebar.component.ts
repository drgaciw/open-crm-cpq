import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';

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
      <h3 class="nav-group">CRM</h3>
      <a mat-list-item routerLink="/dashboard" routerLinkActive="active-link">
        <mat-icon matListItemIcon>dashboard</mat-icon>
        <span matListItemTitle>Dashboard</span>
      </a>
      <a mat-list-item routerLink="/organizations" routerLinkActive="active-link">
        <mat-icon matListItemIcon>business</mat-icon>
        <span matListItemTitle>Accounts</span>
      </a>
      <a mat-list-item routerLink="/contacts" routerLinkActive="active-link">
        <mat-icon matListItemIcon>people</mat-icon>
        <span matListItemTitle>Contacts</span>
      </a>
      <a mat-list-item routerLink="/leads" routerLinkActive="active-link">
        <mat-icon matListItemIcon>leaderboard</mat-icon>
        <span matListItemTitle>Leads</span>
      </a>
      <a mat-list-item routerLink="/deals" routerLinkActive="active-link">
        <mat-icon matListItemIcon>handshake</mat-icon>
        <span matListItemTitle>Opportunities</span>
      </a>

      <h3 class="nav-group">CPQ</h3>
      <a mat-list-item routerLink="/catalog" routerLinkActive="active-link">
        <mat-icon matListItemIcon>inventory_2</mat-icon>
        <span matListItemTitle>Catalog</span>
      </a>
      <a mat-list-item routerLink="/quotes" routerLinkActive="active-link">
        <mat-icon matListItemIcon>request_quote</mat-icon>
        <span matListItemTitle>Quotes</span>
      </a>
      <a mat-list-item routerLink="/approvals" routerLinkActive="active-link">
        <mat-icon matListItemIcon>fact_check</mat-icon>
        <span matListItemTitle>Approvals</span>
      </a>
      <a mat-list-item routerLink="/price-books" routerLinkActive="active-link">
        <mat-icon matListItemIcon>menu_book</mat-icon>
        <span matListItemTitle>Price Books</span>
      </a>
      <a mat-list-item routerLink="/pricing-customers" routerLinkActive="active-link">
        <mat-icon matListItemIcon>groups</mat-icon>
        <span matListItemTitle>Pricing Customers</span>
      </a>
      <a mat-list-item routerLink="/rule-sets" routerLinkActive="active-link">
        <mat-icon matListItemIcon>rule</mat-icon>
        <span matListItemTitle>Rule Sets</span>
      </a>
      <a mat-list-item routerLink="/product-bundles" routerLinkActive="active-link">
        <mat-icon matListItemIcon>inventory</mat-icon>
        <span matListItemTitle>Bundles</span>
      </a>
      <a mat-list-item routerLink="/pricing/simulator" routerLinkActive="active-link">
        <mat-icon matListItemIcon>science</mat-icon>
        <span matListItemTitle>Pricing Simulator</span>
      </a>
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
export class SidebarComponent {}
