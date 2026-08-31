import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { computed, signal } from '@angular/core';
import { of } from 'rxjs';
import { PriceBookListComponent } from './price-book-list.component';
import { PriceBookCreateDialogComponent } from '../price-book-create-dialog/price-book-create-dialog.component';
import { MOCK_PRICE_BOOKS } from '../price-books.mock';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';
import { PaginationMeta, PriceBookDto } from '@frontend/shared-models';

describe('PriceBookListComponent', () => {
  let fixture: ComponentFixture<PriceBookListComponent>;
  let component: PriceBookListComponent;
  let router: Router;
  let listSpy: ReturnType<typeof vi.fn>;
  let listByRegionSpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  const priceBooks = signal<PriceBookDto[]>([...MOCK_PRICE_BOOKS]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: MOCK_PRICE_BOOKS.length,
    totalPages: 1,
  });

  beforeEach(async () => {
    listSpy = vi.fn(() => {
      priceBooks.set([...MOCK_PRICE_BOOKS]);
      pagination.set({
        page: 0,
        size: 20,
        totalElements: MOCK_PRICE_BOOKS.length,
        totalPages: 1,
      });
    });
    listByRegionSpy = vi.fn((region: string) => {
      const next = MOCK_PRICE_BOOKS.filter((b) => b.region === region);
      priceBooks.set(next);
      pagination.set({
        page: 0,
        size: 20,
        totalElements: next.length,
        totalPages: Math.max(1, Math.ceil(next.length / 20) || 1),
      });
    });

    const serviceMock = {
      priceBooks,
      loading,
      error,
      pagination,
      isEmpty: computed(() => priceBooks().length === 0 && !loading()),
      list: listSpy,
      listByRegion: listByRegionSpy,
      selectedPriceBook: signal<PriceBookDto | null>(null),
    };

    dialogOpen = vi.fn().mockReturnValue({
      afterClosed: () => of(undefined),
    });

    await TestBed.configureTestingModule({
      imports: [PriceBookListComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: PriceBookService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PriceBookListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    priceBooks.set([...MOCK_PRICE_BOOKS]);
    loading.set(false);
    error.set(null);
  });

  it('loads price books via PriceBookService.list on init', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(listSpy).toHaveBeenCalledWith(0, 20, 'name,asc');
    expect(compiled.textContent).toContain('Price Books');
    expect(compiled.textContent).toContain(
      'Browse price books from the pricebook-service API',
    );
    expect(component.tableRows().length).toBe(MOCK_PRICE_BOOKS.length);
    expect(compiled.textContent).toContain('North America Standard');
  });

  it('filters by region via PriceBookService.listByRegion', () => {
    component.regionFilter.set('EMEA');
    component.applyRegionFilter();
    fixture.detectChanges();

    expect(listByRegionSpy).toHaveBeenCalledWith('EMEA', 0, 20);
    expect(component.tableRows().length).toBe(1);
    expect(component.tableRows()[0].name).toBe('EMEA Enterprise');
  });

  it('shows empty state when filters match nothing', () => {
    listByRegionSpy.mockImplementation(() => {
      priceBooks.set([]);
      pagination.set({
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      });
    });
    component.regionFilter.set('ZZZ');
    component.applyRegionFilter();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(component.isEmpty()).toBe(true);
    expect(compiled.textContent).toContain('No matching price books');
  });

  it('shows empty state when there are no price books', () => {
    priceBooks.set([]);
    pagination.set({
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No price books yet');
  });

  it('shows error banner when PriceBookService.error is set', () => {
    error.set('Failed to load price books');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load price books');

    component.clearError();
    fixture.detectChanges();
    expect(error()).toBeNull();
  });

  it('navigates to detail on row click', () => {
    component.onRowClick({
      id: 2,
      name: 'EMEA Enterprise',
      region: 'EMEA',
      currency: 'EUR',
      isDefault: 'NO',
      effectiveDate: '2026-03-01',
      expirationDate: '—',
    });

    expect(router.navigate).toHaveBeenCalledWith(['/price-books', 2]);
  });

  it('opens create dialog for New Price Book', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const button = Array.from(compiled.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('New Price Book'),
    ) as HTMLButtonElement | undefined;
    expect(button).toBeTruthy();

    button?.click();
    expect(dialogOpen).toHaveBeenCalledWith(
      PriceBookCreateDialogComponent,
      expect.objectContaining({ width: '520px', disableClose: true }),
    );
  });

  it('navigates to detail after successful create dialog close', () => {
    dialogOpen.mockReturnValue({
      afterClosed: () =>
        of({
          id: 99,
          uuid: 'u-99',
          name: 'Created Book',
          createdAt: '',
          updatedAt: '',
        } satisfies PriceBookDto),
    });

    component.openCreateDialog();

    expect(router.navigate).toHaveBeenCalledWith(['/price-books', 99]);
  });

  it('does not navigate when create dialog is cancelled', () => {
    dialogOpen.mockReturnValue({ afterClosed: () => of(undefined) });
    component.openCreateDialog();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('uses shared-ui data table', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-data-table')).toBeTruthy();
    expect(compiled.querySelector('app-page-header')).toBeTruthy();
  });
});
