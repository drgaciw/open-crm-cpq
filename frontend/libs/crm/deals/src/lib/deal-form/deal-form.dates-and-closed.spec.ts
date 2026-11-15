import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Subject } from 'rxjs';
import { DealFormComponent } from './deal-form.component';
import { DealService } from '../deal.service';
import { OrganizationService } from '@frontend/crm-organizations';
import { ContactService } from '@frontend/crm-contacts';
import { ContactDto, DealDto, OrganizationDto } from '@frontend/shared-models';

@Component({ standalone: true, template: '' })
class RouterStubComponent {}

const ACME = { id: 3, name: 'Acme Corp' } as OrganizationDto;
const GLOBEX = { id: 7, name: 'Globex' } as OrganizationDto;
const ADA = { id: 5, firstName: 'Ada', lastName: 'Lovelace' } as ContactDto;

describe('DealFormComponent closed deals and calendar dates', () => {
  let fixture: ComponentFixture<DealFormComponent>;
  let component: DealFormComponent;
  let dealService: {
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    getById: ReturnType<typeof vi.fn>;
    selectedDeal: ReturnType<typeof signal<DealDto | null>>;
    error: ReturnType<typeof signal<string | null>>;
  };

  async function setup(id?: string): Promise<void> {
    const save = new Subject<DealDto>();
    dealService = {
      create: vi.fn(() => save.asObservable()),
      update: vi.fn(() => save.asObservable()),
      getById: vi.fn(),
      selectedDeal: signal<DealDto | null>(null),
      error: signal<string | null>(null),
    };
    await TestBed.configureTestingModule({
      imports: [DealFormComponent],
      providers: [
        provideRouter([
          { path: 'deals/:id', component: RouterStubComponent },
          { path: 'deals', component: RouterStubComponent },
        ]),
        provideNoopAnimations(),
        { provide: DealService, useValue: dealService },
        { provide: OrganizationService, useValue: { organizations: signal([ACME, GLOBEX]), loading: signal(false), search: vi.fn() } },
        { provide: ContactService, useValue: { contacts: signal([ADA]), loading: signal(false), search: vi.fn() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DealFormComponent);
    component = fixture.componentInstance;
    if (id) fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
  }

  const control = (name: string) => component.form.controls[name];
  function loadDeal(partial: Partial<DealDto>): void {
    dealService.selectedDeal.set({
      id: 12, name: 'Expansion', stage: 'PROSPECTING', currency: 'USD', type: 'NEW_BUSINESS', ...partial,
    } as DealDto);
    fixture.detectChanges();
  }

  it('edit form of a closed opportunity locks stage, amount and currency (H-CRM-040)', async () => {
    await setup('12');
    loadDeal({ stage: 'LOST', amount: 5000, currency: 'USD', description: 'Lost on price' });
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('This opportunity is closed. Its stage, amount and currency can no longer change.');
    for (const name of ['stage', 'amount', 'currency']) expect(control(name).disabled).toBe(true);
    expect((el.querySelector('button[type="submit"]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('saving a closed opportunity sends neither stage nor amount nor currency, which the API refuses (H-CRM-040)', async () => {
    await setup('12');
    loadDeal({ stage: 'LOST', amount: 5000, currency: 'USD' });
    await fixture.whenStable();
    control('description').setValue('Post-mortem note');
    component.onSubmit();
    expect(dealService.update).toHaveBeenCalledTimes(1);
    const sent = dealService.update.mock.calls[0][1] as Record<string, unknown>;
    expect(sent['description']).toBe('Post-mortem note');
    for (const key of ['stage', 'amount', 'currency']) expect(sent[key]).toBeUndefined();
  });

  it('loads the stored close date as local midnight of that day (H-CRM-041)', async () => {
    await setup('12');
    loadDeal({ expectedCloseDate: '2027-06-30' });
    await fixture.whenStable();
    expect((control('expectedCloseDate').value as Date).getTime()).toBe(new Date(2027, 5, 30).getTime());
  });

  it('saves the picked close date by its local calendar day (H-CRM-021)', async () => {
    await setup();
    control('name').setValue('Auckland deal');
    // A datepicker value at local midnight, 15 March, in Pacific/Auckland: its UTC day is the 14th.
    const picked = new Date(2027, 2, 15);
    picked.toISOString = () => '2027-03-14T11:00:00.000Z';
    control('expectedCloseDate').setValue(picked);
    component.onSubmit();
    expect(dealService.create).toHaveBeenCalledWith(expect.objectContaining({ expectedCloseDate: '2027-03-15' }));
  });
});
