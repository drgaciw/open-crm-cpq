import { Component, inject, OnInit, input, signal, effect, ChangeDetectionStrategy, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { LookupSelection, displayLookup, resolveLookupId } from '@frontend/shared-utils';
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
import { ContactDto, LEAD_PRIORITY_LABELS, LeadPriority, OrganizationDto } from '@frontend/shared-models';
import { PageHeaderComponent } from '@frontend/shared-ui';

@Component({
  selector: 'lib-lead-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatAutocompleteModule, MatProgressSpinnerModule,
    MatSelectModule, PageHeaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header [title]="isEditMode ? 'Edit Lead' : 'New Lead'">
      <nav breadcrumb aria-label="Breadcrumb">
        <button mat-button type="button" (click)="goBack()">
          <mat-icon>arrow_back</mat-icon>
          {{ isEditMode ? 'Lead' : 'Leads' }}
        </button>
      </nav>
    </app-page-header>

    <mat-card class="crm-form-shell">
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
                  <mat-option [value]="p">{{ priorityLabel(p) }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Estimated Value</mat-label>
              <input matInput formControlName="estimatedValue" type="number" placeholder="0">
              <span matTextPrefix>$&nbsp;</span>
              @if (form.get('estimatedValue')?.hasError('min')) {
                <mat-error>Estimated value cannot be negative</mat-error>
              }
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
              @if (form.get('contactSearch')?.hasError('notPicked')) {
                <mat-error>Pick a contact from the list, or clear the field.</mat-error>
              }
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
              @if (form.get('organizationSearch')?.hasError('notPicked')) {
                <mat-error>Pick an organization from the list, or clear the field.</mat-error>
              }
            </mat-form-field>
          </div>

          <div class="form-actions">
            <button mat-button type="button" (click)="goBack()">Cancel</button>
            <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid || saving()">
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
    :host { display: block; }
    .option-detail { font-size: 0.75rem; color: var(--crm-color-muted, #666); margin-left: var(--crm-space-sm, 8px); }
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
  /** Signal so OnPush refreshes after async HTTP error (chaos: offline/500). */
  saving = signal(false);
  selectedContactId: number | null = null;
  selectedOrgId: number | null = null;
  /** Labels the linked records were shown under, to tell edited text apart from them. */
  private selectedContactLabel: string | null = null;
  private selectedOrgLabel: string | null = null;
  filteredContacts = signal<ContactDto[]>([]);
  filteredOrgs = signal<OrganizationDto[]>([]);
  readonly priorities: LeadPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

  priorityLabel(priority: LeadPriority): string {
    return LEAD_PRIORITY_LABELS[priority] ?? priority;
  }

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
      estimatedValue: ['', Validators.min(0)],
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
          if (lead.contactId) {
            this.selectedContactId = lead.contactId;
            this.selectedContactLabel = lead.contactName ?? null;
          }
          if (lead.organizationId) {
            this.selectedOrgId = lead.organizationId;
            this.selectedOrgLabel = lead.organizationName ?? null;
          }
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

  /** The controls hold a picked DTO, or a plain name when patched or typed (H-CRM-034). */
  readonly displayContact = displayLookup<ContactDto>(contactLabel);
  readonly displayOrg = displayLookup<OrganizationDto>(orgLabel);

  onContactSelected(contact: ContactDto): void {
    this.selectedContactId = contact.id;
    this.selectedContactLabel = contactLabel(contact);
    this.form.get('contactSearch')?.setErrors(null);
  }

  onOrgSelected(org: OrganizationDto): void {
    this.selectedOrgId = org.id;
    this.selectedOrgLabel = orgLabel(org);
    this.form.get('organizationSearch')?.setErrors(null);
  }

  clearContact(): void {
    this.selectedContactId = null;
    this.selectedContactLabel = null;
    this.form.patchValue({ contactSearch: '' });
  }

  clearOrg(): void {
    this.selectedOrgId = null;
    this.selectedOrgLabel = null;
    this.form.patchValue({ organizationSearch: '' });
  }

  private selection(id: number | null, label: string | null): LookupSelection | null {
    return id === null ? null : { id, label };
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    // H-CRM-023: typed-but-not-picked text is resolved or blocked, never silently dropped.
    const contact = resolveLookupId(
      this.form.value.contactSearch,
      this.selection(this.selectedContactId, this.selectedContactLabel),
      this.filteredContacts(),
      contactLabel,
    );
    const org = resolveLookupId(
      this.form.value.organizationSearch,
      this.selection(this.selectedOrgId, this.selectedOrgLabel),
      this.filteredOrgs(),
      orgLabel,
    );
    if (!contact.ok || !org.ok) {
      for (const [name, result] of [['contactSearch', contact], ['organizationSearch', org]] as const) {
        if (!result.ok) {
          this.form.get(name)?.setErrors({ notPicked: true });
          this.form.get(name)?.markAsTouched();
        }
      }
      return;
    }
    this.saving.set(true);

    const formValue = this.form.value;
    delete formValue.contactSearch;
    delete formValue.organizationSearch;
    const data = {
      ...formValue,
      contactId: contact.id,
      organizationId: org.id,
      estimatedValue:
        formValue.estimatedValue === null || formValue.estimatedValue === undefined || formValue.estimatedValue === ''
          ? undefined
          : Number(formValue.estimatedValue),
    };

    const idVal = this.id();
    if (this.isEditMode && idVal) {
      this.leadService.update(Number(idVal), data).subscribe({
        next: () => this.router.navigate(['/leads', idVal]),
        error: () => this.saving.set(false),
      });
    } else {
      this.leadService.create(data).subscribe({
        next: (lead) => this.router.navigate(['/leads', lead.id]),
        error: () => this.saving.set(false),
      });
    }
  }

  goBack(): void {
    const idVal = this.id();
    this.router.navigate(this.isEditMode && idVal ? ['/leads', idVal] : ['/leads']);
  }
}

function contactLabel(contact: ContactDto): string {
  return `${contact.firstName ?? ''} ${contact.lastName ?? ''}`.trim();
}

function orgLabel(org: OrganizationDto): string {
  return org.name ?? '';
}
