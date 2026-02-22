import { Component, inject, OnInit, input, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
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
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (orgService.loading()) {
      <div class="loading"><mat-spinner diameter="40"></mat-spinner></div>
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
      </div>
    }
  `,
  styles: [`
    .loading { display: flex; justify-content: center; padding: 48px; }
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
    this.orgService.getById(Number(this.id()));
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
