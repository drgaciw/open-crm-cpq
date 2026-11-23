import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { LeadFormComponent } from './lead-form.component';
import { LeadService } from '../lead.service';
import { ContactService } from '@frontend/crm-contacts';
import { OrganizationService } from '@frontend/crm-organizations';

describe('LeadFormComponent', () => {
  let fixture: ComponentFixture<LeadFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LeadFormComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: LeadService,
          useValue: {
            create: vi.fn(() => of({ id: 1 })),
            update: vi.fn(() => of({ id: 1 })),
            getById: vi.fn(),
            selectedLead: signal(null),
          },
        },
        {
          provide: ContactService,
          useValue: {
            contacts: signal([]),
            loading: signal(false),
            search: vi.fn(),
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

    fixture = TestBed.createComponent(LeadFormComponent);
    fixture.detectChanges();
  });

  it('applies cdkTextareaAutosize with min/max rows to the description textarea', () => {
    const textareaDe = fixture.debugElement.query(By.directive(CdkTextareaAutosize));
    expect(textareaDe).toBeTruthy();
    const autosize = textareaDe.injector.get(CdkTextareaAutosize);
    expect(autosize.minRows).toBe(3);
    expect(autosize.maxRows).toBe(10);
  });
});
