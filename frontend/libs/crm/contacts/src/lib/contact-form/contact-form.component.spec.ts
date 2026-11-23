import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { ContactFormComponent } from './contact-form.component';
import { ContactService } from '../contact.service';
import { OrganizationService } from '@frontend/crm-organizations';

describe('ContactFormComponent', () => {
  let fixture: ComponentFixture<ContactFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ContactFormComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: ContactService,
          useValue: {
            create: vi.fn(() => of({ id: 1 })),
            update: vi.fn(() => of({ id: 1 })),
            getById: vi.fn(),
            selectedContact: signal(null),
          },
        },
        {
          provide: OrganizationService,
          useValue: {
            organizations: signal([]),
            loading: signal(false),
            search: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ContactFormComponent);
    fixture.detectChanges();
  });

  it('applies cdkTextareaAutosize with min/max rows to the notes textarea', () => {
    const textareaDe = fixture.debugElement.query(By.directive(CdkTextareaAutosize));
    expect(textareaDe).toBeTruthy();
    const autosize = textareaDe.injector.get(CdkTextareaAutosize);
    expect(autosize.minRows).toBe(3);
    expect(autosize.maxRows).toBe(10);
  });
});
