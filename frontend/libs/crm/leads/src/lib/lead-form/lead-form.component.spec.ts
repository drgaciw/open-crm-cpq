import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Subject } from 'rxjs';
import { LeadFormComponent } from './lead-form.component';
import { LeadService } from '../lead.service';
import { ContactService } from '@frontend/crm-contacts';
import { OrganizationService } from '@frontend/crm-organizations';
import { ContactDto, LeadDto, OrganizationDto } from '@frontend/shared-models';

@Component({ standalone: true, template: '' })
class RouterStubComponent {}

const ACME = { id: 3, name: 'Acme Corp' } as OrganizationDto;
const GLOBEX = { id: 7, name: 'Globex' } as OrganizationDto;
const ADA = { id: 5, firstName: 'Ada', lastName: 'Lovelace' } as ContactDto;

describe('LeadFormComponent linked records and validation (H-CRM-016, H-CRM-023, H-CRM-034)', () => {
  let fixture: ComponentFixture<LeadFormComponent>;
  let component: LeadFormComponent;
  let leadService: {
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    getById: ReturnType<typeof vi.fn>;
    selectedLead: ReturnType<typeof signal<LeadDto | null>>;
  };

  async function setup(id?: string): Promise<void> {
    const save = new Subject<LeadDto>();
    leadService = {
      create: vi.fn(() => save.asObservable()),
      update: vi.fn(() => save.asObservable()),
      getById: vi.fn(),
      selectedLead: signal<LeadDto | null>(null),
    };
    await TestBed.configureTestingModule({
      imports: [LeadFormComponent],
      providers: [
        provideRouter([
          { path: 'leads/:id', component: RouterStubComponent },
          { path: 'leads', component: RouterStubComponent },
        ]),
        provideNoopAnimations(),
        { provide: LeadService, useValue: leadService },
        { provide: ContactService, useValue: { contacts: signal([ADA]), loading: signal(false), search: vi.fn() } },
        { provide: OrganizationService, useValue: { organizations: signal([ACME, GLOBEX]), loading: signal(false), search: vi.fn() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LeadFormComponent);
    component = fixture.componentInstance;
    if (id) fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }

  const control = (name: string) => component.form.controls[name];
  const input = (name: string): HTMLInputElement =>
    fixture.nativeElement.querySelector(`input[formcontrolname="${name}"]`);

  function loadLinkedLead(): void {
    leadService.selectedLead.set({
      id: 9, title: 'Renewal', status: 'NEW', priority: 'MEDIUM',
      contactId: 5, contactName: 'Ada Lovelace', organizationId: 7, organizationName: 'Globex',
    } as LeadDto);
    fixture.detectChanges();
  }

  it('edit mode shows the linked contact and organization names (H-CRM-034)', async () => {
    await setup('9');
    loadLinkedLead();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(input('contactSearch').value).toBe('Ada Lovelace');
    expect(input('organizationSearch').value).toBe('Globex');
  });

  it('edit mode keeps the linked records when unchanged', async () => {
    await setup('9');
    loadLinkedLead();

    component.onSubmit();

    expect(leadService.update).toHaveBeenCalledWith(9, expect.objectContaining({ contactId: 5, organizationId: 7 }));
  });

  it('typed exact organization and contact names without picking still link them (H-CRM-023)', async () => {
    await setup();
    control('title').setValue('New lead');
    control('organizationSearch').setValue('acme corp');
    control('contactSearch').setValue('Ada Lovelace');

    component.onSubmit();

    expect(leadService.create).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 3, contactId: 5 }));
  });

  it('typed organization text that matches nothing blocks save with a visible message (H-CRM-023)', async () => {
    await setup();
    control('title').setValue('New lead');
    control('organizationSearch').setValue('Initech');

    component.onSubmit();
    fixture.detectChanges();

    expect(leadService.create).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Pick an organization from the list');
  });

  it('a negative estimated value is rejected in the form (H-CRM-016)', async () => {
    await setup();
    control('title').setValue('New lead');
    control('estimatedValue').setValue(-5);
    control('estimatedValue').markAsTouched();
    fixture.detectChanges();

    expect(component.form.invalid).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Estimated value cannot be negative');
    const submit: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(submit.disabled).toBe(true);
  });

  it('zero is a valid estimated value', async () => {
    await setup();
    control('title').setValue('New lead');
    control('estimatedValue').setValue(0);

    expect(component.form.valid).toBe(true);
  });

  it('clearing a linked record submits no link', async () => {
    await setup('9');
    loadLinkedLead();
    component.clearOrg();

    component.onSubmit();

    expect(leadService.update).toHaveBeenCalledWith(9, expect.objectContaining({ organizationId: null, contactId: 5 }));
  });
});
