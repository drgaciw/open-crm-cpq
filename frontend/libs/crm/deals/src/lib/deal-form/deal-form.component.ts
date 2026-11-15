import { Component, inject, OnInit, input, signal, effect, ChangeDetectionStrategy, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { LookupSelection, displayLookup, parseLocalDate, resolveLookupId, toLocalDateString } from '@frontend/shared-utils';
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
import {
  OrganizationDto,
  ContactDto,
  DealCreateRequest,
  DealStage,
  DealType,
  DealUpdateRequest,
} from '@frontend/shared-models';
import { PageHeaderComponent } from '@frontend/shared-ui';
import { dealActionErrorMessage } from '../deal-action-error';
import { DEAL_STAGE_ORDER, DEAL_TYPES, dealStageLabel, dealTypeLabel, isOpenDealStage } from '../deal-stage';

@Component({
  selector: 'app-deal-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatDatepickerModule,
    MatNativeDateModule, MatAutocompleteModule, MatProgressSpinnerModule,
    PageHeaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header [title]="isEditMode ? 'Edit opportunity' : 'New opportunity'">
      <nav breadcrumb aria-label="Breadcrumb">
        <button mat-button type="button" (click)="goBack()">
          <mat-icon>arrow_back</mat-icon>
          {{ isEditMode ? 'Opportunity' : 'Opportunities' }}
        </button>
      </nav>
    </app-page-header>

    @if (saveError() || dealService.error()) {
      <div class="error-banner" role="alert">
        <mat-icon>error_outline</mat-icon>
        <span>{{ saveError() || dealService.error() }}</span>
        <button mat-button type="button" (click)="dismissError()">Dismiss</button>
      </div>
    }

    @if (closed()) {
      <!-- H-CRM-040 / ADR-032 D-03: after close the API refuses stage, amount and currency changes. -->
      <div class="closed-notice" role="note">
        <mat-icon>lock</mat-icon>
        <span>This opportunity is closed. Its stage, amount and currency can no longer change.</span>
      </div>
    }

    <mat-card class="crm-form-shell">
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <h3 class="section-title">Opportunity</h3>
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Name</mat-label>
              <input matInput formControlName="name">
              @if (form.get('name')?.hasError('required')) { <mat-error>Required</mat-error> }
            </mat-form-field>
          </div>

          <div class="form-row three-col">
            <mat-form-field appearance="outline">
              <mat-label>Amount</mat-label>
              <input matInput type="number" formControlName="amount" min="0">
              @if (form.get('amount')?.hasError('min')) { <mat-error>Amount cannot be negative</mat-error> }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Currency</mat-label>
              <input matInput formControlName="currency" maxlength="3" autocomplete="off">
              @if (form.get('currency')?.hasError('pattern')) { <mat-error>Use a 3-letter currency code</mat-error> }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Type</mat-label>
              <mat-select formControlName="type">
                @for (type of typeOptions; track type.value) {
                  <mat-option [value]="type.value">{{ type.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>

          @if (isEditMode) {
            <div class="form-row">
              <mat-form-field appearance="outline" style="max-width: 300px;">
                <mat-label>Stage</mat-label>
                <mat-select formControlName="stage">
                  @for (s of stageOptions; track s.value) {
                    <mat-option [value]="s.value">{{ s.label }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
            </div>
          }

          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Expected Close Date</mat-label>
              <input matInput [matDatepicker]="picker" formControlName="expectedCloseDate">
              <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
              <mat-datepicker #picker></mat-datepicker>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Owner</mat-label>
              <input matInput formControlName="ownerId" autocomplete="off">
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" style="max-width: 300px;">
              <mat-label>Term (months)</mat-label>
              <input matInput type="number" formControlName="termMonths" min="1" step="1" data-testid="deal-term-months">
              @if (form.get('termMonths')?.invalid) { <mat-error>Enter a whole number of months, 1 or more</mat-error> }
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
              @if (form.get('contactSearch')?.hasError('notPicked')) {
                <mat-error>Pick a contact from the list, or clear the field.</mat-error>
              }
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
              @if (form.get('orgSearch')?.hasError('notPicked')) {
                <mat-error>Pick an organization from the list, or clear the field.</mat-error>
              }
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
            <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid || saving()">
              @if (saving()) { <mat-spinner diameter="20"></mat-spinner> }
              @else { {{ isEditMode ? 'Update' : 'Create' }} }
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    :host { display: block; }
    .crm-form-shell { max-width: 900px; }
    .error-banner {
      display: flex;
      align-items: center;
      gap: var(--crm-space-sm, 8px);
      margin: 0 0 var(--crm-space-md, 16px);
      padding: 12px var(--crm-space-md, 16px);
      border-radius: var(--crm-radius-sm, 8px);
      background: var(--crm-color-danger-container, #ffebee);
      color: var(--crm-color-danger, #c62828);
      max-width: 900px;
    }
    .closed-notice {
      display: flex;
      align-items: center;
      gap: var(--crm-space-sm, 8px);
      margin: 0 0 var(--crm-space-md, 16px);
      padding: 12px var(--crm-space-md, 16px);
      border-radius: var(--crm-radius-sm, 8px);
      background: var(--crm-color-surface-container, #f3f3f3);
      max-width: 900px;
    }
  `],
})
export class DealFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  readonly dealService = inject(DealService);
  private readonly orgService = inject(OrganizationService);
  private readonly contactService = inject(ContactService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  id = input<string>();
  form!: FormGroup;
  isEditMode = false;
  /** Signal so OnPush refreshes after async HTTP error (chaos: offline/500). */
  saving = signal(false);
  /** Edit mode on a WON/LOST opportunity: stage, amount and currency are locked (H-CRM-040). */
  readonly closed = signal(false);
  readonly saveError = signal<string | null>(null);
  selectedContactId: number | null = null;
  selectedOrgId: number | null = null;
  /** Labels the linked records were shown under, to tell edited text apart from them. */
  private selectedContactLabel: string | null = null;
  private selectedOrgLabel: string | null = null;
  filteredContacts = signal<ContactDto[]>([]);
  filteredOrgs = signal<OrganizationDto[]>([]);

  constructor() {
    effect(() => {
      const contacts = this.contactService.contacts();
      if (contacts.length > 0 || !this.contactService.loading()) {
        this.filteredContacts.set(contacts);
      }
    });

    effect(() => {
      const orgs = this.orgService.organizations();
      if (orgs.length > 0 || !this.orgService.loading()) {
        this.filteredOrgs.set(orgs);
      }
    });
  }

  stageOptions: { value: DealStage; label: string }[] = DEAL_STAGE_ORDER.map((stage) => ({
    value: stage,
    label: dealStageLabel(stage),
  }));

  typeOptions: { value: DealType; label: string }[] = DEAL_TYPES.map((type) => ({
    value: type,
    label: dealTypeLabel(type),
  }));

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', Validators.required],
      amount: [null, [Validators.min(0)]],
      currency: ['USD', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
      type: ['NEW_BUSINESS' satisfies DealType, Validators.required],
      stage: ['PROSPECTING' satisfies DealStage],
      expectedCloseDate: [null],
      termMonths: [null, [Validators.min(1), Validators.pattern(/^\d+$/)]],
      ownerId: [''],
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
          const closed = !isOpenDealStage(d.stage);
          this.closed.set(closed);
          for (const name of ['stage', 'amount', 'currency']) {
            if (closed) this.form.get(name)?.disable({ emitEvent: false });
            else this.form.get(name)?.enable({ emitEvent: false });
          }
          this.form.patchValue({
            name: d.name,
            amount: d.amount,
            currency: d.currency ?? 'USD',
            type: d.type ?? 'NEW_BUSINESS',
            stage: d.stage,
            // H-CRM-041: a date-only value is a calendar day; new Date('YYYY-MM-DD') is UTC midnight.
            expectedCloseDate: parseLocalDate(d.expectedCloseDate),
            termMonths: d.termMonths ?? null,
            ownerId: d.ownerId ?? '',
            contactSearch: d.primaryContactName ?? '',
            orgSearch: d.organizationName ?? '',
            description: d.description,
          });
          if (d.primaryContactId) {
            this.selectedContactId = d.primaryContactId;
            this.selectedContactLabel = d.primaryContactName ?? null;
          }
          if (d.organizationId) {
            this.selectedOrgId = d.organizationId;
            this.selectedOrgLabel = d.organizationName ?? null;
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

  /** The controls hold a picked DTO, or a plain name when patched or typed. */
  readonly displayContact = displayLookup<ContactDto>(contactLabel);
  readonly displayOrg = displayLookup<OrganizationDto>(orgLabel);

  onContactSelected(c: ContactDto): void {
    this.selectedContactId = c.id;
    this.selectedContactLabel = contactLabel(c);
    this.form.get('contactSearch')?.setErrors(null);
  }

  onOrgSelected(o: OrganizationDto): void {
    this.selectedOrgId = o.id;
    this.selectedOrgLabel = orgLabel(o);
    this.form.get('orgSearch')?.setErrors(null);
  }

  private selection(id: number | null, label: string | null): LookupSelection | null {
    return id === null ? null : { id, label };
  }

  dismissError(): void {
    this.saveError.set(null);
    this.dealService.error.set(null);
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    // Typed-but-not-picked contact/organization text is resolved or blocked, never dropped.
    const contact = resolveLookupId(
      this.form.value.contactSearch,
      this.selection(this.selectedContactId, this.selectedContactLabel),
      this.filteredContacts(),
      contactLabel,
    );
    const org = resolveLookupId(
      this.form.value.orgSearch,
      this.selection(this.selectedOrgId, this.selectedOrgLabel),
      this.filteredOrgs(),
      orgLabel,
    );
    if (!contact.ok || !org.ok) {
      for (const [name, result] of [['contactSearch', contact], ['orgSearch', org]] as const) {
        if (!result.ok) {
          this.form.get(name)?.setErrors({ notPicked: true });
          this.form.get(name)?.markAsTouched();
        }
      }
      return;
    }
    this.saving.set(true);
    this.saveError.set(null);
    const closeDate = this.form.value.expectedCloseDate as Date | null;
    // H-CRM-021: keep the picked calendar day; toISOString() is the previous day east of UTC.
    const expectedCloseDate = toLocalDateString(closeDate);
    const currency = String(this.form.value.currency ?? 'USD').toUpperCase();
    const ownerId = String(this.form.value.ownerId ?? '').trim();
    const termMonths = this.form.value.termMonths ?? undefined;

    const idVal = this.id();
    if (this.isEditMode && idVal) {
      const data: DealUpdateRequest = {
        name: this.form.value.name,
        // H-CRM-040: a closed deal's stage, amount and currency are frozen; sending them is refused.
        amount: this.closed() ? undefined : this.form.value.amount ?? undefined,
        currency: this.closed() ? undefined : currency,
        stage: this.closed() ? undefined : (this.form.value.stage as DealStage),
        type: this.form.value.type as DealType,
        expectedCloseDate,
        termMonths,
        description: this.form.value.description || undefined,
        ownerId: ownerId || undefined,
        primaryContactId: contact.id ?? undefined,
        organizationId: org.id ?? undefined,
      };
      this.dealService.update(Number(idVal), data).subscribe({
        next: () => this.router.navigate(['/deals', idVal]),
        error: (err) => {
          this.saving.set(false);
          this.saveError.set(dealActionErrorMessage(err, 'Could not update this opportunity'));
        },
      });
    } else {
      const data: DealCreateRequest = {
        name: this.form.value.name,
        amount: this.form.value.amount ?? undefined,
        currency,
        type: this.form.value.type as DealType,
        expectedCloseDate,
        termMonths,
        description: this.form.value.description || undefined,
        ownerId: ownerId || undefined,
        primaryContactId: contact.id ?? undefined,
        organizationId: org.id ?? undefined,
      };
      this.dealService.create(data).subscribe({
        next: (d) => this.router.navigate(['/deals', d.id]),
        error: (err) => {
          this.saving.set(false);
          this.saveError.set(dealActionErrorMessage(err, 'Could not create this opportunity'));
        },
      });
    }
  }

  goBack(): void {
    const idVal = this.id();
    this.router.navigate(this.isEditMode && idVal ? ['/deals', idVal] : ['/deals']);
  }
}

function contactLabel(contact: ContactDto): string {
  return `${contact.firstName ?? ''} ${contact.lastName ?? ''}`.trim();
}

function orgLabel(org: OrganizationDto): string {
  return org.name ?? '';
}
