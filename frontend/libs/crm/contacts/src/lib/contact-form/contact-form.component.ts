import {
  Component,
  inject,
  OnInit,
  input,
  signal,
  effect,
  ChangeDetectionStrategy,
  Injector,
} from '@angular/core';
import { Router } from '@angular/router';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ContactService } from '../contact.service';
import { OrganizationService } from '@frontend/crm-organizations';
import { OrganizationDto } from '@frontend/shared-models';

@Component({
  selector: 'app-contact-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatAutocompleteModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="form-header">
      <button mat-icon-button (click)="goBack()" aria-label="Go back">
        <mat-icon>arrow_back</mat-icon>
      </button>
      <h1>{{ isEditMode ? 'Edit Contact' : 'New Contact' }}</h1>
    </div>

    <mat-card class="form-card">
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <h3 class="section-title">Personal Information</h3>
          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>First Name</mat-label>
              <input matInput formControlName="firstName" />
              @if (form.get('firstName')?.hasError('required')) {
                <mat-error>Required</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Last Name</mat-label>
              <input matInput formControlName="lastName" />
              @if (form.get('lastName')?.hasError('required')) {
                <mat-error>Required</mat-error>
              }
            </mat-form-field>
          </div>

          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput formControlName="email" type="email" />
              @if (form.get('email')?.hasError('email')) {
                <mat-error>Invalid email</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Phone</mat-label>
              <input matInput formControlName="phone" />
            </mat-form-field>
          </div>

          <h3 class="section-title">Work Information</h3>
          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Job Title</mat-label>
              <input
                matInput
                formControlName="jobTitle"
                placeholder="e.g. VP of Sales"
              />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Department</mat-label>
              <input matInput formControlName="department" />
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Organization</mat-label>
              <input
                matInput
                formControlName="organizationSearch"
                [matAutocomplete]="orgAuto"
                (input)="searchOrgs($event)"
              />
              <mat-autocomplete
                #orgAuto="matAutocomplete"
                [displayWith]="displayOrg"
                (optionSelected)="onOrgSelected($event.option.value)"
              >
                @for (org of filteredOrgs(); track org.id) {
                  <mat-option [value]="org">{{ org.name }}</mat-option>
                }
              </mat-autocomplete>
            </mat-form-field>
          </div>

          <h3 class="section-title">Additional</h3>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Notes</mat-label>
              <textarea matInput formControlName="notes" rows="4"></textarea>
            </mat-form-field>
          </div>

          <div class="form-actions">
            <button mat-button type="button" (click)="goBack()">Cancel</button>
            <button
              mat-raised-button
              color="primary"
              type="submit"
              [disabled]="form.invalid || saving"
            >
              @if (saving) {
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
  styles: [
    `
      .form-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 24px;
      }
      .form-header h1 {
        margin: 0;
        font-size: 1.5rem;
      }
      .form-card {
        max-width: 800px;
      }
      .section-title {
        margin: 24px 0 12px;
        font-size: 1rem;
        font-weight: 500;
        color: #333;
      }
      .section-title:first-of-type {
        margin-top: 0;
      }
      .form-row {
        display: flex;
        gap: 16px;
        margin-bottom: 8px;
      }
      .two-col > * {
        flex: 1;
      }
      .full-width {
        width: 100%;
      }
      .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
        margin-top: 24px;
        padding-top: 16px;
        border-top: 1px solid #eee;
      }
    `,
  ],
})
export class ContactFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly contactService = inject(ContactService);
  private readonly orgService = inject(OrganizationService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  id = input<string>();
  form!: FormGroup;
  isEditMode = false;
  saving = false;
  selectedOrgId: number | null = null;
  filteredOrgs = signal<OrganizationDto[]>([]);

  constructor() {
    // F1: effect() created once in constructor to reactively update filteredOrgs
    effect(() => {
      const orgs = this.orgService.organizations();
      if (orgs.length > 0 || !this.orgService.loading()) {
        this.filteredOrgs.set(orgs);
      }
    });
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      email: ['', Validators.email],
      phone: [''],
      jobTitle: [''],
      department: [''],
      organizationSearch: [''],
      notes: [''],
    });

    const idVal = this.id();
    if (idVal) {
      this.isEditMode = true;
      this.contactService.getById(Number(idVal));
      effect(
        () => {
          const c = this.contactService.selectedContact();
          if (c) {
            this.form.patchValue({
              firstName: c.firstName,
              lastName: c.lastName,
              email: c.email,
              phone: c.phone,
              jobTitle: c.jobTitle,
              department: c.department,
              notes: c.notes,
              organizationSearch: c.organizationName ?? '',
            });
            if (c.organizationId) this.selectedOrgId = c.organizationId;
          }
        },
        { injector: this.injector },
      );
    }
  }

  searchOrgs(event: Event): void {
    const q = (event.target as HTMLInputElement).value;
    if (q.length >= 2) {
      this.orgService.search(q);
    }
  }

  displayOrg(org: OrganizationDto): string {
    return org?.name ?? '';
  }

  onOrgSelected(org: OrganizationDto): void {
    this.selectedOrgId = org.id;
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.saving = true;
    const { organizationSearch, ...formValue } = this.form.value;
    const data = { ...formValue, organizationId: this.selectedOrgId };

    const idVal = this.id();
    if (this.isEditMode && idVal) {
      this.contactService.update(Number(idVal), data).subscribe({
        next: () => this.router.navigate(['/contacts', idVal]),
        error: () => (this.saving = false),
      });
    } else {
      this.contactService.create(data).subscribe({
        next: (c) => this.router.navigate(['/contacts', c.id]),
        error: () => (this.saving = false),
      });
    }
  }

  goBack(): void {
    const idVal = this.id();
    this.router.navigate(
      this.isEditMode && idVal ? ['/contacts', idVal] : ['/contacts'],
    );
  }
}
