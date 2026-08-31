import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import {
  PriceBookDetailComponent,
  isPriceBookPublished,
} from './price-book-detail.component';
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
import { ConfirmDialogComponent } from '@frontend/shared-ui';
import { PriceBookEntryDialogComponent } from '../price-book-entry-dialog/price-book-entry-dialog.component';
import { PriceBookEditDialogComponent } from '../price-book-edit-dialog/price-book-edit-dialog.component';

describe('isPriceBookPublished', () => {
  const today = new Date('2026-08-30T12:00:00Z');

  it('is false when effectiveDate is missing', () => {
    expect(isPriceBookPublished({ effectiveDate: undefined }, today)).toBe(
      false,
    );
  });

  it('is true when effectiveDate is today or earlier', () => {
    expect(
      isPriceBookPublished({ effectiveDate: '2026-08-30' }, today),
    ).toBe(true);
    expect(
      isPriceBookPublished({ effectiveDate: '2026-01-01' }, today),
    ).toBe(true);
  });

  it('is false when effectiveDate is after today', () => {
    expect(
      isPriceBookPublished({ effectiveDate: '2026-09-01' }, today),
    ).toBe(false);
  });
});

describe('PriceBookDetailComponent', () => {
  let fixture: ComponentFixture<PriceBookDetailComponent>;
  let component: PriceBookDetailComponent;
  let router: Router;
  let getByIdSpy: ReturnType<typeof vi.fn>;
  let getEntriesSpy: ReturnType<typeof vi.fn>;
  let updateSpy: ReturnType<typeof vi.fn>;
  let deleteSpy: ReturnType<typeof vi.fn>;
  let createEntrySpy: ReturnType<typeof vi.fn>;
  let updateEntrySpy: ReturnType<typeof vi.fn>;
  let deleteEntrySpy: ReturnType<typeof vi.fn>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  /** Unpublished mock (no effectiveDate) — write-enabled. */
  const unpublishedBook = MOCK_PRICE_BOOKS[2];
  /** Published mock (effectiveDate in the past relative to 2026-08-30). */
  const publishedBook = MOCK_PRICE_BOOKS[0];

  const selectedPriceBook = signal<PriceBookDto | null>(unpublishedBook);
  const entries = signal<PriceBookEntryDto[]>([
    {
      id: 21,
      uuid: 'pbe-21',
      priceBookId: unpublishedBook.id,
      productExternalId: 'APAC-SKU',
      listPrice: 50,
      floorPrice: 40,
      currency: 'USD',
      createdAt: '',
      updatedAt: '',
    },
  ]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const entriesPagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  });

  beforeEach(async () => {
    getByIdSpy = vi.fn((id: number) => {
      selectedPriceBook.set(findMockPriceBook(id) ?? null);
    });
    getEntriesSpy = vi.fn((bookId: number) => {
      if (bookId === unpublishedBook.id) {
        entries.set([
          {
            id: 21,
            uuid: 'pbe-21',
            priceBookId: unpublishedBook.id,
            productExternalId: 'APAC-SKU',
            listPrice: 50,
            floorPrice: 40,
            currency: 'USD',
            createdAt: '',
            updatedAt: '',
          },
        ]);
      } else {
        entries.set(
          MOCK_PRICE_BOOK_ENTRIES.filter((e) => e.priceBookId === bookId),
        );
      }
      entriesPagination.set({
        page: 0,
        size: 20,
        totalElements: entries().length,
        totalPages: 1,
      });
    });
    updateSpy = vi.fn();
    deleteSpy = vi.fn().mockReturnValue(of(void 0));
    createEntrySpy = vi.fn();
    updateEntrySpy = vi.fn();
    deleteEntrySpy = vi.fn().mockReturnValue(of(void 0));
    dialogOpen = vi.fn().mockReturnValue({
      afterClosed: () => of(undefined),
    });

    const serviceMock = {
      selectedPriceBook,
      entries,
      loading,
      error,
      entriesPagination,
      getById: getByIdSpy,
      getEntries: getEntriesSpy,
      update: updateSpy,
      delete: deleteSpy,
      createEntry: createEntrySpy,
      updateEntry: updateEntrySpy,
      deleteEntry: deleteEntrySpy,
    };

    await TestBed.configureTestingModule({
      imports: [PriceBookDetailComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: PriceBookService, useValue: serviceMock },
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PriceBookDetailComponent);
    fixture.componentRef.setInput('id', String(unpublishedBook.id));
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    selectedPriceBook.set(unpublishedBook);
    entries.set([
      {
        id: 21,
        uuid: 'pbe-21',
        priceBookId: unpublishedBook.id,
        productExternalId: 'APAC-SKU',
        listPrice: 50,
        floorPrice: 40,
        currency: 'USD',
        createdAt: '',
        updatedAt: '',
      },
    ]);
    loading.set(false);
    error.set(null);
  });

  it('loads book and entries via PriceBookService on init', () => {
    expect(getByIdSpy).toHaveBeenCalledWith(unpublishedBook.id);
    expect(getEntriesSpy).toHaveBeenCalledWith(unpublishedBook.id);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('APAC Promo');
    expect(compiled.textContent).toContain('Entries');
    expect(compiled.textContent).toContain('APAC-SKU');
    expect(compiled.textContent).toContain('Add entry');
  });

  it('enables header Edit/Delete and entry Add/Edit/Delete when unpublished', () => {
    expect(component.canEditEntries()).toBe(true);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(
      compiled.querySelector('.edit-book-btn')?.hasAttribute('disabled'),
    ).toBe(false);
    expect(
      compiled.querySelector('.delete-book-btn')?.hasAttribute('disabled'),
    ).toBe(false);
    expect(compiled.querySelector('.add-entry-btn')?.hasAttribute('disabled')).toBe(
      false,
    );
    expect(compiled.textContent).toContain('Edit');
    expect(compiled.textContent).toContain('Delete');
    expect(compiled.textContent).toContain('Draft (editable)');
  });

  it('disables header and entry writes when book is published', () => {
    selectedPriceBook.set(publishedBook);
    entries.set([...MOCK_PRICE_BOOK_ENTRIES]);
    fixture.componentRef.setInput('id', String(publishedBook.id));
    fixture.detectChanges();

    expect(component.canEditEntries()).toBe(false);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(
      compiled.querySelector('.edit-book-btn')?.hasAttribute('disabled'),
    ).toBe(true);
    expect(
      compiled.querySelector('.delete-book-btn')?.hasAttribute('disabled'),
    ).toBe(true);
    expect(
      compiled.querySelector('.add-entry-btn')?.hasAttribute('disabled'),
    ).toBe(true);
    expect(compiled.querySelector('.row-edit-btn')).toBeNull();
    expect(compiled.querySelector('.row-delete-btn')).toBeNull();
    expect(compiled.textContent).toContain('Published (read-only)');
    expect(compiled.textContent).toContain('immutable');
  });

  it('opens edit dialog and reloads getById after save', () => {
    const saved: PriceBookDto = {
      ...unpublishedBook,
      name: 'APAC Promo Renamed',
    };
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(saved),
    });
    getByIdSpy.mockClear();

    component.openEditBook();

    expect(dialogOpen).toHaveBeenCalledWith(
      PriceBookEditDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          book: expect.objectContaining({ id: unpublishedBook.id }),
        }),
      }),
    );
    expect(getByIdSpy).toHaveBeenCalledWith(unpublishedBook.id);
  });

  it('deletes book after ConfirmDialog and navigates to list', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(true),
    });

    component.confirmDeleteBook();

    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          title: 'Delete price book',
          confirmText: 'Delete',
          confirmColor: 'warn',
        }),
      }),
    );
    expect(deleteSpy).toHaveBeenCalledWith(unpublishedBook.id);
    expect(router.navigate).toHaveBeenCalledWith(['/price-books']);
  });

  it('does not delete book when ConfirmDialog is cancelled', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(false),
    });

    component.confirmDeleteBook();

    expect(deleteSpy).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('surfaces PRICE_BOOK_PUBLISHED_IMMUTABLE on book delete failure', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(true),
    });
    deleteSpy.mockReturnValueOnce(
      throwError(() => ({
        error: {
          message: 'Published price books are immutable',
          errorCode: 'PRICE_BOOK_PUBLISHED_IMMUTABLE',
        },
      })),
    );

    component.confirmDeleteBook();
    fixture.detectChanges();

    expect(component.bannerError()).toContain('PRICE_BOOK_PUBLISHED_IMMUTABLE');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('PRICE_BOOK_PUBLISHED_IMMUTABLE');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('does not open edit dialog when published', () => {
    selectedPriceBook.set(publishedBook);
    fixture.componentRef.setInput('id', String(publishedBook.id));
    fixture.detectChanges();
    dialogOpen.mockClear();

    component.openEditBook();
    expect(dialogOpen).not.toHaveBeenCalled();
  });

  it('opens create entry dialog and reloads getEntries after save', () => {
    const saved: PriceBookEntryDto = {
      id: 99,
      uuid: 'u-99',
      priceBookId: unpublishedBook.id,
      productExternalId: 'NEW',
      listPrice: 10,
      createdAt: '',
      updatedAt: '',
    };
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(saved),
    });
    getEntriesSpy.mockClear();

    component.openCreateEntry();

    expect(dialogOpen).toHaveBeenCalledWith(
      PriceBookEntryDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          bookId: unpublishedBook.id,
          defaultCurrency: 'USD',
        }),
      }),
    );
    expect(getEntriesSpy).toHaveBeenCalledWith(unpublishedBook.id, 0, 20);
  });

  it('opens edit entry dialog for a row', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(undefined),
    });

    component.openEditEntry({
      id: 21,
      productExternalId: 'APAC-SKU',
      listPrice: 50,
      floorPrice: 40,
      currency: 'USD',
    });

    expect(dialogOpen).toHaveBeenCalledWith(
      PriceBookEntryDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          bookId: unpublishedBook.id,
          entry: expect.objectContaining({ id: 21 }),
        }),
      }),
    );
  });

  it('deletes entry after ConfirmDialog and reloads getEntries', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(true),
    });
    getEntriesSpy.mockClear();

    component.confirmDeleteEntry({
      id: 21,
      productExternalId: 'APAC-SKU',
      listPrice: 50,
      floorPrice: 40,
      currency: 'USD',
    });

    expect(dialogOpen).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({
        data: expect.objectContaining({
          title: 'Delete entry',
          confirmText: 'Delete',
          confirmColor: 'warn',
        }),
      }),
    );
    expect(deleteEntrySpy).toHaveBeenCalledWith(unpublishedBook.id, 21);
    expect(getEntriesSpy).toHaveBeenCalledWith(unpublishedBook.id, 0, 20);
  });

  it('does not delete when ConfirmDialog is cancelled', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(false),
    });

    component.confirmDeleteEntry({
      id: 21,
      productExternalId: 'APAC-SKU',
      listPrice: 50,
      floorPrice: 40,
      currency: 'USD',
    });

    expect(deleteEntrySpy).not.toHaveBeenCalled();
  });

  it('surfaces PRICE_BOOK_PUBLISHED_IMMUTABLE on delete failure', () => {
    dialogOpen.mockReturnValueOnce({
      afterClosed: () => of(true),
    });
    deleteEntrySpy.mockReturnValueOnce(
      throwError(() => ({
        error: {
          message: 'Published price books and their entries are immutable',
          errorCode: 'PRICE_BOOK_PUBLISHED_IMMUTABLE',
        },
      })),
    );

    component.confirmDeleteEntry({
      id: 21,
      productExternalId: 'APAC-SKU',
      listPrice: 50,
      floorPrice: 40,
      currency: 'USD',
    });
    fixture.detectChanges();

    expect(component.bannerError()).toContain('PRICE_BOOK_PUBLISHED_IMMUTABLE');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('PRICE_BOOK_PUBLISHED_IMMUTABLE');
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
    expect(getEntriesSpy).toHaveBeenCalledWith(unpublishedBook.id, 1, 10);
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

  it('does not open create dialog when published', () => {
    selectedPriceBook.set(publishedBook);
    fixture.componentRef.setInput('id', String(publishedBook.id));
    fixture.detectChanges();
    dialogOpen.mockClear();

    component.openCreateEntry();
    expect(dialogOpen).not.toHaveBeenCalled();
  });
});
