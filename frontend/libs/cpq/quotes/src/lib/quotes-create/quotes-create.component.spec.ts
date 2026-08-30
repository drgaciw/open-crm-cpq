import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { QuotesCreateComponent } from './quotes-create.component';
import { QuoteService } from '../quote.service';
import { QuoteDto } from '@frontend/shared-models';

describe('QuotesCreateComponent', () => {
  let fixture: ComponentFixture<QuotesCreateComponent>;
  let component: QuotesCreateComponent;
  let router: Router;
  let createSpy: ReturnType<typeof vi.fn>;

  const createdQuote: QuoteDto = {
    id: 99,
    uuid: 'quote-uuid-99',
    quoteNumber: 'Q-2026-0099',
    name: 'New Acme Quote',
    customerExternalId: 'cust-acme',
    status: 'DRAFT',
    durationInMonths: 12,
    createdAt: '2026-08-30T12:00:00Z',
    updatedAt: '2026-08-30T12:00:00Z',
  };

  beforeEach(async () => {
    createSpy = vi.fn(() => of(createdQuote));

    await TestBed.configureTestingModule({
      imports: [QuotesCreateComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: QuoteService, useValue: { create: createSpy } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QuotesCreateComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requires name and customerExternalId', () => {
    expect(component.form.invalid).toBe(true);
    component.form.patchValue({ name: 'Acme', customerExternalId: '' });
    expect(component.form.invalid).toBe(true);
    component.form.patchValue({ customerExternalId: 'cust-acme' });
    expect(component.form.valid).toBe(true);
  });

  it('creates via QuoteService and navigates to detail on success', () => {
    component.form.setValue({
      name: 'New Acme Quote',
      customerExternalId: 'cust-acme',
      currency: 'USD',
      durationInMonths: 12,
      dealExternalId: 'deal-1',
      region: 'NA',
    });
    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      name: 'New Acme Quote',
      customerExternalId: 'cust-acme',
      currency: 'USD',
      durationInMonths: 12,
      dealExternalId: 'deal-1',
      region: 'NA',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/quotes', 99]);
  });

  it('omits blank optional fields from create request', () => {
    component.form.setValue({
      name: 'Bare Quote',
      customerExternalId: 'cust-x',
      currency: '  ',
      durationInMonths: null,
      dealExternalId: '',
      region: '',
    });
    component.onSubmit();

    expect(createSpy).toHaveBeenCalledWith({
      name: 'Bare Quote',
      customerExternalId: 'cust-x',
    });
  });

  it('shows inline error banner on HTTP failure', () => {
    createSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'Customer not found' } })),
    );
    component.form.patchValue({
      name: 'Fail Quote',
      customerExternalId: 'cust-missing',
    });
    component.onSubmit();
    fixture.detectChanges();

    expect(component.error()).toBe('Customer not found');
    expect(component.saving()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Customer not found',
    );
  });

  it('navigates back to list on cancel', () => {
    component.goBack();
    expect(router.navigate).toHaveBeenCalledWith(['/quotes']);
  });
});
