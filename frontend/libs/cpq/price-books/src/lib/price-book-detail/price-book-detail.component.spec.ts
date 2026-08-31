import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { PriceBookDetailComponent } from './price-book-detail.component';
import {
  MOCK_PRICE_BOOKS,
  MOCK_PRICE_BOOK_ENTRIES,
  findMockPriceBook,
} from '../price-books.mock';
import { PriceBookService } from '@frontend/cpq-pricing-data-access';
import {
  PaginationMeta,
  PriceBookDto,
  PriceBookEntryDto,
} from '@frontend/shared-models';

describe('PriceBookDetailComponent', () => {
  let fixture: ComponentFixture<PriceBookDetailComponent>;
  let component: PriceBookDetailComponent;
  let router: Router;
  let getByIdSpy: ReturnType<typeof vi.fn>;
  let getEntriesSpy: ReturnType<typeof vi.fn>;

  const selectedPriceBook = signal<PriceBookDto | null>(MOCK_PRICE_BOOKS[0]);
  const entries = signal<PriceBookEntryDto[]>([...MOCK_PRICE_BOOK_ENTRIES]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const entriesPagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: MOCK_PRICE_BOOK_ENTRIES.length,
    totalPages: 1,
  });

  beforeEach(async () => {
    getByIdSpy = vi.fn((id: number) => {
      selectedPriceBook.set(findMockPriceBook(id) ?? null);
    });
    getEntriesSpy = vi.fn((bookId: number) => {
      entries.set(
        MOCK_PRICE_BOOK_ENTRIES.filter((e) => e.priceBookId === bookId),
      );
      entriesPagination.set({
        page: 0,
        size: 20,
        totalElements: entries().length,
        totalPages: 1,
      });
    });

    const serviceMock = {
      selectedPriceBook,
      entries,
      loading,
      error,
      entriesPagination,
      getById: getByIdSpy,
      getEntries: getEntriesSpy,
    };

    await TestBed.configureTestingModule({
      imports: [PriceBookDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: PriceBookService, useValue: serviceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PriceBookDetailComponent);
    fixture.componentRef.setInput('id', '1');
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    selectedPriceBook.set(MOCK_PRICE_BOOKS[0]);
    entries.set([...MOCK_PRICE_BOOK_ENTRIES]);
    loading.set(false);
    error.set(null);
  });

  it('loads book and entries via PriceBookService on init', () => {
    expect(getByIdSpy).toHaveBeenCalledWith(1);
    expect(getEntriesSpy).toHaveBeenCalledWith(1);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('North America Standard');
    expect(compiled.textContent).toContain('NA');
    expect(compiled.textContent).toContain('USD');
    expect(compiled.textContent).toContain('Entries');
    expect(compiled.textContent).toContain('CRM-SEAT-NAMED');
    expect(compiled.textContent).toContain('CPQ-FLAT');
  });

  it('shows empty entries state when book has no entries', () => {
    entries.set([]);
    entriesPagination.set({
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No entries');
  });

  it('shows not-found empty state when selected book is missing', () => {
    selectedPriceBook.set(null);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Price book not found');
  });

  it('navigates back to list', () => {
    component.goBack();
    expect(router.navigate).toHaveBeenCalledWith(['/price-books']);
  });

  it('paginates entries via getEntries', () => {
    component.onEntriesPageChange({
      pageIndex: 1,
      pageSize: 10,
      length: 20,
    });
    expect(getEntriesSpy).toHaveBeenCalledWith(1, 1, 10);
  });

  it('rejects invalid id without calling service loaders', async () => {
    getByIdSpy.mockClear();
    getEntriesSpy.mockClear();

    const invalidFixture = TestBed.createComponent(PriceBookDetailComponent);
    invalidFixture.componentRef.setInput('id', 'abc');
    invalidFixture.detectChanges();

    expect(getByIdSpy).not.toHaveBeenCalled();
    expect(getEntriesSpy).not.toHaveBeenCalled();
    expect(error()).toBe('Invalid price book id');
  });
});
