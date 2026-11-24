import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Component, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { NEVER, of, throwError } from 'rxjs';
import { DealFormComponent } from './deal-form.component';
import { DealService } from '../deal.service';
import { OrganizationService } from '@frontend/crm-organizations';
import { ContactService } from '@frontend/crm-contacts';

@Component({ standalone: true, template: '' })
class RouterStubComponent {}

describe('DealFormComponent amount validation (chaos)', () => {
  let fixture: ComponentFixture<DealFormComponent>;
  let component: DealFormComponent;
  let create: ReturnType<typeof vi.fn>;
  let update: ReturnType<typeof vi.fn>;

  function mount(id?: string): void {
    fixture = TestBed.createComponent(DealFormComponent);
    component = fixture.componentInstance;
    if (id) fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    create = vi.fn(() => of({ id: 3, name: 'New opp', stage: 'PROSPECTING' }));
    update = vi.fn(() => NEVER);
    await TestBed.configureTestingModule({
      imports: [DealFormComponent],
      providers: [
        provideRouter([
          { path: 'deals/:id', component: RouterStubComponent },
          { path: 'deals', component: RouterStubComponent },
        ]),
        provideNoopAnimations(),
        {
          provide: DealService,
          useValue: {
            create,
            update,
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

    mount();
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

  it('explains an invalid expected close date and enables Create again after correction', () => {
    component.form.patchValue({ name: 'Dated opportunity' });
    const host = fixture.nativeElement as HTMLElement;
    const date = host.querySelector<HTMLInputElement>('input[formcontrolname="expectedCloseDate"]')!;
    const submit = host.querySelector<HTMLButtonElement>('button[type="submit"]')!;

    date.value = 'not-a-calendar-date';
    date.dispatchEvent(new Event('input'));
    date.dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(component.form.get('expectedCloseDate')?.hasError('matDatepickerParse')).toBe(true);
    expect(submit.disabled).toBe(true);
    const error = date.closest('mat-form-field')?.querySelector('mat-error');
    expect(error?.textContent?.trim()).toBe('Enter a valid expected close date');
    expect(date.getAttribute('aria-describedby')?.split(' ')).toContain(error?.id);

    date.value = '12/19/2026';
    date.dispatchEvent(new Event('input'));
    date.dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(host.textContent).not.toContain('Enter a valid expected close date');
    expect(submit.disabled).toBe(false);
  });

  it.each([
    ['Create', undefined],
    ['Update', '9'],
  ])('keeps the %s action and save progress named while the request is pending', (label, id) => {
    if (id) {
      fixture.destroy();
      mount(id);
    }
    component.form.patchValue({ name: 'Opportunity ready to save' });
    fixture.detectChanges();

    const submit = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button[type="submit"]')!;
    expect(submit.textContent?.trim()).toBe(label);
    expect(submit.disabled).toBe(false);

    if (!id) create.mockReturnValueOnce(NEVER);
    submit.click();
    fixture.detectChanges();

    expect(submit.disabled).toBe(true);
    expect(id ? update : create).toHaveBeenCalledOnce();
    const progress = submit.querySelector('[role="progressbar"]');
    expect([submit.getAttribute('aria-label'), progress?.getAttribute('aria-label')]).toEqual([
      label,
      'Saving opportunity',
    ]);
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

  it('create sends currency, type, and owner, and omits stage', async () => {
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
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/deals/3');
  });

  it('create sends the term in months when given and omits it when blank (DRG-636)', () => {
    component.form.patchValue({ name: 'Three year licence', amount: 360000, termMonths: 36 });
    component.onSubmit();
    expect((create.mock.calls[0][0] as Record<string, unknown>)['termMonths']).toBe(36);

    component.form.patchValue({ termMonths: null });
    component.onSubmit();
    expect((create.mock.calls[1][0] as Record<string, unknown>)['termMonths']).toBeUndefined();
  });

  it('rejects a zero, negative or fractional term and accepts a whole one (DRG-636)', () => {
    component.form.patchValue({ name: 'Term check' });
    for (const bad of [0, -12, 1.5]) {
      component.form.patchValue({ termMonths: bad });
      expect(component.form.get('termMonths')?.invalid).toBe(true);
    }
    component.form.patchValue({ termMonths: 12 });
    expect(component.form.get('termMonths')?.valid).toBe(true);
    expect(component.form.valid).toBe(true);
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

  it('applies cdkTextareaAutosize with min/max rows to the description textarea', () => {
    const textareaDe = fixture.debugElement.query(By.directive(CdkTextareaAutosize));
    expect(textareaDe).toBeTruthy();
    const autosize = textareaDe.injector.get(CdkTextareaAutosize);
    expect(autosize.minRows).toBe(3);
    expect(autosize.maxRows).toBe(10);
  });
});
