import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Subject } from 'rxjs';
import { ContactFormComponent } from './contact-form.component';
import { ContactService } from '../contact.service';
import { OrganizationService } from '@frontend/crm-organizations';
import { ContactDto, OrganizationDto } from '@frontend/shared-models';

@Component({ standalone: true, template: '' })
class RouterStubComponent {}

const ACME = { id: 3, name: 'Acme Corp' } as OrganizationDto;
const GLOBEX = { id: 7, name: 'Globex' } as OrganizationDto;

describe('ContactFormComponent organization field (H-CRM-022, H-CRM-033)', () => {
  let fixture: ComponentFixture<ContactFormComponent>;
  let component: ContactFormComponent;
  let contactService: {
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    getById: ReturnType<typeof vi.fn>;
    selectedContact: ReturnType<typeof signal<ContactDto | null>>;
  };
  let saveSubject: Subject<ContactDto>;

  async function setup(id?: string): Promise<void> {
    saveSubject = new Subject<ContactDto>();
    contactService = {
      create: vi.fn(() => saveSubject.asObservable()),
      update: vi.fn(() => saveSubject.asObservable()),
      getById: vi.fn(),
      selectedContact: signal<ContactDto | null>(null),
    };
    await TestBed.configureTestingModule({
      imports: [ContactFormComponent],
      providers: [
        provideRouter([
          { path: 'contacts/:id', component: RouterStubComponent },
          { path: 'contacts', component: RouterStubComponent },
        ]),
        provideNoopAnimations(),
        { provide: ContactService, useValue: contactService },
        {
          provide: OrganizationService,
          useValue: {
            organizations: signal<OrganizationDto[]>([ACME, GLOBEX]),
            loading: signal(false),
            search: vi.fn(),
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ContactFormComponent);
    component = fixture.componentInstance;
    if (id) fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }

  function orgInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input[formcontrolname="organizationSearch"]');
  }

  function orgControl() {
    return component.form.controls['organizationSearch'];
  }

  function fillRequired(): void {
    component.form.patchValue({ firstName: 'Ada', lastName: 'Lovelace' });
  }

  it('edit mode shows the linked organization name in the Organization input (H-CRM-033)', async () => {
    await setup('42');
    contactService.selectedContact.set({
      id: 42, firstName: 'Ada', lastName: 'Lovelace', organizationId: 7, organizationName: 'Globex',
    } as ContactDto);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(orgInput().value).toBe('Globex');
  });

  it('edit mode keeps the linked organization when it is not changed', async () => {
    await setup('42');
    contactService.selectedContact.set({
      id: 42, firstName: 'Ada', lastName: 'Lovelace', organizationId: 7, organizationName: 'Globex',
    } as ContactDto);
    fixture.detectChanges();

    component.onSubmit();

    expect(contactService.update).toHaveBeenCalledWith(42, expect.objectContaining({ organizationId: 7 }));
  });

  it('typed exact organization name without picking still links it (H-CRM-022)', async () => {
    await setup();
    fillRequired();
    orgControl().setValue('acme corp');

    component.onSubmit();

    expect(contactService.create).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 3 }));
  });

  it('typed organization text that matches nothing blocks save with a visible message (H-CRM-022)', async () => {
    await setup();
    fillRequired();
    orgControl().setValue('Initech');

    component.onSubmit();
    fixture.detectChanges();

    expect(contactService.create).not.toHaveBeenCalled();
    expect(component.saving()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Pick an organization from the list');
  });

  it('changing the text after picking does not submit the stale organization id', async () => {
    await setup();
    fillRequired();
    orgControl().setValue(ACME);
    component.onOrgSelected(ACME);
    orgControl().setValue('Something else');

    component.onSubmit();

    expect(contactService.create).not.toHaveBeenCalled();
  });

  it('picked organization is submitted', async () => {
    await setup();
    fillRequired();
    orgControl().setValue(GLOBEX);
    component.onOrgSelected(GLOBEX);

    component.onSubmit();

    expect(contactService.create).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 7 }));
  });

  it('blank organization submits no organization', async () => {
    await setup();
    fillRequired();

    component.onSubmit();

    const payload = contactService.create.mock.calls[0][0];
    expect(payload.organizationId).toBeUndefined();
  });

  it('applies cdkTextareaAutosize with min/max rows to the notes textarea', async () => {
    await setup();
    const textareaDe = fixture.debugElement.query(By.directive(CdkTextareaAutosize));
    expect(textareaDe).toBeTruthy();
    const autosize = textareaDe.injector.get(CdkTextareaAutosize);
    expect(autosize.minRows).toBe(3);
    expect(autosize.maxRows).toBe(10);
  });
});
