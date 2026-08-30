import { Component, inject, OnInit, input, signal, effect, ChangeDetectionStrategy, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { LeadService } from '../lead.service';
import { ContactService } from '@frontend/crm-contacts';
import { OrganizationService } from '@frontend/crm-organizations';
import { ContactDto, LeadPriority, OrganizationDto } from '@frontend/shared-models';

@Component({
  selector: 'lib-lead-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatAutocompleteModule, MatProgressSpinnerModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="form-header">
      <button mat-icon-button (click)="goBack()" aria-label="Go back"><mat-icon>arrow_back</mat-icon></button>
      <h1>{{ isEditMode ? 'Edit Lead' : 'New Lead' }}</h1>
    </div>

    <mat-card class="form-card">
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <h3 class="section-title">Lead Information</h3>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Title</mat-label>
              <input matInput formControlName="title" placeholder="e.g. Acme Corp - CRM Implementation">
              @if (form.get('title')?.hasError('required')) {
                <mat-error>Required</mat-error>
              }
            </mat-form-field>
          </div>

          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Source</mat-label>
              <input matInput formControlName="source" placeholder="e.g. Website, Referral, Conference">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Priority</mat-label>
              <mat-select formControlName="priority" aria-label="Lead priority">
                @for (p of priorities; track p) {
                  <mat-option [value]="p">{{ p }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Estimated Value</mat-label>
              <input matInput formControlName="estimatedValue" type="number" placeholder="0">
              <span matTextPrefix>$&nbsp;</span>
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Description</mat-label>
              <textarea matInput formControlName="description" rows="4"
                        placeholder="Describe the opportunity..."></textarea>
            </mat-form-field>
          </div>

          <h3 class="section-title">Associated Records</h3>
          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Contact</mat-label>
              <input matInput formControlName="contactSearch"
                     [matAutocomplete]="contactAuto"
                     (input)="searchContacts($event)"
                     placeholder="Search for contact...">
              <mat-autocomplete #contactAuto="matAutocomplete"
                                [displayWith]="displayContact"
                                (optionSelected)="onContactSelected($event.option.value)">
                @for (contact of filteredContacts(); track contact.id) {
                  <mat-option [value]="contact">
                    {{ contact.firstName }} {{ contact.lastName }}
                    @if (contact.email) {
                      <span class="option-detail">{{ contact.email }}</span>
                    }
                  </mat-option>
                }
              </mat-autocomplete>
              <button matSuffix mat-icon-button type="button"
                      [style.display]="selectedContactId ? '' : 'none'"
                      (click)="clearContact()"
                      aria-label="Clear">
                <mat-icon>close</mat-icon>
              </button>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Organization</mat-label>
              <input matInput formControlName="organizationSearch"
                     [matAutocomplete]="orgAuto"
                     (input)="searchOrgs($event)"
                     placeholder="Search for organization...">
              <mat-autocomplete #orgAuto="matAutocomplete"
                                [displayWith]="displayOrg"
                                (optionSelected)="onOrgSelected($event.option.value)">
                @for (org of filteredOrgs(); track org.id) {
                  <mat-option [value]="org">
                    {{ org.name }}
                    @if (org.website) {
                      <span class="option-detail">{{ org.website }}</span>
                    }
                  </mat-option>
                }
              </mat-autocomplete>
              <button matSuffix mat-icon-button type="button"
                      [style.display]="selectedOrgId ? '' : 'none'"
                      (click)="clearOrg()"
                      aria-label="Clear">
                <mat-icon>close</mat-icon>
              </button>
            </mat-form-field>
          </div>

          <div class="form-actions">
            <button mat-button type="button" (click)="goBack()">Cancel</button>
            <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid || saving">
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
  styles: [`
    .form-header { display: flex; align-items: center; gap: 8px; margin-bottom: 24px; }
    .form-header h1 { margin: 0; font-size: 1.5rem; }
    .form-card { max-width: 800px; }
    .section-title { margin: 24px 0 12px; font-size: 1rem; font-weight: 500; color: #333; }
    .section-title:first-of-type { margin-top: 0; }
    .form-row { display: flex; gap: 16px; margin-bottom: 8px; }
    .two-col > * { flex: 1; }
    .full-width { width: 100%; }
    .form-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; padding-top: 16px; border-top: 1px solid #eee; }
    .option-detail { font-size: 0.75rem; color: #666; margin-left: 8px; }
  `],
})
export class LeadFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly leadService = inject(LeadService);
  private readonly contactService = inject(ContactService);
  private readonly orgService = inject(OrganizationService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  id = input<string>();
  form!: FormGroup;
  isEditMode = false;
  saving = false;
  selectedContactId: number | null = null;
  selectedOrgId: number | null = null;
  filteredContacts = signal<ContactDto[]>([]);
  filteredOrgs = signal<OrganizationDto[]>([]);
  readonly priorities: LeadPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

  constructor() {
    // F1: effect() created once in constructor to reactively update filteredContacts
    effect(() => {
      const contacts = this.contactService.contacts();
      if (contacts.length > 0 || !this.contactService.loading()) {
        this.filteredContacts.set(contacts);
      }
    });

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
      title: ['', Validators.required],
      source: [''],
      priority: ['MEDIUM' as LeadPriority],
      description: [''],
      estimatedValue: [''],
      contactSearch: [''],
      organizationSearch: [''],
    });

    const idVal = this.id();
    if (idVal) {
      this.isEditMode = true;
      this.leadService.getById(Number(idVal));
      effect(() => {
        const lead = this.leadService.selectedLead();
        if (lead) {
          this.form.patchValue({
            title: lead.title,
            source: lead.source,
            priority: lead.priority ?? 'MEDIUM',
            description: lead.description,
            estimatedValue: lead.estimatedValue,
            contactSearch: lead.contactName ?? '',
            organizationSearch: lead.organizationName ?? '',
          });
          if (lead.contactId) this.selectedContactId = lead.contactId;
          if (lead.organizationId) this.selectedOrgId = lead.organizationId;
        }
      }, { injector: this.injector });
    }
  }

  searchContacts(event: Event): void {
    const q = (event.target as HTMLInputElement).value;
    if (q.length >= 2) {
      this.contactService.search(q);
    }
  }

  searchOrgs(event: Event): void {
    const q = (event.target as HTMLInputElement).value;
    if (q.length >= 2) {
      this.orgService.search(q);
    }
  }

  displayContact(contact: ContactDto): string {
    return contact ? `${contact.firstName} ${contact.lastName}` : '';
  }

  displayOrg(org: OrganizationDto): string {
    return org?.name ?? '';
  }

  onContactSelected(contact: ContactDto): void {
    this.selectedContactId = contact.id;
  }

  onOrgSelected(org: OrganizationDto): void {
    this.selectedOrgId = org.id;
  }

  clearContact(): void {
    this.selectedContactId = null;
    this.form.patchValue({ contactSearch: '' });
  }

  clearOrg(): void {
    this.selectedOrgId = null;
    this.form.patchValue({ organizationSearch: '' });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.saving = true;

    const formValue = this.form.value;
    delete formValue.contactSearch;
    delete formValue.organizationSearch;
    const data = {
      ...formValue,
      contactId: this.selectedContactId,
      organizationId: this.selectedOrgId,
      estimatedValue: formValue.estimatedValue ? Number(formValue.estimatedValue) : undefined,
    };

    const idVal = this.id();
    if (this.isEditMode && idVal) {
      this.leadService.update(Number(idVal), data).subscribe({
        next: () => this.router.navigate(['/leads', idVal]),
        error: () => (this.saving = false),
      });
    } else {
      this.leadService.create(data).subscribe({
        next: (lead) => this.router.navigate(['/leads', lead.id]),
        error: () => (this.saving = false),
      });
    }
  }

  goBack(): void {
    const idVal = this.id();
    this.router.navigate(this.isEditMode && idVal ? ['/leads', idVal] : ['/leads']);
  }
}
