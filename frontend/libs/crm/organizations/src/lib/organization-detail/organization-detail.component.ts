import { Component, inject, OnInit, input, ChangeDetectionStrategy, signal, effect, Injector, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OrganizationService } from '../organization.service';

@Component({
  selector: 'app-organization-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (orgService.error()) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ orgService.error() }}</span>
        <button mat-button type="button" (click)="onRetry()">Retry</button>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
      </div>
    }

    @if (orgService.loading()) {
      <div class="loading" role="status" aria-live="polite"><mat-spinner diameter="40"></mat-spinner></div>
    } @else if (orgService.selectedOrganization(); as org) {
      <div class="detail-header">
        <button mat-icon-button (click)="goBack()" aria-label="Go back to accounts">
          <mat-icon>arrow_back</mat-icon>
        </button>
        <div class="header-info">
          <h1>{{ org.name }}</h1>
          @if (org.industry) {
            <mat-chip>{{ org.industry }}</mat-chip>
          }
        </div>
        <div class="header-actions">
          <button mat-stroked-button (click)="onEdit()">
            <mat-icon>edit</mat-icon> Edit
          </button>
          <button mat-stroked-button color="warn" (click)="onDelete()">
            <mat-icon>archive</mat-icon> Archive
          </button>
        </div>
      </div>

      <div class="detail-grid">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Contact Information</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Phone</span>
                <span class="value">{{ org.phone || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Territory</span>
                <span class="value">{{ org.territory || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Owner</span>
                <span class="value">{{ org.ownerId || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Website</span>
                <span class="value">{{ org.website || '—' }}</span>
              </div>
              @if (org.linkedinUrl) {
                <div class="info-item">
                  <span class="label">LinkedIn</span>
                  <span class="value">{{ org.linkedinUrl }}</span>
                </div>
              }
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card>
          <mat-card-header>
            <mat-card-title>Address</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">Address Line 1</span>
                <span class="value">{{ org.addressLine1 || '—' }}</span>
              </div>
              @if (org.addressLine2) {
                <div class="info-item">
                  <span class="label">Address Line 2</span>
                  <span class="value">{{ org.addressLine2 }}</span>
                </div>
              }
              <div class="info-item">
                <span class="label">City</span>
                <span class="value">{{ org.city || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">State</span>
                <span class="value">{{ org.state || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Postal Code</span>
                <span class="value">{{ org.postalCode || '—' }}</span>
              </div>
              <div class="info-item">
                <span class="label">Country</span>
                <span class="value">{{ org.country || '—' }}</span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        @if (org.description) {
          <mat-card class="full-width">
            <mat-card-header>
              <mat-card-title>Description</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <p>{{ org.description }}</p>
            </mat-card-content>
          </mat-card>
        }

        <mat-card class="full-width">
          <mat-card-header>
            <mat-card-title>Contacts at this account</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (orgService.organizationContactsLoading()) {
              <div class="loading-inline"><mat-spinner diameter="32"></mat-spinner></div>
            } @else if (orgService.organizationContacts().length === 0) {
              <p class="empty-text">No linked contacts yet.</p>
            } @else {
              <ul class="contact-list">
                @for (c of orgService.organizationContacts(); track c.id) {
                  <li>
                    <a [routerLink]="['/contacts', c.id]" class="contact-link">
                      {{ c.firstName }} {{ c.lastName }}
                    </a>
                    @if (c.email) {
                      <span class="contact-meta">{{ c.email }}</span>
                    }
                  </li>
                }
              </ul>
            }
            <p class="section-links">
              <a routerLink="/contacts">Browse contacts</a>
              <a routerLink="/contacts/new">New contact</a>
            </p>
          </mat-card-content>
        </mat-card>

        <mat-card class="full-width" data-testid="account-deals">
          <mat-card-header>
            <mat-card-title>Deals</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (orgService.relatedDealsLoading()) {
              <div class="loading-inline"><mat-spinner diameter="32"></mat-spinner></div>
            } @else if (orgService.relatedDealsError()) {
              <p class="empty-text" role="alert">{{ orgService.relatedDealsError() }}</p>
            } @else if (orgService.relatedDeals().length === 0) {
              <p class="empty-text">No deals linked to this account.</p>
            } @else {
              <ul class="contact-list">
                @for (deal of orgService.relatedDeals(); track deal.id) {
                  <li>
                    <a [routerLink]="['/deals', deal.id]" class="contact-link">{{ deal.name }}</a>
                    <span class="contact-meta">{{ deal.stage }}</span>
                  </li>
                }
              </ul>
            }
            <p class="section-links">
              <a routerLink="/deals">Browse deals</a>
              <a routerLink="/deals/new">New deal</a>
            </p>
          </mat-card-content>
        </mat-card>

        <mat-card class="full-width" data-testid="account-quotes">
          <mat-card-header>
            <mat-card-title>Quotes</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (orgService.relatedQuotesLoading()) {
              <div class="loading-inline"><mat-spinner diameter="32"></mat-spinner></div>
            } @else if (orgService.relatedQuotesError()) {
              <p class="empty-text" role="alert">{{ orgService.relatedQuotesError() }}</p>
            } @else if (orgService.relatedQuotes().length === 0) {
              <p class="empty-text">No quotes found for this account ID.</p>
            } @else {
              <ul class="contact-list">
                @for (quote of orgService.relatedQuotes(); track quote.id) {
                  <li>
                    <a [routerLink]="['/quotes', quote.id]" class="contact-link">{{ quote.name }}</a>
                    <span class="contact-meta">{{ quote.status }}</span>
                  </li>
                }
              </ul>
            }
            @if (orgService.relatedQuotesTruncated()) {
              <p class="empty-text" role="status">Showing up to 250 matching quotes. Browse quotes for more.</p>
            }
            <p class="empty-text">Some quotes with older or differently formatted customer references may not appear. Browse all quotes.</p>
            <p class="section-links">
              <a routerLink="/quotes">Browse quotes</a>
              <a routerLink="/quotes/new">New quote</a>
            </p>
          </mat-card-content>
        </mat-card>

        @if (orgService.hierarchyAvailable()) {
          <mat-card class="full-width" data-testid="account-hierarchy">
            <mat-card-header>
              <mat-card-title>Account hierarchy</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <nav aria-label="Account hierarchy" class="hierarchy-nav">
                @for (ancestor of orgService.hierarchy()?.ancestors ?? []; track ancestor.id) {
                  <a [routerLink]="['/organizations', ancestor.id]">{{ ancestor.name }}</a>
                  <span aria-hidden="true"> / </span>
                }
                <span>{{ org.name }}</span>
              </nav>
              @if (orgService.hierarchy()?.parent; as parent) {
                <p>Parent: <a [routerLink]="['/organizations', parent.id]">{{ parent.name }}</a></p>
              } @else {
                <p>No parent account.</p>
              }
              <h3>Child accounts</h3>
              @if ((orgService.hierarchy()?.children ?? []).length === 0) {
                <p class="empty-text">No child accounts.</p>
              } @else {
                <ul class="contact-list">
                  @for (child of orgService.hierarchy()?.children ?? []; track child.id) {
                    <li>
                      <a [routerLink]="['/organizations', child.id]" class="contact-link">{{ child.name }}</a>
                    </li>
                  }
                </ul>
              }
              @if (orgService.hierarchyError(); as hierarchyError) {
                <div class="error-banner" role="alert" data-testid="hierarchy-error">
                  <mat-icon>error_outline</mat-icon>
                  <span>{{ hierarchyError }}</span>
                </div>
              }
              <div class="parent-form">
                <label>
                  Parent account id
                  <input
                    data-testid="account-parent-id"
                    type="text"
                    inputmode="numeric"
                    [value]="parentIdDraft()"
                    (input)="onParentInput($event)"
                  />
                </label>
                <button mat-stroked-button type="button" data-testid="account-set-parent" (click)="onSetParent()">
                  Set parent
                </button>
                <button mat-button type="button" (click)="onClearParent()">Clear parent</button>
              </div>
            </mat-card-content>
          </mat-card>
        }


      </div>
    }
  `,
  styles: [`
    .loading { display: flex; justify-content: center; padding: 48px; }
    .error-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 16px;
      padding: 12px 16px;
      border-radius: 8px;
      background: #ffebee;
      color: #c62828;
    }
    .detail-header {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 24px;
      flex-wrap: wrap;
    }
    .header-info { flex: 1; }
    .header-info h1 { margin: 0; font-size: 1.5rem; }
    .header-actions { display: flex; gap: 8px; }
    .detail-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
      gap: 16px;
    }
    .full-width { grid-column: 1 / -1; }
    .loading-inline { display: flex; justify-content: center; padding: 16px; }
    .contact-list { list-style: none; margin: 0; padding: 0; }
    .contact-list li { padding: 8px 0; border-bottom: 1px solid #eee; display: flex; flex-wrap: wrap; gap: 12px; align-items: baseline; }
    .contact-link { color: #1976d2; text-decoration: none; font-weight: 500; }
    .contact-link:hover { text-decoration: underline; }
    .contact-meta { font-size: 0.85rem; color: #666; }
    .empty-text { color: #999; margin: 0; }
    .section-links { display: flex; gap: 16px; margin: 12px 0 0; }
    .section-links a { color: #1976d2; text-decoration: none; font-weight: 500; }
    .hierarchy-nav { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-bottom: 8px; }
    .hierarchy-nav a { color: #1976d2; text-decoration: none; }
    .parent-form { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; margin-top: 12px; }
    .parent-form label { display: flex; flex-direction: column; gap: 4px; font-size: 0.75rem; color: #555; }
    .parent-form input { padding: 8px; border: 1px solid #ccc; border-radius: 4px; }
    .info-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      padding: 16px 0;
    }
    .info-item { display: flex; flex-direction: column; gap: 4px; }
    .label { font-size: 0.75rem; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }
    .value { font-size: 0.95rem; }
  `],
})
export class OrganizationDetailComponent implements OnInit {
  readonly orgService = inject(OrganizationService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  id = input.required<string>();
  readonly parentIdDraft = signal('');
  private quotesLoadedFor: number | null = null;

  /** Reload when route `:id` changes (component input binding; not ngOnChanges). */
  private readonly reloadWhenRouteIdChanges = effect(() => {
    const _id = this.id();
    untracked(() => {
      this.parentIdDraft.set('');
      this.loadAccount();
    });
  });

  ngOnInit(): void {
    effect(() => {
      const org = this.orgService.selectedOrganization();
      const id = Number(this.id());
      if (!org || org.id !== id || this.quotesLoadedFor === org.id) {
        return;
      }
      this.quotesLoadedFor = org.id;
      this.orgService.loadRelatedQuotes(org);
    }, { injector: this.injector });
  }

  private loadAccount(): void {
    const id = Number(this.id());
    this.quotesLoadedFor = null;
    this.orgService.resetAccountDetail();
    this.orgService.getById(id);
    this.orgService.loadOrganizationContacts(id);
    this.orgService.loadRelatedDeals(id);
    this.orgService.loadHierarchy(id);
  }

  clearError(): void {
    this.orgService.error.set(null);
  }

  onRetry(): void {
    this.loadAccount();
  }

  onParentInput(event: Event): void {
    this.parentIdDraft.set((event.target as HTMLInputElement).value);
  }

  onSetParent(): void {
    const raw = this.parentIdDraft().trim();
    const parentId = Number(raw);
    if (!raw || !Number.isInteger(parentId)) {
      this.orgService.hierarchyError.set('Enter the numeric id of the parent account.');
      return;
    }
    this.orgService.linkParent(Number(this.id()), parentId);
  }

  onClearParent(): void {
    this.orgService.linkParent(Number(this.id()), null);
  }

  goBack(): void {
    this.router.navigate(['/organizations']);
  }

  onEdit(): void {
    this.router.navigate(['/organizations', this.id(), 'edit']);
  }

  onDelete(): void {
    const org = this.orgService.selectedOrganization();
    if (org && confirm(`Archive "${org.name}"? The account will be hidden from lists.`)) {
      this.orgService.delete(org.id).subscribe({
        next: () => { if (Number(this.id()) === org.id) this.router.navigate(['/organizations']); },
        error: (error: { error?: { errorCode?: string } }) => {
          if (Number(this.id()) !== org.id) return;
          this.orgService.error.set(error?.error?.errorCode === 'HIERARCHY_HAS_CHILDREN'
            ? 'This account has active child accounts. Hierarchy management is unavailable; contact an administrator.'
            : 'Could not archive this account. Please try again.');
        },
      });
    }
  }
}
