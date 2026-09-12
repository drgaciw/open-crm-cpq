import { Component, inject, OnInit, input, ChangeDetectionStrategy } from '@angular/core';
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
        <button mat-icon-button (click)="goBack()" aria-label="Go back to organizations">
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
            <mat-icon>delete</mat-icon> Delete
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
            <mat-card-title>Contacts at this organization</mat-card-title>
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
          </mat-card-content>
        </mat-card>
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

  id = input.required<string>();

  ngOnInit(): void {
    const id = Number(this.id());
    this.orgService.getById(id);
    this.orgService.loadOrganizationContacts(id);
  }

  clearError(): void {
    this.orgService.error.set(null);
  }

  onRetry(): void {
    const id = Number(this.id());
    this.orgService.getById(id);
    this.orgService.loadOrganizationContacts(id);
  }

  goBack(): void {
    this.router.navigate(['/organizations']);
  }

  onEdit(): void {
    this.router.navigate(['/organizations', this.id(), 'edit']);
  }

  onDelete(): void {
    const org = this.orgService.selectedOrganization();
    if (org && confirm(`Delete "${org.name}"?`)) {
      this.orgService.delete(org.id).subscribe(() => {
        this.router.navigate(['/organizations']);
      });
    }
  }
}
