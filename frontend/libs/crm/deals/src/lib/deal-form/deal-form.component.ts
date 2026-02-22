import { Component, inject, OnInit, input, signal, effect, ChangeDetectionStrategy, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DealService } from '../deal.service';
import { OrganizationService } from '@frontend/crm-organizations';
import { ContactService } from '@frontend/crm-contacts';
import { OrganizationDto, ContactDto, DealStage } from '@frontend/shared-models';

@Component({
  selector: 'app-deal-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatDatepickerModule,
    MatNativeDateModule, MatAutocompleteModule, MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="form-header">
      <button mat-icon-button (click)="goBack()" aria-label="Go back"><mat-icon>arrow_back</mat-icon></button>
      <h1>{{ isEditMode ? 'Edit Deal' : 'New Deal' }}</h1>
    </div>

    <mat-card class="form-card">
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <h3 class="section-title">Deal Information</h3>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Deal Name</mat-label>
              <input matInput formControlName="name">
              @if (form.get('name')?.hasError('required')) { <mat-error>Required</mat-error> }
            </mat-form-field>
          </div>

          <div class="form-row three-col">
            <mat-form-field appearance="outline">
              <mat-label>Amount ($)</mat-label>
              <input matInput type="number" formControlName="amount">
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Stage</mat-label>
              <mat-select formControlName="stage">
                @for (s of stageOptions; track s.value) {
                  <mat-option [value]="s.value">{{ s.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Probability (%)</mat-label>
              <input matInput type="number" formControlName="probability" min="0" max="100">
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" style="max-width: 300px;">
              <mat-label>Expected Close Date</mat-label>
              <input matInput [matDatepicker]="picker" formControlName="expectedCloseDate">
              <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
              <mat-datepicker #picker></mat-datepicker>
            </mat-form-field>
          </div>

          <h3 class="section-title">Relationships</h3>
          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Contact</mat-label>
              <input matInput formControlName="contactSearch" [matAutocomplete]="contactAuto"
                     (input)="searchContacts($event)">
              <mat-autocomplete #contactAuto="matAutocomplete" [displayWith]="displayContact"
                                (optionSelected)="onContactSelected($event.option.value)">
                @for (c of filteredContacts(); track c.id) {
                  <mat-option [value]="c">{{ c.firstName }} {{ c.lastName }}</mat-option>
                }
              </mat-autocomplete>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Organization</mat-label>
              <input matInput formControlName="orgSearch" [matAutocomplete]="orgAuto"
                     (input)="searchOrgs($event)">
              <mat-autocomplete #orgAuto="matAutocomplete" [displayWith]="displayOrg"
                                (optionSelected)="onOrgSelected($event.option.value)">
                @for (o of filteredOrgs(); track o.id) {
                  <mat-option [value]="o">{{ o.name }}</mat-option>
                }
              </mat-autocomplete>
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
            <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid || saving">
              @if (saving) { <mat-spinner diameter="20"></mat-spinner> }
              @else { {{ isEditMode ? 'Update' : 'Create' }} }
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .form-header { display: flex; align-items: center; gap: 8px; margin-bottom: 24px; }
    .form-header h1 { margin: 0; font-size: 1.5rem; }
    .form-card { max-width: 900px; }
    .section-title { margin: 24px 0 12px; font-size: 1rem; font-weight: 500; color: #333; }
    .section-title:first-of-type { margin-top: 0; }
    .form-row { display: flex; gap: 16px; margin-bottom: 8px; }
    .two-col > * { flex: 1; }
    .three-col > * { flex: 1; }
    .full-width { width: 100%; }
    .form-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; padding-top: 16px; border-top: 1px solid #eee; }
  `],
})
export class DealFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly dealService = inject(DealService);
  private readonly orgService = inject(OrganizationService);
  private readonly contactService = inject(ContactService);
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

  stageOptions: { value: DealStage; label: string }[] = [
    { value: 'PROSPECTING', label: 'Prospecting' },
    { value: 'QUALIFICATION', label: 'Qualification' },
    { value: 'NEEDS_ANALYSIS', label: 'Needs Analysis' },
    { value: 'PROPOSAL', label: 'Proposal' },
    { value: 'NEGOTIATION', label: 'Negotiation' },
    { value: 'WON', label: 'Won' },
    { value: 'LOST', label: 'Lost' },
  ];

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', Validators.required],
      amount: [null],
      stage: ['PROSPECTING'],
      probability: [10, [Validators.min(0), Validators.max(100)]],
      expectedCloseDate: [null],
      contactSearch: [''],
      orgSearch: [''],
      description: [''],
    });

    const idVal = this.id();
    if (idVal) {
      this.isEditMode = true;
      this.dealService.getById(Number(idVal));
      effect(() => {
        const d = this.dealService.selectedDeal();
        if (d) {
          this.form.patchValue({
            name: d.name, amount: d.amount, stage: d.stage, probability: d.probability,
            expectedCloseDate: d.expectedCloseDate ? new Date(d.expectedCloseDate) : null,
            contactSearch: d.primaryContactName ?? '', orgSearch: d.organizationName ?? '',
            description: d.description,
          });
          if (d.primaryContactId) this.selectedContactId = d.primaryContactId;
          if (d.organizationId) this.selectedOrgId = d.organizationId;
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

  displayContact(c: ContactDto): string { return c ? `${c.firstName} ${c.lastName}` : ''; }
  displayOrg(o: OrganizationDto): string { return o?.name ?? ''; }
  onContactSelected(c: ContactDto): void { this.selectedContactId = c.id; }
  onOrgSelected(o: OrganizationDto): void { this.selectedOrgId = o.id; }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.saving = true;
    const { contactSearch, orgSearch, expectedCloseDate, stage, ...formValue } = this.form.value;

    const idVal = this.id();
    if (this.isEditMode && idVal) {
      // Update request: include stage
      const data = {
        ...formValue,
        stage,
        expectedCloseDate: expectedCloseDate ? expectedCloseDate.toISOString().split('T')[0] : null,
        primaryContactId: this.selectedContactId,
        organizationId: this.selectedOrgId,
      };
      this.dealService.update(Number(idVal), data).subscribe({
        next: () => this.router.navigate(['/deals', idVal]),
        error: () => (this.saving = false),
      });
    } else {
      // Create request: exclude stage (backend doesn't accept it on create)
      const data = {
        ...formValue,
        expectedCloseDate: expectedCloseDate ? expectedCloseDate.toISOString().split('T')[0] : null,
        primaryContactId: this.selectedContactId,
        organizationId: this.selectedOrgId,
      };
      this.dealService.create(data).subscribe({
        next: (d) => this.router.navigate(['/deals', d.id]),
        error: () => (this.saving = false),
      });
    }
  }

  goBack(): void {
    const idVal = this.id();
    this.router.navigate(this.isEditMode && idVal ? ['/deals', idVal] : ['/deals']);
  }
}
