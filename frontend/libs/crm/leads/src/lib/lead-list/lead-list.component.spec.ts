import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal, computed } from '@angular/core';
import { of, throwError } from 'rxjs';
import { LeadListComponent } from './lead-list.component';
import { LeadService } from '../lead.service';
import { LeadDto, PaginationMeta } from '@frontend/shared-models';

function lead(partial: Partial<LeadDto> & Pick<LeadDto, 'id' | 'title' | 'status'>): LeadDto {
  return {
    uuid: `u-${partial.id}`,
    priority: 'MEDIUM',
    createdAt: '',
    updatedAt: '',
    ...partial,
  };
}

describe('LeadListComponent action wiring', () => {
  let fixture: ComponentFixture<LeadListComponent>;
  let component: LeadListComponent;
  let router: Router;
  let listSpy: ReturnType<typeof vi.fn>;
  let qualifySpy: ReturnType<typeof vi.fn>;
  let convertSpy: ReturnType<typeof vi.fn>;
  let markContactedSpy: ReturnType<typeof vi.fn>;
  let markLostSpy: ReturnType<typeof vi.fn>;

  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  });
  const leads = signal<LeadDto[]>([]);
  const loading = signal(false);

  beforeEach(async () => {
    listSpy = vi.fn();
    qualifySpy = vi.fn(() => of(lead({ id: 2, title: 'Q', status: 'QUALIFIED' })));
    convertSpy = vi.fn(() =>
      of(lead({ id: 3, title: 'C', status: 'CONVERTED', convertedDealId: 77 })),
    );
    markContactedSpy = vi.fn(() => of(lead({ id: 1, title: 'N', status: 'CONTACTED' })));
    markLostSpy = vi.fn(() => of(lead({ id: 1, title: 'L', status: 'LOST' })));

    const serviceMock = {
      leads,
      loading,
      pagination,
      isEmpty: computed(() => leads().length === 0 && !loading()),
      list: listSpy,
      search: vi.fn(),
      delete: vi.fn(() => of(void 0)),
      markContacted: markContactedSpy,
      qualify: qualifySpy,
      convertToDeal: convertSpy,
      markLost: markLostSpy,
    };

    await TestBed.configureTestingModule({
      imports: [LeadListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: LeadService, useValue: serviceMock },
        {
          provide: MatDialog,
          useValue: {
            open: vi.fn(() => ({ afterClosed: () => of(undefined) })),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LeadListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('ngOnInit loads the lead list', () => {
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'createdAt,desc', undefined, undefined);
  });

  it('onQualify calls LeadService.qualify and refreshes the list', () => {
    listSpy.mockClear();
    const contacted = lead({ id: 5, title: 'Warm lead', status: 'CONTACTED' });

    component.onQualify(contacted);

    expect(qualifySpy).toHaveBeenCalledWith(5);
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'createdAt,desc', undefined, undefined);
    expect(component.actionLoading()).toBe(false);
  });

  it('onConvert calls convertToDeal and navigates via convertedDealId', () => {
    listSpy.mockClear();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const qualified = lead({ id: 9, title: 'Ready', status: 'QUALIFIED' });

    component.onConvert(qualified);

    expect(confirmSpy).toHaveBeenCalled();
    expect(convertSpy).toHaveBeenCalledWith(9);
    expect(router.navigate).toHaveBeenCalledWith(['/deals', 77]);
    expect(component.actionLoading()).toBe(false);
  });

  it('onConvert does nothing when confirm is cancelled', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    component.onConvert(lead({ id: 9, title: 'Ready', status: 'QUALIFIED' }));

    expect(convertSpy).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('onConvert refreshes list when convertedDealId is missing', () => {
    convertSpy.mockReturnValueOnce(
      of(lead({ id: 9, title: 'Ready', status: 'CONVERTED', convertedDealId: undefined })),
    );
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    listSpy.mockClear();

    component.onConvert(lead({ id: 9, title: 'Ready', status: 'QUALIFIED' }));

    expect(router.navigate).not.toHaveBeenCalledWith(['/deals', expect.anything()]);
    expect(listSpy).toHaveBeenCalled();
  });

  it('onMarkContacted wires LeadService.markContacted', () => {
    listSpy.mockClear();
    component.onMarkContacted(lead({ id: 1, title: 'New', status: 'NEW' }));

    expect(markContactedSpy).toHaveBeenCalledWith(1);
    expect(listSpy).toHaveBeenCalled();
  });

  it('status-gates lost/edit helpers for lifecycle menu', () => {
    expect(component.canMarkLost('NEW')).toBe(true);
    expect(component.canMarkLost('CONTACTED')).toBe(true);
    expect(component.canMarkLost('QUALIFIED')).toBe(true);
    expect(component.canMarkLost('UNQUALIFIED')).toBe(false);
    expect(component.canMarkLost('CONVERTED')).toBe(false);
    expect(component.canEdit('CONVERTED')).toBe(false);
    expect(component.canDelete('CONVERTED')).toBe(false);
  });

  it('Unqualified tab passes UNQUALIFIED status to list', () => {
    listSpy.mockClear();
    // Tab order: All, New, Contacted, Qualified, Unqualified(4), Converted, Lost
    component.onTabChange(4);

    expect(listSpy).toHaveBeenCalledWith(0, 20, 'createdAt,desc', 'UNQUALIFIED', undefined);
  });

  it('priority filter forwards priority query to list', () => {
    listSpy.mockClear();
    component.onPriorityChange('HIGH');

    expect(listSpy).toHaveBeenCalledWith(0, 20, 'createdAt,desc', undefined, 'HIGH');
  });

  it('clears actionLoading when qualify fails', () => {
    qualifySpy.mockReturnValueOnce(throwError(() => new Error('fail')));
    component.onQualify(lead({ id: 5, title: 'Warm', status: 'CONTACTED' }));
    expect(component.actionLoading()).toBe(false);
  });
});
