import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Subject } from 'rxjs';
import { DealFormComponent } from './deal-form.component';
import { DealService } from '../deal.service';
import { OrganizationService } from '@frontend/crm-organizations';
import { ContactService } from '@frontend/crm-contacts';
import { ContactDto, DealDto, OrganizationDto } from '@frontend/shared-models';

@Component({ standalone: true, template: '' })
class RouterStubComponent {}

const ACME = { id: 3, name: 'Acme Corp' } as OrganizationDto;
const GLOBEX = { id: 7, name: 'Globex' } as OrganizationDto;
const ADA = { id: 5, firstName: 'Ada', lastName: 'Lovelace' } as ContactDto;

/** Same defect class as the contact and lead forms (H-CRM-022/023/033/034), found by code review during UAT. */
describe('DealFormComponent linked contact and organization', () => {
  let fixture: ComponentFixture<DealFormComponent>;
  let component: DealFormComponent;
  let dealService: {
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    getById: ReturnType<typeof vi.fn>;
    selectedDeal: ReturnType<typeof signal<DealDto | null>>;
    error: ReturnType<typeof signal<string | null>>;
  };

  async function setup(id?: string): Promise<void> {
    const save = new Subject<DealDto>();
    dealService = {
      create: vi.fn(() => save.asObservable()),
      update: vi.fn(() => save.asObservable()),
      getById: vi.fn(),
      selectedDeal: signal<DealDto | null>(null),
      error: signal<string | null>(null),
    };
    await TestBed.configureTestingModule({
      imports: [DealFormComponent],
      providers: [
        provideRouter([
          { path: 'deals/:id', component: RouterStubComponent },
          { path: 'deals', component: RouterStubComponent },
        ]),
        provideNoopAnimations(),
        { provide: DealService, useValue: dealService },
        { provide: OrganizationService, useValue: { organizations: signal([ACME, GLOBEX]), loading: signal(false), search: vi.fn() } },
        { provide: ContactService, useValue: { contacts: signal([ADA]), loading: signal(false), search: vi.fn() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DealFormComponent);
    component = fixture.componentInstance;
    if (id) fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }

  const control = (name: string) => component.form.controls[name];
  const input = (name: string): HTMLInputElement =>
    fixture.nativeElement.querySelector(`input[formcontrolname="${name}"]`);

  function loadLinkedDeal(): void {
    dealService.selectedDeal.set({
      id: 12, name: 'Expansion', stage: 'PROSPECTING', currency: 'USD', type: 'NEW_BUSINESS',
      primaryContactId: 5, primaryContactName: 'Ada Lovelace', organizationId: 7, organizationName: 'Globex',
    } as DealDto);
    fixture.detectChanges();
  }

  it('edit mode shows the linked contact and organization names', async () => {
    await setup('12');
    loadLinkedDeal();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(input('contactSearch').value).toBe('Ada Lovelace');
    expect(input('orgSearch').value).toBe('Globex');
  });

  it('edit mode keeps the linked records when unchanged', async () => {
    await setup('12');
    loadLinkedDeal();

    component.onSubmit();

    expect(dealService.update).toHaveBeenCalledWith(12, expect.objectContaining({ primaryContactId: 5, organizationId: 7 }));
  });

  it('typed exact contact and organization names without picking still link them', async () => {
    await setup();
    control('name').setValue('New opp');
    control('contactSearch').setValue('ada lovelace');
    control('orgSearch').setValue('Acme Corp');

    component.onSubmit();

    expect(dealService.create).toHaveBeenCalledWith(expect.objectContaining({ primaryContactId: 5, organizationId: 3 }));
  });

  it('typed organization text that matches nothing blocks save with a visible message', async () => {
    await setup();
    control('name').setValue('New opp');
    control('orgSearch').setValue('Initech');

    component.onSubmit();
    fixture.detectChanges();

    expect(dealService.create).not.toHaveBeenCalled();
    expect(component.saving()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Pick an organization from the list');
  });
});
