import { Component, inject, OnInit, input, effect, signal, ChangeDetectionStrategy, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OrganizationType } from '@frontend/shared-models';
import { ACCOUNT_INDUSTRIES } from '../account-industries';
import { OrganizationService } from '../organization.service';

@Component({
  selector: 'app-organization-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="form-header">
      <button mat-icon-button (click)="goBack()" aria-label="Go back">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <h1>{{ isEditMode ? 'Edit Account' : 'New Account' }}</h1>
    </div>

    <mat-card class="form-card">
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <h3 class="section-title">Basic Information</h3>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Account Name</mat-label>
              <input matInput formControlName="name" placeholder="Enter account name">
              @if (form.get('name')?.hasError('required')) {
                <mat-error>Name is required</mat-error>
              }
            </mat-form-field>
          </div>

          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Industry</mat-label>
              <mat-select formControlName="industry">
                <mat-option value="">None</mat-option>
                @for (ind of industries; track ind) {
                  <mat-option [value]="ind">{{ ind }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Website</mat-label>
              <input matInput formControlName="website" placeholder="https://example.com">
            </mat-form-field>
          </div>

          <div class="form-row three-col">
            <mat-form-field appearance="outline">
              <mat-label>Type</mat-label>
              <mat-select formControlName="type">
                <mat-option value="">None</mat-option>
                @for (accountType of accountTypes; track accountType) {
                  <mat-option [value]="accountType">{{ accountType }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Territory</mat-label>
              <input matInput formControlName="territory" placeholder="West">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Owner</mat-label>
              <input matInput formControlName="ownerId" placeholder="Owner id">
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" style="max-width: 300px;">
              <mat-label>Phone</mat-label>
              <input matInput formControlName="phone" placeholder="+1 (555) 000-0000">
            </mat-form-field>
          </div>

          <h3 class="section-title">Address</h3>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Address Line 1</mat-label>
              <input matInput formControlName="addressLine1">
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Address Line 2</mat-label>
              <input matInput formControlName="addressLine2">
            </mat-form-field>
          </div>

          <div class="form-row three-col">
            <mat-form-field appearance="outline">
              <mat-label>City</mat-label>
              <input matInput formControlName="city">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>State</mat-label>
              <input matInput formControlName="state">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Postal Code</mat-label>
              <input matInput formControlName="postalCode">
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" style="max-width: 300px;">
              <mat-label>Country</mat-label>
              <input matInput formControlName="country">
            </mat-form-field>
          </div>

          <h3 class="section-title">Additional</h3>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Description</mat-label>
              <textarea matInput formControlName="description" rows="4"></textarea>
            </mat-form-field>
          </div>

          <div class="form-actions">
            <button mat-button type="button" (click)="goBack()">Cancel</button>
            <button mat-raised-button color="primary" type="submit"
                    [disabled]="form.invalid || saving()">
              @if (saving()) {
                <mat-spinner diameter="20"></mat-spinner>
              } @else {
                {{ isEditMode ? 'Update' : 'Create' }}
              }
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .form-header { display: flex; align-items: center; gap: 8px; margin-bottom: 24px; }
    .form-header h1 { margin: 0; font-size: 1.5rem; }
    .form-card { max-width: 800px; }
    .section-title { margin: 24px 0 12px; font-size: 1rem; font-weight: 500; color: #333; }
    .section-title:first-of-type { margin-top: 0; }
    .form-row { display: flex; gap: 16px; margin-bottom: 8px; }
    .two-col > * { flex: 1; }
    .three-col > * { flex: 1; }
    .full-width { width: 100%; }
    .form-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; padding-top: 16px; border-top: 1px solid #eee; }
  `],
})
export class OrganizationFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly orgService = inject(OrganizationService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  id = input<string>();

  form!: FormGroup;
  isEditMode = false;
  /** Signal so OnPush refreshes after async HTTP error (chaos: offline/500). */
  saving = signal(false);

  industries = [...ACCOUNT_INDUSTRIES];
  accountTypes: OrganizationType[] = [
    'PROSPECT',
    'CUSTOMER',
    'PARTNER',
    'VENDOR',
    'COMPETITOR',
    'OTHER',
  ];

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', Validators.required],
      industry: [''],
      website: [''],
      phone: [''],
      addressLine1: [''],
      addressLine2: [''],
      city: [''],
      state: [''],
      postalCode: [''],
      country: [''],
      description: [''],
      territory: [''],
      ownerId: [''],
      type: [''],
    });

    const idVal = this.id();
    if (idVal) {
      this.isEditMode = true;
      this.orgService.getById(Number(idVal));
      // Patch form when data loads
      effect(() => {
        const org = this.orgService.selectedOrganization();
        if (org) {
          this.form.patchValue(org);
        }
      }, { injector: this.injector });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.saving.set(true);

    const data = this.payload();
    const idVal = this.id();

    if (this.isEditMode && idVal) {
      this.orgService.update(Number(idVal), data).subscribe({
        next: () => this.router.navigate(['/organizations', idVal]),
        error: () => this.saving.set(false),
      });
    } else {
      this.orgService.create(data).subscribe({
        next: (org) => this.router.navigate(['/organizations', org.id]),
        error: () => this.saving.set(false),
      });
    }
  }

  /** Drop a blank enum so Jackson does not reject `type: ""`. */
  private payload(): ReturnType<FormGroup['getRawValue']> {
    const data = { ...this.form.value } as { type?: string };
    if (!data.type) {
      delete data.type;
    }
    return data;
  }

  goBack(): void {
    const idVal = this.id();
    if (this.isEditMode && idVal) {
      this.router.navigate(['/organizations', idVal]);
    } else {
      this.router.navigate(['/organizations']);
    }
  }
}
