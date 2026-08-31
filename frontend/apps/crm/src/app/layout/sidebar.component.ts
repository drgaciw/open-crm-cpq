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
      <h2 class="sidebar-title">Open CRM</h2>
    </div>
    <mat-nav-list>
      <a mat-list-item routerLink="/dashboard" routerLinkActive="active-link">
        <mat-icon matListItemIcon>dashboard</mat-icon>
        <span matListItemTitle>Dashboard</span>
      </a>
      <a mat-list-item routerLink="/organizations" routerLinkActive="active-link">
        <mat-icon matListItemIcon>business</mat-icon>
        <span matListItemTitle>Organizations</span>
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
        <span matListItemTitle>Deals</span>
      </a>
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
        <span matListItemTitle>Pricing customers</span>
      </a>
      <a mat-list-item routerLink="/rule-sets" routerLinkActive="active-link">
        <mat-icon matListItemIcon>rule</mat-icon>
        <span matListItemTitle>Rule sets</span>
      </a>
      <a mat-list-item routerLink="/pricing/simulator" routerLinkActive="active-link">
        <mat-icon matListItemIcon>science</mat-icon>
        <span matListItemTitle>Pricing Simulator</span>
      </a>
    </mat-nav-list>
  `,
  styles: [`
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
    }
    .sidebar-header {
      padding: 16px 24px;
      border-bottom: 1px solid rgba(0, 0, 0, 0.12);
    }
    .sidebar-title {
      margin: 0;
      font-size: 1.25rem;
      font-weight: 600;
      color: #1976d2;
    }
    .active-link {
      background-color: rgba(25, 118, 210, 0.08) !important;
      color: #1976d2 !important;
    }
  `],
})
export class SidebarComponent {}
