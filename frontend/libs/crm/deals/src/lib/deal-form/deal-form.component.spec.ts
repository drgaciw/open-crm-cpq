import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { DealFormComponent } from './deal-form.component';
import { DealService } from '../deal.service';
import { OrganizationService } from '@frontend/crm-organizations';
import { ContactService } from '@frontend/crm-contacts';

describe('DealFormComponent amount validation (chaos)', () => {
  let fixture: ComponentFixture<DealFormComponent>;
  let component: DealFormComponent;
  let create: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    create = vi.fn(() => of({ id: 3, name: 'New opp', stage: 'PROSPECTING' }));
    await TestBed.configureTestingModule({
      imports: [DealFormComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: DealService,
          useValue: {
            create,
            update: vi.fn(),
            getById: vi.fn(),
            selectedDeal: () => null,
            error: signal<string | null>(null),
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

  it('create sends currency, type, and owner, and omits stage', () => {
    component.form.patchValue({
      name: 'New opp',
      amount: 10,
      currency: 'eur',
      type: 'RENEWAL',
      ownerId: 'ada',
      stage: 'WON',
    });

    component.onSubmit();

    expect(create).toHaveBeenCalledTimes(1);
    const payload = create.mock.calls[0][0] as Record<string, unknown>;
    expect(payload).toMatchObject({
      name: 'New opp',
      amount: 10,
      currency: 'EUR',
      type: 'RENEWAL',
      ownerId: 'ada',
    });
    expect(payload['stage']).toBeUndefined();
    expect(payload['probability']).toBeUndefined();
  });

  it('shows an actionable banner when create fails', () => {
    create.mockReturnValueOnce(throwError(() => ({
      error: { message: 'nope', errorCode: 'OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE' },
    })));
    component.form.patchValue({ name: 'Blocked', amount: 1, currency: 'USD' });

    component.onSubmit();
    fixture.detectChanges();

    const banner = (fixture.nativeElement as HTMLElement).querySelector('.error-banner');
    expect(banner?.textContent).toContain('Accept the quote');
    expect(banner?.getAttribute('role')).toBe('alert');
    expect(component.saving()).toBe(false);
  });
});
