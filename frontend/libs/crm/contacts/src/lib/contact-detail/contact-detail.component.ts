import { Component, inject, OnInit, input, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ContactService } from '../contact.service';

@Component({
  selector: 'app-contact-detail',
  standalone: true,
  imports: [RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatChipsModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (service.loading()) {
      <div class="loading" role="status" aria-live="polite"><mat-spinner diameter="40"></mat-spinner></div>
    } @else if (service.error(); as err) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ err }}</span>
        <button mat-button type="button" (click)="clearError()">Dismiss</button>
        <button mat-button type="button" (click)="retry()">Retry</button>
      </div>
    } @else if (service.selectedContact(); as contact) {
      <div class="detail-header">
        <button mat-icon-button (click)="goBack()" aria-label="Go back to contacts"><mat-icon>arrow_back</mat-icon></button>
        <div class="header-info">
          <h1>{{ contact.firstName }} {{ contact.lastName }}</h1>
          @if (contact.jobTitle) { <span class="subtitle">{{ contact.jobTitle }}</span> }
        </div>
        <div class="header-actions">
          <button mat-stroked-button (click)="onEdit()"><mat-icon>edit</mat-icon> Edit</button>
          <button mat-stroked-button color="warn" (click)="onDelete()"><mat-icon>delete</mat-icon> Delete</button>
        </div>
      </div>

      <div class="detail-grid">
        <mat-card>
          <mat-card-header><mat-card-title>Contact Information</mat-card-title></mat-card-header>
          <mat-card-content>
            <div class="info-grid">
              <div class="info-item"><span class="label">Email</span><span class="value">{{ contact.email }}</span></div>
              <div class="info-item"><span class="label">Phone</span><span class="value">{{ contact.phone || '—' }}</span></div>
              <div class="info-item"><span class="label">Department</span><span class="value">{{ contact.department || '—' }}</span></div>
            </div>
          </mat-card-content>
        </mat-card>

        @if (contact.organizationId && contact.organizationName) {
          <mat-card>
            <mat-card-header><mat-card-title>Organization</mat-card-title></mat-card-header>
            <mat-card-content>
              <div class="org-link">
                <mat-icon>business</mat-icon>
                <a [routerLink]="['/organizations', contact.organizationId]" class="link">
                  {{ contact.organizationName }}
                </a>
              </div>
            </mat-card-content>
          </mat-card>
        }

        @if (contact.notes) {
          <mat-card class="full-width">
            <mat-card-header><mat-card-title>Notes</mat-card-title></mat-card-header>
            <mat-card-content><p>{{ contact.notes }}</p></mat-card-content>
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
    .detail-header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
    .header-info { flex: 1; }
    .header-info h1 { margin: 0; font-size: 1.5rem; }
    .subtitle { color: #666; }
    .header-actions { display: flex; gap: 8px; }
    .detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(350px, 1fr)); gap: 16px; }
    .full-width { grid-column: 1 / -1; }
    .info-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; padding: 16px 0; }
    .info-item { display: flex; flex-direction: column; gap: 4px; }
    .label { font-size: 0.75rem; color: #666; text-transform: uppercase; letter-spacing: 0.5px; }
    .value { font-size: 0.95rem; }
    .org-link { display: flex; align-items: center; gap: 8px; padding: 16px 0; }
    .link { color: #1976d2; text-decoration: none; font-weight: 500; }
    .link:hover { text-decoration: underline; }
  `],
})
export class ContactDetailComponent implements OnInit {
  readonly service = inject(ContactService);
  private readonly router = inject(Router);
  id = input.required<string>();

  ngOnInit(): void { this.service.getById(Number(this.id())); }

  clearError(): void {
    this.service.error.set(null);
  }

  retry(): void {
    this.service.getById(Number(this.id()));
  }

  goBack(): void { this.router.navigate(['/contacts']); }
  onEdit(): void { this.router.navigate(['/contacts', this.id(), 'edit']); }
  onDelete(): void {
    const c = this.service.selectedContact();
    if (c && confirm(`Delete "${c.firstName} ${c.lastName}"?`)) {
      this.service.delete(c.id).subscribe(() => this.router.navigate(['/contacts']));
    }
  }
}
