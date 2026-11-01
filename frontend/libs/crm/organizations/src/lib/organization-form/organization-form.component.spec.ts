import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MatSelect } from '@angular/material/select';
import { Subject } from 'rxjs';
import { OrganizationFormComponent } from './organization-form.component';
import { OrganizationService } from '../organization.service';
import { OrganizationDto } from '@frontend/shared-models';

describe('OrganizationFormComponent saving flag (OnPush / chaos)', () => {
  let fixture: ComponentFixture<OrganizationFormComponent>;
  let component: OrganizationFormComponent;
  let createSubject: Subject<OrganizationDto>;
  let selectedOrganization: ReturnType<typeof signal<OrganizationDto | null>>;

  beforeEach(async () => {
    createSubject = new Subject<OrganizationDto>();
    selectedOrganization = signal<OrganizationDto | null>(null);

    await TestBed.configureTestingModule({
      imports: [OrganizationFormComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: OrganizationService,
          useValue: {
            create: vi.fn(() => createSubject.asObservable()),
            update: vi.fn(() => createSubject.asObservable()),
            getById: vi.fn(),
            selectedOrganization,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrganizationFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('clears saving and re-enables Create after create() errors (offline/500)', () => {
    component.form.patchValue({ name: 'Chaos Org' });
    fixture.detectChanges();

    const submit = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(submit.disabled).toBe(false);

    component.onSubmit();
    fixture.detectChanges();
    expect(component.saving()).toBe(true);
    expect(submit.disabled).toBe(true);

    createSubject.error(new Error('Unable to connect to the server'));
    fixture.detectChanges();

    expect(component.saving()).toBe(false);
    expect(submit.disabled).toBe(false);
    expect(submit.textContent).toContain('Create');
  });

  it('uses Account copy and captures territory and owner', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('h1')?.textContent).toContain('New Account');
    expect(el.textContent).toContain('Account Name');
    expect(el.textContent).toContain('Territory');
    expect(el.textContent).toContain('Owner');
    expect(component.form.contains('territory')).toBe(true);
    expect(component.form.contains('ownerId')).toBe(true);
  });

  it('omits a blank account type from the create body', () => {
    const orgService = TestBed.inject(OrganizationService) as unknown as {
      create: ReturnType<typeof vi.fn>;
    };
    component.form.patchValue({ name: 'Acme', type: '' });
    component.onSubmit();
    const body = orgService.create.mock.calls[0][0] as { name: string; type?: string };
    expect(body.name).toBe('Acme');
    expect(body).not.toHaveProperty('type');
  });

  it('does not offer or submit None when editing an account with a stored type', () => {
    selectedOrganization.set({ id: 7, uuid: 'uuid-7', name: 'Acme', type: 'CUSTOMER', createdAt: '', updatedAt: '' });
    const edit = TestBed.createComponent(OrganizationFormComponent);
    edit.componentRef.setInput('id', '7');
    edit.detectChanges();
    const typeSelect = edit.debugElement.query(By.css('mat-select[formControlName="type"]')).componentInstance as MatSelect;
    expect(typeSelect.options.toArray().map(option => option.value)).not.toContain('');
    const service = TestBed.inject(OrganizationService) as unknown as { update: ReturnType<typeof vi.fn> };
    edit.componentInstance.form.get('type')?.setValue('');
    expect(edit.componentInstance.form.invalid).toBe(true);
    edit.componentInstance.onSubmit();
    expect(service.update).not.toHaveBeenCalled();
    edit.componentInstance.form.get('type')?.setValue('PARTNER');
    edit.componentInstance.onSubmit();
    expect(service.update).toHaveBeenCalledWith(7, expect.objectContaining({ type: 'PARTNER' }));
  });

  it('keeps None available for an account that was already untyped', () => {
    selectedOrganization.set({ id: 7, uuid: 'uuid-7', name: 'Acme', type: null, createdAt: '', updatedAt: '' } as unknown as OrganizationDto);
    const edit = TestBed.createComponent(OrganizationFormComponent);
    edit.componentRef.setInput('id', '7');
    edit.detectChanges();
    const typeSelect = edit.debugElement.query(By.css('mat-select[formControlName="type"]')).componentInstance as MatSelect;
    expect(typeSelect.options.toArray().map(option => option.value)).toContain('');
    expect(edit.componentInstance.form.get('type')?.value).toBe('');
    const service = TestBed.inject(OrganizationService) as unknown as { update: ReturnType<typeof vi.fn> };
    edit.componentInstance.onSubmit();
    expect(service.update.mock.calls[0][1]).not.toHaveProperty('type');
  });

  it('does not use a previously selected different account while the edit is loading', () => {
    selectedOrganization.set({ id: 8, uuid: 'uuid-8', name: 'Other', type: 'CUSTOMER', createdAt: '', updatedAt: '' });
    const edit = TestBed.createComponent(OrganizationFormComponent);
    edit.componentRef.setInput('id', '7');
    edit.detectChanges();
    expect(edit.componentInstance.form.get('name')?.value).toBe('');
    const typeSelect = edit.debugElement.query(By.css('mat-select[formControlName="type"]')).componentInstance as MatSelect;
    expect(typeSelect.options.toArray().map(option => option.value)).not.toContain('');
  });
});
