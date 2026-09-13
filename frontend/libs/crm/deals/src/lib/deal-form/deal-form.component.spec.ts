import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { DealFormComponent } from './deal-form.component';
import { DealService } from '../deal.service';
import { OrganizationService } from '@frontend/crm-organizations';
import { ContactService } from '@frontend/crm-contacts';

describe('DealFormComponent amount validation (chaos)', () => {
  let fixture: ComponentFixture<DealFormComponent>;
  let component: DealFormComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DealFormComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: DealService,
          useValue: {
            create: vi.fn(),
            update: vi.fn(),
            getById: vi.fn(),
            selectedDeal: () => null,
          },
        },
        {
          provide: OrganizationService,
          useValue: {
            organizations: () => [],
            loading: () => false,
            search: vi.fn(),
          },
        },
        {
          provide: ContactService,
          useValue: {
            contacts: () => [],
            loading: () => false,
            search: vi.fn(),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DealFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('marks form invalid for negative amount so Create stays disabled', () => {
    component.form.patchValue({ name: 'Chaos Deal', amount: -50, probability: 10 });
    fixture.detectChanges();

    expect(component.form.get('amount')?.hasError('min')).toBe(true);
    expect(component.form.invalid).toBe(true);

    const submit = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
  });

  it('allows zero and positive amounts', () => {
    component.form.patchValue({ name: 'Ok Deal', amount: 0, probability: 10 });
    fixture.detectChanges();
    expect(component.form.get('amount')?.valid).toBe(true);

    component.form.patchValue({ amount: 120000 });
    fixture.detectChanges();
    expect(component.form.get('amount')?.valid).toBe(true);
    expect(component.form.valid).toBe(true);
  });
});
