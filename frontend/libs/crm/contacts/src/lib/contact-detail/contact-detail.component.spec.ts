import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { CopyButtonComponent } from '@frontend/shared-ui';
import { ContactDto } from '@frontend/shared-models';
import { ContactDetailComponent } from './contact-detail.component';
import { ContactService } from '../contact.service';

describe('ContactDetailComponent', () => {
  let fixture: ComponentFixture<ContactDetailComponent>;
  const loading = signal(false);
  const selectedContact = signal<ContactDto | null>({
    id: 11,
    uuid: 'c-11',
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@example.com',
    phone: '+1-555-0100',
    department: 'Engineering',
    createdAt: '',
    updatedAt: '',
  });

  beforeEach(async () => {
    loading.set(false);
    await TestBed.configureTestingModule({
      imports: [ContactDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: ContactService,
          useValue: {
            selectedContact,
            loading,
            error: signal<string | null>(null),
            getById: vi.fn(),
            delete: vi.fn(() => of(void 0)),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ContactDetailComponent);
    fixture.componentRef.setInput('id', '11');
    fixture.detectChanges();
  });

  it('names the progress indicator while contact detail is loading', () => {
    loading.set(true);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const status = host.querySelector('[role="status"]');
    expect(status?.getAttribute('aria-live')).toBe('polite');
    expect(status?.querySelector('[role="progressbar"]')?.getAttribute('aria-label')).toBe('Loading contact');
    expect(host.querySelector('h1')).toBeNull();
  });

  it('wires CopyButtonComponent for email and phone (DoD P1.2)', () => {
    const copyButtons = fixture.debugElement.queryAll(
      By.directive(CopyButtonComponent),
    );
    expect(copyButtons).toHaveLength(2);
    const values = copyButtons.map((de) =>
      (de.componentInstance as CopyButtonComponent).value(),
    );
    expect(values).toEqual(['ada@example.com', '+1-555-0100']);
  });
});
