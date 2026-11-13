import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Component, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MatSelect } from '@angular/material/select';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Subject } from 'rxjs';
import { OrganizationFormComponent } from './organization-form.component';
import { OrganizationService } from '../organization.service';
import { OrganizationDto } from '@frontend/shared-models';

@Component({ standalone: true, template: '' })
class RouterStubComponent {}

const orgFormRoutes = [
  { path: 'organizations/:id', component: RouterStubComponent },
  { path: 'organizations', component: RouterStubComponent },
];

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
        provideRouter(orgFormRoutes),
        provideNoopAnimations(),
        {
          provide: OrganizationService,
          useValue: {
            create: vi.fn(() => createSubject.asObservable()),
            update: vi.fn(() => createSubject.asObservable()),
            getById: vi.fn(),
            resetAccountDetail: vi.fn(),
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

describe('OrganizationFormComponent reused edit route', () => {
  let fixture: ComponentFixture<OrganizationFormComponent>;
  let http: HttpTestingController;

  const account = (id: number, name: string): OrganizationDto => ({
    id, uuid: `uuid-${id}`, name, type: 'CUSTOMER', createdAt: '', updatedAt: '',
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrganizationFormComponent],
      providers: [
        provideRouter(orgFormRoutes),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrganizationFormComponent);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('blocks a stale save immediately when the route ID changes and reloads the new account', async () => {
    http.expectOne('/api/v1/organizations/7').flush(account(7, 'First'));
    fixture.detectChanges();
    fixture.componentRef.setInput('id', '8');
    fixture.componentInstance.onSubmit();
    http.expectNone(request => request.method === 'PUT');
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('name')?.value).toBe('');
    http.expectOne('/api/v1/organizations/8').flush(account(8, 'Second'));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('name')?.value).toBe('Second');
    fixture.componentInstance.onSubmit();
    const update = http.expectOne('/api/v1/organizations/8');
    expect(update.request.body.name).toBe('Second');
    update.flush(account(8, 'Second'));
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/organizations/8');
  });

  it('cancels an old load before accepting a new account', () => {
    const old = http.expectOne('/api/v1/organizations/7');
    fixture.componentRef.setInput('id', '8');
    fixture.detectChanges();
    expect(old.cancelled).toBe(true);
    http.expectOne('/api/v1/organizations/8').flush(account(8, 'Second'));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('name')?.value).toBe('Second');
  });

  it('cancels an unfinished load when the editor is destroyed', () => {
    const old = http.expectOne('/api/v1/organizations/7');
    fixture.destroy();
    expect(old.cancelled).toBe(true);
  });

  it('does not submit an edit after its account fails to load', () => {
    http.expectOne('/api/v1/organizations/7').flush({ message: 'Unavailable' }, { status: 503, statusText: 'Unavailable' });
    fixture.componentInstance.form.patchValue({ name: 'Typed while offline', type: 'CUSTOMER' });
    fixture.detectChanges();
    fixture.componentInstance.onSubmit();
    http.expectNone(request => request.method === 'PUT');
    expect((fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('cancels an old save callback when the route changes', () => {
    http.expectOne('/api/v1/organizations/7').flush(account(7, 'First'));
    fixture.detectChanges();
    fixture.componentInstance.onSubmit();
    const oldSave = http.expectOne(request => request.method === 'PUT' && request.url === '/api/v1/organizations/7');
    fixture.componentRef.setInput('id', '8');
    fixture.detectChanges();
    expect(oldSave.cancelled).toBe(true);
    http.expectOne('/api/v1/organizations/8').flush(account(8, 'Second'));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.get('name')?.value).toBe('Second');
  });
});
