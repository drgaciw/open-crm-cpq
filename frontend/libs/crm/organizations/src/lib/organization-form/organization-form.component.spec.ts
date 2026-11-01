import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Subject } from 'rxjs';
import { OrganizationFormComponent } from './organization-form.component';
import { OrganizationService } from '../organization.service';
import { OrganizationDto } from '@frontend/shared-models';

describe('OrganizationFormComponent saving flag (OnPush / chaos)', () => {
  let fixture: ComponentFixture<OrganizationFormComponent>;
  let component: OrganizationFormComponent;
  let createSubject: Subject<OrganizationDto>;

  beforeEach(async () => {
    createSubject = new Subject<OrganizationDto>();

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
            selectedOrganization: () => null,
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
});
