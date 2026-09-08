import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import {
  PriceBookDto,
  PriceBookCreateRequest,
  PriceBookUpdateRequest,
  PriceBookEntryDto,
  PriceBookEntryCreateRequest,
  PriceBookEntryUpdateRequest,
  PaginationMeta,
  SpringPageResponse,
} from '@frontend/shared-models';

/** Prefer message; append errorCode when present (H1 shape). */
function httpErrorMessage(err: unknown, fallback: string): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || fallback;
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
}

/**
 * Price book HTTP service.
 * Signal-state pattern matches DealService (not BaseCrudService).
 * Base path: `/api/v1/price-books` (pricebook-service via gateway).
 */
@Injectable({ providedIn: 'root' })
export class PriceBookService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/price-books';

  readonly priceBooks = signal<PriceBookDto[]>([]);
  readonly selectedPriceBook = signal<PriceBookDto | null>(null);
  readonly entries = signal<PriceBookEntryDto[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });
  readonly entriesPagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });

  readonly isEmpty = computed(() => this.priceBooks().length === 0 && !this.loading());

  list(page = 0, size = 20, sort = 'name,asc', region?: string): void {
    this.error.set(null);
    this.loading.set(true);
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    if (region) {
      params = params.set('region', region);
    }

    this.http
      .get<SpringPageResponse<PriceBookDto>>(this.basePath, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.priceBooks.set(p.content ?? (p as unknown as PriceBookDto[]));
          if (p.totalElements !== undefined) {
            this.pagination.set({
              page: p.number ?? 0,
              size: p.size ?? size,
              totalElements: p.totalElements ?? 0,
              totalPages: p.totalPages ?? 0,
            });
          }
        },
        error: (err) => {
          this.error.set(httpErrorMessage(err, 'An error occurred'));
        },
      });
  }

  listByRegion(region: string, page = 0, size = 20): void {
    this.list(page, size, 'name,asc', region);
  }

  getById(id: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<PriceBookDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (book) => this.selectedPriceBook.set(book),
        error: (err) => {
          this.error.set(httpErrorMessage(err, 'An error occurred'));
        },
      });
  }

  create(request: PriceBookCreateRequest): Observable<PriceBookDto> {
    return this.http.post<PriceBookDto>(this.basePath, request);
  }

  update(id: number, request: PriceBookUpdateRequest): Observable<PriceBookDto> {
    return this.http.put<PriceBookDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  getEntries(bookId: number, page = 0, size = 20): void {
    this.error.set(null);
    this.loading.set(true);
    const params = new HttpParams().set('page', page).set('size', size);
    this.http
      .get<SpringPageResponse<PriceBookEntryDto>>(`${this.basePath}/${bookId}/entries`, {
        params,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.entries.set(p.content ?? (p as unknown as PriceBookEntryDto[]));
          if (p.totalElements !== undefined) {
            this.entriesPagination.set({
              page: p.number ?? 0,
              size: p.size ?? size,
              totalElements: p.totalElements ?? 0,
              totalPages: p.totalPages ?? 0,
            });
          }
        },
        error: (err) => {
          this.error.set(httpErrorMessage(err, 'An error occurred'));
        },
      });
  }

  getEntry(bookId: number, entryId: number): Observable<PriceBookEntryDto> {
    return this.http.get<PriceBookEntryDto>(
      `${this.basePath}/${bookId}/entries/${entryId}`,
    );
  }

  createEntry(
    bookId: number,
    request: PriceBookEntryCreateRequest,
  ): Observable<PriceBookEntryDto> {
    return this.http.post<PriceBookEntryDto>(
      `${this.basePath}/${bookId}/entries`,
      request,
    );
  }

  updateEntry(
    bookId: number,
    entryId: number,
    request: PriceBookEntryUpdateRequest,
  ): Observable<PriceBookEntryDto> {
    return this.http.put<PriceBookEntryDto>(
      `${this.basePath}/${bookId}/entries/${entryId}`,
      request,
    );
  }

  deleteEntry(bookId: number, entryId: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${bookId}/entries/${entryId}`);
  }
}
