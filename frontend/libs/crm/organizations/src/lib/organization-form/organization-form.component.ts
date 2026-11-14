import { Component, inject, OnInit, input, effect, signal, computed, untracked, DestroyRef, ChangeDetectionStrategy, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subscription } from 'rxjs';
import { OrganizationType } from '@frontend/shared-models';
import { PageHeaderComponent } from '@frontend/shared-ui';
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
    PageHeaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header [title]="isEditMode() ? 'Edit Account' : 'New Account'">
      <nav breadcrumb aria-label="Breadcrumb">
        <button mat-button type="button" (click)="goBack()">
          <mat-icon>arrow_back</mat-icon>
          {{ isEditMode() ? 'Account' : 'Accounts' }}
        </button>
      </nav>
    </app-page-header>

    <mat-card class="crm-form-shell">
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
                @if (allowNoType()) {
                  <mat-option value="">None</mat-option>
                }
                @for (accountType of accountTypes; track accountType) {
                  <mat-option [value]="accountType">{{ accountType }}</mat-option>
                }
              </mat-select>
              @if (isEditMode() && !allowNoType()) {
                <mat-hint>An existing type cannot be cleared.</mat-hint>
              }
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
                    [disabled]="form.invalid || saving() || !readyForSave()">
              @if (saving()) {
                <mat-spinner diameter="20"></mat-spinner>
              } @else {
                {{ isEditMode() ? 'Update' : 'Create' }}
              }
            </button>
          </div>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    :host { display: block; }
  `],
})
export class OrganizationFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly orgService = inject(OrganizationService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  id = input<string>();

  form!: FormGroup;
  readonly isEditMode = computed(() => !!this.id());
  /** Signal so OnPush refreshes after async HTTP error (chaos: offline/500). */
  saving = signal(false);
  readonly allowNoType = signal(true);
  private readonly requestedId = signal<string | null>(null);
  private readonly loadedId = signal<string | null>(null);
  readonly readyForSave = computed(() => !this.id() || this.loadedId() === this.id());
  private saveRequest: Subscription | null = null;

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

    const emptyForm = this.form.getRawValue();
    effect(() => {
      const idVal = this.id();
      untracked(() => {
        this.saveRequest?.unsubscribe();
        this.saveRequest = null;
        this.saving.set(false);
        this.loadedId.set(null);
        this.requestedId.set(idVal ?? null);
        this.orgService.resetAccountDetail();
        this.form.reset(emptyForm, { emitEvent: false });
        this.allowNoType.set(!idVal);
        const type = this.form.get('type');
        type?.setValidators(idVal ? Validators.required : null);
        type?.updateValueAndValidity({ emitEvent: false });
        if (idVal) this.orgService.getById(Number(idVal));
      });
    }, { injector: this.injector });
    effect(() => {
      const idVal = this.id();
      const requested = this.requestedId();
      const org = this.orgService.selectedOrganization();
      if (!idVal || requested !== idVal || !org || org.id !== Number(idVal) || this.loadedId() === idVal) return;
      untracked(() => {
        const hasType = !!org.type;
        this.allowNoType.set(!hasType);
        const type = this.form.get('type');
        type?.setValidators(hasType ? Validators.required : null);
        this.form.patchValue({ ...org, type: org.type ?? '' });
        type?.updateValueAndValidity({ emitEvent: false });
        this.loadedId.set(idVal);
      });
    }, { injector: this.injector });
    this.destroyRef.onDestroy(() => {
      this.saveRequest?.unsubscribe();
      this.orgService.resetAccountDetail();
    });
  }

  onSubmit(): void {
    if (this.form.invalid || this.saving() || !this.readyForSave()) return;
    this.saving.set(true);

    const data = this.payload();
    const idVal = this.id();

    if (idVal) {
      this.saveRequest = this.orgService.update(Number(idVal), data).subscribe({
        next: () => { if (this.id() === idVal) this.router.navigate(['/organizations', idVal]); },
        error: () => { if (this.id() === idVal) this.saving.set(false); },
      });
    } else {
      this.saveRequest = this.orgService.create(data).subscribe({
        next: (org) => { if (!this.id()) this.router.navigate(['/organizations', org.id]); },
        error: () => { if (!this.id()) this.saving.set(false); },
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
    if (this.isEditMode() && idVal) {
      this.router.navigate(['/organizations', idVal]);
    } else {
      this.router.navigate(['/organizations']);
    }
  }
}
