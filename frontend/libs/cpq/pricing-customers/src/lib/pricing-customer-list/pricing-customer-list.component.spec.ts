import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { computed, signal } from '@angular/core';
import { of } from 'rxjs';
import { PricingCustomerListComponent } from './pricing-customer-list.component';
import { PricingCustomerCreateDialogComponent } from '../pricing-customer-create-dialog/pricing-customer-create-dialog.component';
import { MOCK_PRICING_CUSTOMERS } from '../pricing-customers.mock';
import { PricingCustomerService } from '@frontend/cpq-pricing-data-access';
import { PaginationMeta, PricingCustomerDto } from '@frontend/shared-models';

describe('PricingCustomerListComponent', () => {
  let fixture: ComponentFixture<PricingCustomerListComponent>;
  let component: PricingCustomerListComponent;
  let router: Router;
  let listSpy: ReturnType<typeof vi.fn>;
  let listBySegmentSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  const customers = signal<PricingCustomerDto[]>([...MOCK_PRICING_CUSTOMERS]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: MOCK_PRICING_CUSTOMERS.length,
    totalPages: 1,
  });

  beforeEach(async () => {
    listSpy = vi.fn(() => {
      customers.set([...MOCK_PRICING_CUSTOMERS]);
      pagination.set({
        page: 0,
        size: 20,
        totalElements: MOCK_PRICING_CUSTOMERS.length,
        totalPages: 1,
      });
    });
    listBySegmentSpy = vi.fn((segment: string) => {
      const next = MOCK_PRICING_CUSTOMERS.filter((c) => c.segment === segment);
      customers.set(next);
      pagination.set({
        page: 0,
        size: 20,
        totalElements: next.length,
        totalPages: Math.max(1, Math.ceil(next.length / 20) || 1),
      });
    });

    const serviceMock = {
      customers,
      loading,
      error,
      pagination,
      isEmpty: computed(() => customers().length === 0 && !loading()),
      list: listSpy,
      listBySegment: listBySegmentSpy,
      selectedCustomer: signal<PricingCustomerDto | null>(null),
    };

    dialogOpen = vi.fn().mockReturnValue({
      afterClosed: () => of(undefined),
    });

    await TestBed.configureTestingModule({
      imports: [PricingCustomerListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: PricingCustomerService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PricingCustomerListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    customers.set([...MOCK_PRICING_CUSTOMERS]);
    loading.set(false);
    error.set(null);
  });

  it('loads pricing customers via PricingCustomerService.list on init', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc');
    expect(compiled.textContent).toContain('Pricing customers');
    expect(compiled.textContent).toContain('/api/v1/pricing-customers');
    expect(component.tableRows().length).toBe(MOCK_PRICING_CUSTOMERS.length);
    expect(compiled.textContent).toContain('Acme Corp');
  });

  it('filters by segment via PricingCustomerService.listBySegment', () => {
    component.onSegmentChange('SMB');
    fixture.detectChanges();

    expect(listBySegmentSpy).toHaveBeenCalledWith('SMB', 0, 20);
    expect(component.tableRows().length).toBe(1);
    expect(component.tableRows()[0].name).toBe('Beta SMB');
  });

  it('shows empty state when filters match nothing', () => {
    listBySegmentSpy.mockImplementation(() => {
      customers.set([]);
      pagination.set({
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      });
    });
    component.onSegmentChange('STRATEGIC');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(component.isEmpty()).toBe(true);
    expect(compiled.textContent).toContain('No matching pricing customers');
  });

  it('shows empty state when there are no pricing customers', () => {
    customers.set([]);
    pagination.set({
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No pricing customers yet');
  });

  it('shows error banner when PricingCustomerService.error is set', () => {
    error.set('Failed to load pricing customers');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load pricing customers');
  });

  it('navigates to detail on row click', () => {
    component.onRowClick(component.tableRows()[0]);
    expect(router.navigate).toHaveBeenCalledWith(['/pricing-customers', 1]);
  });

  it('opens create dialog and navigates on success', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(MOCK_PRICING_CUSTOMERS[0]),
    });
    component.openCreateDialog();
    expect(dialogOpen).toHaveBeenCalledWith(
      PricingCustomerCreateDialogComponent,
      expect.objectContaining({ width: '560px', disableClose: true }),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/pricing-customers', 1]);
  });

  it('clears segment filter and reloads all', () => {
    component.onSegmentChange('ENTERPRISE');
    listSpy.mockClear();
    component.clearSegmentFilter();
    expect(component.segmentFilter()).toBe('');
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc');
  });
});
