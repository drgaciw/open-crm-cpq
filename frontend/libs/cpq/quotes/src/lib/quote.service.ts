import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, finalize } from 'rxjs';
import {
  QuoteDto,
  QuoteCreateRequest,
  QuoteUpdateRequest,
  QuoteStatus,
  QuoteDealType,
  QuoteLineItemDto,
  LineItemCreateRequest,
  QuoteDocumentDto,
  DocumentType,
  PaginationMeta,
  SpringPageResponse,
} from '@frontend/shared-models';

/** H1-shaped banner text: `message [CODE]` when errorCode is present. */
function httpErrorMessage(err: unknown): string {
  const e = err as {
    errorCode?: string;
    error?: { message?: string; errorCode?: string };
    message?: string;
  } | null;
  const code = e?.errorCode || e?.error?.errorCode;
  const message = e?.error?.message || e?.message || 'An error occurred';
  if (code) {
    return `${message} [${code}]`;
  }
  return message;
}

/**
 * Quote HTTP service.
 * Signal-state pattern matches {@link DealService} (HttpClient + signals; not BaseCrudService).
 * Base path: `/api/v1/quotes` (quote-service via gateway).
 */
@Injectable({ providedIn: 'root' })
export class QuoteService {
  private readonly http = inject(HttpClient);
  private readonly basePath = '/api/v1/quotes';

  readonly quotes = signal<QuoteDto[]>([]);
  readonly selectedQuote = signal<QuoteDto | null>(null);
  readonly lineItems = signal<QuoteLineItemDto[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<PaginationMeta>({
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  });

  readonly isEmpty = computed(() => this.quotes().length === 0 && !this.loading());

  list(
    page = 0,
    size = 20,
    sort = 'createdAt,desc',
    filters?: {
      status?: QuoteStatus;
      dealType?: QuoteDealType;
      customerExternalId?: string;
    },
  ): void {
    this.error.set(null);
    this.loading.set(true);
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', sort);
    if (filters?.status) {
      params = params.set('status', filters.status);
    }
    if (filters?.dealType) {
      params = params.set('dealType', filters.dealType);
    }
    if (filters?.customerExternalId) {
      params = params.set('customerExternalId', filters.customerExternalId);
    }

    this.http
      .get<SpringPageResponse<QuoteDto>>(this.basePath, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (p) => {
          this.quotes.set(p.content ?? (p as unknown as QuoteDto[]));
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
          this.error.set(httpErrorMessage(err));
        },
      });
  }

  getById(id: number): void {
    this.error.set(null);
    this.selectedQuote.set(null);
    this.loading.set(true);
    this.http
      .get<QuoteDto>(`${this.basePath}/${id}`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (quote) => this.selectedQuote.set(quote),
        error: (err) => {
          this.error.set(httpErrorMessage(err));
        },
      });
  }

  getByQuoteNumber(quoteNumber: string): Observable<QuoteDto> {
    return this.http.get<QuoteDto>(
      `${this.basePath}/number/${encodeURIComponent(quoteNumber)}`,
    );
  }

  create(request: QuoteCreateRequest): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(this.basePath, request);
  }

  update(id: number, request: QuoteUpdateRequest): Observable<QuoteDto> {
    return this.http.put<QuoteDto>(`${this.basePath}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.basePath}/${id}`);
  }

  submit(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`${this.basePath}/${id}/submit`, {});
  }

  approve(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`${this.basePath}/${id}/approve`, {});
  }

  reject(id: number, reason?: string): Observable<QuoteDto> {
    let params = new HttpParams();
    if (reason) {
      params = params.set('reason', reason);
    }
    return this.http.post<QuoteDto>(`${this.basePath}/${id}/reject`, {}, { params });
  }

  send(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`${this.basePath}/${id}/send`, {});
  }

  accept(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`${this.basePath}/${id}/accept`, {});
  }

  expire(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`${this.basePath}/${id}/expire`, {});
  }

  cancel(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`${this.basePath}/${id}/cancel`, {});
  }

  cloneQuote(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`${this.basePath}/${id}/clone`, {});
  }

  calculate(id: number): Observable<QuoteDto> {
    return this.http.post<QuoteDto>(`${this.basePath}/${id}/calculate`, {});
  }

  listLineItems(quoteId: number): void {
    this.error.set(null);
    this.loading.set(true);
    this.http
      .get<QuoteLineItemDto[]>(`${this.basePath}/${quoteId}/line-items`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (items) => this.lineItems.set(items ?? []),
        error: (err) => {
          this.error.set(err?.error?.message || err?.message || 'An error occurred');
        },
      });
  }

  addLineItem(quoteId: number, request: LineItemCreateRequest): Observable<QuoteLineItemDto> {
    return this.http.post<QuoteLineItemDto>(
      `${this.basePath}/${quoteId}/line-items`,
      request,
    );
  }

  updateLineItem(
    quoteId: number,
    lineItemId: number,
    request: LineItemCreateRequest,
  ): Observable<QuoteLineItemDto> {
    return this.http.put<QuoteLineItemDto>(
      `${this.basePath}/${quoteId}/line-items/${lineItemId}`,
      request,
    );
  }

  removeLineItem(quoteId: number, lineItemId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.basePath}/${quoteId}/line-items/${lineItemId}`,
    );
  }

  /**
   * Generate a quote PDF document.
   * POST /api/v1/quotes/:id/documents/generate?type= (default PROPOSAL).
   */
  generateDocuments(
    quoteId: number,
    type: DocumentType = 'PROPOSAL',
  ): Observable<QuoteDocumentDto> {
    const params = new HttpParams().set('type', type);
    return this.http.post<QuoteDocumentDto>(
      `${this.basePath}/${quoteId}/documents/generate`,
      {},
      { params },
    );
  }

  /** List document metadata for a quote. */
  listDocuments(quoteId: number): Observable<QuoteDocumentDto[]> {
    return this.http.get<QuoteDocumentDto[]>(
      `${this.basePath}/${quoteId}/documents`,
    );
  }

  /**
   * Download raw PDF bytes (not ApiResponse).
   * MUST use responseType blob — interceptor passes non-envelope bodies through.
   */
  downloadDocument(quoteId: number, documentId: number): Observable<Blob> {
    return this.http.get(
      `${this.basePath}/${quoteId}/documents/${documentId}/download`,
      { responseType: 'blob' },
    );
  }
}
