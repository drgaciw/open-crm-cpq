import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { LeadDetailComponent } from './lead-detail.component';
import { LeadService } from '../lead.service';
import { DealDto, LeadDto } from '@frontend/shared-models';

describe('LeadDetailComponent', () => {
  let fixture: ComponentFixture<LeadDetailComponent>;
  let component: LeadDetailComponent;
  let service: LeadService & {
    getById: ReturnType<typeof vi.fn>;
    markContacted: ReturnType<typeof vi.fn>;
    qualify: ReturnType<typeof vi.fn>;
    convertToDeal: ReturnType<typeof vi.fn>;
    markLost: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
    loading: ReturnType<typeof vi.fn>;
    selectedLead: ReturnType<typeof vi.fn> & { set: ReturnType<typeof vi.fn> };
  };
  let router: Router;
  let dialog: MatDialog & { open: ReturnType<typeof vi.fn> };

  const lead = (status: LeadDto['status']): LeadDto => ({
    id: 42,
    uuid: 'lead-42',
    title: 'Modernization project',
    status,
    estimatedValue: 42000,
    source: 'Website',
    createdAt: '2026-05-12T00:00:00Z',
    updatedAt: '2026-05-12T01:00:00Z',
  });

  beforeEach(async () => {
    service = {
      getById: vi.fn(),
      markContacted: vi.fn(),
      qualify: vi.fn(),
      convertToDeal: vi.fn(),
      markLost: vi.fn(),
      delete: vi.fn(),
      loading: vi.fn().mockReturnValue(false),
      selectedLead: Object.assign(vi.fn(), { set: vi.fn() }),
    } as unknown as typeof service;
    dialog = { open: vi.fn() } as unknown as typeof dialog;

    await TestBed.configureTestingModule({
      imports: [LeadDetailComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: LeadService, useValue: service },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LeadDetailComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.componentRef.setInput('id', '42');
  });

  it('loads the requested lead from the gateway-backed detail endpoint', () => {
    service.selectedLead.mockReturnValue(lead('NEW'));

    fixture.detectChanges();

    expect(service.getById).toHaveBeenCalledWith(42);
    expect(fixture.nativeElement.textContent).toContain('Modernization project');
    expect(fixture.nativeElement.textContent).toContain('Mark Contacted');
    expect(fixture.nativeElement.textContent).toContain('Mark Lost');
  });

  it('marks a new lead as contacted and updates selected lead state', () => {
    const contacted = { ...lead('CONTACTED'), firstContactedAt: '2026-05-12T02:00:00Z' };
    service.selectedLead.mockReturnValue(lead('NEW'));
    service.markContacted.mockReturnValue(of(contacted));

    component.onMarkContacted();

    expect(component.actionLoading()).toBe(false);
    expect(service.markContacted).toHaveBeenCalledWith(42);
    expect(service.selectedLead.set).toHaveBeenCalledWith(contacted);
  });

  it('qualifies a contacted lead and updates selected lead state', () => {
    const qualified = lead('QUALIFIED');
    service.selectedLead.mockReturnValue(lead('CONTACTED'));
    service.qualify.mockReturnValue(of(qualified));

    component.onQualify();

    expect(component.actionLoading()).toBe(false);
    expect(service.qualify).toHaveBeenCalledWith(42);
    expect(service.selectedLead.set).toHaveBeenCalledWith(qualified);
  });

  it('converts a qualified lead to a deal and navigates to the deal detail', () => {
    const deal: DealDto = {
      id: 77,
      uuid: 'deal-77',
      name: 'Modernization project',
      stage: 'PROSPECTING',
      sourceLeadId: 42,
      createdAt: '2026-05-12T02:00:00Z',
      updatedAt: '2026-05-12T02:00:00Z',
    };
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    service.selectedLead.mockReturnValue(lead('QUALIFIED'));
    service.convertToDeal.mockReturnValue(of(deal));

    component.onConvertToDeal();

    expect(component.actionLoading()).toBe(false);
    expect(service.convertToDeal).toHaveBeenCalledWith(42);
    expect(navigate).toHaveBeenCalledWith(['/deals', 77]);
  });

  it('does not convert a qualified lead when confirmation is cancelled', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    service.selectedLead.mockReturnValue(lead('QUALIFIED'));

    component.onConvertToDeal();

    expect(service.convertToDeal).not.toHaveBeenCalled();
    expect(component.actionLoading()).toBe(false);
  });

  it('marks a lead as lost with the dialog reason', () => {
    const lost = { ...lead('LOST'), lostReason: 'No budget' };
    service.selectedLead.mockReturnValue(lead('CONTACTED'));
    service.markLost.mockReturnValue(of(lost));
    dialog.open.mockReturnValue({ afterClosed: () => of('No budget') } as never);

    component.onMarkLost();

    expect(dialog.open).toHaveBeenCalled();
    expect(service.markLost).toHaveBeenCalledWith(42, 'No budget');
    expect(service.selectedLead.set).toHaveBeenCalledWith(lost);
    expect(component.actionLoading()).toBe(false);
  });

  it('resets action loading when a lifecycle request fails', () => {
    service.selectedLead.mockReturnValue(lead('NEW'));
    service.markContacted.mockReturnValue(throwError(() => new Error('gateway failed')));

    component.onMarkContacted();

    expect(component.actionLoading()).toBe(false);
  });
});
